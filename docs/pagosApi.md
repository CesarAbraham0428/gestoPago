# Flujo de pagos de la API con GestoPago

Documenta cómo la API de Spring Boot procesa `POST /pagos`: validaciones, cuándo se descuenta el saldo de la cuenta del cliente, cómo responde ante fallos y qué mecanismos garantizan la consistencia. Para el contrato de los endpoints externos ver [pagosGestoPago.md](pagosGestoPago.md).

## 1. Componentes

| Componente | Responsabilidad |
|---|---|
| `PagoController` | `POST /pagos`. Aplica el límite de solicitudes, recibe el `PagoServicioRequest` validado y el id del usuario autenticado. |
| `LimitePagosService` | Rate limiting por usuario: contador en Redis con ventana fija de 60 s. |
| `PagoServiceImpl` | **Orquestador**. Resuelve el producto, arma y firma la petición, llama a GestoPago y decide el desenlace. **No es transaccional**. |
| `PinAutorizacionService` | Verifica el PIN y lo bloquea 15 min tras 5 intentos fallidos. Lo usan el pago y el cambio de PIN. |
| `PagoRegistroService` | Pasos transaccionales sobre BD (`reservar`, `aprobar`, `revertir`, `marcarEnRevision`, `confirmarAplicado`, `confirmarNoAplicado`). Cada método es una transacción corta. |
| `GestoPagoPagosClient` | Llama a `sendTx.do`, `confirmTx.do` y `verifyReference.do` (Feign), interpreta el XML y clasifica los errores en *seguros* o *inciertos*. |
| `PagoConciliacionScheduler` | Cada 60 s llama a `confirmTx` para los pagos que quedaron sin resultado. |
| `CuentaRepository` | Consultas con bloqueo pesimista de la fila de la cuenta. |
| `PagoRepository` | Guarda el pago; `upc` es `UNIQUE`; consulta de pagos por conciliar. |
| `CifradoTokenConverter` | Cifra con AES-256-GCM el token Bearer de GestoPago al guardarlo en `gestopago_tokens`. |

## 2. Request

`POST /pagos` (requiere sesión; el usuario sale del `Authentication`).

| Campo | Regla |
|---|---|
| `numeroCuenta` | Obligatorio, 18 dígitos. |
| `idProducto`, `idServicio` | Obligatorios, positivos. Deben existir en el catálogo local. |
| `monto` | Obligatorio, ≥ 0.01, máx. 2 decimales. |
| `telefono` | Opcional, 10 dígitos (recargas). |
| `referencia` | Opcional, alfanumérica 1–100 (pago de servicios). |
| `pinAutorizacion` | Obligatorio, 4–6 dígitos. No aparece en logs (`toString` lo omite). |

Cualquier campo extra se rechaza (`@JsonAnySetter` lanza excepción). El cliente **no** dice si es recarga o servicio: la API lo decide con el `tipoFront` del producto.

## 3. Flujo paso a paso

El saldo se **descuenta antes** de llamar a GestoPago (se "retiene") y se devuelve si el pago falla. No se espera al final para descontar.

```
POST /pagos
   │
   ▼
 0. Límite de solicitudes por usuario (Redis)                           429 si se excede
 1. Buscar producto en catálogo local            (sin transacción)      404 si no existe
 2. Resolver destino según tipoFront             (sin transacción)      400 si faltan datos
    · pago de servicio: verifyReference.do si el servicio tiene consulta  400/422 si el monto no es el adeudo
 3. Generar UPC aleatorio (15 car.) y armar/serializar la petición JSON
   │
   ▼
 4. registro.reservar(...)          ══ TRANSACCIÓN 1 (commit) ══
      · bloquear cuenta (SELECT … FOR UPDATE)
      · validar propietario, cuenta activa, PIN (con bloqueo por intentos), saldo
      · saldo = saldo - (monto + comisión)
      · INSERT pagos (estado PENDIENTE, upc, petición firmada en claro)
   │      ← el saldo ya está descontado y persistido
   ▼
 5. Firmar (AES-256) y enviar a sendTx.do      (sin transacción ni bloqueo, hasta 70 s)
   │   (si el token venció → renovar y reintentar UNA vez con el mismo UPC)
   ▼
 6. Según la respuesta:
      CODIGO 01        → registro.aprobar(...)           ══ TX 2 ══ estado APROBADO
      CODIGO 82        → registro.marcarEnRevision(...)  ══ TX 2 ══ estado EN_REVISION
      error incierto   → registro.marcarEnRevision(...)  ══ TX 2 ══ estado EN_REVISION
      cualquier otro   → registro.revertir(...)          ══ TX 2 ══ saldo devuelto, RECHAZADO
```

