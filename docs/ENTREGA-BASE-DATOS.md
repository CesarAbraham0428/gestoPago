# GestoPago — entrega de base de datos

## Ubicación y ejecución

El script de la base está en **`scripts/crear-base-datos.sql`**. La carpeta
`scripts` contiene los archivos ejecutables y `docs` contiene la documentación.

En pgAdmin o DBeaver:

1. Conectado a `postgres`, selecciona y ejecuta únicamente el bloque
   `CREATE DATABASE gestopago ...;`, con autocommit activado. Si la base existe,
   omite este paso.
2. Abre una conexión / Query Tool a `gestopago`.
3. Del mismo archivo, selecciona y ejecuta desde `BEGIN;` hasta `COMMIT;`.
   Ese bloque crea todas las tablas, columnas, relaciones, restricciones,
   índices, secuencias, enumeraciones, funciones y triggers.

PostgreSQL no cambia de base dentro de una conexión SQL; por eso el editor
requiere ejecutar los dos bloques con conexiones distintas. Ejecuta el esquema
sobre una base vacía. Crear la base debe hacerse fuera de una transacción.

## Script de pruebas

`scripts/test-postgres.ps1` automatiza pruebas del backend sobre PostgreSQL:
lee las credenciales de `.env`, crea una base temporal con nombre aleatorio,
aplica las migraciones o el esquema manual, ejecuta las pruebas con Gradle y
elimina únicamente esa base temporal al terminar. No instala la base definitiva.

Sin opciones prueba las migraciones. `-EsquemaManual` carga el bloque de esquema
de `scripts/crear-base-datos.sql`. `-InstalacionCompleta` usa también el bloque
`CREATE DATABASE` del archivo, con el nombre sustituido por uno temporal.

## Contenido

| Elementos | Incluidos |
| --- | --- |
| Tablas de la API | `clientes`, `domicilios`, `cuentas`, `usuarios`, `producto`, `gestopago_tokens` |
| Tablas históricas | `personas`, `registro`, conservadas por compatibilidad con la migración V3; la API actual no las usa |
| Enumeraciones | Sexo, estado civil y rol |
| Integridad | Identificadores automáticos, unicidad de CURP/RFC/correos/cuentas y relaciones entre tablas |
| Índices adicionales | Correo sin distinción de mayúsculas, fecha de creación de clientes y cliente de una cuenta |
| Triggers | Auditoría, mayoría de edad, normalización, identidad inmutable, registro completo, sincronización de correo y baja lógica |

Las claves primarias y restricciones `UNIQUE` también crean índices
automáticamente; el script no los duplica. El registro de cliente, domicilio,
usuario y primera cuenta debe realizarse dentro de **una misma transacción**:
la integridad del registro completo se verifica al confirmar (`COMMIT`).

No se incluyen datos personales, contraseñas, tokens ni un catálogo ficticio.
El catálogo se obtiene mediante la integración de la API con GestoPago.

## Tipos de datos revisados

| Campo o grupo | Tipo | Motivo y correspondencia con Java |
| --- | --- | --- |
| IDs y relaciones | `INTEGER` con identidad o `SERIAL` | Compatible con `Integer`; suficiente para el alcance académico |
| Saldo e ingreso mensual | `NUMERIC(18,2)` | Importes exactos con dos decimales; `BigDecimal`, sin errores de punto flotante |
| Precio de producto | `NUMERIC(12,2)` | Compatible con el precio `BigDecimal` del catálogo |
| Fecha de nacimiento | `DATE` | Fecha civil sin hora; `LocalDate` |
| Auditoría de clientes, domicilios, cuentas y usuarios | `TIMESTAMPTZ` | Instantes absolutos; `Instant` |
| Auditoría de tokens | `TIMESTAMP` | Se mantiene compatible con el uso actual de `LocalDateTime` en entidad, mapper y servicio |
| Teléfonos y código postal | `VARCHAR(10)` y `VARCHAR(5)` | Conservan ceros iniciales; validación de dígitos para el contexto mexicano |
| Número de cuenta | `VARCHAR(18)` | Identificador de 18 dígitos, no una cantidad; conserva ceros iniciales. No implica validación de CLABE |
| CURP y RFC | `VARCHAR(18)` y `VARCHAR(13)` | Identificadores normalizados y validados mediante restricciones |
| Nacionalidad | `VARCHAR(100)` | Admite otras nacionalidades; no se limita a un enum con un único valor |
| Hash de usuario | `VARCHAR(60)` | Longitud exacta de la representación BCrypt usada por el backend |
| Correo | `VARCHAR(100)` | Límite actual del contrato de la API y sus DTO; normalización e índice único |
| Token y textos del catálogo | `TEXT` | Contenido de longitud variable recibido del proveedor |
| Vencimiento de token | `BIGINT` | Duración en segundos; `Long`, no una fecha |
| Banderas | `BOOLEAN` | Estados binarios; `boolean`/`Boolean` |

