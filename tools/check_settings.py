#!/usr/bin/env python3
"""Exercise first-install settings through the real UI; requires untouched defaults.
Explicitly accepts this app's notification permission prompt for the monitor test.
Ends with monitoring off and no selected days, matching the starting defaults.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from check_device import ADB,PACKAGE,UI_FILE

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    args=parser.parse_args();device=[ADB,'-s',args.serial]
    def run(*command):
        return subprocess.run(device+list(command),check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=25).stdout
    def prefs():
        tree=ET.fromstring(run('shell','run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
        return {n.get('name'):set(x.text for x in n) if n.tag=='set' else n.get('value',n.text or '') for n in tree}
    def tree():
        run('shell','uiautomator','dump',UI_FILE)
        try:return ET.fromstring(run('shell','cat',UI_FILE))
        finally:run('shell','rm','-f',UI_FILE)
    def tap_node(n):
        b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
        if len(b)!=4 or b[2]<=b[0] or b[3]<=b[1]:raise RuntimeError('UI element has no tappable bounds')
        run('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
    def tap(resource,scroll=False):
        for _ in range(8 if scroll else 1):
            doc=tree();nodes=list(doc.iter('node'))
            node=next((n for n in nodes if n.get('resource-id')==PACKAGE+':id/'+resource),None)
            if node is not None:tap_node(node);return
            app=[n for n in nodes if n.get('package')==PACKAGE]
            if not app:raise RuntimeError('Unlock phone and leave Eternal Watch visible')
            if not scroll:break
            sc=next(n for n in app if n.get('class')=='android.widget.ScrollView')
            b=list(map(int,re.findall(r'\d+',sc.get('bounds',''))));x=(b[0]+b[2])//2
            run('shell','input','swipe',str(x),str(int(b[3]*.8)),str(x),str(int(b[3]*.4)),'250')
        raise RuntimeError('UI element missing: '+resource)
    def service():
        s=run('shell','dumpsys','activity','services',PACKAGE).decode()
        return '.WatchService' in s and 'isForeground=true' in s
    before=prefs()
    if before.get('monitor_enabled','false')!='false' or before.get('selected_days',set()):
        raise RuntimeError('This first-install smoke test requires monitoring off and an empty day selection')
    run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity');tap('tab_settings');tap('monitor_toggle')
    doc=tree()
    allow=next((n for n in doc.iter('node') if n.get('resource-id')=='com.android.permissioncontroller:id/permission_allow_button'),None)
    if allow is not None:tap_node(allow)
    for _ in range(20):
        if service():break
        time.sleep(.2)
    if not service() or prefs().get('monitor_enabled')!='true':raise RuntimeError('Monitoring did not start after permission grant')
    print('Real monitor switch starts the foreground service.',flush=True)
    tap('monitor_toggle')
    for _ in range(20):
        if not service():break
        time.sleep(.2)
    if service() or prefs().get('monitor_enabled')!='false':raise RuntimeError('Monitoring did not stop')
    print('Real monitor switch stops the service and clears saved restart choice.',flush=True)
    tap('select_all_days',True)
    if len(prefs().get('selected_days',set()))!=49:raise RuntimeError('Select-all did not save all 49 days')
    tap('clear_selected_days')
    if prefs().get('selected_days',set()):raise RuntimeError('Clear selection did not persist')
    tap('search_days');run('shell','input','text','Reconstruction')
    doc=tree();node=next((n for n in doc.iter('node') if n.get('class')=='android.widget.CheckBox' and n.get('text')=='Reconstruction Day'),None)
    if node is None:raise RuntimeError('Day search did not find Reconstruction Day')
    tap_node(node)
    if prefs().get('selected_days')!={'Reconstruction Day'}:raise RuntimeError('Single-day selection did not persist')
    print('Select-all, clear, search and individual-day selection passed.',flush=True)
    run('shell','input','keyevent','4')
    tap('clear_selected_days')
    run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
    if prefs().get('selected_days',set()) or prefs().get('monitor_enabled')!='false':raise RuntimeError('Defaults not restored')
    print('Finished with original defaults: monitoring off, no selected days.',flush=True)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError,StopIteration) as error:
        print('Settings check failed:',str(error) if isinstance(error,RuntimeError) else type(error).__name__)
        raise SystemExit(1)
