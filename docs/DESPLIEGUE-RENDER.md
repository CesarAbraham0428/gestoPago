# Despliegue en Render

Tres recursos desde `render.yaml`: **API** (`backend/`), **web** (`frontend/`) y **Redis**. La base de datos es la
PostgreSQL de Render que ya tienes (no se crea otra: el plan gratuito permite solo una por cuenta). La app móvil
no se despliega: se compila como APK y apunta a la API.

## 1. Crear los servicios

1. **New > Blueprint**, elige este repositorio y confirma `render.yaml`.
2. Render pide los valores marcados con `sync: false`:

| Variable | Valor |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://<host>:5432/<base>` — host **interno** de tu base (sin dominio, p. ej. `dpg-xxxx-a`) si está en la misma región que la API; con el host externo agrega `?sslmode=require` |
| `DB_USERNAME`, `DB_PASSWORD` | los de esa base (Dashboard > tu base > Connections) |
| `GESTOPAGO_AUTH_ID_DISTRIBUIDOR`, `GESTOPAGO_AUTH_CODIGO_DISPOSITIVO`, `GESTOPAGO_AUTH_PASSWORD`, `GESTOPAGO_PAGOS_SECRET_KEY`, `GESTOPAGO_PAGOS_IV` | los de GestoPago (los de tu `.env`) |
| `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GMAIL_REFRESH_TOKEN`, `MAIL_REMITENTE` | API de Gmail para el código 2FA; `MAIL_REMITENTE` es la cuenta de Gmail autorizada |

`APP_JWT_SECRET` y `GESTOPAGO_TOKEN_KEY` los genera Render. `FORWARD_HEADERS=native`, `APP_2FA_CONSOLA=false`,
`SWAGGER_ENABLED=false` y las variables de Redis ya vienen en el archivo.

3. Si Render asigna URL distintas a `https://gestopago-api.onrender.com` y `https://gestopago-web.onrender.com`,
   corrige `CORS_ORIGENES` (API) y `API_BASE_URL` (web). `API_BASE_URL` se compila dentro de la app: cambiarla exige
   redesplegar la web.

### Correo del segundo factor

Render gratis **bloquea el SMTP** (puertos 25, 465 y 587), por eso el código se envía con la API HTTPS de Gmail,
que usa el 443. Para pruebas sin Gmail puedes poner `APP_2FA_CONSOLA=true`: el código aparece en los Logs de la API.

## 2. App Android

```powershell
cd frontend
flutter build apk -t lib/main_mobile.dart --release --obfuscate --split-debug-info=build/simbolos `
  --dart-define=API_BASE_URL=https://gestopago-api.onrender.com
```

El APK queda en `build/app/outputs/flutter-apk/app-release.apk`. Hacia internet la app exige HTTPS; el HTTP solo se
permite hacia `localhost`, `127.0.0.1` y `10.0.2.2` (desarrollo).

## 3. Verificación después de desplegar

1. `https://<api>/actuator/health` responde `{"status":"UP"}` (el primer acceso tarda si el servicio estaba dormido).
2. `https://<api>/clientes` sin sesión responde **401** con `Strict-Transport-Security` y `Content-Security-Policy`.
3. **IP real** (importante, no verificado en Render): envía más de 20 peticiones en un minuto y mira los Logs.
   ```powershell
   1..25 | ForEach-Object { try { Invoke-WebRequest -Method Post -Uri https://<api>/auth/login -ContentType application/json -Body '{}' -SkipHttpErrorCheck | Out-Null } catch {} }
   ```
   Debe aparecer `Límite de solicitudes superado: <tu IP pública> POST /auth/login`. Si ves una IP interna (`10.x`) o
   una que no es la tuya (p. ej. de un CDN), la API no está viendo al cliente real y los límites por IP serían
   compartidos: avísame con la IP que aparece para ajustar la configuración del proxy de confianza.
4. Inicia sesión en la web: el código llega al correo y se invalida tras 5 intentos incorrectos.

## 4. Qué protege la API

| Capa | Límite |
| --- | --- |
| Todas las rutas, por IP | 120 / min (`LIMITE_GLOBAL_POR_IP`) |
| Todas las rutas, por usuario con sesión | 120 / min (`LIMITE_GLOBAL_POR_USUARIO`) |
| Login, verificación 2FA y registro, ráfaga por IP | 20 / min (`LIMITE_RAFAGA_PUBLICA_POR_IP`) |
| Login por correo / por IP | 10 y 40 cada 15 min |
| Registro por IP | 10 / hora |
| Pagos por usuario | 5 / min |
| Código 2FA | 5 intentos, vence en 5 min, 30 s entre códigos |
| PIN de la cuenta | se bloquea 15 min tras 5 intentos |
| Cuerpo de petición | máximo 64 KB (`LIMITE_TAMANO_BYTES`) |

Al superar un límite la API responde **429** con la cabecera `Retry-After` (segundos); la web y la app móvil muestran
una cuenta regresiva y desactivan el envío. Las IPv6 se agrupan por prefijo /64. Si Redis no responde, los límites
siguen aplicándose en la memoria de la instancia. El bloqueo en el cliente es solo comodidad: el límite real lo
impone siempre el servidor.

> Limitación del plan gratuito de Render: la base de datos expira a los 30 días y los servicios web se duermen tras
> 15 minutos sin tráfico.
