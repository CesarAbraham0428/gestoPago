# GestoAPI

API REST para registrar y administrar clientes, domicilios, cuentas bancarias y usuarios de acceso. Incluye autenticación con JWT y consulta del catálogo de productos de GestoPago. Está desarrollada con Java 17, Spring Boot, PostgreSQL y Redis; el backend se ejecuta de forma independiente del contenido de `frontend/`.

## Solución implementada

El registro valida los datos y guarda **cliente, domicilio, cuenta bancaria y usuario en una sola transacción**. Si una operación falla, se revierte el registro completo. La cuenta se asocia al cliente, recibe un número único de 18 dígitos generado mediante una secuencia y comienza **ACTIVA**, con saldo de **0.00** definido por el sistema. El usuario también inicia activo y utiliza el correo del cliente para acceder.

La aplicación separa controladores REST, servicios de negocio y repositorios JPA. PostgreSQL almacena las tablas `clientes` (datos personales, de contacto y laborales), `domicilios` (dirección), `cuentas` (número, saldo y estado) y `usuarios` (correo y hash de contraseña). Flyway administra el esquema; las restricciones y los triggers refuerzan la unicidad, la integridad y la baja lógica.

Las contraseñas se almacenan como **hash BCrypt**, nunca en texto plano. El login verifica las credenciales y que usuario y cliente estén activos antes de emitir un JWT. Los servicios protegidos requieren `Authorization: Bearer <token>`; la consulta y el cambio de contraseña de un usuario solo se permiten a su propietario.

El catálogo se consulta en el orden Redis → PostgreSQL → GestoPago. Los datos obtenidos de GestoPago se guardan en PostgreSQL y después en Redis. Una tarea diaria sincroniza el catálogo y otra revisa la renovación del token de GestoPago, que es distinto del JWT de acceso a esta API. La sincronización reemplaza el catálogo si no existe uno previo o si el nuevo contiene más productos.

## Reglas de negocio y validaciones

- **Edad:** el cliente debe tener al menos 18 años; la fecha de nacimiento es obligatoria y no puede ser futura.
- **Identidad:** CURP y RFC son obligatorios, únicos e inmodificables. Se valida su formato mediante expresiones regulares: CURP de 18 caracteres y RFC de 12 o 13.
- **Nombres:** primer nombre y ambos apellidos son obligatorios; segundo nombre es opcional. Cada valor proporcionado admite solo letras y espacios, con longitud de 2 a 50 caracteres.
- **Contacto:** correo obligatorio, válido, único y de máximo 100 caracteres. El correo del usuario también es único y se sincroniza cuando cambia el del cliente. El teléfono móvil es obligatorio y debe tener exactamente 10 dígitos; el alternativo, si se proporciona, cumple la misma longitud.
- **Domicilio e ingresos:** domicilio obligatorio, código postal de exactamente 5 dígitos e ingreso mensual mayor que cero.
- **Cuentas:** número de cuenta único e inmodificable; saldo inicial no negativo, fijado actualmente en cero. Solo los clientes activos pueden tener cuentas activas.
- **Usuarios:** cada cliente tiene un único usuario asociado. La contraseña requiere al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial; se admite un máximo de 72 bytes UTF-8 y 72 caracteres por el uso de BCrypt.
- **Actualización:** se pueden modificar datos personales, de contacto, domicilio e información laboral de clientes activos. CURP, RFC y número de cuenta no se pueden modificar.
- **Baja lógica:** desactivar un cliente conserva sus datos y desactiva automáticamente sus cuentas y su usuario. Las cuentas solo se activan o desactivan; no se eliminan físicamente. No se puede reactivar una cuenta si su cliente está inactivo.
- **Acceso:** solo los usuarios autenticados pueden consumir los endpoints protegidos. Se rechaza el login con credenciales incorrectas o con usuario o cliente inactivo.

## Endpoints de la API

URL local predeterminada: `http://localhost:8080`. El registro y el login son públicos; los demás endpoints de esta tabla requieren JWT.

