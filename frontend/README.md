# GestoPago · Frontend (web y móvil)

Dos aplicaciones Flutter que comparten un solo código y consumen la misma API REST (Spring Boot). No duplican lógica de negocio: validan solo para guiar al usuario y el servidor decide siempre.

| Aplicación | Punto de entrada | Marco de navegación |
|---|---|---|
| Web | `lib/main_web.dart` | Barra lateral en pantallas anchas; barra inferior en ventanas angostas |
| Móvil | `lib/main_mobile.dart` | Barra inferior; riel lateral en tabletas horizontales |

## Ejecutar

```bash
flutter pub get
```

```bash
flutter run -d chrome -t lib/main_web.dart --dart-define=API_BASE_URL=http://localhost:8080
```

```bash
flutter run -t lib/main_mobile.dart --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` es el `localhost` del equipo visto desde el emulador de Android. Para producción compila con una URL `https://`:

```bash
flutter build web -t lib/main_web.dart --dart-define=API_BASE_URL=https://api.tu-dominio.com
```

La API debe permitir el origen de la web: variable `CORS_ORIGENES` de la API (por defecto `http://localhost:*`, `http://127.0.0.1:*`).

## Arquitectura

Screaming architecture (la estructura dice qué hace la app) con puertos y adaptadores dentro de cada módulo:

```
lib/
├── authentication/   Iniciar y cerrar sesión, sesión vigente
├── productos/        Catálogo y detalle
├── pagos/            Compra, resultado, historial
│   ├── domain/
│   │   ├── entities/        Modelos del negocio (sin Flutter ni HTTP)
│   │   └── ports/           Puertos de salida: interfaces que el dominio necesita
│   ├── application/         Casos de uso y controladores (ChangeNotifier)
│   └── infraestructure/
│       ├── adapters/        Adaptadores de salida: HTTP, almacenamiento seguro
│       └── ui/              Adaptadores de entrada: pantallas y widgets
├── core/             Núcleo compartido: cliente HTTP, errores, tema, formato
├── app/              Raíz de composición, rutas y marcos web/móvil
├── main_web.dart
└── main_mobile.dart
```

- `app/dependencias.dart` es el único lugar donde un puerto se une a su adaptador. Las pruebas pueden sustituir cualquier adaptador.
- `productos` no depende de `pagos`: la acción de compra se inyecta en la pantalla del catálogo como un widget.
- Sin animaciones: transiciones de página desactivadas y sin efectos de entrada.

## Seguridad

| Requisito | Cómo se cumple |
|---|---|
| HTTPS | La URL base se define al compilar (`API_BASE_URL`). Una compilación de producción con HTTP hacia un host que no sea el propio equipo se niega a arrancar. |
| Autenticación | Acceso en dos pasos: contraseña (`/auth/login`) y código de 6 dígitos enviado al correo (`/auth/verificar`); el servidor valida ambos. El JWT resultante se envía como `Authorization: Bearer`. |
| Control de acceso | `redirect` del enrutador: sin sesión solo existe `/login`. El servidor valida cada petición. |
| Sesión única | Si el usuario ya tiene sesión en otro dispositivo (web o móvil), el servidor responde `409`; la app pide confirmar y reenvía el código con `cerrarSesionAnterior: true`. La sesión desplazada recibe `401` y vuelve al login con el motivo. |
| Expiración de sesión | El token trae `expiraEnMs`; un temporizador cierra la sesión al vencer. Además se cierra tras 15 min sin interacción (`GuardiaInactividad`). Un `401` en cualquier ruta protegida también la cierra. |
| Cierre de sesión | Llama a `/auth/logout` (el JWT queda revocado en el servidor) y borra el token del dispositivo y los datos en memoria (catálogo, historial). Si no hay red, igual se cierra localmente. |
| Credenciales | Token en `flutter_secure_storage`; la contraseña y el PIN no se guardan, no se registran y los campos los ocultan. |
| Validación | Formato de correo, celular, referencia, monto y PIN antes de enviar; el servidor vuelve a validar. |
| Errores | Se muestra el campo `mensaje` de la API (ya redactado para el usuario); los errores 5xx y de red usan textos genéricos. |

### No implementado

- **Imágenes de productos**: el catálogo no las incluye. Cada servicio se identifica con un monograma estable.
- **Registro de usuarios**: fuera del alcance de los módulos pedidos (`POST /clientes` requiere CURP, RFC y domicilio).

## Integración con la API

Contrato general de respuesta: `{ "codigo": 0|1, "mensaje": "...", "datos": ... }`. Los errores devuelven `codigo: 1` y el `mensaje` explica el motivo. El cliente HTTP (`core/network/api_client.dart`) desenvuelve este formato para todos los adaptadores.

### Autenticación

| | |
|---|---|
| Paso 1 | `POST /auth/login` (público) · `{ "correo", "password" }` → `datos: { desafioId, expiraEnMs, correoEnmascarado }`. 401 credenciales incorrectas · 403 inactivo · 429 demasiados intentos · 503 no se pudo enviar el correo |
| Paso 2 | `POST /auth/verificar` (público) · `{ "desafioId", "codigo", "cerrarSesionAnterior" }` → `datos: { token, tipo, expiraEnMs, usuario, nombreCompleto }`. 401 código incorrecto/vencido · 409 hay otra sesión activa · 429 demasiados intentos |
| Cierre | `POST /auth/logout` (Bearer) → 200; revoca la sesión en el servidor |
| Validación en la app | Correo con formato válido (máx. 100); contraseña obligatoria (máx. 72); código de exactamente 6 dígitos |
| Adaptador | `authentication/infraestructure/adapters/api_autenticacion_gateway.dart` |