### 3.1 Resolución del destino (paso 2)

| Producto | Qué se envía a GestoPago | Validación |
|---|---|---|
| `tipoFront 1` (recarga) | `telefono` = el del cliente; sin `referencia`/`montoPago` | Si el catálogo tiene precio fijo > 0, `monto` debe ser **igual** (400 si no). Falta `telefono` → 400. |
| `tipoFront 1` + `idCatTipoServicio` 10/11 (canjeable) | `telefono` = `#1111111111` | Igual precio fijo. |
| `tipoFront 2` (pago de servicio) | `telefono` = `1111111111`, `referencia`, `montoPago` = monto | Falta `referencia` → 400. Si el servicio tiene consulta de adeudo, `monto` debe ser **igual** al adeudo (ver 3.3). Se cobra además la comisión (`precio` del catálogo). |
| Cualquier otro `tipoFront` | — | 400 "este producto no se puede pagar". |

### 3.2 Validaciones dentro de `reservar` (paso 4)

Se ejecutan en este orden, **con la cuenta ya bloqueada**:

| Validación | Respuesta |
|---|---|
| Cuenta existe | 404 |
| Usuario es propietario de la cuenta y usuario/cliente activos | 403 (401 si no hay usuario) |
| Cuenta activa | 409 "La cuenta no está activa" |
| Cuenta tiene PIN configurado | 409 |
| PIN no bloqueado | 423 "El PIN está bloqueado por intentos fallidos…" |
| PIN correcto (comparado con hash `PasswordEncoder`) | 403 "PIN de autorización incorrecto" |
| Saldo ≥ monto + comisión | 422 "Saldo insuficiente para realizar el pago" |

Si alguna falla, la transacción se revierte completa: no se descuenta saldo ni se crea el pago. La única excepción es el PIN incorrecto: la transacción **sí se confirma** (`noRollbackFor = PinIncorrectoException`) para que el intento fallido quede guardado (ver 7.9).

### 3.3 Verificación del adeudo (`verifyReference.do`)

En pagos de servicio, el cliente no debe poder pagar un monto inventado. Si en el catálogo existe un producto de consulta (`tipoFront 4`) con el mismo `idServicio`, antes de reservar se llama a `verifyReference.do` con `{idProducto: <producto de consulta>, idServicio, referencia}` (firmado, con el mismo token):

| Resultado | Respuesta | Saldo |
|---|---|---|
| `CODIGO 01` y `SALDO` = monto | Continúa el pago | — |
| `SALDO` ≠ monto | 400 "monto: debe ser igual al adeudo de la referencia (X)" | Sin cambio |
| `SALDO` ≤ 0 | 422 "La referencia no tiene adeudo pendiente" | Sin cambio |
| Otro código o sin `SALDO` | 422 "No fue posible verificar la referencia…" | Sin cambio |
| Error de comunicación | 502 | Sin cambio |

No todos los servicios ofrecen consulta: en el catálogo actual solo 35 de 148 servicios `tipoFront 2` tienen producto `tipoFront 4`. Para el resto, el monto solo se valida por formato (> 0, 2 decimales), igual que antes.

## 4. Estados del pago (`EstadoPago`)

| Estado | Significado | ¿Saldo del cliente? |
|---|---|---|
| `PENDIENTE` | Reservado; aún sin respuesta de GestoPago. | Descontado |
| `APROBADO` | GestoPago confirmó (código `01`, o `confirmTx` dijo aplicada). | Descontado (definitivo) |
| `RECHAZADO` | GestoPago rechazó, no se envió, o `confirmTx` dijo no aplicada. | **Devuelto** |
| `EN_REVISION` | No se sabe si GestoPago aplicó el pago. | Descontado (retenido) hasta conciliar |

Transiciones permitidas:

```
PENDIENTE ──► APROBADO
PENDIENTE ──► RECHAZADO
PENDIENTE ──► EN_REVISION ──► APROBADO
                          └─► RECHAZADO
```
`APROBADO` y `RECHAZADO` son finales: `devolverSaldo` no hace nada si el pago ya está en alguno de ellos.

