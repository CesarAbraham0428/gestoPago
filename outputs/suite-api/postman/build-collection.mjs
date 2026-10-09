import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';
import {api} from '../cases.mjs';

const out=path.dirname(fileURLToPath(import.meta.url));
const vars=['token','tokenOtro','tokenPrincipal','clienteId','usuarioId','cuentaId','numeroCuenta','clienteOtroId','usuarioOtroId','cuentaOtroId','numeroCuentaOtro','clienteLimiteId','correo','correoOtro','correoNuevo','curp','rfc','fechaHoy','password','passwordNueva','payload','registroEnviado','clienteAntes','creados'];
const script=(s)=>({type:'text/javascript',exec:s.trim().split('\n')});
const event=(pre,post)=>[...(pre?[{listen:'prerequest',script:script(pre)}]:[]),{listen:'test',script:script(post)}];
const test=(name,expr)=>`pm.test(${JSON.stringify(name)},()=>{${expr}});`;
const get=`const v=k=>pm.collectionVariables.get(k);`;
const stop=`pm.execution.setNextRequest(null);`;
const privacy=`const sinSecretos=x=>{if(x && typeof x==='object'){for(const [k,val] of Object.entries(x)){pm.expect(/password|hash/i.test(k), 'Campo sensible: '+k).to.equal(false);sinSecretos(val);}}};`;
const follow=(url,assertion,token='token',method='GET',body)=>`pm.sendRequest({url:pm.variables.replaceIn(${JSON.stringify(url)}),method:${JSON.stringify(method)},header:{Authorization:'Bearer '+v('${token}'),'Content-Type':'application/json'}${body?`,body:{mode:'raw',raw:pm.variables.replaceIn(${JSON.stringify(body)})}`:''}},(err,res)=>{pm.test(pm.info.requestName+' / verificación posterior',()=>{pm.expect(err).to.equal(null);${assertion}});});`;
const init=`
${vars.map(k=>`pm.collectionVariables.unset('${k}');`).join('\n')}
pm.collectionVariables.set('password','Prueba123!');
pm.collectionVariables.set('passwordNueva','NuevaPrueba456!');
pm.collectionVariables.set('creados','[]');
const partes=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Mexico_City',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
const date=k=>partes.find(p=>p.type===k).value;
pm.collectionVariables.set('fechaHoy',date('year')+'-'+date('month')+'-'+date('day'));
`;
const fixture=`
${get}
const guid=pm.variables.replaceIn('{{$guid}}').replace(/-/g,'');
const a='ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const prefix=a[parseInt(guid.slice(0,2),16)%26]+'A'+a[parseInt(guid.slice(2,4),16)%26]+a[parseInt(guid.slice(4,6),16)%26];
const fecha='900101';
const persona={primerNombre:'Ana',segundoNombre:'Luis',apellidoPaterno:'Perez',apellidoMaterno:'Ruiz',fechaNacimiento:'1990-01-01',
curp:prefix+fecha+'HDFRZN'+(parseInt(guid.slice(6,8),16)%10)+(parseInt(guid.slice(8,10),16)%10),
rfc:prefix+fecha+guid.slice(10,13).toUpperCase(),sexo:'Masculino',nacionalidad:'Mexicana',estadoCivil:'Soltero',
correoElectronico:'qa.'+guid+'@example.com',telefonoMovil:'5512345678',ocupacion:'Programador',empresa:'Pruebas API',ingresoMensual:1500,
domicilio:{calle:'Reforma',numeroExterior:'10',colonia:'Centro',municipio:'Cuauhtemoc',estado:'Ciudad de Mexico',codigoPostal:'06000',pais:'Mexico'},password:v('password')};
`;
const saveBody=`pm.collectionVariables.set('payload',JSON.stringify(persona));pm.collectionVariables.set('registroEnviado',JSON.stringify(persona));`;
function registrationPre(n){
 let extra='';
 if(n===1)extra=`pm.collectionVariables.set('correo',persona.correoElectronico);pm.collectionVariables.set('curp',persona.curp);pm.collectionVariables.set('rfc',persona.rfc);pm.collectionVariables.set('correoNuevo','nuevo.'+persona.correoElectronico);`;
 if(n>=2&&n<=4)extra=`persona.${['','', 'curp','rfc','correoElectronico'][n]}=v('${['','','curp','rfc','correo'][n]}');`;
 const invalid={5:['primerNombre','A1'],6:['correoElectronico','invalido'],7:['telefonoMovil','123'],8:['ingresoMensual',0],9:['domicilio.codigoPostal','6000'],10:['password','abcdefgh'],11:['fechaNacimiento','2999-01-01']};
 if(invalid[n])extra=`persona.${invalid[n][0]}=${JSON.stringify(invalid[n][1])};`;
 if(n===12||n===13)extra=`const hoy=new Date(v('fechaHoy')+'T12:00:00Z');const nacimiento=new Date(Date.UTC(hoy.getUTCFullYear()-18,hoy.getUTCMonth(),hoy.getUTCDate()+${n===13?1:0}));persona.fechaNacimiento=nacimiento.toISOString().slice(0,10);`;
 return (n===1?init:'')+fixture+extra+saveBody;
}
function persistIds(suffix=''){
 return `try{const b=pm.response.json();const req=JSON.parse(v('registroEnviado'));pm.test(pm.info.requestName+' / cliente, usuario y cuenta',()=>{pm.expect(b.id).to.be.a('number');pm.expect(b.usuario.id).to.be.a('number');pm.expect(b.cuentas).to.have.lengthOf(1);pm.expect(b.cuentas[0].saldo).to.equal(0);pm.expect(b.cuentas[0].estaActiva).to.equal(true);pm.expect(b.usuario.activo).to.equal(true);pm.expect(b.activo).to.equal(true);pm.expect(b.curp).to.equal(req.curp);pm.expect(b.rfc).to.equal(req.rfc);pm.expect(b.correoElectronico).to.equal(req.correoElectronico);pm.expect(pm.response.headers.get('Location')).to.equal('/clientes/'+b.id);sinSecretos(b);});
 if(pm.response.code!==201||!b.id||!b.usuario?.id||!b.cuentas?.[0]?.numeroCuenta){${stop}}else{
 pm.collectionVariables.set('cliente${suffix}Id',b.id);${suffix==='Limite'?'':`pm.collectionVariables.set('usuario${suffix}Id',b.usuario.id);pm.collectionVariables.set('cuenta${suffix}Id',b.cuentas[0].id);pm.collectionVariables.set('numeroCuenta${suffix}',b.cuentas[0].numeroCuenta);`}
 const creados=JSON.parse(v('creados'));creados.push(b.id);pm.collectionVariables.set('creados',JSON.stringify(creados));}
 }catch(e){pm.test('Respuesta de registro utilizable',()=>{throw e;});${stop}}`;
}
function request(name,method,url,status,body=null,extra='',pre='',auth){
 const post=get+privacy+test(name+': HTTP '+status,`pm.response.to.have.status(${status});`)+(status>=400?test(name+': error API',`const b=pm.response.json();pm.expect(b.codigo).to.equal(1);pm.expect(b.mensaje).to.be.a('string').and.not.be.empty;`):'')+(status===204?test(name+': sin cuerpo',`pm.expect(pm.response.text()).to.equal('');`):'')+extra;
 return {name,request:{method,header:[{key:'Accept',value:'application/json'},...(body!==null?[{key:'Content-Type',value:'application/json'}]:[])],url,...(auth?{auth}:{}),...(body!==null?{body:{mode:'raw',raw:body,options:{raw:{language:'json'}}}}:{})},event:event(pre,post),response:[]};
}
const noauth={type:'noauth'};
const bearer=k=>({type:'bearer',bearer:[{key:'token',value:'{{'+k+'}}',type:'string'}]});
function login(name,correo,password,token='token',status=200){
 return request(name,'POST','{{baseUrl}}/auth/login',status,JSON.stringify({correo:'{{'+correo+'}}',password:'{{'+password+'}}'}),status===200?`try{const b=pm.response.json();${test(name+': JWT válido en respuesta',`pm.expect(b.token).to.be.a('string').and.not.be.empty;pm.expect(b.tipo).to.equal('Bearer');`)}if(pm.response.code===200&&b.token){pm.collectionVariables.set('${token}',b.token);${token==='token'?`pm.collectionVariables.set('tokenPrincipal',b.token);`:''}}else{${stop}}}catch(e){${stop}throw e;}`:'','',noauth);
}
const pageTest=(label,assert)=>test(label,`const b=pm.response.json();pm.expect(b.content).to.be.an('array').and.not.be.empty;b.content.forEach(c=>{${assert}});sinSecretos(b);`);
const detailTest=(label,assert)=>test(label,`const b=pm.response.json();${assert}sinSecretos(b);`);
const unchanged=()=>follow('{{baseUrl}}/clientes/{{clienteId}}',`pm.expect(res.code).to.equal(200);const b=res.json();const antes=JSON.parse(v('clienteAntes'));for(const k of Object.keys(antes)){if(k!=='fechaActualizacion')pm.expect(b[k],k).to.deep.equal(antes[k]);}`);
const snapshots=()=>`pm.collectionVariables.set('clienteAntes',JSON.stringify(pm.response.json()));`;
const folders=[{name:'01 Preparación y registro',item:[]},{name:'02 Clientes',item:[]},{name:'03 Usuarios',item:[]},{name:'04 Cuentas',item:[]},{name:'05 Baja y cascada',item:[]},{name:'06 Sin autenticación',item:[]},{name:'07 Limpieza',item:[]}];
const byId=new Map();
for(const row of api){
 const [id,module,,method,originalUrl,title,,,status]=row,n=Number(id.slice(4));
 let url=originalUrl,body=null,pre='',extra='',auth;
 if(n<=13){body='{{payload}}';pre=registrationPre(n);auth=noauth;
  if(n===1||n===12)extra=persistIds(n===12?'Limite':'');
  else{const uniqueKey=n===2?'rfc':'curp';extra=follow('{{baseUrl}}/clientes?'+uniqueKey+'={{registroClave}}',`pm.expect(res.code).to.equal(404);`);pre+=`pm.collectionVariables.set('registroClave',persona.${uniqueKey});`;}
 }
 if(n===16)url='{{baseUrl}}/clientes?curp={{curp}}';
 if(n===17)url='{{baseUrl}}/clientes?rfc={{rfc}}';
 if(n===18)url='{{baseUrl}}/clientes?correo={{correo}}';
 if(n>=14&&n<=21){
  const checks={14:`pm.expect(c).not.to.have.property('usuario');pm.expect(c).not.to.have.property('curp');`,15:`pm.expect(c.id).to.equal(Number(v('clienteId')));`,16:`pm.expect(c.curp).to.equal(v('curp'));`,17:`pm.expect(c.rfc).to.equal(v('rfc'));`,18:`pm.expect(c.correoElectronico).to.equal(v('correo'));`,19:`pm.expect(c.cuentas.some(a=>a.numeroCuenta===v('numeroCuenta'))).to.equal(true);`,20:`pm.expect(c.activo).to.equal(true);`,21:`pm.expect(new Intl.DateTimeFormat('en-CA',{timeZone:'America/Mexico_City',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(c.fechaCreacion))).to.equal(v('fechaHoy'));`};
  extra=pageTest(id+': filtro y privacidad',checks[n]);
 }
 if(n===22)extra=detailTest(id+': detalle',`pm.expect(b.id).to.equal(Number(v('clienteId')));pm.expect(b.usuario.id).to.equal(Number(v('usuarioId')));pm.expect(b.cuentas[0].numeroCuenta).to.equal(v('numeroCuenta'));`)+snapshots();
 if(n===30){body='{"telefonoMovil":"5598765432","domicilio":{"calle":"Insurgentes"}}';extra=detailTest(id+': PATCH parcial',`const a=JSON.parse(v('clienteAntes'));pm.expect(b.telefonoMovil).to.equal('5598765432');pm.expect(b.domicilio.calle).to.equal('Insurgentes');for(const k of Object.keys(a)){if(!['telefonoMovil','domicilio','fechaActualizacion'].includes(k))pm.expect(b[k],k).to.deep.equal(a[k]);}for(const k of Object.keys(a.domicilio)){if(!['calle','fechaActualizacion'].includes(k))pm.expect(b.domicilio[k],k).to.deep.equal(a.domicilio[k]);}`)+snapshots()+unchanged();}
 if(n===31){body='{"segundoNombre":null}';extra=detailTest(id+': opcional nulo',`const a=JSON.parse(v('clienteAntes'));pm.expect(b.segundoNombre).to.equal(null);for(const k of Object.keys(a)){if(!['segundoNombre','fechaActualizacion'].includes(k))pm.expect(b[k],k).to.deep.equal(a[k]);}`)+snapshots()+unchanged();}
 if(n===32){body='{"correoElectronico":"{{correoNuevo}}"}';extra=detailTest(id+': sincronización',`pm.expect(b.correoElectronico).to.equal(v('correoNuevo'));pm.expect(b.usuario.correo).to.equal(v('correoNuevo'));`)+snapshots()+unchanged();}
 if(n>=33&&n<=37){body=JSON.stringify({[['curp','rfc','numeroCuenta','activo','password'][n-33]]:'cambio'});extra=unchanged();}
 if(n===38){body='[]';extra=unchanged();}
 if(n===39){body='{"correoElectronico":"{{correoOtro}}"}';extra=unchanged();}
 if(n===40||n===41){body='{}';extra=test(id+': Allow',`pm.expect(pm.response.headers.get('Allow')).to.be.a('string').and.not.be.empty;${n===41?`pm.expect(pm.response.headers.get('Allow')).to.include('PATCH');`:''}`);}
 if(n===42)extra=detailTest(id+': identidad',`pm.expect(b.id).to.equal(Number(v('usuarioId')));pm.expect(b.clienteId).to.equal(Number(v('clienteId')));pm.expect(b.correo).to.equal(v('correoNuevo'));pm.expect(b.activo).to.equal(true);`);
 if(n>=44&&n<=47){const actual=n===45?'Incorrecta123!':'{{password}}',nuevo=n===46?'abcdefgh':'{{passwordNueva}}';body=JSON.stringify({passwordActual:actual,passwordNueva:nuevo});}
 if(n===48)extra=pageTest(id+': resumen',`pm.expect(c).to.have.all.keys('id','clienteId','numeroCuenta','estaActiva');`);
 if(n>=49&&n<=52)extra=pageTest(id+': filtro',n===51?`pm.expect(c.saldo).to.equal(0);`:n===52?`pm.expect(c.id).to.equal(Number(v('cuentaId')));`:`pm.expect(c.estaActiva).to.equal(true);`);
 if(n===53||n===54)extra=detailTest(id+': cuenta propia',`pm.expect(b.id).to.equal(Number(v('cuentaId')));pm.expect(b.clienteId).to.equal(Number(v('clienteId')));pm.expect(b.numeroCuenta).to.equal(v('numeroCuenta'));pm.expect(b.saldo).to.equal(0);`);
 if(n===55)extra=test(id+': saldo inicial',`pm.expect(pm.response.json()).to.equal(0);`);
 if(n===67||n===68){body=JSON.stringify({activa:n===68});extra=detailTest(id+': estado',`pm.expect(b.estaActiva).to.equal(${n===68});`)+follow('{{baseUrl}}/cuentas/{{numeroCuenta}}',`pm.expect(res.code).to.equal(200);pm.expect(res.json().estaActiva).to.equal(${n===68});`);}
 if(n===69){body='{}';extra=follow('{{baseUrl}}/cuentas/{{numeroCuenta}}',`pm.expect(res.code).to.equal(200);pm.expect(res.json().estaActiva).to.equal(true);`);}
 if(n===70)extra=follow('{{baseUrl}}/cuentas/{{numeroCuenta}}',`pm.expect(res.code).to.equal(200);pm.expect(res.json().id).to.equal(Number(v('cuentaId')));pm.expect(res.json().estaActiva).to.equal(false);`);
 if(n===71)extra=follow('{{baseUrl}}/clientes/{{clienteId}}',`pm.expect(res.code).to.equal(200);const b=res.json();pm.expect(b.activo).to.equal(false);pm.expect(b.usuario.activo).to.equal(false);pm.expect(b.cuentas.every(c=>c.estaActiva===false)).to.equal(true);pm.collectionVariables.set('clienteAntes',JSON.stringify(b));`,'tokenOtro')+follow('{{baseUrl}}/usuarios/{{usuarioId}}',`pm.expect(res.code).to.equal(401);`,'tokenPrincipal')+`pm.collectionVariables.set('token',v('tokenOtro'));`;
 if(n===72){body='{"empresa":"Nueva"}';extra=unchanged();}
 if(n===73){body='{"activa":true}';extra=follow('{{baseUrl}}/cuentas/{{numeroCuenta}}',`pm.expect(res.code).to.equal(200);pm.expect(res.json().estaActiva).to.equal(false);`);}
 if(n>=74)auth=noauth;
 if(status>=400&&body===null&&['PUT','PATCH'].includes(method))body=row[7].startsWith('{')?row[7]:'{}';
 const item=request(id+' | '+title,method,url,status,body,extra,pre,auth);item.request.description=`Módulo: ${module}\nCaso de la suite: ${id}\nResultado esperado: ${row[9]}\nFuente: controladores y servicios locales. Pendiente de ejecutar contra la API. Los datos de registro se generan por ejecución.`;
 byId.set(n,item);
}
folders[0].item.push(byId.get(1));
folders[0].item.push(request('AUX | Registrar segundo cliente','POST','{{baseUrl}}/clientes',201,'{{payload}}',persistIds('Otro'),fixture+`pm.collectionVariables.set('correoOtro',persona.correoElectronico);`+saveBody,noauth));
folders[0].item.push(login('AUX | Login cliente principal','correo','password'),login('AUX | Login segundo cliente','correoOtro','password','tokenOtro'));
for(let n=2;n<=13;n++)folders[0].item.push(byId.get(n));
for(let n=14;n<=41;n++){folders[1].item.push(byId.get(n));if(n===32)folders[1].item.push(login('AUX | Login con correo actualizado','correoNuevo','password'));}
// Los negativos de contraseña conservan la contraseña inicial. El cambio válido va después.
for(const n of [42,43,45,46,47])folders[2].item.push(byId.get(n));
folders[2].item.push(login('AUX | Contraseña original sigue vigente','correoNuevo','password'),byId.get(44),login('AUX | Contraseña anterior rechazada','correoNuevo','password','token',401),login('AUX | Login con contraseña nueva','correoNuevo','passwordNueva'));
for(let n=48;n<=70;n++)folders[3].item.push(byId.get(n));
// Reactivar antes de la baja de cliente para verificar que la cascada realmente la desactiva.
folders[4].item.push(request('AUX | Preparar cuenta activa para cascada','PUT','{{baseUrl}}/cuentas/{{numeroCuenta}}/estado',200,'{"activa":true}',detailTest('Cuenta activa antes de cascada',`pm.expect(b.estaActiva).to.equal(true);`)));
for(let n=71;n<=73;n++)folders[4].item.push(byId.get(n));
for(let n=74;n<=78;n++)folders[5].item.push(byId.get(n));
folders[6].item.push(request('AUX | Desactivar cliente del límite de edad','DELETE','{{baseUrl}}/clientes/{{clienteLimiteId}}',204,null,'','',bearer('tokenOtro')),request('AUX | Desactivar segundo cliente','DELETE','{{baseUrl}}/clientes/{{clienteOtroId}}',204,null,`pm.collectionVariables.unset('token');pm.collectionVariables.unset('tokenOtro');pm.collectionVariables.unset('tokenPrincipal');`,'',bearer('tokenOtro')));

