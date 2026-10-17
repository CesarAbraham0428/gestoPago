# GestoAPI

API REST para registrar y administrar clientes, domicilios, cuentas bancarias y usuarios de acceso. Incluye autenticación con JWT y consulta del catálogo de productos de GestoPago. Está desarrollada con Java 17, Spring Boot, PostgreSQL y Redis; el backend se ejecuta de forma independiente del contenido de `frontend/`.

## Solución implementada

El registro valida los datos y guarda **cliente, domicilio, cuenta bancaria y usuario en una sola transacción**. Si una operación falla, se revierte el registro completo. La cuenta se asocia al cliente, recibe un número único de 18 dígitos generado mediante una secuencia y comienza **ACTIVA**, con saldo de **0.00** definido por el sistema. El usuario también inicia activo y utiliza el correo del cliente para acceder.

La aplicación separa controladores REST, servicios de negocio y repositorios JPA. PostgreSQL almacena las tablas `clientes` (datos personales, de contacto y laborales), `domicilios` (dirección), `cuentas` (número, saldo y estado) y `usuarios` (correo y hash de contraseña). Flyway administra el esquema; las restricciones y los triggers refuerzan la unicidad, la integridad y la baja lógica.

Las contraseñas se almacenan como **hash BCrypt**, nunca en texto plano. El login verifica las credenciales y que usuario y cliente estén activos antes de emitir un JWT. Los servicios protegidos requieren `Authorization: Bearer <token>`; las operaciones de usuario se limitan al propietario y las de clientes/cuentas verifican propietario o administrador.

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
| POST | `/clientes` | Registrar cliente, domicilio, cuenta y usuario. |
| POST | `/auth/login` | Iniciar sesión con `correo` y `password`. |
| POST | `/clientes/consultar` | Consultar clientes completos con filtros en un request JSON. |
| PATCH | `/clientes` | Identificar por RFC/CURP y modificar solo los campos enviados. |
| POST | `/clientes/desactivar` | Dar de baja lógica mediante RFC o CURP. |
| POST | `/cuentas/consultar` | Consultar cuentas completas con filtros JSON. |
| PATCH | `/cuentas/estado` | Activar/desactivar con `numeroCuenta` y `activa`. |
| GET | `/usuarios/me` | Consultar el usuario autenticado. |
| POST | `/usuarios/consultar` | Consultar usuarios mediante filtros combinables en JSON. |
| PUT | `/usuarios/me/password` | Cambiar su contraseña con `passwordActual` y `passwordNueva`. |
| GET | `/productos` | Consultar el catálogo de GestoPago. |

Los controladores no utilizan `@PathVariable`, `@RequestParam` ni `@ModelAttribute`: todas las entradas y criterios se reciben mediante Request DTO con `@Valid @RequestBody`. Cada recurso tiene un único endpoint de búsqueda con filtros combinables. RFC, CURP y número de cuenta identifican el recurso, pero no conceden acceso: `AutorizacionService` consulta el usuario y el rol reales en la base. Un cliente solo puede acceder a sus propios datos, usuario y cuentas; un administrador activo puede administrar otros clientes. Las listas aplican el permiso antes de paginar y contar. La consulta y contraseña de `/usuarios/me` siempre corresponden al usuario autenticado, incluso para administradores.

**Filtros de clientes:** `rfc`, `curp`, `nombre`, `correo`, `numeroCuenta`, `activo`, `desde`, `hasta`, `pagina` y `tamanio`. Los filtros presentes se combinan con AND; nombre busca una coincidencia parcial en nombres o apellidos. RFC y CURP se comparan completos sin distinguir mayúsculas. Las fechas usan `YYYY-MM-DD`, años de 1 a 9999 y filtran la fecha de registro incluyendo ambos días en `America/Mexico_City`. No se admite filtrar por `id`.

```json
{
  "nombre": "Ana",
  "pagina": 0,
  "tamanio": 20
}
```

Envíalo a `POST /clientes/consultar` con JWT y `Content-Type: application/json`. Se retiró `GET /clientes`; los filtros se envían exclusivamente en el cuerpo JSON.

**Filtros de cuentas:** `activo`, `saldo` (coincidencia exacta), `numeroCuenta`, `pagina` y `tamanio`. La página inicia en cero y el tamaño admitido es de 1 a 100. Un filtro sin coincidencias devuelve una lista vacía con HTTP 200.

**Filtros de usuarios:** `rfc`, `curp`, `correo`, `nombre`, `activo`, `pagina` y `tamanio`. Se envían a `POST /usuarios/consultar`. Los filtros se combinan con AND; `nombre` busca coincidencias parciales en nombres o apellidos. RFC y CURP corresponden al cliente asociado; ambos y el correo se comparan completos sin distinguir mayúsculas. Un usuario normal solo obtiene su propio registro, incluso al buscar por los datos de otro cliente. Los administradores pueden consultar todos los registros, incluidos los inactivos. Nunca se devuelve el hash de contraseña. Los filtros `id` y `clienteId` se rechazan con HTTP 400; los IDs se conservan internamente para relaciones y autorización.