## 5. Respuestas según el desenlace

### 5.1 Comprobante de éxito (HTTP 200)

Usa los mismos datos que devuelve `sendTx`, sin folio propio:

```json
{
  "estado": "APROBADO",
  "idTx": 291437188,
  "numAutorizacion": "100024988",
  "monto": 10.00,
  "comision": 0.00,
  "referencia": "25720381",
  "saldoDisponible": 490.00,
  "fecha": "25/Feb/2022 18:15:25",
  "pin": "40919958",
  "legend": "Para comenzar a ver: ..."
}
```

| Campo | Origen |
|---|---|
| `idTx`, `numAutorizacion`, `fecha`, `pin`, `legend` | `ID_TX`, `NUM_AUTORIZACION`, `FECHA`, `PIN`, `legend` de GestoPago. `pin`/`legend` solo en artículos canjeables (el `pin` puede ser una URL). |
| `monto`, `referencia` | Los del pago (`referencia` solo en pago de servicios). |
| `comision` | Comisión cobrada al cliente (`precio` del catálogo en pago de servicios; 0 en recargas). El cargo total fue `monto + comision`. |
| `saldoDisponible` | Saldo de la **cuenta del cliente** después del cargo. No es el `SALDO` de GestoPago, que es el del distribuidor. |

No se devuelven `COMISION` (es la comisión del distribuidor), ni número de cuenta, teléfono o `upc` (el cliente ya los conoce o son internos). El `upc` solo aparece como "referencia" en el mensaje del 504.

### 5.2 Qué pasa con cada respuesta de GestoPago

El significado de un código **depende del endpoint**: `06` en `sendTx` es "duplicado" y en `confirmTx` es "sí se aplicó".

| Endpoint | Código | Significa | Qué hace la API | HTTP al cliente | Saldo |
|---|---|---|---|---|---|
| `sendTx` | `01` | Operación exitosa | `APROBADO`, devuelve el comprobante | 200 | Descontado |
| `sendTx` | `02` | Saldo insuficiente **del distribuidor** (tu saldo en GestoPago) | `RECHAZADO`; log de error para recargar saldo; no revela el motivo | 503 "no se realizó ningún cargo" | Devuelto |
| `sendTx` | `06` | Duplicado: recargas, mismo `upc` en 15 min; pago de servicios, mismo `upc` **o** mismo servicio + monto en 24 h | `RECHAZADO` | 409 "Pago duplicado…; no se realizó ningún cargo" | Devuelto |
| `sendTx` | `82` | Tiempo de espera agotado en GestoPago (62 s): **no se sabe** si se aplicó | `EN_REVISION`; el scheduler llamará a `confirmTx` | 504 | **Retenido** |
| `sendTx` | otro | Rechazo del proveedor | `RECHAZADO` | 422 "El proveedor rechazó el pago; no se realizó ningún cargo" | Devuelto |
| `confirmTx` | `06` (con autorización ≠ `0`/`-1`) o `01` | La transacción **sí** se aplicó | `APROBADO` | — | Se queda descontado |
| `confirmTx` | `70` | La transacción **no** se aplicó | `RECHAZADO` | — | Devuelto |
| `confirmTx` | otro / error | Sin resultado concluyente | Solo log; se reintenta cada 60 s | — | Retenido |

Al cliente **nunca** se le muestra el texto de GestoPago: todos los mensajes son propios. El `TEXTO` del proveedor se guarda en `pagos.mensaje` y la respuesta completa en `pagos.respuesta`.

Por qué el `82` no se trata como error: GestoPago dice "la transacción no ha sido registrada" pero no garantiza que no se haya cobrado; solo `confirmTx` lo resuelve. Y el `06` de `sendTx` sí devuelve el saldo porque el pago aplicado "anteriormente" es **otro** pago (con su propio registro); este nuevo no se cobró.

### 5.3 Errores de la API y de comunicación

