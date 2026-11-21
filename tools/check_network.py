#!/usr/bin/env python3
"""Exercise the real Android metering policy over USB, without cellular traffic.

Temporarily overrides the connected Wi-Fi's metering and global Data Saver;
restores exact original override values and Data Saver in finally. Network names
are held in a private, ignored recovery file and never printed. Requires an
authorized/unlocked phone, notifications allowed and monitoring stopped.
"""
import argparse
import copy
import json
import re
import selectors
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from check_device import ADB, PACKAGE, ROOT

def choice_xml(node):
    """Compare preference content; Android may reorder keys and indent differently."""
    node=copy.deepcopy(node)
    for child in node.iter():child.tail=None
    return ET.tostring(node)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--serial',required=True)
    args=parser.parse_args()
    device=[ADB,'-s',args.serial]

    def shell(*command):
        result=subprocess.run(device+['shell',shlex.join(command)],capture_output=True,timeout=35)
        # AOSP setMeteredWifiNetwork() returns -1 even after a successful update.
        # Never infer success from this exception: set_metering checks readback.
        quirk=(command[:4]==('cmd','netpolicy','set','metered-network') and result.returncode==255
               and not result.stdout and not result.stderr)
        if result.returncode and not quirk:
            raise RuntimeError('ADB command failed; network identifiers omitted')
        return result.stdout.decode(errors='replace')

    def wifi_states():
        result={}
        for line in shell('cmd','netpolicy','list','wifi-networks').splitlines():
            if ';' not in line:continue
            identity,value=line.rsplit(';',1)
            if identity in result and result[identity]!=value:
                raise RuntimeError('Conflicting Wi-Fi overrides; no safe exact restoration')
            result[identity]=value
        return result

    preferences=ET.fromstring(shell('run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
    choices={n.get('name'):choice_xml(n) for n in preferences if n.get('name') not in {'sample','error'}}
    if any(n.get('name')=='monitor_enabled' and n.get('value')=='true' for n in preferences):
        raise RuntimeError('Stop monitoring before this explicit network test')
    status=shell('cmd','wifi','status')
    match=re.search(r'SSID: (.*?), BSSID:',status)
    if not match:raise RuntimeError('No connected Wi-Fi; no network settings changed')
    ssid=match.group(1).strip('"')
    original={key:value for key,value in wifi_states().items() if key.strip('"')==ssid}
    if not original:raise RuntimeError('No matching Wi-Fi policy; no settings changed')
    original_saver=shell('cmd','netpolicy','get','restrict-background').strip().endswith('enabled')
    recovery=ROOT/'test-results/network-recovery.json'
    recovery.parent.mkdir(exist_ok=True)
    if recovery.exists() and not json.loads(recovery.read_text()).get('restored',False):
        raise RuntimeError('Unfinished network recovery file exists; restore it before another test')
    recovery.write_text(json.dumps({'wifi_overrides':original,'data_saver':original_saver,
        'app_choices':{k:v.decode() for k,v in choices.items()},'restored':False}))
    recovery.chmod(0o600)

    def set_metering(values):
        for identity,value in values.items():
            shell('cmd','netpolicy','set','metered-network',identity,'undefined' if value=='none' else value)
        for _ in range(30):
            current=wifi_states()
            if all(current.get(k)==v for k,v in values.items()):return
            time.sleep(.2)
        raise RuntimeError('Wi-Fi override readback did not match')

    def instrument(expected,allow=False):
        output=shell('am','instrument','-w','-e','expected',expected,'-e','allow_metered',str(allow).lower(),PACKAGE+'/.NetworkChecks')
        if 'Network policy '+expected+' passed' not in output:raise RuntimeError(output.strip())
        print(output.strip(),flush=True)

    try:
        shell('cmd','netpolicy','set','restrict-background','false')
        set_metering({k:'false' for k in original});instrument('ALLOWED')
        set_metering({k:'true' for k in original});instrument('METERED')
        instrument('ALLOWED',True)
        shell('cmd','netpolicy','set','restrict-background','true');instrument('DATA_SAVER',True)
        shell('cmd','netpolicy','set','restrict-background','false')
        command=['am','instrument','-w','-e','expected','METERED','-e','watch_resume','true',PACKAGE+'/.NetworkChecks']
        process=subprocess.Popen(device+['shell',shlex.join(command)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        output=[];ready=False
        # Read raw chunks to avoid TextIO buffering hiding the readiness marker.
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout,selectors.EVENT_READ)
            deadline=time.monotonic()+30
            while time.monotonic()<deadline:
                if not selector.select(timeout=1):continue
                chunk=process.stdout.read1(4096)
                if not chunk:break
                output.append(chunk)
                if b'NETWORK_RESUME_READY' in b''.join(output):ready=True;break
        if ready:set_metering({k:'false' for k in original})
        tail,_=process.communicate(timeout=40);output.append(tail)
        text=b''.join(output).decode(errors='replace')
        if not ready or 'Network policy METERED passed' not in text:
            raise RuntimeError('Automatic resume check failed: '+text.strip())
        print('Actual background service resumes fetching and updates its notification after reconnect.',flush=True)
    finally:
        failures=[]
        try:
            shell('cmd','netpolicy','set','restrict-background',str(original_saver).lower())
            restored=shell('cmd','netpolicy','get','restrict-background').strip().endswith('enabled')
            if restored!=original_saver:raise RuntimeError('Data Saver readback differs')
        except Exception:failures.append('Data Saver restoration failed')
        try:set_metering(original)
        except Exception:failures.append('Wi-Fi override restoration failed')
        try:
            after=ET.fromstring(shell('run-as',PACKAGE,'cat','shared_prefs/watch.xml'))
            restored_choices={n.get('name'):choice_xml(n) for n in after if n.get('name') not in {'sample','error'}}
            if choices!=restored_choices:raise RuntimeError('App choices differ')
            for _ in range(25):
                service=shell('dumpsys','activity','services',PACKAGE)
                if '.WatchService' not in service or 'isForeground=true' not in service:break
                time.sleep(.2)
            else:raise RuntimeError('Temporary monitor still running')
        except Exception:failures.append('App choice or monitor restoration failed')
        if failures:raise RuntimeError('; '.join(failures)+'; private recovery file retained')
        recovery.write_text(json.dumps({'restored':True}))
        print('Exact original Wi-Fi overrides, Data Saver and app choices restored; temporary monitor stopped.',flush=True)


if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError) as error:
        print('Network check failed:',error)
        raise SystemExit(1)
