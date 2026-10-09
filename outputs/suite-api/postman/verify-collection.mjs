import fs from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

const collection=JSON.parse(await fs.readFile(new URL('./GestoPago_Automatizada.postman_collection.json',import.meta.url),'utf8'));
const all=collection.item.flatMap(f=>f.item);
const ids=new Set(all.map(x=>x.name.match(/^API-(\d{3})/)?.[1]).filter(Boolean));
assert.equal(ids.size,78);
for(let n=1;n<=78;n++)assert(ids.has(String(n).padStart(3,'0')));
assert.equal(all.length,91);
assert.equal(all.filter(x=>x.name.startsWith('AUX')).length,10);
assert(!JSON.stringify(collection).includes('vault:'));
assert(!JSON.stringify(collection).includes('portalventas'));
assert(!JSON.stringify(collection).includes('/productos'));
assert.equal(collection.auth.bearer[0].value,'{{token}}');
assert(all.every(x=>x.request.url.startsWith('{{baseUrl}}/')));
const curp=/^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$/;
const rfc=/^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$/;
const map=new Map([['baseUrl','http://localhost:8080']]);
const replace=s=>s.replace(/\{\{([^}]+)\}\}/g,(_,k)=>k==='$guid'?randomUUID():(assert(map.has(k)&&map.get(k)!=='',`Variable sin valor: ${k}`),String(map.get(k))));
const pm={collectionVariables:{get:k=>map.get(k),set:(k,v)=>map.set(k,v),unset:k=>map.delete(k)},variables:{replaceIn:replace}};
const pre=item=>{const e=item.event.find(x=>x.listen==='prerequest');if(e)vm.runInNewContext(e.script.exec.join('\n'),{pm,Intl,Date});};
const first=all[0];
const emails=new Set();
for(let i=0;i<300;i++){
 pre(first);
 const p=JSON.parse(map.get('payload'));
 assert(curp.test(p.curp));assert(rfc.test(p.rfc));assert(!emails.has(p.correoElectronico));emails.add(p.correoElectronico);
 assert.equal(p.curp.length,18);assert.equal(p.rfc.length,13);
}
const auxIds={clienteId:100,usuarioId:100,cuentaId:100,numeroCuenta:'000000000000000100',clienteOtroId:101,usuarioOtroId:101,cuentaOtroId:101,numeroCuentaOtro:'000000000000000101',clienteLimiteId:102};
// Comprueba plantillas y dependencias con resultados simulados, sin hacer HTTP.
for(const item of all){
 for(const e of item.event)new vm.Script(e.script.exec.join('\n'));
 pre(item);replace(item.request.url);
 if(item.request.auth?.type==='bearer')replace(item.request.auth.bearer[0].value);
 else if(!item.request.auth)assert(map.has('token'),'Falta login previo');
 if(item.request.body)JSON.parse(replace(item.request.body.raw));
 if(item.name.startsWith('API-001'))for(const k of ['clienteId','usuarioId','cuentaId','numeroCuenta'])map.set(k,auxIds[k]);
 if(item.name==='AUX | Registrar segundo cliente')for(const k of ['clienteOtroId','usuarioOtroId','cuentaOtroId','numeroCuentaOtro'])map.set(k,auxIds[k]);
 if(item.name.startsWith('API-012'))map.set('clienteLimiteId',auxIds.clienteLimiteId);
 if(item.name.startsWith('AUX | Login')||item.name==='AUX | Contraseña original sigue vigente'){
  const post=item.event.find(e=>e.listen==='test').script.exec.join('\n');
  for(const k of ['token','tokenOtro','tokenPrincipal'])if(post.includes(`set('${k}'`))map.set(k,'JWT-simulado');
 }
}
const index=n=>all.findIndex(x=>x.name.startsWith('API-'+String(n).padStart(3,'0')+' |'));
assert(index(45)<index(44));assert(index(46)<index(44));assert(index(47)<index(44));
assert(index(68)<index(70));assert(index(70)<index(71));assert(index(71)<index(72));assert(index(71)<index(73));
assert(all.slice(index(74),index(78)+1).every(x=>x.request.auth.type==='noauth'));
assert(all.at(-1).name==='AUX | Desactivar segundo cliente');
console.log('OK: 78 IDs, 91 solicitudes, sintaxis de scripts, 300 fixtures válidos, variables y orden de dependencias. Sin llamadas HTTP.');
