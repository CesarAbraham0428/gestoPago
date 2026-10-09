import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire('C:/Users/lopez/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json');
const {Workbook,SpreadsheetFile}=await import(pathToFileURL(require.resolve('@oai/artifact-tool')).href);
const root=process.cwd(), out=path.join(root,'outputs/suite-api');
const wb=Workbook.create();
const names=['UsuarioServiceTest','CuentaServiceTest','ClientePatchTest','ClienteControllerTest','ClienteValidationTest','PasswordValidatorTest','RespuestaPrivacidadTest'];
const folders=['service','service','service','controller','validation','validation','model'];
const rows=[];let historical=0;
for(let i=0;i<names.length;i++){
 const rel=`src/test/java/com/proyecto/servicios/${folders[i]}/${names[i]}.java`, src=await fs.readFile(path.join(root,rel),'utf8');
 let xml='';try{xml=await fs.readFile(path.join(root,`build/test-results/test/TEST-com.proyecto.servicios.${folders[i]}.${names[i]}.xml`),'utf8')}catch{}
 const count=Number(xml.match(/tests="(\d+)"/)?.[1]||0);historical+=count;
 const stamp=xml.match(/timestamp="([^"]+)"/)?.[1]||'Sin reporte';
 for(const m of src.matchAll(/(@Test|@ParameterizedTest)([\s\S]*?)\bvoid\s+(\w+)\s*\([^)]*\)[^{]*\{/g)){
  let p=m.index+m[0].length,depth=1,end=p;for(;end<src.length&&depth;end++){if(src[end]==='{')depth++;if(src[end]==='}')depth--;}
  const body=src.slice(p,end-1), asserts=body.split(/\r?\n/).filter(x=>/assert|verify\(|andExpect/.test(x)).map(x=>x.trim()).join('\n');
  const title=m[3].replace(/([a-záéíóúñ])([A-ZÁÉÍÓÚÑ])/g,'$1 $2');
  rows.push([`UT-${String(rows.length+1).padStart(3,'0')}`,names[i].startsWith('Usuario')?'Usuarios':names[i].startsWith('Cuenta')?'Cuentas':names[i].startsWith('Password')?'Usuarios / Clientes':'Clientes',folders[i]==='controller'?'Controlador aislado (MockMvc)':'Unitaria',title,names[i]+'#'+m[3],m[1]==='@ParameterizedTest'?m[2].trim().replace(/\s+/g,' '):'Fixture y mocks definidos en el método / BeforeEach',asserts||'Consultar aserciones en el método fuente','Existente',xml?'Aprobado histórico':'Sin reporte',stamp,'No reejecutada: Gradle no encuentra Java; reporte previo, sin validar correspondencia con código actual.',rel,'','','']);
 }
}
const proposed=[['Clientes','Registrar cliente: normalización, creación relacionada y rollback','ClienteServiceImpl.registrar','Mocks de repositorios, encoder y EntityManager; datos válidos y fallos inducidos','Correo en minúsculas; CURP/RFC normalizados; saldo cero; ninguna persistencia parcial ante fallo.'],['Clientes','Consultar: filtros, rango y paginación','ClienteServiceImpl.consultar','Filtros permitidos; desde > hasta; página negativa; tamaño 0/101','Validación previa; resumen sin filtros; detalle con filtros; 404 sin coincidencias.'],['Clientes','Baja lógica coordina persistencia','ClienteServiceImpl.desactivar','Cliente existente e inexistente','Marca activo=false y flush; inexistente lanza 404. Cascada real requiere integración.'],['Cuentas','Cambio de estado y bloqueo del cliente','CuentaService.cambiarEstado / desactivar','Cliente activo/inactivo; activa=true/false; cuenta inexistente','409 al reactivar con cliente inactivo; baja permitida; flush y respuesta actualizada.'],['Cuentas','Contrato HTTP y filtros','CuentaController','MockMvc: aliases activo/activa; desconocidos; cuerpo nulo','400 con aliases contradictorios/filtros desconocidos; valida activa obligatoria; rutas y códigos correctos.'],['Usuarios','Contrato HTTP autenticado','UsuarioController','MockMvc con Authentication, propio/ajeno y request inválido','GET 200; PUT password 204; validación 400; identidad remitida al servicio.']];
for(const p of proposed) rows.push([`UT-${String(rows.length+1).padStart(3,'0')}`,p[0],'Unitaria propuesta',p[1],p[2],p[3],p[4],'Propuesta','Pendiente','','Brecha de pruebas aisladas; puede existir cobertura de integración.','src/main/java/com/proyecto/servicios/','','','']);
const api=[];
function add(module,method,url,scenario,data,status,expected,setup='JWT válido y datos de prueba activos',ref='Contrato en controlador / servicio'){
 const id=`API-${String(api.length+1).padStart(3,'0')}`;
 api.push([id,module,'Alta',method,'{{baseUrl}}'+url,scenario,setup,data,status,expected,'Manual / Collection Runner',`pm.test("${id}: HTTP ${status}", () => pm.response.to.have.status(${status}));`+(status>=400?'\npm.test("Error API", () => pm.expect(pm.response.json().codigo).to.eql(1));':''),'Pendiente','','','','',ref]);
}
const reg={primerNombre:'Ana',apellidoPaterno:'Perez',apellidoMaterno:'Ruiz',fechaNacimiento:'1990-01-01',curp:'PERA900101MDFRZN01',rfc:'PERA900101AB1',sexo:'Femenino',nacionalidad:'Argentina',estadoCivil:'Soltero',correoElectronico:'ana.qa@example.com',telefonoMovil:'0123456789',ocupacion:'Ingeniera',empresa:'Empresa',ingresoMensual:12000.50,domicilio:{calle:'Reforma',numeroExterior:'10',colonia:'Centro',municipio:'Cuauhtemoc',estado:'Ciudad de Mexico',codigoPostal:'06000',pais:'Mexico'},password:'Segura123!'};
add('Clientes','POST','/clientes','Registro válido',JSON.stringify(reg,null,2),201,'Location=/clientes/{id}; cliente, domicilio, usuario activo y cuenta activa con saldo 0; sin password/hash. Guardar ids y numeroCuenta.','Sin JWT; CURP/RFC/correo únicos; API conectada a BD de pruebas','ClientesPostgresTest#registroPersisteClienteDomicilioCuentaActivaYUsuarioConBCrypt');
for(const campo of ['curp','rfc','correoElectronico'])add('Clientes','POST','/clientes',`Duplicado: ${campo}`,`Body de registro con ${campo} de cliente existente; otros identificadores únicos`,409,'codigo=1; no crea registros parciales.');
for(const [campo,valor] of [['primerNombre','A1'],['correoElectronico','invalido'],['telefonoMovil','123'],['ingresoMensual',0],['domicilio.codigoPostal','6000'],['password','abcdefgh'],['fechaNacimiento','2999-01-01']])add('Clientes','POST','/clientes',`Dato inválido: ${campo}`,`Body de registro; sustituir ${campo} por ${JSON.stringify(valor)}`,400,'codigo=1; no registra cliente.','Sin JWT; identificadores únicos');
add('Clientes','POST','/clientes','Edad mínima aceptada','fechaNacimiento = fecha de hoy en America/Mexico_City menos 18 años; identificadores únicos',201,'Registro válido.','Sin JWT; BD de pruebas');
add('Clientes','POST','/clientes','Menor de edad','fechaNacimiento = hoy menos 18 años más 1 día; identificadores únicos',400,'Rechaza registro.','Sin JWT; BD de pruebas');
add('Clientes','GET','/clientes?pagina=0&tamanio=20','Lista global','Sin body',200,'Page con resumen; sin credenciales ni datos completos del detalle.');
for(const [key,value] of [['id','{{clienteId}}'],['curp',reg.curp],['rfc',reg.rfc],['correo',reg.correoElectronico],['numeroCuenta','{{numeroCuenta}}'],['activo','true'],['desde','{{fechaHoy}}&hasta={{fechaHoy}}']])add('Clientes','GET',`/clientes?${key}=${value}`,`Filtro ${key}`,'Sin body',200,'Page de detalles; todos cumplen filtro; fechas inclusivas en zona Mexico City.');
add('Clientes','GET','/clientes/{{clienteId}}','Detalle existente','Sin body',200,'id correcto, domicilio, usuario y cuentas; sin password ni passwordHash.');
add('Clientes','GET','/clientes/2147483647','Detalle inexistente','Sin body',404,'codigo=1.');
add('Clientes','GET','/clientes?curp=inexistente','Filtro sin coincidencias','Sin body',404,'codigo=1; no lista vacía en consulta filtrada.');
for(const q of ['pagina=-1','tamanio=0','tamanio=101','desde=2026-10-08&hasta=2026-10-07','desconocido=1'])add('Clientes','GET','/clientes?'+q,'Consulta inválida: '+q,'Sin body',400,'codigo=1.');
add('Clientes','PATCH','/clientes/{{clienteId}}','Modificar solo campos enviados','{"telefonoMovil":"5598765432","domicilio":{"calle":"Insurgentes"}}',200,'GET posterior conserva campos omitidos e identificadores; cambia teléfono y calle.');
add('Clientes','PATCH','/clientes/{{clienteId}}','Limpiar opcional','{"segundoNombre":null}',200,'segundoNombre=null; otros campos conservados.');
add('Clientes','PATCH','/clientes/{{clienteId}}','Sincronizar correo de usuario','{"correoElectronico":"ana.nueva.qa@example.com"}',200,'GET refleja correo nuevo en cliente y usuario; login auxiliar con nuevo correo funciona.');
for(const field of ['curp','rfc','numeroCuenta','activo','password'])add('Clientes','PATCH','/clientes/{{clienteId}}','Campo protegido: '+field,JSON.stringify({[field]:'cambio'}),400,'codigo=1; GET posterior confirma ausencia de cambios.');
add('Clientes','PATCH','/clientes/{{clienteId}}','JSON no objeto','[] (repetir con null y "texto")',400,'codigo=1; no modifica.');
add('Clientes','PATCH','/clientes/{{clienteId}}','Correo ya usado','{"correoElectronico":"{{correoOtro}}"}',409,'codigo=1; conserva correo anterior.');
for(const url of ['/clientes','/clientes/{{clienteId}}'])add('Clientes','PUT',url,'Método no admitido','{}',405,'Allow presente; para detalle incluye PATCH.');
add('Usuarios','GET','/usuarios/{{usuarioId}}','Consultar usuario propio','Sin body',200,'id, clienteId, correo, activo; sin password/hash.');
add('Usuarios','GET','/usuarios/{{usuarioOtroId}}','Consultar usuario ajeno','Sin body',403,'Solo puede consultar su usuario.');
add('Usuarios','PUT','/usuarios/{{usuarioId}}/password','Cambiar contraseña válida','{"passwordActual":"Segura123!","passwordNueva":"Nueva123!"}',204,'Sin cuerpo; login auxiliar nuevo funciona y contraseña anterior devuelve 401. Ejecutar después de consultas y renovar token.');
add('Usuarios','PUT','/usuarios/{{usuarioId}}/password','Contraseña actual incorrecta','{"passwordActual":"Incorrecta123!","passwordNueva":"Nueva123!"}',401,'No cambia contraseña.');
add('Usuarios','PUT','/usuarios/{{usuarioId}}/password','Contraseña nueva débil','{"passwordActual":"Segura123!","passwordNueva":"abcdefgh"}',400,'No cambia contraseña.');
add('Usuarios','PUT','/usuarios/{{usuarioOtroId}}/password','Cambio de usuario ajeno','{"passwordActual":"Segura123!","passwordNueva":"Nueva123!"}',403,'No cambia contraseña ajena.');
add('Cuentas','GET','/cuentas','Lista global','Sin body',200,'Page de resumen; incluye id, clienteId, numeroCuenta, estaActiva.');
for(const [q,label] of [['activo=true','Activas'],['activa=true','Alias activa'],['saldo=0','Saldo exacto'],['id={{cuentaId}}','ID']])add('Cuentas','GET','/cuentas?'+q,'Filtrar '+label,'Sin body',200,'Page de detalle; solo coincidencias.');
for(const url of ['/cuentas/id/{{cuentaId}}','/cuentas/{{numeroCuenta}}','/cuentas/{{numeroCuenta}}/saldo'])add('Cuentas','GET',url,'Consulta existente','Sin body',200,url.endsWith('/saldo')?'Número JSON igual al saldo esperado (inicial 0).':'Datos de la cuenta coinciden con cliente y número.');
for(const url of ['/cuentas/id/2147483647','/cuentas/inexistente','/cuentas/inexistente/saldo','/cuentas?saldo=9999999999999999.99'])add('Cuentas','GET',url,'Sin coincidencias','Sin body',404,'codigo=1.');
for(const q of ['saldo=-1','activo=true&activa=false','clienteId=1','numeroCuenta=1','pagina=-1','tamanio=0','tamanio=101'])add('Cuentas','GET','/cuentas?'+q,'Consulta inválida','Sin body',400,'codigo=1.');
add('Cuentas','PUT','/cuentas/{{numeroCuenta}}/estado','Desactivar cuenta','{"activa":false}',200,'estaActiva=false; GET confirma; registro conservado.');
add('Cuentas','PUT','/cuentas/{{numeroCuenta}}/estado','Reactivar con cliente activo','{"activa":true}',200,'estaActiva=true; GET confirma.');
add('Cuentas','PUT','/cuentas/{{numeroCuenta}}/estado','Estado obligatorio','{} (repetir con {"activa":null})',400,'codigo=1; estado sin cambios.');
add('Cuentas','DELETE','/cuentas/{{numeroCuenta}}','Baja lógica de cuenta','Sin body',204,'Sin cuerpo; GET con JWT válido confirma estaActiva=false y datos conservados.');
add('Clientes','DELETE','/clientes/{{clienteId}}','Baja lógica con cascada','Sin body',204,'Cliente, usuario y cuentas quedan inactivos; token del cliente deja de ser válido (401). Consultar con token del segundo cliente.','Ejecutar al final; segundo cliente activo y tokenOtro disponible');
add('Clientes','PATCH','/clientes/{{clienteId}}','Cliente inactivo','{"empresa":"Nueva"}',409,'Sin cambios.','JWT del segundo cliente; cliente objetivo dado de baja');
add('Cuentas','PUT','/cuentas/{{numeroCuenta}}/estado','Reactivar con cliente inactivo','{"activa":true}',409,'Cuenta conserva estado inactivo.','JWT del segundo cliente; cliente objetivo dado de baja');
for(const url of ['/usuarios/{{usuarioId}}','/clientes','/clientes/{{clienteId}}','/cuentas','/cuentas/{{numeroCuenta}}'])add(url.startsWith('/usuarios')?'Usuarios':url.startsWith('/clientes')?'Clientes':'Cuentas','GET',url,'Sin autenticación','Sin body',401,'codigo=1; no entrega datos.','Omitir Authorization');
function sheet(name,headers,data,widths,notes,statusCol){
 const s=wb.worksheets.add(name);s.showGridLines=false;s.tabColor='#23395B';
 s.getRange('A2').values=[[name==='Unitarias'?'Suite de pruebas — API | Unitarias':'Suite de pruebas — API | HTTP']];
 s.getRange('A2').format.font={name:'Arial',size:15,bold:true,color:'#23395B'};
 notes.forEach((v,i)=>{s.getRangeByIndexes(i+3,0,1,6).merge();s.getCell(i+3,0).values=[[v]];});
 const head=notes.length+5,end=head+data.length;
 s.getRangeByIndexes(head-1,0,1,headers.length).values=[headers];s.getRangeByIndexes(head,0,data.length,headers.length).values=data;
 const all=s.getRangeByIndexes(3,0,end-3,headers.length);all.format.font={name:'Arial',size:10};all.format.wrapText=true;all.format.verticalAlignment='top';
 widths.forEach((w,i)=>s.getRangeByIndexes(0,i,end,1).format.columnWidth=w);
 s.getRangeByIndexes(head-1,0,1,headers.length).format={fill:'#23395B',font:{name:'Arial',size:10,bold:true,color:'#FFFFFF'},rowHeight:32,wrapText:true};
 for(let r=head;r<end;r++){const lines=Math.max(...data[r-head].map((v,c)=>String(v).split('\n').reduce((sum,l)=>sum+Math.max(1,Math.ceil(l.length/(widths[c]*1.15))),0)));s.getRangeByIndexes(r,0,1,headers.length).format.rowHeight=Math.max(48,lines*14+12);if((r-head)%2===0)s.getRangeByIndexes(r,0,1,headers.length).format.fill='#F2F5F9';}
 s.getRangeByIndexes(3,0,notes.length,headers.length).format.rowHeight=38;
 const table=s.tables.add(`A${head}:${String.fromCharCode(64+headers.length)}${end}`,true,name==='Unitarias'?'PruebasUnitarias':'PruebasHTTP');table.showFilterButton=true;
 s.freezePanes.freezeRows(head);s.freezePanes.freezeColumns(2);
 s.getRangeByIndexes(head,statusCol,data.length,1).dataValidation={rule:{type:'list',values:['Pendiente','Aprobado','Fallido','Bloqueado','Aprobado histórico','Sin reporte']}};
 s.getRangeByIndexes(head,statusCol,data.length,1).conditionalFormats.add('containsText',{text:'Fallido',format:{fill:'#FDE2E2',font:{color:'#9C2020'}}});
 return s;
}
sheet('Unitarias',['ID','Módulo','Nivel','Escenario / objetivo','Clase # método','Datos / variantes','Aserciones esperadas (fuente)','Implementación','Estado','Fecha reporte UTC','Observaciones','Fuente','Resultado actual','Evidencia','Responsable / fecha'],rows,[12,19,23,45,56,55,70,18,23,28,52,65,35,35,28],[
 'Alcance: solo API de usuarios, clientes y cuentas. Productos y frontend excluidos. Análisis: 08/10/2026.',
 `${rows.length-proposed.length} métodos existentes + ${proposed.length} propuestas. Reportes previos: ${historical} ejecuciones parametrizadas; no equivalen al número de métodos.`,
 'Estado histórico tomado de build/test-results/test. Reejecución intentada: Gradle no encuentra Java / JAVA_HOME. No representa aprobación actual.',
 'Ejecutar: .\\gradlew.bat test --tests "*UsuarioServiceTest" --tests "*CuentaServiceTest" --tests "*ClientePatchTest" --tests "*ClienteControllerTest" --tests "*ClienteValidationTest" --tests "*PasswordValidatorTest" --tests "*RespuestaPrivacidadTest"',
 'ClienteControllerTest usa MockMvc standalone: no verifica JWT. ClientesPostgresTest es integración, no unitaria; sus resultados y requisitos se describen en la hoja HTTP.'
],8);
sheet('Manuales-Automatizadas',['ID','Módulo','Prioridad','Método','URL','Escenario','Precondiciones / orden','Body / datos','HTTP esperado','Validaciones esperadas','Modalidad posible','Script Postman inicial','Estado','HTTP actual','Resultado actual','Evidencia','Responsable / fecha','Fuente / cobertura'],api,[12,16,12,12,58,38,60,65,15,75,26,75,22,15,42,35,28,65],[
 `Plan de ${api.length} casos para las 13 operaciones HTTP de usuarios, clientes y cuentas. Todos pendientes; no se ejecutaron solicitudes Postman.`,
 'Entorno: baseUrl=http://localhost:8080 (ajustar SERVER_PORT); clienteId, usuarioId, cuentaId, numeroCuenta (texto con ceros), usuarioOtroId, correoOtro, fechaHoy, token y tokenOtro.',
 'Preparar dos clientes con POST /clientes (identificadores únicos); login auxiliar POST /auth/login body {"correo":"...","password":"Segura123!"}; guardar token. Authorization: Bearer {{token}}.',
 'Orden: registros y login → consultas y negativos → cambios de correo/password (renovar acceso) → cambios de cuenta → baja de cliente al final. Restablecer fixture antes de cada ejecución.',
 'Automatización: crear requests de cada fila, añadir script en Scripts > Post-response y completar validaciones del body/GET posterior. Collection Runner ejecuta la colección; aquí solo se entrega el formato Excel, no una colección.',
 'Guardar IDs del alta: const r=pm.response.json(); pm.environment.set("clienteId",r.id); pm.environment.set("usuarioId",r.usuario.id); pm.environment.set("cuentaId",r.cuentas[0].id); pm.environment.set("numeroCuenta",r.cuentas[0].numeroCuenta);',
 'Integración existente: ClientesPostgresTest requiere TEST_DB_URL con nombre gestopago_backend_test_...; usa PostgreSQL real y limpia esa BD temporal. No es Postman ni prueba unitaria. Reporte previo: 59 tests, 0 fallos, 0 omitidos (2026-10-08T20:32:10.989Z); no reejecutado.',
 'Ejemplo adicional: pm.test("Sin credenciales",()=>{ const r=pm.response.json(); pm.expect(r).not.to.have.property("passwordHash"); pm.expect(r).not.to.have.property("password"); }); Validar también objetos anidados.',
 'Fuente automatización: https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs | CLI: https://learning.postman.com/docs/postman-cli/postman-cli-run-collection',
 'Fuentes locales: src/main/java/com/proyecto/servicios/controller/{Usuario,Cliente,Cuenta}Controller.java; service/UsuarioService.java; service/CuentaService.java; service/Impl/ClienteServiceImpl.java; security/SecurityConfiguration.java.'
],12);
wb.recalculate();
console.log((await wb.inspect({kind:'sheet',include:'id,name',maxChars:1200})).ndjson);
console.log((await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#NUM!',options:{useRegex:true,maxResults:10},maxChars:1000})).ndjson);
for(const n of ['Unitarias','Manuales-Automatizadas']){const img=await wb.render({sheetName:n,range:n==='Unitarias'?'A2:G13':'A2:J17',scale:1,format:'png'});await fs.writeFile(path.join(out,n+'.png'),new Uint8Array(await img.arrayBuffer()));}
await (await SpreadsheetFile.exportXlsx(wb)).save(path.join(out,'Suite_Pruebas_API.xlsx'));
console.log(JSON.stringify({methods:rows.length-proposed.length,proposals:proposed.length,historical,manualCases:api.length}));
