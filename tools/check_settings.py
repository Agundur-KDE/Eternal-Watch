#!/usr/bin/env python3
"""Exercise settings through the real UI and restore the saved app choices.
Explicitly accepts this app's notification permission prompt for the monitor test.
Only selected_days and monitor_enabled are modified; restore them even on failure.
"""
import argparse
import copy
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from check_device import ADB,PACKAGE,UI_FILE

def restore_choices(current,before):
    """Preserve new feed data and unrelated preferences, including missing defaults."""
    result=copy.deepcopy(current)
    keys={'selected_days','monitor_enabled'}
    for node in list(result):
        if node.get('name') in keys:result.remove(node)
    for node in before:
        if node.get('name') in keys:result.append(copy.deepcopy(node))
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    args=parser.parse_args();device=[ADB,'-s',args.serial]
    def run(*command,data=None):
        return subprocess.run(device+list(command),input=data,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30).stdout
    def preferences_xml():
        return ET.fromstring(run('shell','run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
    def prefs():
        tree=preferences_xml()
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
    def expect_selection(expected):
        # UI events and SharedPreferences.apply() reach the on-disk XML asynchronously.
        latest=set()
        for _ in range(20):
            latest=prefs().get('selected_days',set())
            if (len(latest)==expected if isinstance(expected,int) else latest==expected):return
            time.sleep(.2)
        raise RuntimeError('Day selection mismatch: '+str(len(latest))+' saved, expected '+str(expected if isinstance(expected,int) else len(expected)))
    root=Path(__file__).resolve().parents[1]
    apk=root/'dist/Eternal-Watch-debug.apk'
    metadata=root/'app/build/outputs/apk/debug/output-metadata.json'
    if not apk.is_file() or not metadata.is_file():raise RuntimeError('Build the matching debug APK first; it is used to restore the service after testing')
    version=json.loads(metadata.read_text())['elements'][0]
    installed=run('shell','dumpsys','package',PACKAGE).decode()
    match=re.search(r'versionCode=(\d+)',installed)
    if not match or int(match.group(1))!=version['versionCode'] or 'versionName='+version['versionName'] not in installed:
        raise RuntimeError('Installed app version does not match the local restoration APK')
    before=preferences_xml()
    original=prefs()
    changed=False
    def exercise():
        nonlocal changed
        run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--ez','qa_keep_awake','true');tap('tab_settings')
        if prefs().get('monitor_enabled','false')=='true':
            changed=True
            tap('monitor_toggle')
            for _ in range(20):
                if not service():break
                time.sleep(.2)
            if service():raise RuntimeError('Could not suspend monitoring before the settings test')
        changed=True
        tap('monitor_toggle')
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
        expect_selection(49)
        tap('clear_selected_days')
        expect_selection(set())
        tap('search_days');run('shell','input','text','Reconstruction')
        run('shell','input','keyevent','66')  # Submit the real search action and dismiss IME.
        doc=tree();node=next((n for n in doc.iter('node') if n.get('class')=='android.widget.CheckBox' and n.get('text')=='Reconstruction Day'),None)
        if node is None:raise RuntimeError('Day search did not find Reconstruction Day')
        tap_node(node)
        expect_selection({'Reconstruction Day'})
        print('Select-all, clear, search and individual-day selection passed.',flush=True)
    try:
        exercise()
    finally:
        if changed:
            # Kill cached SharedPreferences before atomic replacement. A real package
            # replacement then lets the app restore its own saved monitoring choice.
            run('shell','am','stop-app',PACKAGE)
            current=preferences_xml()
            restored=restore_choices(current,before)
            command='run-as '+PACKAGE+' sh -c '+shlex.quote('umask 077; cat > shared_prefs/watch-restore.xml && mv shared_prefs/watch-restore.xml shared_prefs/watch.xml')
            run('shell','-T',command,data=ET.tostring(restored,encoding='utf-8',xml_declaration=True))
            run('install','-r',str(apk))
            expected=original.get('monitor_enabled','false')=='true'
            for _ in range(50):
                if service()==expected:break
                time.sleep(.2)
            if service()!=expected:raise RuntimeError('Choices restored, but original service state did not recover')
            after=prefs()
            if any(after.get(key)!=original.get(key) for key in ('selected_days','monitor_enabled')):
                raise RuntimeError('Original alert choices were not restored')
            print('Original day selection and monitoring choice restored; latest feed cache preserved.',flush=True)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError,StopIteration) as error:
        print('Settings check failed:',str(error) if isinstance(error,RuntimeError) else type(error).__name__)
        raise SystemExit(1)