| Situación | Qué hace la API | HTTP | Saldo |
|---|---|---|---|
| Más de `limite-por-minuto` solicitudes del mismo usuario en 60 s | Rechaza | 429 | Sin cambio |
| Validación del request | Rechaza | 400 | Sin cambio |
| Producto inexistente | Rechaza | 404 | Sin cambio |
| Cuenta/propiedad/saldo/PIN inválido | Rechaza (ver 3.2) | 403 / 409 / 422 / 423 | Sin cambio |
| Monto distinto al adeudo, sin adeudo o consulta fallida | Rechaza (ver 3.3) | 400 / 422 / 502 | Sin cambio |
| Timeout de lectura (70 s), HTTP 5xx/408, respuesta ilegible | `EN_REVISION` | 504 "quedó en revisión… no lo intentes de nuevo" | **Retenido** |
| No se pudo conectar (`ConnectException`, DNS, connect timeout) | Revierte (GestoPago nunca recibió la petición) | 502 "no se realizó ningún cargo" | Devuelto |
| HTTP 4xx distinto de 401/403 | Revierte (rechazada antes de procesarse) | 502 | Devuelto |
| Falla antes de enviar (token, firma, configuración) | Revierte y relanza la excepción | 502/500 | Devuelto |
| Token vencido (401/403) | Renueva el token y reintenta **una vez** con el mismo UPC; si vuelve a fallar, revierte | 502 | Devuelto |

**Criterio clave**: solo se devuelve el saldo cuando hay *certeza* de que GestoPago no cobró. Ante duda se deja `EN_REVISION` porque devolver el dinero podría regalar un pago que sí se aplicó.

## 6. Conciliación con `confirmTx`

`PagoConciliacionScheduler` (`@Scheduled`, cada `gestopago.pagos.conciliacion-ms` = 60 s, con `@EnableScheduling` en `App`) ejecuta `conciliarPendientes()`:

1. Busca (`PagoRepository.idsPorConfirmar`) pagos creados en las últimas **24 h** que sean:
   - `EN_REVISION` con ≥ **62 s** de antigüedad (regla de GestoPago), o
   - `PENDIENTE` con ≥ **5 min** (abandonados: la API se cayó entre reservar y enviar).
2. Por cada uno, reenvía a `confirmTx.do` la **petición original guardada** en `pagos.peticion` (mismos parámetros y mismo UPC, como exige GestoPago).
3. Resultado:

| Respuesta de `confirmTx` | Acción |
|---|---|
| `01`, o `06` con autorización válida (≠ `0`, ≠ `-1`) | `confirmarAplicado` → `APROBADO` (el saldo se queda descontado) |
| `70` | `confirmarNoAplicado` → `RECHAZADO` y **se devuelve el saldo** |
| Otro / error / sin respuesta | Solo log; se reintenta en el siguiente ciclo |

Un error al confirmar un pago no detiene los demás (cada uno va en su `try/catch`).

**Límite**: si pasan 24 h sin resultado concluyente, el pago deja de ser candidato (GestoPago ya no acepta `confirmTx`) y queda `EN_REVISION` con el saldo retenido: requiere revisión manual. Lo mismo si `peticion` es nulo.

## 7. Mecanismos de consistencia

### 7.1 `@Transactional` por pasos cortos

`PagoServiceImpl` **no** es transaccional; cada paso de BD vive en `PagoRegistroService` con su propia transacción (`sfTransactionManager`, único `PlatformTransactionManager`). Se llaman desde otro bean, por lo que el proxy de Spring aplica.

Razón: la llamada HTTP a GestoPago puede tardar hasta 62–70 s. Si fuera una sola transacción, mantendría la conexión del pool y el bloqueo de la cuenta durante todo ese tiempo, y un fallo de GestoPago revertiría también el registro de evidencia. Con transacciones cortas:

- El descuento y el registro `PENDIENTE` se confirman **antes** de salir a la red (si la API se cae después, queda rastro para conciliar).
- El bloqueo de la cuenta se suelta antes de la llamada externa.

### 7.2 `@Lock(PESSIMISTIC_WRITE)` en la cuenta

`CuentaRepository.bloquearPorNumero` y `bloquearPorId` hacen `SELECT … FOR UPDATE`. Dos pagos simultáneos sobre la misma cuenta se serializan: el segundo espera el commit del primero y ve el saldo ya descontado, así que no se puede gastar dos veces el mismo saldo. `bloquearPorId` se usa también al devolver el saldo, para que la suma sea atómica respecto a otros pagos.

### 7.3 Descontar antes de enviar

Es lo que hace segura la concurrencia: el saldo "reservado" ya no está disponible para otro pago mientras se espera a GestoPago.

### 7.4 UPC único

