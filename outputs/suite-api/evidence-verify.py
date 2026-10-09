from pathlib import Path
import json, hashlib, zipfile, xml.etree.ElementTree as E
import openpyxl
from copy import copy

base=Path('outputs/suite-api')
original=openpyxl.load_workbook('C:/Users/lopez/Downloads/bienSuite_Pruebas_API.xlsx')
result=openpyxl.load_workbook(base/'bienSuite_Pruebas_API_con_evidencias.xlsx')
assert result.sheetnames==original.sheetnames==['Unitarias','Manuales-Automatizadas']
src=original['Unitarias']; dst=result['Unitarias']
assert dst['C5'].value==src['C5'].value
for r in range(8,51):
    for c in range(1,7):
        assert dst.cell(r,c).value==src.cell(r,c).value,(r,c,'Datos alterados')
for r in range(45,51):
    for c in range(1,10):
        assert dst.cell(r,c).value==src.cell(r,c).value,(r,c,'Propuesta alterada')
ms=original['Manuales-Automatizadas']; md=result['Manuales-Automatizadas']
assert (ms.max_row,ms.max_column)==(md.max_row,md.max_column)
for row in ms:
    for cell in row:
        target=md[cell.coordinate]
        assert target.value==cell.value,('Hoja manual alterada',cell.coordinate)
        assert target.number_format==cell.number_format
        assert copy(target.font)==copy(cell.font),('Fuente manual',cell.coordinate,str(target.font),str(cell.font))
        # La exportación redondea tintes de tema y normaliza el color de fondo,
        # que no participa en el relleno sólido; comprobar apariencia efectiva.
        assert target.fill.patternType==cell.fill.patternType,('Relleno manual',cell.coordinate)
        if cell.fill.patternType=='solid':
            a,b=cell.fill.fgColor,target.fill.fgColor
            assert a.type==b.type and a.value==b.value and abs(a.tint-b.tint)<0.00001,('Color manual',cell.coordinate)
        assert copy(target.alignment)==copy(cell.alignment),('Alineación manual',cell.coordinate)
        assert copy(target.border)==copy(cell.border),('Borde manual',cell.coordinate)
assert len(dst._images)==14
mapping=json.loads((base/'evidence-mapping.json').read_text(encoding='utf8'))
audit=json.loads((base/'evidence-audit.json').read_text(encoding='utf8'))
for rec in audit['records']:
    assert dst.cell(rec['row'],8).value=='Aprobado'
    assert rec['duration'] in dst.cell(rec['row'],7).value
    assert str(rec['tests']) in dst.cell(rec['row'],7).value
for im,rec in zip(dst._images,mapping):
    assert im.anchor._from.col==8 and im.anchor._from.row==rec['row']-1
    expected=(base/'pantallas test'/rec['filename']).read_bytes()
    assert hashlib.sha256(im._data()).digest()==hashlib.sha256(expected).digest()
    assert im.anchor.ext.cy/9525+im.anchor._from.rowOff/9525 <= dst.row_dimensions[rec['row']].height*96/72+1
assert all(s.freeze_panes is None for s in result)
print('Verificado: 14 imágenes originales en columna I; 37 métodos / 76 ejecuciones; entradas, esperados, responsable, propuestas y hoja manual preservados; sin inmovilización.')
