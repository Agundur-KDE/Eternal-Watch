#!/usr/bin/env python3
"""Prepare local APK/source packages from an isolated clean commit; never upload."""
import argparse
import base64
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def run(*command,capture=False,directory=ROOT):
    result=subprocess.run(list(command),cwd=directory,check=True,stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None

def sha256(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def ignored_if_local(path):
    if path.is_relative_to(ROOT) and not run('git','check-ignore',str(path),capture=True).strip():
        raise RuntimeError('Signing material must be excluded from Git')

def main():
    parser=argparse.ArgumentParser()
    mode=parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--unsigned',action='store_true')
    mode.add_argument('--signing-config',type=Path)
    parser.add_argument('--bundle',action='store_true')
    parser.add_argument('--output-dir',type=Path,default=ROOT/'dist')
    args=parser.parse_args()
    if run('git','status','--porcelain','--untracked-files=normal',capture=True).strip():
        raise RuntimeError('Commit the complete source first; release packages require a clean working tree')
    commit=run('git','rev-parse','HEAD',capture=True).decode().strip()
    config=None
    if args.signing_config:
        config_path=args.signing_config.resolve();ignored_if_local(config_path)
        if config_path.stat().st_mode & 0o077:raise RuntimeError('Signing config must be owner-only (chmod 600)')
        config=json.loads(config_path.read_text())
        for field in ('keystore','store_password_file','key_password_file'):
            path=Path(config[field]).expanduser().resolve();ignored_if_local(path)
            if not path.is_file() or path.stat().st_mode & 0o077:raise RuntimeError('Signing material must exist with owner-only permissions')
            config[field]=str(path)
        if not re.fullmatch(r'[0-9a-fA-F]{64}',config['certificate_sha256']):raise RuntimeError('Expected certificate SHA-256 is required')
    paths=['app','gradle','gradlew','gradlew.bat','gradle.properties','build.gradle','settings.gradle',
        'tools','LICENSE','NOTICE.md','README.md','docs/release.md','docs/verification.md']
    source=run('git','archive','--format=tar',commit,'--',*paths,capture=True)
    sdk=Path(os.environ['ANDROID_HOME'])
    versions=sorted((p for p in (sdk/'build-tools').iterdir() if (p/'apksigner').exists()),key=lambda p:tuple(int(x) for x in re.findall(r'\d+',p.name)))
    build_tools=versions[-1]
    destination=args.output_dir.resolve();destination.mkdir(parents=True,exist_ok=True)
    # Extract only committed inputs. Ignored assets/secrets in the developer's tree
    # cannot enter this build. Stage artifacts privately until every check passes.
    with tempfile.TemporaryDirectory(prefix='.ew-staging-',dir=destination) as temporary:
        scratch=Path(temporary);work=scratch/'source';work.mkdir()
        output=scratch/'artifacts';output.mkdir(mode=0o700)
        if config:
            config=dict(config)
            # apksigner caches file readers and consumes consecutive lines when a
            # path is reused. Independent private copies also support PKCS12's
            # usual shared store/key password without exposing it in arguments.
            for field in ('store_password_file','key_password_file'):
                password=scratch/(field+'.txt')
                password.touch(mode=0o600)
                shutil.copyfile(config[field],password)
                config[field]=str(password)
        with tarfile.open(fileobj=io.BytesIO(source)) as archive:
            for member in archive.getmembers():
                path=Path(member.name)
                if path.is_absolute() or '..' in path.parts or not (member.isfile() or member.isdir()):
                    raise RuntimeError('Unsafe source archive entry')
                if any(part in {'build','.local','test-results','.git','audits'} for part in path.parts) or 'security-audit-' in member.name or member.name.endswith(('.jks','.keystore','.p12','.pfx')):
                    raise RuntimeError('Forbidden private/build material in source package')
            if hasattr(tarfile,'data_filter'):archive.extractall(work,filter='data')
            else:archive.extractall(work)  # All entries were validated above (Python 3.9).
        run('python3','tools/check_translations.py',directory=work)
        tasks=['assembleRelease','testDebugUnitTest','lintRelease']
        if args.bundle:tasks.append('bundleRelease')
        run('./gradlew','--no-daemon',*tasks,directory=work)
        metadata=json.loads((work/'app/build/outputs/apk/release/output-metadata.json').read_text())['elements'][0]
        version=metadata['versionName'];base='Eternal-Watch-'+version+'-'+commit[:12]
        suffix='' if config else '-UNSIGNED'
        final=destination/(base+suffix)
        if final.exists():raise RuntimeError('This artifact version already exists; existing artifacts are never overwritten')
        unsigned=work/'app/build/outputs/apk/release/app-release-unsigned.apk'
        badging=run(str(build_tools/'aapt2'),'dump','badging',str(unsigned),capture=True).decode()
        tree=run(str(build_tools/'aapt2'),'dump','xmltree',str(unsigned),'--file','AndroidManifest.xml',capture=True).decode()
        if 'application-debuggable' in badging or 'E: instrumentation' in tree:raise RuntimeError('Debug flags/instrumentation in release')
        with zipfile.ZipFile(unsigned) as archive:
            dex=b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
            for java in (work/'app/src/debug/java').rglob('*.java'):
                if ('/'+java.stem+';').encode() in dex or ('/'+java.stem+'$').encode() in dex:raise RuntimeError('Debug-only class in release APK')
        apk=output/(base+suffix+'.apk');certificate=None
        if config:
            signer=build_tools/'apksigner'
            run(str(signer),'sign','--ks',config['keystore'],'--ks-key-alias',config['alias'],
                '--ks-pass','file:'+config['store_password_file'],'--key-pass','file:'+config['key_password_file'],
                '--v4-signing-enabled','false','--out',str(apk),str(unsigned))
            verification=run(str(signer),'verify','--verbose','--print-certs',str(apk),capture=True).decode()
            match=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',verification)
            if not match or match.group(1).lower()!=config['certificate_sha256'].lower():raise RuntimeError('Release certificate mismatch')
            if 'CN=Android Debug' in verification:raise RuntimeError('Android debug key forbidden for release')
            certificate=match.group(1)
        else:shutil.copyfile(unsigned,apk)
        artifacts=[apk]
        if args.bundle:
            bundle=output/(base+suffix+'.aab');raw=work/'app/build/outputs/bundle/release/app-release.aab'
            if config:
                jdk=Path(os.environ['JAVA_HOME'])
                run(str(jdk/'bin/jarsigner'),'-keystore',config['keystore'],'-storepass:file',config['store_password_file'],
                    '-keypass:file',config['key_password_file'],'-signedjar',str(bundle),str(raw),config['alias'])
                verification=run(str(jdk/'bin/jarsigner'),'-J-Duser.language=en','-verify','-strict','-verbose',
                    '-keystore',config['keystore'],'-storepass:file',config['store_password_file'],str(bundle),config['alias'],capture=True).decode()
                if 'jar verified.' not in verification:raise RuntimeError('AAB is not fully verified')
                pem=run(str(jdk/'bin/keytool'),'-printcert','-jarfile',str(bundle),'-rfc',capture=True).decode()
                certificate_match=re.search(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----',pem,re.S)
                if not certificate_match or hashlib.sha256(base64.b64decode(certificate_match.group(1))).hexdigest()!=certificate:
                    raise RuntimeError('AAB signer differs from expected APK certificate')
            else:shutil.copyfile(raw,bundle)
            artifacts.append(bundle)
        source_path=output/(base+'-source.tar.gz')
        with source_path.open('wb') as target:
            with gzip.GzipFile(fileobj=target,mode='wb',mtime=0,filename='') as compressed:compressed.write(source)
        artifacts.append(source_path)
        if run('git','rev-parse','HEAD',capture=True).decode().strip()!=commit or run('git','status','--porcelain','--untracked-files=normal',capture=True).strip():
            raise RuntimeError('Source changed during build; no artifacts released')
        manifest={'version':version,'version_code':metadata['versionCode'],'source_commit':commit,'isolated_build':True,
            'signed':config is not None,'certificate_sha256':certificate,'artifacts':{p.name:sha256(p) for p in artifacts}}
        (output/(base+suffix+'-manifest.json')).write_text(json.dumps(manifest,indent=2)+'\n')
        # Same-filesystem directory rename exposes the complete verified set at once.
        os.rename(output,final)
        print('Local release artifacts prepared; signed='+str(config is not None)+'. Nothing uploaded.')
        print(final)

if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.SubprocessError,KeyError,OSError) as error:
        raise SystemExit('Release preparation failed: '+str(error))