// Variantes que la suite solicita repetir: quedan identificadas con el mismo ID y sufijo.
for(const raw of ['null','"texto"']){
 const i=folders[1].item.findIndex(x=>x.name.startsWith('API-038'));
 folders[1].item.splice(i+1,0,request('API-038 variante '+raw,'PATCH','{{baseUrl}}/clientes/{{clienteId}}',400,raw,unchanged()));
}
const i69=folders[3].item.findIndex(x=>x.name.startsWith('API-069'));
folders[3].item.splice(i69+1,0,request('API-069 variante null','PUT','{{baseUrl}}/cuentas/{{numeroCuenta}}/estado',400,'{"activa":null}',follow('{{baseUrl}}/cuentas/{{numeroCuenta}}',`pm.expect(res.code).to.equal(200);pm.expect(res.json().estaActiva).to.equal(true);`)));

const collection={info:{name:'GestoPago - Suite API automatizada',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'78 casos API-001 a API-078 de la suite, más variantes y auxiliares. Usuarios, clientes y cuentas. Ejecutar TODA la colección en orden con 1 iteración, API y BD de pruebas disponibles. Crea tres clientes sintéticos y termina con bajas lógicas; no elimina físicamente los registros. No ejecuta productos ni servicios externos. Si falla el registro o login, se detiene; puede requerir limpiar los IDs guardados en la variable creados. No hay resultados reales hasta ejecutar.'},auth:bearer('token'),variable:[{key:'baseUrl',value:'http://localhost:8080',type:'string'},...vars.map(key=>({key,value:'',type:'string'})),{key:'registroClave',value:'',type:'string'}],item:folders};
const all=folders.flatMap(f=>f.item);
for(const item of all)for(const e of item.event)new vm.Script(e.script.exec.join('\n'));
if(byId.size!==78)throw new Error('Cobertura de IDs incompleta');
for(const item of all){if(!item.request.url.startsWith('{{baseUrl}}/'))throw new Error('URL fuera del alcance');if(!item.event.some(e=>e.listen==='test'))throw new Error('Request sin validaciones');}
await fs.mkdir(out,{recursive:true});
await fs.writeFile(path.join(out,'GestoPago_Automatizada.postman_collection.json'),JSON.stringify(collection,null,2));
await fs.writeFile(path.join(out,'Local.postman_environment.json'),JSON.stringify({name:'GestoPago - Local pruebas',values:[{key:'baseUrl',value:'http://localhost:8080',enabled:true}],_postman_variable_scope:'environment'},null,2));
await fs.writeFile(path.join(out,'cobertura.json'),JSON.stringify({casosSuite:78,solicitudes:all.length,variantes:3,auxiliares:all.length-81,estado:'Preparada; no ejecutada contra API',casos:api.map(r=>({id:r[0],modulo:r[1],metodo:r[3],httpEsperado:r[8],escenario:r[5]}))},null,2));
console.log(JSON.stringify({output:out,casos:78,requests:all.length,scriptsVerificados:all.reduce((n,x)=>n+x.event.length,0)}));
