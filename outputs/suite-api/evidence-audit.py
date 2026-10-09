from pathlib import Path
from html.parser import HTMLParser
import json, re, xml.etree.ElementTree as ET
import openpyxl

class TableReader(HTMLParser):
    def __init__(self):
        super().__init__(); self.active=False; self.rows=[]; self.row=[]; self.cell=None
    def handle_starttag(self, tag, attrs):
        attrs=dict(attrs)
        if tag=='table' and 'test-results' in attrs.get('class',''): self.active=True
        if self.active and tag=='tr': self.row=[]
        if self.active and tag=='td': self.cell=''
    def handle_data(self, data):
        if self.cell is not None: self.cell+=data
    def handle_endtag(self, tag):
        if tag=='td' and self.cell is not None: self.row.append(self.cell.strip()); self.cell=None
        if tag=='tr' and self.active and self.row: self.rows.append(self.row)
        if tag=='table' and self.active: self.active=False

root=Path.cwd(); base=root/'build/reports/tests/test'
w=openpyxl.load_workbook('C:/Users/lopez/Downloads/bienSuite_Pruebas_API.xlsx')
s=w['Unitarias']; records=[]; classes={}
for row in s.iter_rows():
    if not str(row[0].value).startswith('UT-') or '#' not in str(row[3].value): continue
    cls, method=row[3].value.split('#')
    candidates=list(base.glob('com.proyecto.servicios.*/index.html'))
    report=next(p for p in candidates if p.parent.name.endswith('.'+cls))
    reader=TableReader(); reader.feed(report.read_text(encoding='utf8'))
    match=[r for r in reader.rows if r[0].split('(')[0]==method]
    assert len(match)==1,(cls,method,match)
    result=match[0]; assert result[3:5]==['0','0'] and result[6]=='100%',result
    source=next((root/'src/test/java').rglob(cls+'.java'))
    src=source.read_text(encoding='utf8')
    assert re.search(r'\bvoid\s+'+re.escape(method)+r'\(',src),(source,method)
    xmlpath=root/'build/test-results/test'/('TEST-'+report.parent.name+'.xml')
    xml=ET.parse(xmlpath).getroot()
    assert xml.get('failures')=='0' and xml.get('errors')=='0' and xml.get('skipped')=='0'
    detail=next((p for p in report.parent.glob('*/index.html') if p.parent.name.startswith(method+'(')),None)
    variants=[]
    if detail:
        vr=TableReader(); vr.feed(detail.read_text(encoding='utf8'))
        variants=[r[0] for r in vr.rows]
        assert len(variants)==int(result[2]),(method,variants)
    else:
        tests=[t for t in xml.findall('testcase') if t.get('name')==result[0]]
        assert len(tests)==1 and not list(tests[0]),(method,tests)
    records.append({'row':row[0].row,'id':row[0].value,'class':cls,'method':method,'tests':int(result[2]),'duration':result[5],'variants':variants,'source':str(source.relative_to(root)),'report':str(report.relative_to(root))})
    classes[cls]={'tests':int(xml.get('tests')),'timestamp':xml.get('timestamp')}
assert len(records)==37 and sum(x['tests'] for x in records)==76
assert sum(x['tests'] for x in classes.values())==76
audit={'records':records,'classes':classes,'summary':{'tests':76,'failures':0,'skipped':0,'duration':'27.542s','generated':'08/10/2026 21:57:56 America/Mexico_City'},'excludedScreenshot':'224041'}
Path('outputs/suite-api/evidence-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf8')
print('Auditados 37 métodos, 76 ejecuciones; nombres, variantes y resultados coinciden con HTML, XML y métodos fuente.')
print(json.dumps(classes,ensure_ascii=False))