| Método | Ruta | Función |
| --- | --- | --- |
| POST | `/clientes` | Registrar cliente, domicilio, cuenta y usuario; incluye `password` en el cuerpo. |
| POST | `/auth/login` | Iniciar sesión con `correo` y `password`; devuelve el token JWT. |
| GET | `/clientes` | Listar clientes o buscar mediante filtros. |
| GET | `/clientes/{id}` | Consultar el detalle de un cliente por ID. |
| PATCH | `/clientes/{id}` | Actualizar solo los campos enviados, incluido el domicilio. |
| DELETE | `/clientes/{id}` | Dar de baja lógica al cliente, sus cuentas y su usuario. |
| GET | `/cuentas` | Listar cuentas o buscar mediante filtros. |
| GET | `/cuentas/id/{id}` | Consultar una cuenta por su ID interno. |
| GET | `/cuentas/{numeroCuenta}` | Consultar una cuenta por su número. |
| GET | `/cuentas/{numeroCuenta}/saldo` | Consultar el saldo disponible. |
| PUT | `/cuentas/{numeroCuenta}/estado` | Activar o desactivar con `{"activa": true}` o `{"activa": false}`. |
| DELETE | `/cuentas/{numeroCuenta}` | Desactivar la cuenta mediante baja lógica. |
| GET | `/usuarios/{id}` | Consultar el propio usuario. |
| PUT | `/usuarios/{id}/password` | Cambiar la propia contraseña con `passwordActual` y `passwordNueva`. |
| GET | `/productos` | Consultar el catálogo de GestoPago. |

**Filtros de clientes:** `id`, `curp`, `rfc`, `correo`, `numeroCuenta`, `activo`, `desde` y `hasta`. Se pueden combinar; las fechas usan `YYYY-MM-DD` y filtran la fecha de registro, incluyendo ambos días en la zona `America/Mexico_City`.

```http
GET /clientes?curp=VALOR_CURP
GET /clientes?rfc=VALOR_RFC
GET /clientes?correo=cliente%40ejemplo.com
GET /clientes?numeroCuenta=000000000000000001
GET /clientes?activo=true
GET /clientes?desde=2026-10-01&hasta=2026-10-09
```

**Filtros de cuentas:** `id`, `saldo` (coincidencia exacta) y `activo`; también se admite `activa` como alias de `activo`. Por ejemplo: `GET /cuentas?activo=true`.

Ambos listados aceptan `pagina` (desde 0, por defecto 0) y `tamanio` (por defecto 20, de 1 a 100). Los resultados están en `content`: los listados sin filtros devuelven resúmenes y las búsquedas con filtros devuelven información completa. Una búsqueda filtrada sin coincidencias devuelve `404`.

Swagger y la especificación OpenAPI son públicos y permiten consultar los cuerpos y respuestas:

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [OpenAPI](http://localhost:8080/v3/api-docs)

## Manejo de errores

Las excepciones personalizadas distinguen cliente ya registrado, CURP/RFC/correo duplicados, cliente o cuenta no encontrados, validaciones y errores de autenticación. El manejador central responde con `codigo` y `mensaje`.

| HTTP | Motivo |
| --- | --- |
| 400 | Datos, formato, filtros o contraseña inválidos. |
| 401 | Credenciales incorrectas o falta de autenticación válida. |
| 403 | Usuario o cliente inactivo en el login, o acceso a otro usuario. |
| 404 | Cliente, cuenta o usuario no encontrado. |
| 409 | Registro duplicado o conflicto con el estado del cliente. |
| 500 | Error interno o de persistencia. |
| 502 | Error al comunicarse con GestoPago. |

## Ejecución local

1. Instala Java 17 e inicia PostgreSQL y Redis.
2. Copia `.env.example` a `.env` y configura las credenciales de PostgreSQL, GestoPago y `APP_JWT_SECRET` con un valor aleatorio de al menos 32 bytes. No compartas este archivo.
3. Ejecuta desde la raíz del proyecto:

   ```powershell
   .\gradlew.bat bootRun
   ```

Flyway crea el esquema en una base vacía. Para la instalación mediante SQL manual, consulta [las instrucciones de base de datos](docs/ENTREGA-BASE-DATOS.md) y [el script de creación](scripts/crear-base-datos.sql); con ese script, configura `SPRING_FLYWAY_BASELINE_VERSION=5` en el primer inicio. Las tablas históricas `personas` y `registro` se conservan, pero ya no participan en la API.

## Pruebas

```powershell
.\gradlew.bat test
```

La suite cubre validación, clientes, cuentas, usuarios, autenticación, JWT y catálogo. Para verificar también PostgreSQL, instala sus herramientas cliente en `PATH` y ejecuta:

```powershell
.\scripts\test-postgres.ps1
.\scripts\test-postgres.ps1 -EsquemaManual
```

Estas ejecuciones verifican, respectivamente, las migraciones en una base vacía y la adopción del esquema manual. Crean y eliminan una base temporal, por lo que requieren permiso para crear bases. Sin `TEST_DB_URL`, las pruebas de PostgreSQL se omiten en `gradlew test`.