`upc` se genera aleatoriamente (15 caracteres) y la columna es `UNIQUE`. Sirve para: (a) la detección de duplicados de GestoPago (código `06`), (b) reintentar tras renovar el token sin riesgo de cobrar dos veces, y (c) correlacionar `confirmTx` con el `sendTx` original.

### 7.5 Idempotencia de las transiciones y `@Version`

`devolverSaldo` ignora pagos ya `APROBADO`/`RECHAZADO`, y `marcarEnRevision` solo cambia un pago `PENDIENTE`. Por eso reintentar la conciliación o llamar `revertir` dos veces no devuelve el saldo dos veces.

`Pago` tiene `@Version` (bloqueo optimista). Si dos procesos leen el mismo pago al mismo tiempo, por ejemplo dos instancias del scheduler, ambos lo ven `EN_REVISION`. Solo el primer commit gana; el segundo falla con `OptimisticLockingFailureException` y su transacción se revierte completa, incluida la devolución del saldo. Así no se devuelve dos veces aunque las comprobaciones de estado se hayan hecho en paralelo. Que se llame dos veces a `confirmTx` no es un problema, porque solo consulta.

### 7.6 Fallo al guardar la aprobación

Si GestoPago cobró (`01`) pero falla `registro.aprobar` (`DataAccessException`/`TransactionException`), la API **responde éxito igualmente** con el comprobante (sin saldo) y deja un log de error para conciliar. El pago queda `PENDIENTE`; el scheduler lo resolverá con `confirmTx` a los 5 min, que devolverá aplicada y lo marcará `APROBADO`. Así el cliente no repite un pago que ya se cobró.

### 7.7 Timeouts alineados

`readTimeout` de `gestopago-pagos` = **70 s** (> 62 s que espera GestoPago antes de abandonar) y `connectTimeout` = 5 s. Consultas JPA con timeout de 10 s. Variables: `GESTOPAGO_PAGOS_READ_TIMEOUT_MS`, `GESTOPAGO_PAGOS_CONNECT_TIMEOUT_MS`.

### 7.8 Evidencia (log en BD)

La tabla `pagos` guarda `peticion`, `respuesta` y `respuesta_confirmacion` tal cual (requisito de GestoPago). Los logs de aplicación omiten token, cuerpo firmado y PIN. Las columnas del pedido (cuenta, producto, monto, upc, petición…) son `updatable = false`: no se pueden alterar después.

### 7.9 Bloqueo del PIN por intentos fallidos

Un PIN de 4 dígitos tiene solo 10,000 combinaciones, así que `PinAutorizacionService` cuenta los fallos en `cuentas.intentos_pin_fallidos`:

- Un PIN correcto reinicia el contador.
- Al **5.º** fallo se fija `cuentas.pin_bloqueado_hasta = ahora + 15 min` y el contador vuelve a 0.
- Mientras dure el bloqueo, todo intento (incluso con el PIN correcto) responde **423**.

El contador debe sobrevivir a la excepción, por eso `reservar` y `CuentaService.configurarPin` usan `@Transactional(noRollbackFor = PinIncorrectoException.class)`. Antes de un fallo de PIN no se modifica nada más, así que confirmar la transacción no deja datos a medias. La fila de la cuenta está bloqueada (`FOR UPDATE`) en ambos casos, de modo que intentos simultáneos no pierden incrementos. El cambio de PIN también exige el PIN actual y comparte el mismo contador, para que no sea una vía alterna de fuerza bruta.

### 7.10 Límite de solicitudes (rate limiting)

`LimitePagosService` incrementa `limite:pagos:<usuarioId>` en Redis (`INCR`; `EXPIRE` de 60 s en la primera solicitud de la ventana). Si se pasan de `gestopago.pagos.limite-por-minuto` (5 por defecto), responde 429 antes de tocar la BD o GestoPago. El contador vive en Redis, así que se comparte entre instancias.

Si Redis no responde, la solicitud **pasa** y se deja un `warn`. Es una decisión deliberada: el bloqueo del PIN sigue protegiendo en la BD y una caída de Redis no debe detener los pagos.

### 7.11 Token de GestoPago cifrado en reposo

La columna `gestopago_tokens.token` se guarda cifrada con AES-256-GCM (`CifradoTokenConverter`, IV aleatorio por escritura y autenticado). La llave (`GESTOPAGO_TOKEN_KEY`) es distinta de la llave de firma de GestoPago y solo llega por variable de entorno. Hibernate obtiene el converter de Spring gracias a `SpringBeanContainer` en `ConfigDB`. La migración `V8` borra los tokens que estaban en claro; el servicio de token pide uno nuevo en la siguiente solicitud.

