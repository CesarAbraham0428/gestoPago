# Registro, login y catálogo protegido

## Datos almacenados

- La tabla registro contiene el usuario, el hash BCrypt de la contraseña y el estado activo.
- La tabla personas contiene nombre, apellidos, correo y teléfono. registro.persona_id vincula ambas tablas.
- La migración V3__create_registros_y_datos_personales.sql crea estas tablas o agrega correo y teléfono a una tabla personas existente.
- Las contraseñas se guardan con BCrypt. Nunca se guarda la contraseña original.

La aplicación ejecuta Flyway al iniciar. La base configurada en DB_URL, DB_USERNAME y DB_PASSWORD debe ser PostgreSQL y el usuario de base de datos debe poder crear y alterar tablas.

Una cuenta puede desactivarse desde la base de datos; los siguientes logins en línea serán rechazados:

~~~sql
UPDATE registro SET activo = FALSE WHERE usuario = 'ana.lopez';
~~~

## Endpoints

### Crear cuenta — POST /auth/register

~~~json
{
  "usuario": "ana.lopez",
  "password": "una-clave-de-8-o-mas",
  "nombre": "Ana",
  "apellidoPaterno": "López",
  "apellidoMaterno": "García",
  "correo": "ana@example.com",
  "telefono": "5512345678"
}
~~~

Devuelve HTTP 201 con un token Bearer y el nombre de usuario. El usuario se normaliza a minúsculas. Un usuario duplicado devuelve 409.

### Iniciar sesión — POST /auth/login

~~~json
{
  "usuario": "ana.lopez",
  "password": "una-clave-de-8-o-mas"
}
~~~

Devuelve HTTP 200 con token, tipo, expiraEnMs, usuario y nombreCompleto. Las credenciales incorrectas devuelven 401; una cuenta con activo = false devuelve 403.

### Consultar productos — GET /productos

Enviar el token recibido al iniciar sesión:

~~~http
Authorization: Bearer <token>
~~~

Sin token válido la respuesta es 401. Todas las rutas existentes requieren autenticación salvo registro, login, documentación OpenAPI y el endpoint de error.

## Configuración local

La API escucha en el puerto 8080 por defecto. Se puede cambiar con SERVER_PORT. Define APP_JWT_SECRET con una clave aleatoria de al menos 32 bytes; la aplicación no tiene una clave secreta integrada y no inicia si falta esta variable. El token dura 8 horas por defecto y se puede cambiar con APP_JWT_EXPIRATION_MS.

La URL, los timeouts y el ritmo de renovación de GestoPago se configuran mediante las variables `GESTOPAGO_AUTH_URL`, `GESTOPAGO_AUTH_CONNECT_TIMEOUT_MS`, `GESTOPAGO_AUTH_READ_TIMEOUT_MS`, `GESTOPAGO_AUTH_REFRESH_RATE_MS` y `GESTOPAGO_AUTH_RENEWAL_CHECK_MS`. La API revisa el vencimiento indicado por `expires_in` y, si el catálogo responde 401/403, renueva el token y reintenta la consulta una vez. Los fallos de autenticación, HTTP, timeout y comunicación se registran por categoría sin incluir el token ni la contraseña en logs o respuestas.

Inicia el backend desde la raíz del repositorio después de configurar PostgreSQL, Redis y los valores de GestoPago en el archivo .env:

~~~powershell
.\gradlew.bat bootRun
~~~

La app Windows está en frontend. El comando para iniciar es flutter run -d windows; la URL de la API puede cambiarse con --dart-define=API_BASE_URL=http://localhost:8080.

Después de un login correcto, la app guarda un verificador cifrado en el almacenamiento seguro de Windows durante 7 días. Si la API no responde, compara usuario y contraseña con ese verificador. No guarda la contraseña ni el JWT. El catálogo se almacena en %LOCALAPPDATA%\GestoPago\productos.json. Las cuentas se pueden usar sin conexión en el equipo donde hayan iniciado sesión antes; los cambios de estado de la cuenta solo se comprueban cuando la API vuelve a estar disponible.
