#!/usr/bin/env python3
"""Test the actual monitor channel switch and restore channel/app choices.
Requires an unlocked authorized phone, enabled monitor channel, matching debug APK.
"""
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from check_device import ADB, PACKAGE, ROOT
from check_network import choice_xml

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    args=parser.parse_args();device=[ADB,'-s',args.serial]
    def run(*command,data=None):
        return subprocess.run(device+list(command),input=data,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30).stdout
    def preferences():return ET.fromstring(run('shell','run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
    def enabled():return any(n.get('name')=='monitor_enabled' and n.get('value')=='true' for n in preferences())
    def service():
        value=run('shell','dumpsys','activity','services',PACKAGE).decode()
        return '.WatchService' in value and 'isForeground=true' in value
    def tree():
        path='/data/local/tmp/ew-channel-check.xml';run('shell','uiautomator','dump',path)
        try:return ET.fromstring(run('shell','cat',path))
        finally:run('shell','rm','-f',path)
    def node(identifier):
        return next((n for n in tree().iter('node') if n.get('resource-id')==identifier),None)
    def tap(target):
        if target is None:raise RuntimeError('Unlock the phone; required control is not visible')
        bounds=list(map(int,re.findall(r'\d+',target.get('bounds',''))))
        if len(bounds)!=4:raise RuntimeError('No tappable control bounds')
        run('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
    def channel_screen():
        run('shell','am','start','-W','-a','android.settings.CHANNEL_NOTIFICATION_SETTINGS',
            '--es','android.provider.extra.APP_PACKAGE',PACKAGE,'--es','android.provider.extra.CHANNEL_ID','monitor')
        control=node('android:id/switch_widget')
        if control is None:raise RuntimeError('No unambiguous Android channel switch; no fallback coordinates are used')
        return control
    def app():run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--ez','qa_keep_awake','true')
    def wait_service(expected):
        for _ in range(40):
            if service()==expected:return
            time.sleep(.2)
        raise RuntimeError('Foreground service state did not match expected state')

    apk=ROOT/'dist/Eternal-Watch-debug.apk'
    metadata=json.loads((ROOT/'app/build/outputs/apk/debug/output-metadata.json').read_text())['elements'][0]
    installed=run('shell','dumpsys','package',PACKAGE).decode()
    if 'versionName='+metadata['versionName'] not in installed or not re.search(r'versionCode='+str(metadata['versionCode'])+r'\b',installed):
        raise RuntimeError('Installed version does not match restoration APK')
    before=preferences();original_service=service()
    if original_service!=enabled():raise RuntimeError('Start the opted-in monitor or turn it off before this explicit test')
    original=next((ET.tostring(n,encoding='unicode') for n in before if n.get('name')=='monitor_enabled'),None)
    choices={n.get('name'):choice_xml(n) for n in before if n.get('name') not in {'sample','error'}}
    recovery=ROOT/'test-results/channel-recovery.json';recovery.parent.mkdir(exist_ok=True)
    if recovery.exists() and not json.loads(recovery.read_text()).get('restored',False):raise RuntimeError('Unfinished channel recovery must be resolved first')
    recovery.touch(mode=0o600);recovery.chmod(0o600)
    recovery.write_text(json.dumps({'original_monitor_xml':original,'original_service':original_service,'restored':False}))
    channel_changed=False;app_changed=False
    try:
        control=channel_screen()
        if control.get('checked')!='true':raise RuntimeError('Enable the monitor channel before this explicit test')
        app();tap(node(PACKAGE+':id/tab_settings'))
        if not enabled():
            app_changed=True;tap(node(PACKAGE+':id/monitor_toggle'))
        wait_service(True)
        control=channel_screen();channel_changed=True;tap(control)
        if node('android:id/switch_widget').get('checked')!='false':raise RuntimeError('Channel switch did not turn off')
        wait_service(False)
        if not enabled():raise RuntimeError('Blocking the channel lost the saved monitoring choice')
        app();tap(node(PACKAGE+':id/tab_settings'))
        warning=node(PACKAGE+':id/monitor_channel_status')
        if warning is None or not warning.get('text'):raise RuntimeError('No visible channel warning in app settings')
        if service():raise RuntimeError('Opening the app restarted a monitor with blocked channel')
        print('Blocked monitor channel stops the real foreground service; choice retained and settings warning visible.',flush=True)
        control=channel_screen();tap(control)
        if node('android:id/switch_widget').get('checked')!='true':raise RuntimeError('Channel switch did not turn back on')
        channel_changed=False;app();wait_service(True)
        print('Reopening after channel re-enable resumes the opted-in monitor.',flush=True)
    finally:
        if channel_changed:
            control=channel_screen()
            if control.get('checked')!='true':tap(control)
            if node('android:id/switch_widget').get('checked')!='true':raise RuntimeError('Channel restoration failed; recovery state retained')
        if app_changed:
            run('shell','am','stop-app',PACKAGE)
            current=preferences()
            for item in list(current):
                if item.get('name')=='monitor_enabled':current.remove(item)
            if original is not None:current.append(ET.fromstring(original))
            command='run-as '+PACKAGE+' sh -c '+shlex.quote('umask 077; cat > shared_prefs/watch-restore.xml && mv shared_prefs/watch-restore.xml shared_prefs/watch.xml')
            run('shell','-T',command,data=ET.tostring(current,encoding='utf-8',xml_declaration=True))
            run('install','-r',str(apk));wait_service(original_service)
        after={n.get('name'):choice_xml(n) for n in preferences() if n.get('name') not in {'sample','error'}}
        if after!=choices:raise RuntimeError('App choice restoration differs; recovery state retained')
        recovery.write_text(json.dumps({'restored':True}))
        print('Original app choices and enabled channel restored; current feed cache retained.',flush=True)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError) as error:
        print('Channel check failed:',error if isinstance(error,RuntimeError) else type(error).__name__)
        raise SystemExit(1)
