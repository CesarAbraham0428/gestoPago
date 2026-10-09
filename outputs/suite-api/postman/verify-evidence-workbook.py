from pathlib import Path
from copy import copy
import json, hashlib, re
import openpyxl

folder=Path('outputs/suite-api/postman')
source=openpyxl.load_workbook('C:/Users/lopez/Downloads/bienSuite_Pruebas_API_con_evidencias.xlsx')
result=openpyxl.load_workbook(folder/'bienSuite_Pruebas_API_evidencias_completas.xlsx')
audit=json.loads((folder/'workbook-evidence-audit.json').read_text(encoding='utf-8'))
report=json.loads(Path('C:/Users/lopez/Downloads/GestoPago - Suite API automatizada.postman_test_run.json').read_text(encoding='utf-8-sig'))
assert result.sheetnames==source.sheetnames==['Unitarias','Manuales-Automatizadas']
original,target=source.worksheets[0],result.worksheets[0]
assert str(original.merged_cells)==str(target.merged_cells)
assert len(original._images)==len(target._images)==14
for row in original:
    for c in row:
        other=target[c.coordinate]
        assert other.value==c.value,('Unitarias: valor',c.coordinate)
        assert copy(other.font)==copy(c.font),('Unitarias: fuente',c.coordinate)
        assert copy(other.alignment)==copy(c.alignment),('Unitarias: alineación',c.coordinate)
        assert copy(other.border)==copy(c.border),('Unitarias: borde',c.coordinate)
        assert other.number_format==c.number_format
        assert other.fill.patternType==c.fill.patternType
        if c.fill.patternType=='solid':
            a,b=c.fill.fgColor,other.fill.fgColor
            assert a.type==b.type and a.value==b.value and abs(a.tint-b.tint)<1e-5
for a,b in zip(original._images,target._images):
    assert a.anchor._from==b.anchor._from and a.anchor.ext==b.anchor.ext,'Posición o tamaño unitario alterado'
    assert hashlib.sha256(a._data()).digest()==hashlib.sha256(b._data()).digest()
for k,d in original.row_dimensions.items():
    assert d.height==target.row_dimensions[k].height,('Altura unitaria',k)
for k,d in original.column_dimensions.items():
    assert abs(d.width-target.column_dimensions[k].width)<1e-5,('Ancho unitario',k)

s,t=source.worksheets[1],result.worksheets[1]
assert (s.max_row,s.max_column)==(t.max_row,t.max_column)==(94,11)
assert t['C5'].value==s['C5'].value
assert len(t._images)==len(audit['shots'])==55
for row in range(8,86):
    for col in [1,2,3,4,7]:
        assert s.cell(row,col).value==t.cell(row,col).value,('Caso original alterado',row,col)
    assert t.cell(row,10).value=='Aprobado'
    assert t.cell(row,9).value=='Automatizada (Postman Runner)'
assert len(audit['validations'])==78 and all(r['good'] for r in audit['validations'])
for rec in audit['validations']:
    results=[r for r in report['results'] if r['name'].startswith(rec['runCaseId'])]
    assert results and all(r['responseCode']['code']==rec['http'] for r in results)
    assert all(all(v is True for v in r['tests'].values()) for r in results)
    assert t.cell(rec['row'],5).value==results[0]['url']
    assert t.cell(rec['row'],1).value==rec['id']
    assert all(str(ms) in t.cell(rec['row'],8).value for ms in rec['times'])
    expected=int(re.search(r'HTTP (\d+)',s.cell(rec['row'],7).value)[1])
    assert expected==rec['http']
for im,shot in zip(t._images,audit['shots']):
    assert im.anchor._from.row==shot['row']-1
    assert im.anchor._from.col==(5 if shot['col']=='F' else 10)
    assert im.anchor._from.rowOff/9525>=shot['captionHeight']-1
    assert im.anchor.ext.cx/9525<1030
    assert im.anchor.ext.cy/9525+im.anchor._from.rowOff/9525<=t.row_dimensions[shot['row']].height*96/72+1
    expected=Path('C:/Users/lopez/OneDrive/Pictures/Pantallas Test Postman')/shot['filename']
    assert hashlib.sha256(im._data()).digest()==hashlib.sha256(expected.read_bytes()).digest(),shot['filename']
assert all(w.freeze_panes is None for w in result)
assert report['totalPass']==216 and report['totalFail']==0
print('OK: 55 capturas originales, 78 escenarios con HTTP y validaciones concordantes, cuerpos de petición, mapeo de contraseña por escenario, primera hoja con sus 14 imágenes preservada. Sin inmovilización ni nuevas hojas.')
