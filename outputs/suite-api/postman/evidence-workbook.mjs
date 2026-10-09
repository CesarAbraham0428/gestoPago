import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL,fileURLToPath} from 'node:url';
const require=createRequire('C:/Users/lopez/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json');
const {FileBlob,SpreadsheetFile}=await import(pathToFileURL(require.resolve('@oai/artifact-tool')).href);
const out=path.dirname(fileURLToPath(import.meta.url));
const input='C:/Users/lopez/Downloads/bienSuite_Pruebas_API_con_evidencias.xlsx';
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(process.argv.includes('--inspect-final')?path.join(out,'bienSuite_Pruebas_API_evidencias_completas.xlsx'):input));
const sheet=wb.worksheets.getItem('Manuales-Automatizadas');
if(process.argv.includes('--inspect-final')){
 const p=await wb.render({sheetName:sheet.name,range:'K51:K51',scale:1,format:'png'});
 await fs.writeFile(path.join(out,'password-mapping.png'),new Uint8Array(await p.arrayBuffer()));
 process.exit(0);
}
if(process.argv.includes('--inspect')){
 console.log((await wb.inspect({kind:'region',sheetId:sheet.name,range:'A7:K8',maxChars:1600,tableMaxCellChars:100})).ndjson);
 const p=await wb.render({sheetName:sheet.name,range:'D7:K8',scale:1,format:'png'});
 await fs.writeFile(path.join(out,'before.png'),new Uint8Array(await p.arrayBuffer()));
 process.exit(0);
}
const run=JSON.parse(await fs.readFile('C:/Users/lopez/Downloads/GestoPago - Suite API automatizada.postman_test_run.json','utf8'));
const shots=JSON.parse(await fs.readFile(path.join(out,'evidence-shots.json'),'utf8'));
const col=JSON.parse(await fs.readFile(path.join(out,'GestoPago_Automatizada.postman_collection.json'),'utf8'));
const requests=col.item.flatMap(x=>x.item);
// El archivo del usuario numera los negativos de contraseña antes del cambio válido.
const runToSuite={44:47,45:44,46:45,47:46};
const byId=new Map();const methods=new Map(run.collection.requests.map(r=>[r.id,r.method]));
for(const r of run.results){const id=r.name.match(/^API-(\d{3})/)?.[1];if(id){const n=Number(id);if(!byId.has(n))byId.set(n,[]);byId.get(n).push(r);}}
if(byId.size!==78||run.totalPass!==216||run.totalFail!==0||run.results.length!==91)throw Error('Resumen inesperado');
for(const s of shots){
 const r=s.id?byId.get(s.id)?.[0]:s.target==='login'?run.results.find(r=>r.name==='AUX | Login cliente principal'):null;
 if(r&&(r.responseCode.code!==s.http||r.time!==s.ms))throw Error('Captura y JSON no concuerdan: '+s.time);
 s.suiteId=s.id?(runToSuite[s.id]??s.id):null;
 s.row=s.id?s.suiteId+7:s.target==='login'?90:92;
 s.col=s.kind==='peticion'?'F':'K';
 s.filename=`Captura de pantalla 2026-10-09 ${s.time}.png`;
 s.label=s.id?`API-${String(s.id).padStart(3,'0')}`:s.target==='login'?'Login auxiliar':'Resumen general';
}
const email=new URL(byId.get(18)[0].url).searchParams.get('correo');
const cv={baseUrl:'http://localhost:8080',clienteId:'3',usuarioId:'3',cuentaId:'3',numeroCuenta:'000000000000000003',usuarioOtroId:'4',correo:email,correoNuevo:'nuevo.'+email,curp:new URL(byId.get(16)[0].url).searchParams.get('curp'),rfc:new URL(byId.get(17)[0].url).searchParams.get('rfc'),password:'Prueba123!',passwordNueva:'NuevaPrueba456!',fechaHoy:'2026-10-09'};
const replace=s=>s.replace(/\{\{([^}]+)\}\}/g,(m,k)=>cv[k]??m);
const base={primerNombre:'Ana',segundoNombre:'Luis',apellidoPaterno:'Perez',apellidoMaterno:'Ruiz',fechaNacimiento:'1990-01-01',curp:cv.curp,rfc:cv.rfc,sexo:'Masculino',nacionalidad:'Mexicana',estadoCivil:'Soltero',correoElectronico:email,telefonoMovil:'5512345678',ocupacion:'Programador',empresa:'Pruebas API',ingresoMensual:1500,domicilio:{calle:'Reforma',numeroExterior:'10',colonia:'Centro',municipio:'Cuauhtemoc',estado:'Ciudad de Mexico',codigoPostal:'06000',pais:'Mexico'},password:cv.password};
const changes={5:{primerNombre:'A1'},6:{correoElectronico:'invalido'},7:{telefonoMovil:'123'},8:{ingresoMensual:0},9:{'domicilio.codigoPostal':'6000'},10:{password:'abcdefgh'},11:{fechaNacimiento:'2999-01-01'},12:{fechaNacimiento:'2008-10-09'},13:{fechaNacimiento:'2008-10-10'}};
const capturedResponses={2:'{"codigo":1,"mensaje":"CURP duplicada"}',3:'{"codigo":1,"mensaje":"RFC duplicado"}',4:'{"codigo":1,"mensaje":"Correo electrónico duplicado"}',5:'codigo=1; primerNombre: Debe contener solo letras y espacios y entre 2 y 50 caracteres sin espacios exteriores',6:'codigo=1; correoElectronico: debe ser una dirección de correo electrónico con formato correcto',7:'codigo=1; telefonoMovil: debe coincidir con [0-9]{10}',8:'{"codigo":1,"mensaje":"ingresoMensual: debe ser mayor que 0"}',10:'codigo=1; Contraseña inválida: mínimo 8 caracteres, mayúscula, minúscula, número y carácter especial; máximo 72 bytes UTF-8',11:'{"codigo":1,"mensaje":"fechaNacimiento: debe ser una fecha pasada"}',13:'{"codigo":1,"mensaje":"El cliente debe tener al menos 18 años y la fecha no puede ser futura"}',33:'{"codigo":1,"mensaje":"Campo no permitido en actualización: curp"}',34:'{"codigo":1,"mensaje":"Campo no permitido en actualización: rfc"}',35:'{"codigo":1,"mensaje":"Campo no permitido en actualización: numeroCuenta"}',39:'{"codigo":1,"mensaje":"Correo electrónico duplicado"}',45:'{"codigo":1,"mensaje":"Credenciales inválidas"}',58:'{"codigo":1,"mensaje":"Cuenta no encontrada"}',72:'{"codigo":1,"mensaje":"El cliente está inactivo"}'};
const validations=[];
for(let n=1;n<=78;n++){
 const suiteNumber=runToSuite[n]??n,row=suiteNumber+7,id=`API-${String(suiteNumber).padStart(3,'0')}`,runCaseId=`API-${String(n).padStart(3,'0')}`;
 if(sheet.getRange(`A${row}`).values[0][0]!==id)throw Error('ID incorrecto en fila '+row);
 const results=byId.get(n),original=requests.find(x=>x.name.startsWith(runCaseId+' |'));
 const status=Number(sheet.getRange(`G${row}`).values[0][0].match(/HTTP (\d+)/)[1]);
 const good=results.every(r=>r.responseCode.code===status&&Object.values(r.tests).every(t=>t===true)&&Object.values(r.testPassFailCounts).every(t=>t.fail===0));
 if(methods.get(results[0].id)!==sheet.getRange(`D${row}`).values[0][0])throw Error('Método incorrecto '+id);
 const assertions=results.reduce((s,r)=>s+Object.keys(r.tests).length,0),ms=results.map(r=>r.time);
 const tests=results.flatMap(r=>Object.keys(r.tests).map(t=>t.replace(/^API-\d{3}\s*\|?\s*/,'')));
 sheet.getRange(`E${row}`).values=[[results[0].url]];
 let body=original.request.body?replace(original.request.body.raw):null;
 let instructions=body===null?'Sin cuerpo de petición.':`Content-Type: application/json\nCuerpo de petición: ${body}`;
 if(n<=13){
  if(n===1)instructions='POST público, sin JWT. Cuerpo enviado: Registro base de la fila 89. IDs creados: cliente 3, usuario 3 y cuenta 3. Véase la captura Request debajo.';
  else if(n>=2&&n<=4){const k=['curp','rfc','correoElectronico'][n-2];instructions=`Registro base con ${k} duplicado: ${JSON.stringify(base[k])}. Los otros identificadores se generaron nuevos. Plantilla del cuerpo: Registro base de la fila 89.`;}
  else instructions=`Registro base con ${JSON.stringify(changes[n])}. CURP, RFC y correo nuevos por solicitud. ${shots.some(s=>s.id===n&&s.kind==='peticion')?'Cuerpo enviado visible en la captura Request debajo.':'Plantilla del cuerpo en la fila 89; el JSON del Runner no incluye el cuerpo enviado.'}`;
 }
 if(n===38)instructions='Content-Type: application/json\nCuerpos ejecutados: [], null y "texto". Los tres deben rechazarse sin cambios.';
 if(n===69)instructions='Content-Type: application/json\nCuerpos ejecutados: {} y {"activa":null}. Ambos deben rechazarse sin cambiar el estado.';
 if(n===39)instructions='Content-Type: application/json\nCuerpo: {"correoElectronico":"{{correoOtro}}"}. correoOtro es el correo del segundo cliente creado. El valor literal no está en el JSON exportado.';
 if(n===71||n===72||n===73)instructions+='\nBaja ejecutada al final. Comprobaciones posteriores con el JWT del segundo cliente activo.';
 if(n>=74)instructions+='\nAuthorization omitido: prueba sin autenticación.';
 sheet.getRange(`F${row}`).values=[[instructions]];
 const summary=`HTTP ${results.map(r=>r.responseCode.code).join(' / ')}. ${assertions} validaciones registradas en JSON, ${good?'todas aprobadas':'revisar fallos'}. Tiempo${ms.length>1?'s':''}: ${ms.join(' / ')} ms.${n===71?' La captura muestra 4 validaciones; dos verificaciones posteriores comparten nombre y el JSON conserva una entrada.':''}\n${tests.join('\n')}`;
 sheet.getRange(`H${row}`).values=[[summary+(capturedResponses[n]?'\nRespuesta visible en captura: '+capturedResponses[n]:'')]];
 sheet.getRange(`I${row}`).values=[['Automatizada (Postman Runner)']];
 sheet.getRange(`J${row}`).values=[[good?'Aprobado':'Fallido']];
 const own=shots.filter(s=>s.id===n&&s.kind!=='peticion');
 const shared=shots.find(s=>s.id!==n&&s.visible?.includes(n));
 let note=`Ejecución: 09/10/2026, 00:00:28 (México).\nJSON: ${results.map(r=>r.name).join('; ')}.\n`;
 if(id!==runCaseId)note+=`Correspondencia por escenario: ${id} en la suite = ${runCaseId} en Postman.\n`;
 if(own.length)note+=`Captura de ${own[0].kind} debajo: ${own[0].filename}.`;
 else if(shared)note+=`Ejecución visible en la lista izquierda de K${shared.row} (${shared.label}). El panel derecho de esa captura corresponde a otro caso. Detalle de esta fila contrastado con el JSON.`;
 else note+='Evidencia de ejecución en el JSON exportado. Sin captura individual de este caso.';
 if(shots.some(s=>s.id===n&&s.kind==='peticion'))note+=`\nCuerpo enviado: captura en F${row}.`;
 sheet.getRange(`K${row}`).values=[[note]];
 validations.push({id,runCaseId,executionNumber:n,row,url:results[0].url,http:status,good,assertions,times:ms,requestImages:shots.filter(s=>s.id===n&&s.kind==='peticion').map(s=>s.filename),responseImages:own.map(s=>s.filename),shared:shared?{row:shared.row,id:shared.label}:null});
}
// Ancho reservado para las imágenes originales, sin paneles fijados ni nuevas hojas.
sheet.getRange('F1:F94').format.columnWidthPx=1030;
sheet.getRange('K1:K94').format.columnWidthPx=1030;
sheet.getRange('E1:E94').format.columnWidthPx=490;
sheet.getRange('G1:H94').format.columnWidthPx=380;
sheet.getRange('F8:H85').format.wrapText=true;
sheet.getRange('F8:H85').format.verticalAlignment='top';
sheet.getRange('K8:K94').format.wrapText=true;
sheet.getRange('K8:K94').format.verticalAlignment='top';
sheet.getRange('I8:I85').format.wrapText=true;
for(const rec of validations){
 const hasImage=shots.some(s=>s.row===rec.row);
 if(!hasImage)sheet.getRange(`A${rec.row}:K${rec.row}`).format.rowHeightPx=Math.max(175,(byId.get(rec.executionNumber).length>1?240:175));
}
sheet.getRange('A2').values=[['Pruebas automatizadas de API']];
sheet.getRange('A3').values=[['Postman Runner: 78 casos, 91 solicitudes principales. 216 validaciones aprobadas, 0 fallos y 0 errores. Duración: 23.418 s. Ejecución del 09/10/2026.']];
sheet.getRange('A3:K3').format.rowHeightPx=30;
sheet.getRange('C88').values=[['Ejecución local con baseUrl=http://localhost:8080. Tres clientes sintéticos creados y dados de baja al final. Cliente principal: 3; usuario: 3; cuenta: 3; número: 000000000000000003. Segundo cliente/usuario: 4. Cliente de límite de edad: 5. Productos y servicios externos excluidos.']];
sheet.getRange('C89').values=[[JSON.stringify(base,null,2)]];
sheet.getRange('A89:K89').format.rowHeightPx=525;
for(const r of [90,92]){sheet.unmergeCells(`C${r}:K${r}`);sheet.mergeCells(`C${r}:J${r}`);}
sheet.getRange('C90').values=[['POST /auth/login, público. Cuerpo inicial: '+JSON.stringify({correo:email,password:cv.password})+'\nEl JWT se guarda en token y se hereda como Bearer. Segundo JWT: tokenOtro. Al actualizar correo se usa '+cv.correoNuevo+'. Después del cambio de contraseña se usa '+cv.passwordNueva+'. Captura del login auxiliar en K90.']];
sheet.getRange('C91').values=[['Orden ejecutado: registro, segundo cliente, logins, negativos de registro, consultas y cambios de cliente, negativos de contraseña antes del cambio válido, consultas y cambios de cuenta, baja de cliente, casos sin JWT y limpieza. No se reejecutaron pruebas para preparar este documento.']];
sheet.getRange('C92').values=[['Resumen real del Runner: 216 PASS, 0 FAIL, 0 SKIPPED, 0 errores. Inicio: 09/10/2026 00:00:28.736; fin: 00:00:52.154 (México). Duración total: 23.418 s. Suma de tiempos de respuesta: 13.557 s; media: 149 ms. Captura general en K92.']];
sheet.getRange('C93').values=[['Fuentes: GestoPago - Suite API automatizada.postman_test_run.json y las 55 capturas originales de Pantallas Test Postman. Request en Pasos / datos; Response y ejecución en Evidencia / notas. Variantes de API-038 y API-069 agrupadas. Correspondencia por escenario: suite API-044/045/046/047 = Postman API-045/046/047/044, respectivamente. Se conservaron los IDs del Excel.']];
sheet.getRange('C94').values=[['El export del Runner guarda códigos HTTP, URLs, tiempos y nombres/resultados de tests; no incluye los cuerpos de petición/respuesta. Los cuerpos literales y mensajes visibles se documentan desde las capturas; las plantillas se identifican como tales. El resumen cuenta 216 validaciones; el JSON contiene 215 nombres únicos porque API-071 tiene dos comprobaciones con el mismo nombre.']];
for(const r of [88,90,91,92,93,94]){sheet.getRange(`C${r}:J${r}`).format.wrapText=true;sheet.getRange(`C${r}:J${r}`).format.verticalAlignment='top';if(![90,92].includes(r))sheet.getRange(`A${r}:K${r}`).format.rowHeightPx=90;}
const imageDir=path.join(out,'capturas');await fs.mkdir(imageDir,{recursive:true});
for(const shot of shots){
 const bytes=await fs.readFile(path.join('C:/Users/lopez/OneDrive/Pictures/Pantallas Test Postman',shot.filename));
 const w=bytes.readUInt32BE(16),h=bytes.readUInt32BE(20),width=1000,height=Math.round(h*width/w);
 const cell=`${shot.col}${shot.row}`;
 const captionText=shot.target==='summary'?'Resumen general del Runner. 216 aprobadas, 0 fallos, 0 omitidas y 0 errores.':shot.target==='login'?'Login auxiliar del cliente principal: HTTP 200, JWT Bearer y 2 validaciones aprobadas.':sheet.getRange(cell).values[0][0];
 const captionHeight=Math.max(shot.col==='F'?58:60,captionText.split('\n').reduce((n,line)=>n+Math.max(1,Math.ceil(line.length/130)),0)*17+8);
 const rowHeight=Math.min(545,Math.max(535,height+captionHeight+12));
 // Una imagen por celda; dos capturas del mismo caso se colocan en F y K.
 const displayWidth=Math.min(width,Math.floor((rowHeight-captionHeight-10)*w/h)),displayHeight=Math.round(h*displayWidth/w);
 sheet.getRange(`A${shot.row}:K${shot.row}`).format.rowHeightPx=rowHeight;
 if(shot.target==='summary')sheet.getRange(cell).values=[['Resumen general del Runner. 216 aprobadas, 0 fallos, 0 omitidas y 0 errores.']];
 if(shot.target==='login')sheet.getRange(cell).values=[['Login auxiliar del cliente principal: HTTP 200, JWT Bearer y 2 validaciones aprobadas.']];
 sheet.getRange(cell).format.verticalAlignment='top';sheet.getRange(cell).format.wrapText=true;
 sheet.images.add({dataUrl:'data:image/png;base64,'+bytes.toString('base64'),anchor:{from:{row:shot.row-1,col:shot.col==='F'?5:10,rowOffsetPx:captionHeight,colOffsetPx:10},extent:{widthPx:displayWidth,heightPx:displayHeight}}});
 await fs.writeFile(path.join(imageDir,shot.filename),bytes);
 Object.assign(shot,{cell,width:w,height:h,displayWidth,displayHeight,captionHeight,rowHeight});
}
sheet.freezePanes.unfreeze();
wb.recalculate();
console.log((await wb.inspect({kind:'region',sheetId:sheet.name,range:'H75:K80',maxChars:1900,tableMaxCellChars:130})).ndjson);
for(const [range,name] of [['F8:F8','body-registration'],['K9:K9','response-duplicate'],['F37:K37','partial-update'],['H78:K79','cascade'],['K92:K92','summary']]){
 const p=await wb.render({sheetName:sheet.name,range,scale:1,format:'png'});await fs.writeFile(path.join(out,name+'.png'),new Uint8Array(await p.arrayBuffer()));
}
await fs.writeFile(path.join(out,'workbook-evidence-audit.json'),JSON.stringify({runId:run.id,totalPass:run.totalPass,totalFail:run.totalFail,runRequests:91,uniqueTests:215,validations,shots},null,2));
const destination=path.join(out,'bienSuite_Pruebas_API_evidencias_completas.xlsx');
await(await SpreadsheetFile.exportXlsx(wb)).save(destination);
console.log(JSON.stringify({destination,imagesAdded:shots.length,cases:validations.length,passed:validations.filter(x=>x.good).length}));
