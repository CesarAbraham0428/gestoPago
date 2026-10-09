import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
const require=createRequire('C:/Users/lopez/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json');
const {FileBlob,SpreadsheetFile}=await import(pathToFileURL(require.resolve('@oai/artifact-tool')).href);
const root=process.cwd(),out=path.join(root,'outputs/suite-api');
const audit=JSON.parse(await fs.readFile(path.join(out,'evidence-audit.json'),'utf8'));
const input='C:/Users/lopez/Downloads/bienSuite_Pruebas_API.xlsx';
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load(input));
const s=wb.worksheets.getItem('Unitarias');
const shots=[
 [8,'223933','UsuarioServiceTest',null,'E-01 · UsuarioServiceTest · 9 ejecuciones aprobadas. Detalle de esta fila en Resultado obtenido.'],
 [16,'223919','CuentaServiceTest',null,'E-02 · CuentaServiceTest · 6 ejecuciones aprobadas. Detalle de esta fila en Resultado obtenido.'],
 [22,'223825','ClientePatchTest',null,'E-03 · ClientePatchTest · 22 ejecuciones aprobadas. Incluye los 8 métodos de UT-015 a UT-022.'],
 [23,'223850','ClientePatchTest','rechazaCamposProtegidosSinMutarEntidades','E-04 · UT-016 · 7 variantes: curp, rfc, numeroCuenta, cuentas, activo, rol y password.'],
 [24,'223903','ClientePatchTest','rechazaDatosInvalidosSinMutarEntidades','E-05 · UT-017 · 7 variantes inválidas; todas aprobadas, sin fallos ni omitidas.'],
 [25,'223837','ClientePatchTest','exigeUnObjetoJsonAntesDeConsultarLaBase','E-06 · UT-018 · 3 variantes: [], null y "texto"; todas aprobadas.'],
 [30,'223800','ClienteControllerTest',null,'E-07 · ClienteControllerTest · 3 métodos aprobados. MockMvc aislado, sin cadena JWT.'],
 [33,'223948','ClienteValidationTest',null,'E-08 · ClienteValidationTest · 24 ejecuciones aprobadas. Resumen de 6 métodos.'],
 [34,'224100','ClienteValidationTest','rechazaCampoInvalido','E-09 · UT-027 · 15 variantes aprobadas. Captura validada: variante 15 = 0.024 s.'],
 [36,'224024','ClienteValidationTest','actualizacionRechazaIdentificadoresInmutables','E-10 · UT-029 · 3 variantes: curp, rfc y numeroCuenta.'],
 [37,'223954','ClienteValidationTest','aceptaAmbasLongitudesDeRfcConFormatoValido','E-11 · UT-030 · RFC de 12 y 13 caracteres: ambas variantes aprobadas.'],
 [38,'224008','ClienteValidationTest','aceptaLosLimitesInclusivosDeLongitudDelNombre','E-12 · UT-031 · Longitudes 2 y 50: ambas variantes aprobadas.'],
 [39,'223741','PasswordValidatorTest',null,'E-13 · Resumen general: 76 tests, 0 fallos, 0 omitidos, 27.542 s. PasswordValidatorTest: 10 tests; detalle contrastado con HTML/XML, sin captura de clase.'],
 [43,'223810','RespuestaPrivacidadTest',null,'E-14 · RespuestaPrivacidadTest · 2 métodos aprobados, 0 fallos y 0 omitidos.']
];
const classRows={UsuarioServiceTest:8,CuentaServiceTest:16,ClientePatchTest:22,ClienteControllerTest:30,ClienteValidationTest:33,PasswordValidatorTest:39,RespuestaPrivacidadTest:43};
const shotByRow=new Map(shots.map(v=>[v[0],v]));
// Mantener datos de entrada y resultados esperados del archivo del usuario.
for(const rec of audit.records){
 const present=s.getRange(`D${rec.row}`).values[0][0];
 if(present!==`${rec.class}#${rec.method}`)throw Error('Asignación inválida: '+rec.id);
 s.getRange(`G${rec.row}`).values=[[`Aprobado: ${rec.tests} ${rec.tests===1?'ejecución':'ejecuciones'}, 0 fallos, 0 omitidos; ${rec.duration}. Aserciones del método sin fallos. Reporte del 08/10/2026, 21:57:56 (México).`]];
 s.getRange(`H${rec.row}`).values=[['Aprobado']];
 if(!shotByRow.has(rec.row)){
  const anchor=classRows[rec.class];
  s.getRange(`I${rec.row}`).values=[[`Ver captura en I${anchor}: ${rec.class}.\nMétodo: ${rec.method}.\n${rec.tests} ${rec.tests===1?'ejecución aprobada':'ejecuciones aprobadas'}; ${rec.duration}.${rec.class==='PasswordValidatorTest'?'\nEl resumen acredita el total de la clase; método y variantes verificados en su HTML/XML.':''}`]];
  s.getRange(`I${rec.row}`).format.verticalAlignment='top';
 }
}
s.getRange('I1:I55').format.columnWidthPx=1030;
const imageDir=path.join(out,'pantallas test');await fs.mkdir(imageDir,{recursive:true});
const evidenceRecords=[];
for(const [row,time,cls,method,caption] of shots){
 const rec=audit.records.find(v=>v.row===row);
 if(rec.class!==cls || (method&&rec.method!==method))throw Error('Captura no corresponde: '+row);
 const filename=`Captura de pantalla 2026-10-08 ${time}.png`;
 const bytes=await fs.readFile(path.join('C:/Users/lopez/OneDrive/Pictures/Pantallas Test',filename));
 const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20);
 const displayedWidth=1000,displayedHeight=Math.round(height*displayedWidth/width);
 const target=path.join(imageDir,filename);await fs.writeFile(target,bytes);
 s.getRange(`I${row}`).values=[[caption]];
 s.getRange(`I${row}`).format={wrapText:true,verticalAlignment:'top',font:{name:'Arial',size:11,color:'#253B53'}};
 const rowHeight=displayedHeight+75;
 if(rowHeight>545)throw Error('Imagen supera alto seguro de fila: '+filename);
 s.getRange(`A${row}:I${row}`).format.rowHeightPx=rowHeight;
 s.images.add({dataUrl:'data:image/png;base64,'+bytes.toString('base64'),anchor:{from:{row:row-1,col:8,rowOffsetPx:60,colOffsetPx:10},extent:{widthPx:displayedWidth,heightPx:displayedHeight}}});
 evidenceRecords.push({row,cell:`I${row}`,id:rec.id,class:cls,method:method||'Resumen de clase',filename,width,height,displayedWidth,displayedHeight});
}
s.freezePanes.unfreeze();
s.getRange('C53').values=[['Capturas del reporte generado el 08/10/2026 a las 21:57:56 (America/Mexico_City): 37 métodos, 76 ejecuciones; 0 fallos, 0 omitidos, 100 % de éxito, 27.542 s. Las 6 propuestas continúan pendientes. Las imágenes se comparten por clase y se referencian desde sus filas.']];
s.getRange('C54').values=[['Concordancia: clase y método contrastados con HTML, variantes con HTML/XML y datos/resultados con el código de las pruebas. Sin captura individual de PasswordValidatorTest: el resumen acredita 10 tests y el detalle se verificó en sus reportes. La captura 224041 se excluyó porque muestra 0.044 s en la variante 15; 224100 y los reportes muestran 0.024 s.']];
s.getRange('C55').values=[['Fuentes: capturas originales de Pantallas Test; build/reports/tests/test y build/test-results/test; src/test/java/com/proyecto/servicios. Las capturas muestran ejecución y éxito; las entradas y aserciones se corroboraron en las pruebas fuente. No se ejecutaron nuevas pruebas ni se evaluaron endpoints de Postman.']];
s.getRange('A53:I55').format.rowHeight=55;
wb.recalculate();
console.log((await wb.inspect({kind:'region',sheetId:'Unitarias',range:'D39:I42',maxChars:2500,tableMaxCellChars:130})).ndjson);
await fs.writeFile(path.join(out,'evidence-mapping.json'),JSON.stringify(evidenceRecords,null,2));
for(const [range,name] of [['G8:I8','evidence-user'],['H34:I34','evidence-variants'],['H39:I39','evidence-summary']]){
 const p=await wb.render({sheetName:'Unitarias',range,scale:1,format:'png'});
 await fs.writeFile(path.join(out,name+'.png'),new Uint8Array(await p.arrayBuffer()));
}
await (await SpreadsheetFile.exportXlsx(wb)).save(path.join(out,'bienSuite_Pruebas_API_con_evidencias.xlsx'));
console.log('Guardado: bienSuite_Pruebas_API_con_evidencias.xlsx; 14 imágenes originales embebidas, 37 filas verificadas.');
