import fs from 'node:fs/promises';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {rows,api} from './cases.mjs';
import {descriptions} from './descriptions.mjs';
const require=createRequire('C:/Users/lopez/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json');
const {Workbook,SpreadsheetFile}=await import(pathToFileURL(require.resolve('@oai/artifact-tool')).href);
const out=process.cwd()+'/outputs/suite-api';
const wb=Workbook.create();
const states=['Pendiente','En ejecución','Aprobado','Fallido','Bloqueado','Aprobado histórico'];
const unitData=rows.map(r=>{
 const method=r[4].split('#')[1];const desc=descriptions[method];
 const report=r[7]==='Existente';
 if(report&&!desc)throw new Error('Falta descripción: '+method);
 return [r[0],r[1].includes(' / ')?'Usuarios':r[1],desc?.[0]||'[Propuesta] '+r[3],r[4],desc?.[1]||r[5],desc?.[2]||r[6],report?'Reporte guardado: aprobado. Sin nueva ejecución.':'Sin implementar ni ejecutar.',report?'Aprobado histórico':'Pendiente',report?`Reporte JUnit de ${r[4].split('#')[0]} en build/test-results/test.\nUTC: ${r[9]}\n${r[2].startsWith('Controlador')?'MockMvc aislado, sin JWT.':'Mockito / validación / serialización.'}`:'Propuesta para ampliar cobertura aislada; no constituye prueba aprobada.',report?new Date(r[9]):null];
});
const apiData=api.map(r=>{
 let body=r[7];if(r[0]==='API-001')body='Enviar el JSON de «Registro base» al pie de esta hoja. Guardar los IDs y el número de cuenta de la respuesta.';
 const contract=r[1]==='Usuarios'?'UsuarioController / UsuarioService':r[1]==='Clientes'?'ClienteController / ClienteServiceImpl':'CuentaController / CuentaService';
 return [r[0],r[1],r[5],r[3],r[4],`${r[6]}\n${body}`,`HTTP ${r[8]}. ${r[9]}`,'Sin ejecutar.','Manual','Pendiente',`${contract}.\nAutomatizable en Postman: comprobar HTTP ${r[8]}, contenido y efectos indicados.`,null];
});
// Ejecutar casos negativos de contraseña antes del cambio válido.
const success=apiData.findIndex(r=>r[2]==='Cambiar contraseña válida');
const [passwordSuccess]=apiData.splice(success,1);
const firstAccount=apiData.findIndex(r=>r[1]==='Cuentas');
apiData.splice(firstAccount,0,passwordSuccess);
apiData.forEach((r,i)=>r[0]=`API-${String(i+1).padStart(3,'0')}`);
function make(name,subtitle,headers,widths,status,lists,data){
 const s=wb.worksheets.add(name);s.showGridLines=false;s.tabColor='#253B53';
 const last=String.fromCharCode(64+headers.length),bottom=7+data.length;
 s.getRange(`A1:${last}${bottom}`).format.font={name:'Arial',size:11,color:'#263445'};
 s.getRange(`A1:${last}${bottom}`).format.verticalAlignment='center';
 widths.forEach((w,i)=>s.getRangeByIndexes(0,i,bottom,1).format.columnWidth=w);
 s.getRange('A2').values=[[subtitle]];
 s.getRange('A2').format.font={name:'Arial',size:16,bold:true,color:'#253B53'};
 s.getRange('A2').format.rowHeight=30;
 s.getRange('A3').values=[['GestoPago · Usuarios, clientes y cuentas']];
 s.getRange('A3').format.font={name:'Arial',size:10,color:'#687787'};
 s.getRange(`A4:${last}4`).format.borders={bottom:{style:'thin',color:'#C6D1DC'}};
 s.getRange('A5:B5').merge();
 s.getRange('A5').values=[['Responsable:']];
 s.getRange('A5').format.font={name:'Arial',size:11,bold:true,color:'#253B53'};
 s.getRange('C5:E5').merge();
 s.getRange('C5:E5').format={fill:'#FFF4D6',rowHeight:27,borders:{preset:'outside',style:'thin',color:'#8898A9'}};
 s.getRange(`A7:${last}7`).values=[headers];
 s.getRange(`A7:${last}7`).format={fill:'#253B53',font:{name:'Arial',size:11,bold:true,color:'#FFFFFF'},rowHeight:34,wrapText:true,horizontalAlignment:'center',verticalAlignment:'center'};
 s.getRange(`A8:${last}${bottom}`).values=data;
 s.getRange(`A8:${last}${bottom}`).format.wrapText=true;
 s.getRange(`A8:${last}${bottom}`).format.rowHeight=38;
 for(let r=8;r<=bottom;r++){
  s.getRange(`A${r}:${last}${r}`).format.fill=r%2===0?'#F2F5F8':'#FFFFFF';
  s.getRange(`A${r}:${last}${r}`).format.borders={preset:'all',style:'thin',color:'#A7B4C2'};
  const lines=Math.max(...data[r-8].map((v,c)=>v instanceof Date?1:String(v??'').split('\n').reduce((n,line)=>n+Math.max(1,Math.ceil(line.length/(widths[c]*0.95))),0)));
  s.getRange(`A${r}:${last}${r}`).format.rowHeight=Math.max(60,lines*15+12);
 }
 s.getRange(`A8:A${bottom}`).format.horizontalAlignment='center';
 s.getRange(`B8:B${bottom}`).dataValidation={rule:{type:'list',values:['Usuarios','Clientes','Cuentas']}};
 const sr=s.getRangeByIndexes(7,status,data.length,1);
 sr.dataValidation={rule:{type:'list',values:states}};
 sr.format.horizontalAlignment='center';
 for(const [text,fill,color] of [['Aprobado','#E9F3EC','#276441'],['Fallido','#FBEAEA','#9F3030'],['Bloqueado','#FFF2DC','#8A6317'],['En ejecución','#E9F0FC','#365F9A']])sr.conditionalFormats.add('containsText',{text,format:{fill,font:{color}}});
 for(const [col,values] of lists)s.getRangeByIndexes(7,col,data.length,1).dataValidation={rule:{type:'list',values}};
 s.getRangeByIndexes(7,headers.length-1,data.length,1).setNumberFormat('dd/mm/yyyy');
 s.freezePanes.unfreeze();
 const help=name==='Unitarias'?[
 ['Lectura del estado','Aprobado histórico identifica reportes previos, sin reejecución ni comprobación de que correspondan a la revisión actual del código. Los 37 métodos existentes corresponden a 76 ejecuciones parametrizadas. Las 6 propuestas siguen pendientes.'],
 ['Ejecución','Configurar Java / JAVA_HOME y ejecutar gradlew.bat test con --tests para UsuarioServiceTest, CuentaServiceTest, ClientePatchTest, ClienteControllerTest, ClienteValidationTest, PasswordValidatorTest y RespuestaPrivacidadTest. Registrar aquí el nuevo resultado y su fecha.'],
 ['Fuentes','Métodos de src/test/java/com/proyecto/servicios/ y reportes XML de build/test-results/test. ClientesPostgresTest es integración con PostgreSQL, no unitaria; se utiliza como referencia de los escenarios HTTP.']
 ]:[
 ['Preparación','Usar BD de pruebas. baseUrl=http://localhost:8080 (ajustar puerto). Crear dos clientes activos con CURP/RFC/correo distintos mediante POST /clientes. Guardar clienteId, usuarioId, cuentaId, numeroCuenta, usuarioOtroId y correoOtro. Mantener numeroCuenta como texto.'],
 ['Registro base',JSON.stringify(JSON.parse(api[0][7]))],
 ['Autenticación','Auxiliar: POST /auth/login con {"correo":"ana.qa@example.com","password":"Segura123!"}. Guardar token de la respuesta y enviar Authorization: Bearer {{token}}. Guardar tokenOtro del segundo cliente. fechaHoy usa America/Mexico_City, formato YYYY-MM-DD.'],
 ['Orden y datos','Ejecutar consultas y negativos con fixture original. Los casos son independientes: restablecer datos si una prueba cambia correo o contraseña. Usar un correo único para cada alta positiva. Renovar token si corresponde. Ejecutar la baja del cliente al final; consultar después con tokenOtro.'],
 ['Automatización','La modalidad indica cómo se ejecutó la prueba; queda Manual como punto de partida. Para automatizar, crear requests en Postman y añadir aserciones de HTTP, JSON y efectos posteriores; ejecutar con Collection Runner. Este archivo documenta los casos y no contiene una colección ejecutable.'],
 ['Ejemplo Postman','pm.test("Estado HTTP", () => pm.response.to.have.status(200)); Adaptar el 200 al resultado esperado de cada fila y añadir las validaciones del cuerpo. Fuente: https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs'],
 ['Cobertura previa','ClientesPostgresTest: reporte guardado con 59 ejecuciones, 0 fallos y 0 omitidos (2026-10-08T20:32:10.989Z). Es cobertura de integración y no prueba que los casos Postman se hayan ejecutado.']
 ];
 for(let i=0;i<help.length;i++){
  const r=bottom+3+i;s.getRange(`A${r}:B${r}`).merge();s.getRange(`A${r}`).values=[[help[i][0]]];
  s.getRange(`C${r}:${last}${r}`).merge();s.getRange(`C${r}`).values=[[help[i][1]]];
  s.getRange(`A${r}:${last}${r}`).format={font:{name:'Arial',size:10,color:'#506275'},wrapText:true,verticalAlignment:'center',rowHeight:help[i][0]==='Registro base'?65:45};
  s.getRange(`A${r}`).format.font.bold=true;
 }
 return s;
}
make('Unitarias','Pruebas unitarias',
 ['ID','Módulo','Caso de prueba','Clase / método','Datos de entrada','Resultado esperado','Resultado obtenido','Estado','Evidencia / notas','Fecha'],
 [10,14,28,24,28,30,30,17,26,13],7,[],unitData);
