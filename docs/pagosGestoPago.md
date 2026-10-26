# GestoPago — Endpoints de pago: `sendTx` y `confirmTx`

Fuente: documentación Postman de GestoPago / PuntoRed ("PuntoRed API Technical Specification").
Base URL: `https://gestopago.portalventas.net/sistema/service/`

---

## 1. Requisitos comunes

| Aspecto | Detalle |
|---|---|
| Autenticación | `Authorization: Bearer <token>` obtenido en `POST /sistema/app/jwt-gp/authenticate/`. El token dura 24 h; se pide **uno por día** (no uno por petición). Token vencido → HTTP 403 `{"success": false, "token": "EXPIRED", ...}`. |
| `X-API-Key` | Header opcional, solo si GestoPago asignó una. |
| Cifrado del payload | El JSON de parámetros se cifra con **AES-256-CBC, PKCS5/PKCS7**, usando `SECRET_KEY` e `IV` (ambos en Base64) entregados al integrador; el resultado se envía en Base64. |
| Formato de respuesta | **XML** (`<RESPONSE>`), no JSON. |
| Log obligatorio | Se deben guardar las peticiones y respuestas intercambiadas (archivo o BD). |

---

## 2. `sendTx` — realizar una venta

`POST /sistema/service/sendTx.do`

Vende cualquier producto/servicio (recarga, pago de servicio, tarjeta canjeable). Los parámetros que espera dependen del **`tipoFront`** del producto (ver sección 4).

El resultado es éxito o fallo; los códigos de error deben mapearse en la aplicación.

### Parámetros (JSON, cifrado)

| Campo | Tipo | Req. | Descripción | Longitud |
|---|---|---|---|---|
| `idProducto` | integer | Sí | Se obtiene de `getProductList`. | |
| `idServicio` | integer | Sí | Se obtiene de `getProductList`. | |
| `telefono` | string | Sí | Teléfono a recargar, o valor fijo según `tipoFront` / `idCatTipoServicio` (ver sección 4). | 10 – 20 |
| `horaLocal` | string | Sí | Fecha-hora local `yyyyMMddHHmmss`, ej. `20101109131526`. | 1 – 15 |
| `referencia` | string | Solo pago de servicios | Referencia alfanumérica del recibo a pagar. | 1 – 100 |
| `montoPago` | double | Solo pago de servicios | Monto a pagar. | |
| `upc` | string | Sí | *Unique Payment Code*: identificador único de la operación, generado por el integrador (ver sección 5). | 3 – 15 |
| `unidad` | string | Sí | Unidad comercial o nombre de sucursal. | 3 – 25 |

### Ejemplos de payload

Recarga (`tipoFront 1`):
```json
{
  "idProducto": 582,
  "idServicio": 133,
  "telefono": "5555555551",
  "horaLocal": "20220101235900",
  "upc": "UPC_Test_XX",
  "unidad": "083"
}
```

Pago de servicio (`tipoFront 2`):
```json
{
  "idProducto": 205,
  "idServicio": 76,
  "telefono": "1111111111",
  "horaLocal": "20220101235900",
  "referencia": "25720381",
  "montoPago": 601.99,
  "upc": "UPC_Test_XX",
  "unidad": "083"
}
```

Artículo canjeable (gift card, `idCatTipoServicio` 10/11):
```json
{
  "idProducto": 7030,
  "idServicio": 178,
  "telefono": "#1111111111",
  "horaLocal": "20220101235900",
  "upc": "UPC_Test_XX",
  "unidad": "083"
}
```

### Respuesta (XML)

| Campo | Descripción | Tipo |
|---|---|---|
| `MENSAJE/CODIGO` | `01` = éxito; cualquier otro valor es error. | Integer (1–4) |
| `MENSAJE/TEXTO` | Mensaje descriptivo. | String |
| `MENSAJE/REFERENCIA` | Referencia a la que se aplicó el pago (vacío en recargas). | String (1–100) |
| `MENSAJE/PIN` | PIN/folio a canjear (solo artículos canjeables). Puede ser una **URL** de descarga en vez de folio(s) separados por coma. | Varchar (1–250) |
| `MENSAJE/legend` | Instrucciones de canje del producto (solo canjeables). | Texto |
| `ID_TX` | Número único de transacción. | Integer |
| `NUM_AUTORIZACION` | Número único de autorización (`-1` si falló). | String (1–60) |
| `SALDO` / `SALDO_F` | Saldo del distribuidor **después** de la transacción (`_F` formateado). `SALDO` puede venir en notación científica. | Double |
| `COMISION` / `COMISION_F` | Comisión cobrada (`_F` formateada). | Double |
| `FECHA` | Fecha-hora de la transacción, formato `d/MMM/Y H:i:s` (ej. `25/Feb/2022 18:15:25`). | DateTime |
| `MONTO` | Monto de la transacción. | Double |

