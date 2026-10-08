# API de clientes y autenticación

Backend independiente de Flutter. Usa Swagger en `/swagger-ui/index.html`, Postman o curl.
Las operaciones de clientes y cuentas requieren autenticación, salvo el registro.
Los endpoints de usuarios permiten acceder únicamente al usuario identificado por el JWT.
Para este proyecto académico cualquier usuario autenticado puede consultar y administrar clientes y cuentas;
no hay una política de administración por rol implementada.

## 1. Registrar cliente

`POST /clientes` sin token. Devuelve HTTP 201 con cliente, domicilio, cuentas y usuario, sin contraseñas.

```json
{
  "primerNombre": "Ana",
  "segundoNombre": null,
  "apellidoPaterno": "Perez",
  "apellidoMaterno": "Ruiz",
  "fechaNacimiento": "1990-01-01",
  "curp": "PERA900101MDFRZN01",
  "rfc": "PERA900101AB1",
  "sexo": "Femenino",
  "nacionalidad": "Mexicana",
  "estadoCivil": "Soltero",
  "correoElectronico": "ana@example.com",
  "telefonoMovil": "5512345678",
  "telefonoAlternativo": null,
  "ocupacion": "Ingeniera",
  "empresa": "Empresa",
  "ingresoMensual": 12000.50,
  "domicilio": {
    "calle": "Reforma",
    "numeroExterior": "10",
    "numeroInterior": null,
    "colonia": "Centro",
    "municipio": "Cuauhtemoc",
    "estado": "Ciudad de Mexico",
    "codigoPostal": "06000",
    "pais": "Mexico"
  },
  "password": "Segura123!"
}
```

CURP/RFC del ejemplo son datos ficticios que cumplen la estructura; no se valida su existencia ante RENAPO/SAT.
Nacionalidad: texto obligatorio de hasta 100 caracteres (por ejemplo `Mexicana` o `Argentina`). Sexo: `Masculino`, `Femenino`.
Estado civil: `Soltero`, `Casado`, `Divorciado`, `Viudo`, `Union libre`.
Campos opcionales se envían como `null` o se omiten; no se aceptan cadenas vacías.
El saldo inicial es cero; el cliente no elige saldo, número de cuenta, rol ni estado.
La cuenta y el usuario quedan activos. La contraseña se guarda con BCrypt.
El registro de las cuatro entidades es transaccional: un fallo revierte todos los registros.

## 2. Iniciar sesión

`POST /auth/login`:

```json
{"correo":"ana@example.com","password":"Segura123!"}
```

Respuesta:

```json
{"token":"<JWT>","tipo":"Bearer","expiraEnMs":28800000,"usuario":"ana@example.com","nombreCompleto":"Ana Perez Ruiz"}
```

Envía `Authorization: Bearer <JWT>` en las operaciones protegidas, incluido `GET /productos`.
En Swagger pulsa **Authorize** y pega el JWT.
El subject del JWT es el ID del usuario: actualizar el correo no cambia la identidad.
En cada solicitud protegida se verifica que el usuario y el cliente continúen activos.
Los tokens del modelo antiguo ya no sirven; inicia sesión de nuevo con correo.
`POST /auth/register` se sustituyó por `POST /clientes`.

## 3. Consultar

- `GET /clientes?pagina=0&tamanio=20` devuelve una página; tamaño máximo 100.
- Filtros combinables: `curp`, `rfc`, `correo`, `numeroCuenta`, `activo`, `desde`, `hasta`.
- Ejemplo: `/clientes?activo=true&desde=2026-10-01&hasta=2026-10-31`.
- Las fechas usan `YYYY-MM-DD`, incluyen ambos días y se interpretan en America/Mexico_City.
- `GET /clientes/{id}` devuelve cliente, domicilio, usuario y cuentas.
- `GET /cuentas?activa=true` consulta cuentas activas.
- `GET /cuentas/{numeroCuenta}` consulta una cuenta.
- `GET /cuentas/{numeroCuenta}/saldo` devuelve el saldo numérico.
- `GET /usuarios/{id}` devuelve el propio usuario sin hash de contraseña.

## 4. Actualizar

`PUT /clientes/{id}` recibe los mismos campos editables del registro, excluyendo
`curp`, `rfc` y `password`. Tampoco admite número de cuenta, estado, rol o ID.
Es una actualización completa, no PATCH: incluye todos los campos obligatorios y el domicilio.
Se actualiza también el correo del usuario mediante el trigger existente.

`PUT /usuarios/{id}/password`:

```json
{"passwordActual":"Segura123!","passwordNueva":"Nueva123!"}
```

Devuelve HTTP 204. Exige contraseña actual válida, nueva contraseña fuerte y usuario propio.
BCrypt acepta como máximo 72 bytes UTF-8; se valida ese límite antes de codificar.

## 5. Baja lógica

- `DELETE /clientes/{id}` devuelve HTTP 204; desactiva cliente, usuario y todas sus cuentas.
- `DELETE /cuentas/{numeroCuenta}` devuelve HTTP 204; desactiva la cuenta sin eliminarla.
- `PUT /cuentas/{numeroCuenta}/estado` recibe `{"activa":true}` o `{"activa":false}`.
- Una cuenta solo puede activarse si su cliente sigue activo.
- Un JWT emitido previamente se rechaza si el cliente o usuario fue desactivado.

## Errores

400: validación o formato inválido. 401: credenciales o token inválidos.
403: usuario inactivo o acceso al usuario ajeno. 404: registro no encontrado.
409: duplicados o conflicto de estado. 500: fallo interno/persistencia.

## PostgreSQL y Flyway

`DB_URL=jdbc:postgresql://localhost:5432/gestopago` en `.env`.
V1/V2 conservan productos y tokens; V3 se conserva por historial.
V4 adopta el esquema manual existente o crea el mismo esquema en una base vacía.
V5 amplía nacionalidad, valida letras en los nombres, limita BCrypt a 60 caracteres y alinea la mayoría de edad con America/Mexico_City.
Los triggers existentes manejan auditoría, sincronización y protección de datos.
JPA no crea ni modifica tablas automáticamente.
`docs/esquema-clientes.sql` es una copia del esquema manual, sin datos ni credenciales.
Cambiar la BD de conexión no copia automáticamente productos guardados en otra base;
el flujo de catálogo conserva su lógica Redis → PostgreSQL → GestoPago.