make('Manuales-Automatizadas','Pruebas manuales y automatizadas',
 ['ID','Módulo','Caso de prueba','Método HTTP','Endpoint','Pasos / datos','Resultado esperado','Resultado obtenido','Modalidad','Estado','Evidencia / notas','Fecha'],
 [10,14,28,15,28,30,30,30,19,17,26,13],9,[[3,['GET','POST','PUT','PATCH','DELETE']],[8,['Manual','Automatizada']]],apiData);
wb.recalculate();
console.log((await wb.inspect({kind:'sheet',include:'id,name',maxChars:1500})).ndjson);
console.log(JSON.stringify({unitRows:unitData.length,httpRows:apiData.length}));
for(const name of ['Unitarias','Manuales-Automatizadas']){
 const img=await wb.render({sheetName:name,range:name==='Unitarias'?'A1:J12':'A1:L12',scale:1,format:'png'});
 await fs.writeFile(`${out}/${name}-formato.png`,new Uint8Array(await img.arrayBuffer()));
}
const file=await SpreadsheetFile.exportXlsx(wb);
try{await file.save(`${out}/Suite_Pruebas_API.xlsx`);console.log('SAVED Suite_Pruebas_API.xlsx');}
catch(e){if(/permission|EBUSY|EACCES|EPERM/i.test(String(e))){await file.save(`${out}/Suite_Pruebas_API_Formato.xlsx`);console.log('SAVED Suite_Pruebas_API_Formato.xlsx (original abierto)');}else throw e;}