## 8. Resumen de la línea de tiempo del saldo

```
cargo = monto + comisión (comisión = 0 en recargas)

Saldo cuenta:   S ──reservar──► S − cargo ──┬─ 01 ───────────────────────► S − cargo  (APROBADO)
                                            ├─ rechazo/no enviado ───────► S         (RECHAZADO)
                                            └─ incierto (EN_REVISION) ───► S − cargo … 
                                                       confirmTx: aplicada ► S − cargo (APROBADO)
                                                       confirmTx: no aplicada ► S      (RECHAZADO)
```

## 9. Consideraciones y limitaciones conocidas

- **Varias instancias del scheduler**: pueden conciliar el mismo pago a la vez. Ambas llamarían a `confirmTx`, que solo consulta, pero `@Version` garantiza que solo una aplique el cambio de estado y la devolución del saldo (ver 7.5).
- **Solicitudes duplicadas del cliente**: no hay clave de idempotencia del cliente. Dos envíos del mismo pago generan dos UPC y dos cargos. El rate limit (7.10) reduce el riesgo, y GestoPago rechaza con `06` el mismo servicio + monto en 24 h solo en pago de servicios.
- **Contrato de `verifyReference.do`**: la documentación de GestoPago solo indica los parámetros (`idProducto`, `idServicio`, `referencia`) y que devuelve `SALDO`. Se asume el mismo formulario firmado y la respuesta XML de `sendTx`; hay que confirmarlo en el ambiente de pruebas de GestoPago.
- **Comparación exacta con el adeudo**: no se permiten pagos parciales ni anticipados en servicios con consulta.
- **Pagos > 24 h sin resolver** requieren revisión manual (ver sección 6).
- **Saldo del distribuidor en GestoPago** agotado (código `02`) se informa al cliente como indisponibilidad temporal; hay que monitorear los logs de error y recargarlo.
- **Respuesta 504**: el cliente no debe reintentar; el pago ya tiene registro y se resolverá solo con `confirmTx`. Un reintento crearía un pago nuevo con otro UPC y otro descuento.
- **Artículos canjeables resueltos por `confirmTx`**: `confirmTx` solo devuelve `NUM_AUTORIZACION` e `ID_TX`, sin `PIN` ni `legend`. Si una gift card queda `EN_REVISION` y luego se confirma como aplicada, el cobro es válido pero la API no recibe el PIN; hay que pedirlo a soporte de GestoPago (no hay endpoint documentado para recuperarlo).

## 10. Configuración relevante

| Propiedad | Default | Uso |
|---|---|---|
| `gestopago.pagos.url` / `path` / `confirm-path` / `verify-path` | URL de auth / `/sistema/service/sendTx.do` / `/sistema/service/confirmTx.do` / `/sistema/service/verifyReference.do` | Endpoints. |
| `gestopago.auth.token-key` (`GESTOPAGO_TOKEN_KEY`) | — (obligatoria) | Llave AES-256 en Base64 para cifrar el token en BD. Generar con `openssl rand -base64 32`. |
| `gestopago.pagos.limite-por-minuto` | `5` | Solicitudes de pago por usuario cada 60 s. |
| `gestopago.pagos.secret_key`, `iv` | — (obligatorios) | Llave e IV AES-256 (Base64). |
| `gestopago.pagos.parametro-firma` | `signed` | Nombre del campo del formulario con el payload cifrado. |
| `gestopago.pagos.api-key` | vacío | Se envía como `X-API-Key` si existe. |
| `gestopago.pagos.unidad` | `Sucursal Matriz` | Campo `unidad` (3–25 caracteres, validado al arrancar). |
| `gestopago.pagos.zona` | `America/Mexico_City` | Zona horaria para `horaLocal`. |
| `gestopago.pagos.conciliacion-ms` | `60000` | Intervalo del scheduler. |
| `GESTOPAGO_PAGOS_READ_TIMEOUT_MS` | `70000` | Timeout de lectura de `sendTx`/`confirmTx`. |
| `GESTOPAGO_PAGOS_CONNECT_TIMEOUT_MS` | `5000` | Timeout de conexión. |