La entrega incorpora los ajustes de V5: nacionalidad como texto y hash BCrypt
de 60 caracteres. **El backend actual ya tiene esos tipos**, por lo que no se
requieren cambios de entidades, DTO ni respuestas de la API por esta revisión.
Se conservan los límites y la nulabilidad del proveedor en `producto`.

Para un cambio futuro de auditoría de tokens a `TIMESTAMPTZ`, habría que migrar
los datos indicando su zona original y sustituir `LocalDateTime` por `Instant`
en entidad, mapper y servicio. Esta entrega conserva el contrato actual.

## Conexión del backend y Flyway

El esquema representa el estado final de **V1 a V5**. Las migraciones originales
se conservan intactas para no alterar sus checksums en bases existentes.

Si creaste la base con el script de entrega, configura tu `.env`:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/gestopago
DB_USERNAME=postgres
DB_PASSWORD=tu_contrasena_local
SPRING_FLYWAY_BASELINE_VERSION=5
```

Puedes usar otra cuenta PostgreSQL que tenga permisos sobre el esquema.
Completa también las demás variables de la aplicación, como el secreto JWT.
Luego ejecuta `./gradlew.bat bootRun`. En el primer arranque, Flyway registra
el esquema manual como versión 5 y omite V1–V5. Las migraciones posteriores se
aplican normalmente. Se hizo configurable esa versión en `FlywayConfig`.

Si creas únicamente una base vacía y quieres que Flyway construya el esquema,
omite `SPRING_FLYWAY_BASELINE_VERSION` o usa `0`, su valor predeterminado.
No uses baseline 5 para un esquema parcial o anterior a V5. En una base con
historial Flyway existente se utiliza ese historial, no un baseline nuevo.

## Verificación reproducible

La versión original de la instalación se ejecutó correctamente en PostgreSQL local: creó
8 tablas, 21 índices (incluidos los de claves primarias y unicidad), 9 secuencias,
14 triggers, 8 funciones y 4 claves foráneas. Se verificaron también los tipos
de saldo, ingreso, fecha de nacimiento, nacionalidad, número de cuenta y hash.
Una segunda ejecución sobre la misma base fue rechazada y conservó los datos.
Las migraciones originales V1–V5 también se ejecutaron correctamente en otra
base temporal, con las mismas 8 tablas y 21 índices.

La ejecución de `test-postgres.ps1 -InstalacionCompleta` terminó con
**198 pruebas correctas, cero fallos y cero pruebas omitidas**. Incluye 59
pruebas de integración sobre PostgreSQL real. Los logs confirmaron que Flyway
registró correctamente la instalación manual con baseline 5.

Con las credenciales locales de `.env` y `psql` en PATH:

```powershell
./scripts/test-postgres.ps1
./scripts/test-postgres.ps1 -EsquemaManual
./scripts/test-postgres.ps1 -InstalacionCompleta
```

Los tres comandos crean una base temporal exclusiva para pruebas y la eliminan
al terminar. El primero comprueba las migraciones originales; el segundo
comprueba este esquema completo y su adopción por el backend con baseline 5.
El tercero ejecuta los bloques de creación de base y esquema del archivo SQL
en conexiones separadas y después comprueba el backend.
Las pruebas comprueban persistencia, restricciones, rollback del registro,
autenticación, actualización de correo, auditoría y baja lógica, entre otros casos.
