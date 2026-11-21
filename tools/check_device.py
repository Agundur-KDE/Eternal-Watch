#!/usr/bin/env python3
"""Check live data and the real overview UI on an explicitly selected Android device.
Raw UI dumps are deleted after reading; screenshots stay in ignored test-results.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB='/home/alec/projects/KCast-App/.tools/android-sdk/platform-tools/adb'
PACKAGE='de.agundur.eternalwatch'
ROOT=Path(__file__).resolve().parents[1]
UI_FILE='/data/local/tmp/eternal-watch-check.xml'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--serial',required=True)
    args=parser.parse_args();device=[ADB,'-s',args.serial]
    def run(*command):
        return subprocess.run(device+list(command),check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=25).stdout
    run('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
    sample=None
    for _ in range(30):
        try:
            tree=ET.fromstring(run('shell','run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
            raw=next((n.text for n in tree if n.get('name')=='sample'),None)
            if raw:sample=json.loads(raw);break
        except subprocess.CalledProcessError:pass
        time.sleep(.4)
    if sample is None:raise RuntimeError('No saved feed sample; inspect connectivity')
    print('Mercator JSON is present in the app cache; this alone does not prove a new network fetch.')
    run('shell','uiautomator','dump',UI_FILE)
    try:tree=ET.fromstring(run('shell','cat',UI_FILE))
    finally:run('shell','rm','-f',UI_FILE)
    nodes=[n for n in tree.iter('node') if n.get('package')==PACKAGE]
    if not nodes:raise RuntimeError('Phone is locked or Eternal Watch is not visible; unlock it')
    for name in ('watch_face','feed_status','special_day'):
        node=next((n for n in nodes if n.get('resource-id')==PACKAGE+':id/'+name),None)
        if node is None:raise RuntimeError('Overview element missing: '+name)
        print(name+': '+(node.get('text') or node.get('content-desc','')))
    raw=run('exec-out','screencap','-p');png=raw.find(b'\x89PNG\r\n\x1a\n')
    if png<0:raise RuntimeError('Screenshot has no PNG data')
    target=ROOT/'test-results/overview.png';target.parent.mkdir(exist_ok=True);target.write_bytes(raw[png:])
    print('Overview screenshot saved locally (ignored).')
    # Do not change alert choices or grant permissions during this read-only check.
    package=run('shell','dumpsys','package',PACKAGE).decode()
    print('Homescreen widget provider registered:', '.WatchWidget' in package)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError) as error:
        print('Device check failed:',str(error) if isinstance(error,RuntimeError) else type(error).__name__)
        raise SystemExit(1)