```json
{
  "rfc": "PERA900101AB1",
  "curp": "PERA900101MDFRZN01",
  "correo": "ana@example.com",
  "nombre": "Ana",
  "activo": true,
  "pagina": 0,
  "tamanio": 20
}
```

Las listas mantienen un contrato uniforme y conservan la paginación. Cada cliente devuelve identidad, contacto, información laboral, rol, estado, fechas, domicilio, cuentas completas y usuario sin credenciales. Cada cuenta incluye `id`, `clienteId`, `numeroCuenta`, `saldo`, `estaActiva`, `fechaCreacion` y `fechaActualizacion`. Las búsquedas siguen limitadas a los datos autorizados para el usuario autenticado; un administrador puede consultar todos los clientes y cuentas. Los hashes de contraseña nunca se devuelven.

Ejemplo de la estructura de una lista:

```json
{
  "codigo": 0,
  "mensaje": "Consulta de usuarios realizada",
  "datos": {
    "datos": [],
    "pagina": 0,
    "tamanoPagina": 20,
    "totalElementos": 0,
    "totalPaginas": 0
  }
}
```

Para consultar un cliente específico, envía `{"rfc":"PERA900101AB1"}` o `{"curp":"PERA900101MDFRZN01"}` a `POST /clientes/consultar`. Para consultar una cuenta específica, envía `{"numeroCuenta":"000000000000000001"}` a `POST /cuentas/consultar`. Ambos devuelven una lista paginada con los datos completos; si no hay coincidencias, devuelven una lista vacía con HTTP 200.

Para desactivar un cliente, envía RFC o CURP a `POST /clientes/desactivar`. Debe existir al menos un identificador; si envías ambos, tienen que corresponder al mismo cliente. Esta operación devuelve HTTP 404 si no existe.

La actualización parcial usa un request que separa identificación y cambios:

```json
{
  "identificacion": {"rfc": "PERA900101AB1"},
  "cambios": {
    "empresa": "Nueva empresa",
    "telefonoMovil": "5598765432",
    "domicilio": {"calle": "Insurgentes"}
  }
}
```

Envíalo a `PATCH /clientes`. Los campos omitidos conservan su valor; `null` explícito limpia un campo opcional y se rechaza para los obligatorios. RFC, CURP, rol, estado, contraseña y cuentas no se pueden cambiar por este endpoint. La actualización conserva el bloqueo del cliente y valida el resultado completo antes de modificar entidades.

El saldo se obtiene dentro de cada cuenta devuelta por `POST /cuentas/consultar`; puedes filtrar por `numeroCuenta`. Para cambiar su estado, envía `{"numeroCuenta":"000000000000000001","activa":false}` a `PATCH /cuentas/estado`. Un cliente no puede consultar, activar ni desactivar cuentas ajenas.

## Organización de DTO y errores

Los DTO de entrada están en `models/request`; los de salida en `models/response`, incluidos los DTO de integración en `models/response/gestopago`. `ClienteMapper` pertenece a `mapper`; las entidades JPA permanecen en `entity/sf`.

`controller/advice/GlobalExceptionHandler.java` es el único manejador MVC con `@RestControllerAdvice` y métodos específicos con `@ExceptionHandler`. Centraliza errores de Bean Validation (`MethodArgumentNotValidException`, `ConstraintViolationException` y validación de parámetros), JSON, tipos, métodos HTTP, rutas, permisos, reglas de negocio y persistencia. Reemplaza los dos advice anteriores y reutiliza los estados y encabezados HTTP de Spring, incluido `Allow` para HTTP 405. Los controladores solo delegan en servicios y construyen respuestas; no contienen validaciones manuales ni bloques `try-catch`. Los repositorios Spring Data JPA consultan y persisten las entidades; los controladores reciben y devuelven DTO.

`GenericResponse<T>` define dos formas del contrato público: los éxitos contienen únicamente `codigo`, `mensaje` y `datos`; los fallos contienen únicamente `codigo` y `mensaje`. Se conserva `codigo` numérico (0 en éxitos y 1 en errores). Los errores omiten `datos` y no existe una propiedad pública `errores`. Las validaciones describen los campos incorrectos dentro de `mensaje`, sin incluir los valores rechazados. El login devuelve el token dentro de `datos`; el saldo forma parte de cada cuenta en `datos.datos`. Desactivar un cliente y cambiar una contraseña devuelven HTTP 200 con mensaje de confirmación y `datos: null`; el registro devuelve HTTP 201. Los consumidores deben leer los resultados exitosos dentro de `datos`.

`ApiSecurityErrorWriter` aplica el mismo contrato a los errores de autenticación y autorización que ocurren en los filtros, antes de MVC. Los códigos internos de recuperación del catálogo se procesan en el servicio; el contrato público del catálogo solo incluye `total` y `productos` dentro de `datos`.

Los endpoints declaran JSON como formato de salida. Si `Accept` exige un formato no admitido, la API devuelve HTTP 406 antes de ejecutar la operación. El manejador global fija `Content-Type: application/json` también en los errores, conservando su estado y encabezados, para que el cuerpo uniforme pueda serializarse incluso con un `Accept` incompatible.