Éxito:
```xml
<RESPONSE>
    <ID_TX>291437188</ID_TX>
    <NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
    <SALDO>157731.83</SALDO>
    <COMISION>0.7</COMISION>
    <SALDO_F>157,731.83</SALDO_F>
    <COMISION_F>0.7</COMISION_F>
    <FECHA>25/Feb/2022 18:15:25</FECHA>
    <MONTO>10.0</MONTO>
    <MENSAJE>
        <CODIGO>01</CODIGO>
        <TEXTO>Operacion realizada con exito</TEXTO>
        <REFERENCIA></REFERENCIA>
    </MENSAJE>
</RESPONSE>
```

Artículo canjeable (incluye `PIN` y `legend`):
```xml
<MENSAJE>
    <CODIGO>01</CODIGO>
    <PIN>40919958</PIN>
    <legend><![CDATA[Para comenzar a ver: <br>1. Visita www.netflix.com/codigo ...]]></legend>
    <TEXTO>Operacion realizada con exito</TEXTO>
    <REFERENCIA></REFERENCIA>
</MENSAJE>
```
El cliente final **debe** recibir PIN/folio y `legend` (se imprimen en su comprobante); ese proceso es externo a PuntoRed.

### Códigos observados

| `CODIGO` | Significado | Notas |
|---|---|---|
| `01` | Operación realizada con éxito | |
| `02` | Saldo insuficiente | `NUM_AUTORIZACION = -1` |
| `06` | Transacción aplicada anteriormente (duplicada) | Devuelve la autorización original; reintentar tras 15 min. |
| `82` | Tiempo de espera alcanzado, la transacción no ha sido registrada | Sin respuesta tras **62 s**; hay que llamar a `confirmTx`. |

Respuesta de timeout:
```xml
<RESPONSE>
    <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>
    <MENSAJE>
        <CODIGO>82</CODIGO>
        <TEXTO>Tiempo de espera alcanzado, la transacción no ha sido registrada</TEXTO>
    </MENSAJE>
</RESPONSE>
```

---

## 3. `confirmTx` — confirmar el estado de una venta

`POST /sistema/service/confirmTx.do`

Indica si una transacción fue **aplicada o no**. Es **obligatorio** invocarlo cuando `sendTx` no devolvió resultado (timeout, código `82`, desconexión) y no se pudo recibir la respuesta de la venta.

Reglas:
- Se envía con **exactamente los mismos parámetros** que el `sendTx` original (incluido el mismo `upc` y `horaLocal`).
- **No** ejecutarlo antes de **62 segundos** después del `sendTx`.
- Vida útil de **24 horas** desde el `sendTx`; pasado ese tiempo no debe ejecutarse.

### Payload (JSON, cifrado)

```json
{
  "idProducto": 582,
  "idServicio": 133,
  "telefono": "5555555551",
  "horaLocal": "20220101235900",
  "upc": "UPC_Test_XX",
  "unidad": "083"
}
```
(Para pagos de servicio se añaden también `referencia` y `montoPago`, igual que en el `sendTx`.)

### Respuestas (XML)

Transacción aplicada:
```xml
<RESPONSE>
    <NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>
    <MENSAJE>
        <CODIGO>06</CODIGO>
        <TEXTO>La transaccion fue aplicada exitosamente. Autorizacion [100024988] idTicket[291437188]</TEXTO>
        <ID_TX>291437188</ID_TX>
    </MENSAJE>
</RESPONSE>
```

Transacción no aplicada:
```xml
<RESPONSE>
    <NUM_AUTORIZACION>0</NUM_AUTORIZACION>
    <MENSAJE>
        <CODIGO>70</CODIGO>
        <TEXTO>La transaccion no fue aplicada</TEXTO>
        <ID_TX>-1</ID_TX>
    </MENSAJE>
</RESPONSE>
```

| `CODIGO` | Significado | Acción |
|---|---|---|
| `06` | Aplicada exitosamente (trae `NUM_AUTORIZACION` e `ID_TX`) | Marcar el pago como exitoso. |
| `70` | No aplicada (`NUM_AUTORIZACION = 0`, `ID_TX = -1`) | Marcar como fallido; se puede reintentar con un nuevo `upc`. |

### Flujo ante timeout

```
sendTx ──► respuesta CODIGO 01        → éxito
       ├─► respuesta CODIGO ≠ 01      → error (mapear código)
       └─► sin respuesta / CODIGO 82  → esperar ≥ 62 s → confirmTx (mismos parámetros, ≤ 24 h)
                                           ├─ CODIGO 06 → aplicada
                                           └─ CODIGO 70 → no aplicada
```

---

## 4. `tipoFront`, `tipoReferencia` e `idCatTipoServicio`

