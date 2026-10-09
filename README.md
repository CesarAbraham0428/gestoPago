# GestoAPI

API REST con Spring Boot para clientes, domicilios, cuentas bancarias, usuarios y catálogo de GestoPago. El backend funciona de forma independiente y se prueba con Swagger o un cliente HTTP.

## Arquitectura

- `controller`: endpoints REST y traducción de errores HTTP.
- `service`: autenticación, fallback del catálogo y sincronización.
- `client`: integraciones HTTP con GestoPago, incluida la renovación del token.
- `repositorys` y `entity`: persistencia PostgreSQL mediante JPA y Flyway.
- `cache`: caché Redis del catálogo.
- `scheduler`: sincronización diaria del catálogo y revisión de vencimiento del token.

El endpoint `GET /productos` requiere Bearer Token de la API y consulta Redis → PostgreSQL → GestoPago. Las escrituras originadas en GestoPago siguen PostgreSQL → Redis. La sincronización reemplaza el catálogo solo si no existe uno anterior o si la cantidad nueva es mayor.

## Configuración local

1. Copia `.env.example` a `.env` y define credenciales de la BD `gestopago`, GestoPago y un `APP_JWT_SECRET` aleatorio de al menos 32 bytes. No compartas `.env` ni uses los valores de ejemplo fuera de desarrollo.
2. Inicia PostgreSQL y Redis.
3. Ejecuta la API desde la raíz:

   ```powershell
   .\gradlew.bat bootRun
   ```

La API escucha en `http://localhost:8080`. Flyway conserva V1/V2 (productos y tokens) y V3 por historial. V4 crea el esquema de clientes en una BD vacía o adopta las tablas ya creadas con el script manual, sin recrearlas. V5 completa las validaciones de nombres, amplía nacionalidad y ajusta el almacenamiento BCrypt. Las tablas antiguas `personas` y `registro` se conservan por historial, pero ya no participan en la API.

## Despliegue de la API en Render

El repositorio incluye un `Dockerfile` multi etapa para compilar con Java 17 y ejecutar solo el JAR de Spring Boot. `render.yaml` declara el servicio web, el chequeo de salud y las variables requeridas. El `.dockerignore` excluye el frontend, artefactos locales y archivos `.env` del contexto de la imagen.

1. Sube la rama que quieres desplegar a tu repositorio Git y crea un Blueprint en Render apuntando a ese repositorio. Render tomará la configuración de `render.yaml` y construirá el servicio a partir del `Dockerfile`.
2. Crea o selecciona una base PostgreSQL en la misma región que el servicio. Configura `DB_URL` con el formato `jdbc:postgresql://HOST:PUERTO/BASE`, usando el host interno de Render, y configura por separado `DB_USERNAME` y `DB_PASSWORD`. El usuario de base de datos necesita permisos para que Flyway ejecute las migraciones durante el arranque.
3. Proporciona `GESTOPAGO_AUTH_ID_DISTRIBUIDOR`, `GESTOPAGO_AUTH_CODIGO_DISPOSITIVO` y `GESTOPAGO_AUTH_PASSWORD` cuando Render lo solicite. Render generará `APP_JWT_SECRET`; consérvalo entre despliegues para que los JWT ya emitidos sigan validándose.
4. Redis es opcional para la API: sin Redis, el catálogo utiliza el respaldo en PostgreSQL y no se sirve desde caché.

Spring Boot escucha en el `PORT` que Render asigna. El chequeo configurado en `/actuator/health` es público y comprueba la salud de la aplicación y PostgreSQL; Redis no afecta ese resultado porque solo se usa como caché opcional.

## Endpoints de autenticación

- `POST /clientes`: registra cliente, domicilio, cuenta y usuario en una transacción (público).
- `POST /auth/login`: valida correo y contraseña de un usuario activo y devuelve un Bearer Token (público).
- `GET /clientes`, `GET /clientes/{id}`, `PATCH /clientes/{id}` y `DELETE /clientes/{id}`: consulta, actualización y baja lógica.
- `GET /cuentas`, `GET /cuentas/{numeroCuenta}` y `GET /cuentas/{numeroCuenta}/saldo`: consultas bancarias.
- `PUT /cuentas/{numeroCuenta}/estado` y `DELETE /cuentas/{numeroCuenta}`: activación/desactivación lógica.
- `GET /usuarios/{id}` y `PUT /usuarios/{id}/password`: consulta y cambio de contraseña del propio usuario.
- `GET /productos`: devuelve el catálogo; requiere `Authorization: Bearer <token>`.
- `/swagger-ui/**` y `/v3/api-docs/**`: documentación OpenAPI.

Los listados de clientes y cuentas usan `pagina` (0 por defecto) y `tamanio` (20 por defecto, máximo 100), con resultados en `content`. Los listados globales son resúmenes y los detalles/filtros incluyen información completa. Los cambios de contrato, privacidad y optimización están en [docs/revision-clientes-cuentas.md](docs/revision-clientes-cuentas.md).

Los detalles de las solicitudes y respuestas están en [docs/autenticacion.md](docs/autenticacion.md).
La revisión contra los requisitos y los tipos de datos se documentan en [docs/revision-requerimientos.md](docs/revision-requerimientos.md).

No hay configuración CORS ni dependencia de Flutter en el backend. Los archivos existentes de `frontend/` se conservan como trabajo previo y no se compilan ni ejecutan para la API.

## Pruebas

```powershell
.\gradlew.bat test
```

Las pruebas cubren validación, clientes, cuentas, usuarios, autenticación, JWT y catálogo. El diseño, aislamiento y alcance de cada capa se documentan en [docs/pruebas-api.md](docs/pruebas-api.md). El plan JMeter, sus parámetros y las métricas requeridas están en [perf/README.md](perf/README.md).

Para probar también el esquema real de PostgreSQL, instala las herramientas cliente de PostgreSQL en PATH y ejecuta:

```powershell
.\scripts\test-postgres.ps1
.\scripts\test-postgres.ps1 -EsquemaManual
```

La primera ejecución prueba migraciones sobre una BD vacía; la segunda prueba la adopción del esquema manual. Ambas crean una BD temporal y la eliminan al terminar. El usuario PostgreSQL necesita permiso para crear bases. Sin `TEST_DB_URL`, la suite PostgreSQL se omite en `gradlew test`.