Los errores usan, por ejemplo, `{"codigo":1,"mensaje":"Correo electrónico duplicado"}` con HTTP 409. Se utiliza HTTP 400 para datos inválidos, 401 para autenticación requerida, 403 para permisos insuficientes y 404 para recursos inexistentes/no disponibles. Las validaciones identifican todos los campos inválidos dentro de `mensaje`, también en DTO anidados y en la actualización parcial:

```json
{
  "codigo": 1,
  "mensaje": "Verifique la información capturada e intente nuevamente. correoElectronico: El correo electrónico no tiene un formato válido.; primerNombre: Es obligatorio y no puede contener solo espacios."
}
```

Los errores internos se registran con logging para diagnóstico. Las respuestas al cliente no exponen SQL, nombres de restricciones, contraseñas, trazas ni respuestas del proveedor externo.

Los mensajes generales de Bean Validation se definen una sola vez en `src/main/resources/ValidationMessages.properties`, en español. Las anotaciones conservan los mensajes específicos cuando una regla lo requiere.

Los Request DTO excluyen ID generado, rol, saldo, número de cuenta, estado inicial y fechas de auditoría del registro y de las actualizaciones generales. Los campos desconocidos se rechazan también en DTO anidados. El estado de las cuentas solo cambia mediante la operación autorizada `/cuentas/estado`. `@PasswordSegura` ejecuta las reglas de contraseña mediante Bean Validation al registrar o cambiar una contraseña; reutiliza la misma regla que los servicios.

## Manejo de errores

Las excepciones personalizadas distinguen cliente ya registrado, CURP/RFC/correo duplicados, cliente/cuenta/usuario no encontrados, validaciones y errores de autenticación. El manejador central responde con el contrato `GenericResponse`.

`@ExceptionHandler({ExcepcionA.class, ExcepcionB.class})` permite compartir un manejador entre varias excepciones; la anotación recibe un arreglo de clases. Los errores de JSON y de tipos comparten HTTP 400. Los demás errores HTTP de Spring reutilizan su estado y encabezados mediante `ErrorResponse`, y los errores inesperados y de persistencia comparten el manejador 500 con logging.

Las excepciones de negocio conservan mensajes específicos para correos, RFC y CURP duplicados. Cuando la base de datos detecta directamente una restricción, la respuesta usa un mensaje general controlado: HTTP 400 para restricciones de validación/nulos y 409 para los demás conflictos. No se interpretan nombres internos de restricciones para construir mensajes públicos.

| HTTP | Motivo |
| --- | --- |
| 400 | Datos, formato, filtros o contraseña inválidos. |
| 401 | Credenciales incorrectas o falta de autenticación válida. |
| 403 | Usuario o cliente inactivo en el login, o acceso a otro usuario. |
| 404 | Cliente, cuenta o usuario no encontrado. |
| 409 | Registro duplicado o conflicto con el estado del cliente. |
| 405 / 406 / 415 | Método HTTP, formato de respuesta o tipo de contenido no admitido. |
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

## Despliegue de la API en Render

El repositorio incluye un `Dockerfile` multi etapa para compilar con Java 17 y ejecutar solo el JAR de Spring Boot. `render.yaml` declara el servicio web, el chequeo de salud y las variables requeridas. El `.dockerignore` excluye el frontend, artefactos locales y archivos `.env` del contexto de la imagen.

El servicio existente utiliza la rama `feat/deploy`. Con Auto-Deploy activado para esa rama, cada push inicia un despliegue; se conservan el servicio, la base de datos y sus variables de entorno.

Para configurar un servicio por primera vez:

1. Sube `feat/deploy` a tu repositorio Git y crea un Blueprint en Render apuntando a ese repositorio. Render tomará la configuración de `render.yaml` y construirá el servicio a partir del `Dockerfile`.
2. Crea o selecciona una base PostgreSQL en la misma región que el servicio. Configura `DB_URL` con el formato `jdbc:postgresql://HOST:PUERTO/BASE`, usando el host interno de Render, y configura por separado `DB_USERNAME` y `DB_PASSWORD`. El usuario de base de datos necesita permisos para que Flyway ejecute las migraciones durante el arranque.
3. Proporciona `GESTOPAGO_AUTH_ID_DISTRIBUIDOR`, `GESTOPAGO_AUTH_CODIGO_DISPOSITIVO` y `GESTOPAGO_AUTH_PASSWORD` cuando Render lo solicite. Render generará `APP_JWT_SECRET`; consérvalo entre despliegues para que los JWT ya emitidos sigan validándose.
4. Redis es opcional para la API: sin Redis, el catálogo utiliza el respaldo en PostgreSQL y no se sirve desde caché.

Spring Boot escucha en el `PORT` que Render asigna. El chequeo configurado en `/actuator/health` es público y comprueba la salud de la aplicación y PostgreSQL; Redis no afecta ese resultado porque solo se usa como caché opcional.

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