Cada producto de `getProductList` trae estas propiedades; indican **qué datos hay que mandar** en `sendTx`.

> `getProductList` debe consumirse **una vez al día** y guardarse en BD propia (máx. 3 veces al día o se bloquea la IP). Nunca se usa directamente como fuente del frontend.

### 4.1 `tipoFront` — tipo de payload que requiere el producto

| tipoFront | Uso | Cómo se envía |
|---|---|---|
| **1** | **Recargas** (tiempo aire, datos, tarjetas/gift cards). El número de teléfono es el **único** campo obligatorio. | `telefono` = número a recargar (o valor fijo si es canjeable). No se manda `referencia` ni `montoPago`. `precio` del catálogo = **precio final** a cobrar. |
| **2** | **Pago de servicios** (luz, agua, teléfono fijo, gobierno, etc.). La referencia es obligatoria. | `telefono` fijo `"1111111111"`; `referencia` + `montoPago` obligatorios. `precio` del catálogo = **comisión** generada. |
| 4 | **Verificar una referencia**: recupera el detalle del adeudo (`verifyReference.do`). No es una venta. | `idProducto`, `idServicio`, `referencia`. Devuelve `SALDO` (>0 adeudo, 0 sin adeudo, <0 saldo a favor). |
| 5 | Recupera los pagos relacionados a una referencia. | — |
| 30 | Operaciones **Cash-In**. | `sendTx` con datos del remitente (`remitente`: nombre, apellidos, fecha de nacimiento, dirección, CP). Monto entre $20 y $10,000. |
| 31 | Operaciones **Cash-Out**. | — |
| 40 | **Transferencias de dinero** (Money Transfer). | — |

Para este proyecto los relevantes son **1 (recargas)** y **2 (pago de servicios)**, que son los que `sendTx` documenta.

### 4.2 `tipoReferencia` — de dónde sale la referencia y en qué campo va

| Valor | Significado | Ejemplo de payload |
|---|---|---|
| `a` | El **número telefónico** es el dato obligatorio, va en `telefono`. Típico de recargas. | `{"telefono":"5555555555"}` |
| `b` | **Número de cliente / servicio / contrato / TAG** va en `referencia`. | `{"telefono":"1111111111","referencia":"IMDM266777934"}` |
| `c` | **Código de barras** del recibo va en `referencia`. | `{"telefono":"1111111111","referencia":"55407904123858397300040005100051"}` |
| `ab` | Solo clasifica el producto (acepta teléfono, como `a`). | `{"telefono":"5555555555"}` |
| `bc` | Solo clasifica el producto (acepta referencia, como `b`/`c`). | `{"telefono":"1111111111","referencia":"IMDM266777934"}` |

Relación práctica: `tipoFront 1` ↔ `tipoReferencia a` (teléfono); `tipoFront 2` ↔ `tipoReferencia b` o `c` (número de contrato o código de barras en `referencia`).

### 4.3 `idCatTipoServicio` — categoría del servicio

| id | Categoría | | id | Categoría |
|---|---|---|---|---|
| 1 | Movistar | | 11 | Entretenimiento (**canjeable**) |
| 2 | Telcel | | 12 | Virgin |
| 3 | AT&T / Iusacell | | 13 | Otras recargas |
| 4 | Unefon | | 14 | Venta de seguros |
| 5 | Pago de servicios | | 15 | Pago de derechos de agua |
| 6 | Nextel | | 16 | Juegos y sorteos |
| 7 | Pago de impuestos / gobierno | | 17 | MazTiempo |
| 8 | Productos financieros | | 18 | Autopistas y transportes |
| 9 | Productos por catálogo | | 19 | Internet de prepago |
| 10 | Servicios de prepago (**canjeable**) | | | |

Regla para `telefono` en `sendTx`:
- `tipoFront 1` → el número de teléfono (o referencia) va en `telefono`.
- `tipoFront 2` → `telefono` fijo `"1111111111"`.
- `idCatTipoServicio` 10 u 11 (canjeables) → `telefono` fijo `"#1111111111"`; la respuesta trae `PIN` y `legend`.

---

## 5. UPC (Unique Payment Code) y duplicados

`upc` es generado por el integrador, único por transacción (alfanumérico, 3–15 caracteres; se sugiere un ID interno). Sirve para detectar duplicados por errores de red, timeouts o reintentos automáticos.

| Tipo de servicio | Es duplicado si, en el periodo indicado… |
|---|---|
| Recargas y PINs de entretenimiento | Mismo `upc` en los últimos **15 minutos** para el mismo tipo de servicio. |
| Pago de servicios | En las últimas **24 horas**: mismo `upc`, **o** misma combinación servicio + monto. |

Un duplicado responde `CODIGO 06` ("Transaccion aplicada anteriormente").
