#!/usr/bin/env python3
"""Check required locales, complete keys, format arguments and quantity forms."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]/'app/src/main/res'
def entries(path):
    nodes=list(ET.parse(path).getroot())
    result={n.get('name'):n for n in nodes}
    assert len(result)==len(nodes),'Duplicate resource key: '+str(path)
    return result
def tokens(node):
    return sorted(re.findall(r'%\d+\$[a-zA-Z]',''.join(node.itertext())))

def main():
    base=entries(ROOT/'values/strings.xml')
    required={k:n for k,n in base.items() if n.get('translatable')!='false'}
    for lang,quantities in [('de',{'one','other'}),('es',{'one','many','other'}),('ru',{'one','few','many','other'})]:
        translated=entries(ROOT/f'values-{lang}/strings.xml')
        assert set(translated)==set(required),(lang,'missing',set(required)-set(translated),'extra',set(translated)-set(required))
        for key,node in translated.items():
            reference=required[key]
            assert node.tag==reference.tag,(lang,key,'resource type')
            if node.tag=='plurals':
                assert {n.get('quantity') for n in node}>=quantities,(lang,key,'quantity forms')
                for item in node:assert tokens(item)==tokens(reference[0]),(lang,key,'plural arguments')
            else:assert tokens(node)==tokens(reference),(lang,key,'format arguments')
        print(lang+': '+str(len(required))+' translated resources complete')
    locales=ET.parse(ROOT/'xml/locales_config.xml').getroot()
    assert {n.get('{http://schemas.android.com/apk/res/android}name') for n in locales}=={'de','en','es','ru'}
    print('DE/EN/ES/RU locale registration, arguments and quantities passed.')

if __name__=='__main__':main()
