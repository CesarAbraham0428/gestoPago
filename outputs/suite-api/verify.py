import zipfile
import xml.etree.ElementTree as E
from pathlib import Path

file = Path('outputs/suite-api/Suite_Pruebas_API.xlsx')
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(file) as z:
    strings = [''.join(x.itertext()) for x in E.fromstring(z.read('xl/sharedStrings.xml'))]
    names = [n for n in z.namelist() if n.startswith('xl/worksheets/sheet') and n.endswith('.xml')]
    assert len(names) == 2
    counts = []
    for name in names:
        doc = E.fromstring(z.read(name))
        assert not doc.findall('.//s:pane', ns), 'Se encontraron paneles inmovilizados'
        errors = [c for c in doc.findall('.//s:c', ns) if c.get('t') == 'e']
        assert not errors
        def value(cell):
            if cell.get('t') == 'inlineStr':
                return ''.join(cell.find('s:is', ns).itertext())
            v = cell.find('s:v', ns)
            if v is not None and cell.get('t') == 'str':
                return v.text or ''
            return strings[int(v.text)] if v is not None and cell.get('t') == 's' else ''
        header = doc.find('.//s:row[@r="7"]', ns)
        assert 'Responsable' not in [value(c) for c in header]
        entries = [value(c) for c in doc.findall('.//s:c', ns) if c.get('r', '').startswith('A')]
        counts.append(sum(v.startswith(('UT-', 'API-')) for v in entries))
    assert counts == [43, 78], counts
    print('Verificado: 2 hojas; 43 casos unitarios (37 existentes + 6 propuestas); 78 HTTP; sin paneles inmovilizados ni errores de celda.')
    print(file.resolve())
