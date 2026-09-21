# GestoPago Windows

Cliente Flutter de escritorio para Windows. Permite crear una cuenta, iniciar sesión y consultar el catálogo de productos de la API.

## Ejecutar

Desde esta carpeta:

~~~powershell
flutter pub get
flutter run -d windows
~~~

La URL predeterminada es http://localhost:8080. Se puede cambiar al iniciar la app:

~~~powershell
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8080
~~~

## Flujo de la aplicación

- El registro envía usuario, contraseña y datos personales a POST /auth/register.
- El login envía usuario y contraseña a POST /auth/login y recibe un token Bearer.
- El catálogo se consulta en GET /productos con ese token.
- Después de un login correcto, Windows guarda de forma segura un verificador de contraseña para permitir el acceso local durante 7 días si la API no responde. La contraseña y el token no se guardan en la app.
- El catálogo recibido se guarda en %LOCALAPPDATA%\GestoPago\productos.json para mostrarlo sin conexión.

El registro requiere conexión con la API. Sin conexión, solo puede entrar un usuario que inició sesión previamente en este equipo y cuya sesión local aún no venció.

## Requisitos de Windows

Flutter debe tener habilitado el destino Windows y Visual Studio debe incluir el workload **Desktop development with C++** para compilar el runner nativo. En este equipo, flutter doctor también pide MSVC v142, CMake tools para Windows y Windows 10 SDK. Flutter requiere que Windows Developer Mode esté activo para crear los enlaces de sus plugins.
