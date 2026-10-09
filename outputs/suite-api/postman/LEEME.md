# Ejecutar la suite automatizada en Postman

## Archivos

- `GestoPago_Automatizada.postman_collection.json`: colección lista para importar, formato JSON v2.1.
- `Local.postman_environment.json`: entorno con `baseUrl=http://localhost:8080`.
- `cobertura.json`: correspondencia de los 78 IDs de la suite con los escenarios automatizados.

## Desde la aplicación de Postman

1. Enciende tu API y su base de datos de pruebas.
2. Pulsa **Import** e importa la colección y el entorno de esta carpeta. La colección se llama **GestoPago - Suite API automatizada**.
3. Selecciona el entorno **GestoPago - Local pruebas**. Si tu API usa otra dirección, edita `baseUrl` en ese entorno.
4. Selecciona la colección completa y pulsa **Run**. Deja todas las solicitudes seleccionadas, en el orden existente, con **1 iteración**. Conserva la ejecución secuencial.
5. Pulsa **Run GestoPago - Suite API automatizada**. Revisa los tests aprobados/fallidos y los errores del Runner. Un HTTP 400, 401, 403, 404 o 409 puede ser correcto cuando el caso prueba un rechazo.

No necesitas escribir el JWT ni usar el Vault para esta colección: las solicitudes auxiliares de login guardan el token y la autorización Bearer se hereda desde la colección. Las solicitudes públicas y las pruebas sin autenticación tienen No Auth explícito.

La ejecución crea tres clientes sintéticos con identificadores y correo generados. Prueba los filtros, PATCH, permisos de usuario, contraseña, estado de cuenta y bajas lógicas. Al final desactiva los tres clientes. Los registros permanecen físicamente en la base de datos. Usa una base de pruebas y conserva esta colección separada de tu colección original.

Hay **91 solicitudes principales**: 78 escenarios de la suite, 3 variantes y 10 auxiliares. Algunas validaciones envían consultas adicionales con `pm.sendRequest` para verificar que los cambios se guardaron o que un rechazo no modificó datos. Por eso el número de tests/aserciones y de llamadas HTTP puede ser superior a 91.

Ejecuta siempre toda la colección: las carpetas posteriores dependen de los IDs creados al inicio. Si falla un registro o login auxiliar, la ejecución se detiene. Revisa los resultados y la variable de colección `creados` para identificar registros que puedan haber quedado activos. Al volver a ejecutar desde el principio se generan datos nuevos; no se reutilizan los clientes de la ejecución anterior.

## Reporte HTML con Postman CLI

El CLI es un programa adicional a la aplicación de escritorio. Después de instalarlo desde la documentación oficial de Postman, abre una terminal en esta carpeta y ejecuta:

```powershell
postman collection run ".\GestoPago_Automatizada.postman_collection.json" -e ".\Local.postman_environment.json" -r cli,html,json --reporter-html-export ".\resultados.html" --reporter-json-export ".\resultados.json"
```

El CLI ejecuta pruebas reales. El HTML permite revisar el resumen y los detalles; el JSON permite relacionar los resultados con los IDs de la suite. Un resultado fallido debe conservarse como fallo, no marcarse aprobado porque la colección se haya ejecutado.

- Instalación: https://learning.postman.com/docs/postman-cli/postman-cli-installation/
- Reportes: https://learning.postman.com/docs/postman-cli/postman-cli-reporters/

## Evidencia para el Excel

Guarda `resultados.html` y `resultados.json`, o exporta los resultados del Runner. Comparte estos archivos junto con capturas del resumen y los módulos para actualizar la suite. Los nombres API-001 a API-078 identifican las filas correspondientes; las variantes API-038 y API-069 deben aprobar todas sus ejecuciones. Las solicitudes AUX son preparación o comprobación y no representan casos adicionales del Excel.

Esta entrega fue verificada en estructura, sintaxis, generación de datos y orden de dependencias. **No se ejecutó contra la API y no acredita que las pruebas hayan aprobado.** La colección original y el Excel se conservaron sin cambios.
