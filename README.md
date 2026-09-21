# GestoAPI

API REST con Spring Boot que expone el catálogo de GestoPago y una aplicación Flutter de escritorio para registro, inicio de sesión y consulta de productos.

## Arquitectura

- `controller`: endpoints REST y traducción de errores HTTP.
- `service`: autenticación, fallback del catálogo y sincronización.
- `client`: integraciones HTTP con GestoPago, incluida la renovación del token.
- `repositorys` y `entity`: persistencia PostgreSQL mediante JPA y Flyway.
- `cache`: caché Redis del catálogo.
- `scheduler`: sincronización diaria del catálogo y revisión de vencimiento del token.
- `frontend`: cliente Flutter para Windows.

El endpoint `GET /productos` requiere Bearer Token de la API y consulta Redis → PostgreSQL → GestoPago. Las escrituras originadas en GestoPago siguen PostgreSQL → Redis. La sincronización reemplaza el catálogo solo si no existe uno anterior o si la cantidad nueva es mayor.

## Configuración local

1. Copia `.env.example` a `.env` y define credenciales de PostgreSQL, GestoPago y un `APP_JWT_SECRET` aleatorio de al menos 32 bytes. No compartas `.env` ni uses los valores de ejemplo fuera de desarrollo.
2. Inicia PostgreSQL y Redis.
3. Ejecuta la API desde la raíz:

   ```powershell
   .\gradlew.bat bootRun
   ```

La API escucha en `http://localhost:8080`. Flyway crea o actualiza las tablas al iniciar.

## Endpoints de autenticación

- `POST /auth/register`: crea una cuenta y devuelve un Bearer Token.
- `POST /auth/login`: valida una cuenta activa y devuelve un Bearer Token.
- `GET /productos`: devuelve el catálogo; requiere `Authorization: Bearer <token>`.
- `/swagger-ui/**` y `/v3/api-docs/**`: documentación OpenAPI.

Los detalles de las solicitudes y respuestas están en [docs/autenticacion.md](docs/autenticacion.md). Las reglas funcionales completas están en [especificacion-reglas-negocio-gestoapi.md](especificacion-reglas-negocio-gestoapi.md).

## Cliente Flutter

Desde `frontend/`:

```powershell
flutter pub get
flutter run -d windows
```

Se puede cambiar la URL del backend con `--dart-define=API_BASE_URL=http://localhost:8080`. Los requisitos de compilación de Windows están en [frontend/README.md](frontend/README.md).

## Pruebas

```powershell
.\gradlew.bat test
```

Las pruebas unitarias cubren la capa de servicio, persistencia y cliente de catálogo. El plan JMeter, sus parámetros y las métricas requeridas están en [perf/README.md](perf/README.md).