### Catálogo

| | |
|---|---|
| Endpoint | `GET /productos` (Bearer) |
| Respuesta 200 | `datos: { total, productos: [{ producto, servicio, idServicio, idProducto, idCatTipoServicio, tipoFront, hasDigitoVerificador, tipoReferencia, precio, showAyuda, legend }] }` |
| Errores | 401 sesión inválida · 502 proveedor no disponible |
| Adaptador | `productos/infraestructure/adapters/api_catalogo_repository.dart` |

Cómo se interpreta cada producto (`productos/domain/entities/producto.dart`):

| `tipoFront` | Operación | Significado de `precio` |
|---|---|---|
| 1 | Recarga | Precio fijo; el monto enviado debe ser igual. Si `precio` es 0 o nulo, no se puede comprar. |
| 1 con `idCatTipoServicio` 10 u 11 | Código canjeable | Precio fijo; no pide celular. |
| 2 | Pago de servicio | Comisión que se suma al monto que indica el usuario. |
| Otros (p. ej. 4, consulta) | No disponible | — |

Las categorías de la interfaz (Recargas, Tarjetas y códigos, Pago de servicios, Otros) se derivan de esa tabla porque la API no entrega nombres de categoría.

### Cuentas

| | |
|---|---|
| Endpoint | `POST /cuentas/consultar` (Bearer) |
| Cuerpo | `{ "activo": true, "pagina": 0, "tamanio": 20 }` |
| Respuesta 200 | `datos: { datos: [{ id, numeroCuenta, saldo, estaActiva, ... }], pagina, totalPaginas, ... }` |
| Uso | Cuenta de origen y saldo para validar antes de pagar |
| Adaptador | `pagos/infraestructure/adapters/api_cuentas_repository.dart` |

### Compra

| | |
|---|---|
| Endpoint | `POST /pagos` (Bearer) |
| Cuerpo | `{ numeroCuenta (18 dígitos), idProducto, idServicio, monto (≥0.01, 2 decimales), telefono? (10 dígitos), referencia? (alfanumérica 1–100), pinAutorizacion (4–6 dígitos) }`. Un campo extra se rechaza. |
| Respuesta 200 | `datos: { estado, idTx, numAutorizacion, monto, comision, referencia, saldoDisponible, fecha, pin?, legend? }` |
| Tiempo de espera | 85 s (el servidor puede tardar hasta ~70 s) |
| Adaptador | `pagos/infraestructure/adapters/api_pagos_repository.dart` |

Cada respuesta se clasifica en `pagos/application/realizar_compra.dart`:

| Resultado en pantalla | Cuándo | Qué dice la app |
|---|---|---|
| **Compra exitosa** (recibo) | 200 y `estado == "APROBADO"` | Muestra el recibo. Es el único caso que cuenta como compra realizada. |
| **Compra rechazada** | 400, 403 (PIN), 404, 409 (duplicado), 422 (saldo o rechazo), 423 (PIN bloqueado), 429 (límite), 503 | Muestra el mensaje del servidor y que no hubo cargo. Permite corregir y reintentar. |
| **Compra pendiente** | 504 (pago en revisión), o 200 con otro estado | El saldo está retenido; no se debe repetir el pago. Extrae la referencia del mensaje y permite consultar su estado. |
| **Error de comunicación** | 502 (proveedor sin recibir el pago, sin cargo) · otros 5xx, sin red o tiempo agotado (resultado **incierto**) | Si es incierto, indica revisar el historial antes de reintentar. |

Un 401 cierra la sesión.

### Consulta de estado de un pago

| | |
|---|---|
| Endpoint | `POST /pagos/consulta` (Bearer) |
| Cuerpo | `{ "upc": "15 caracteres A-Z0-9" }` |
| Respuesta 200 | `datos: { upc, estado, mensaje, monto, comision, referencia, idTx?, numAutorizacion? }` con `estado` PENDIENTE, EN_REVISION, APROBADO o RECHAZADO |
| Errores | 400 formato · 404 no existe o es de otro usuario |

### Historial de compras

| | |
|---|---|
| Endpoint | `GET /pagos?pagina=0&tamanio=20` (Bearer) — **agregado al backend para este frontend** |
| Respuesta 200 | `datos: { datos: [{ upc, estado, producto, servicio, idProducto, idServicio, monto, comision, total, referencia, telefono, idTx, numAutorizacion, fecha }], pagina, tamanoPagina, totalElementos, totalPaginas }` |
| Filtro | Solo pagos `APROBADO` del usuario autenticado, del más reciente al más antiguo; `tamanio` máx. 100 |
| Adaptador | `ApiPagosRepository.historial` |

## Cambios en el backend

1. `GET /pagos` (historial): `PagoController`, `PagoService`, `PagoRegistroService`, `PagoRepository` y `HistorialPagoResponse`. El nombre del producto sale del catálogo local.
2. CORS configurable en `SecurityConfiguration` (antes estaba deshabilitado y el navegador bloqueaba a la web).

## Pruebas

```bash
flutter analyze
```

```bash
flutter test
```
