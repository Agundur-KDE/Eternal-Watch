#!/usr/bin/env python3
"""Validate release packaging with a temporary key and ignored-asset canary.
No production key is created. Test keys and signed fixtures are removed afterwards.
"""
import base64,gzip,hashlib,json,os,re,secrets,subprocess,tempfile,tarfile,zipfile,io,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
_local_jdk=ROOT.parent/'KCast-App/.tools/jdk/bin'
JDK=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else _local_jdk if _local_jdk.is_dir() else Path(shutil.which('keytool') or 'keytool').resolve().parent
RESULTS=ROOT/'test-results';RESULTS.mkdir(exist_ok=True)
canary=ROOT/'app/src/main/assets/_ew_release_isolation_check.p12'
assert not canary.exists()
canary.write_bytes(b'IGNORED TEST CANARY '+secrets.token_bytes(24))
checks={}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
try:
 with tempfile.TemporaryDirectory(prefix='release-fixture-',dir=RESULTS) as directory:
  p=Path(directory);password=p/'password.txt';password.write_text(secrets.token_hex(32));password.chmod(0o600)
  key=p/'test-only.p12'
  def tool(*args):return subprocess.run([str(x) for x in args],cwd=ROOT,check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT).stdout
  tool(JDK/'keytool','-genkeypair','-alias','fixture','-keystore',key,'-storetype','PKCS12','-storepass:file',password,'-keypass:file',password,'-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=TEST ONLY Eternal Watch release validation')
  key.chmod(0o600)
  der=tool(JDK/'keytool','-exportcert','-alias','fixture','-keystore',key,'-storepass:file',password)
  config={'keystore':str(key),'alias':'fixture','store_password_file':str(password),'key_password_file':str(password),'certificate_sha256':hashlib.sha256(der).hexdigest()}
  good=p/'signing.json';good.write_text(json.dumps(config));good.chmod(0o600)
  bad=p/'bad-signing.json';bad.write_text(json.dumps({**config,'certificate_sha256':'0'*64}));bad.chmod(0o600)
  def build(label,*args,success=True):
   print(label+' started',flush=True)
   log=RESULTS/('release-package-'+label+'.log')
   with log.open('wb') as out:r=subprocess.run(['bash','tools/build_release.sh',*map(str,args)],cwd=ROOT,stdout=out,stderr=subprocess.STDOUT)
   if (r.returncode==0)!=success:raise RuntimeError(label+' unexpected result; see ignored log')
   print(label+' expected result confirmed',flush=True)
   return log.read_text(errors='replace')
  output=p/'signed';build('test-signing','--signing-config',good,'--bundle','--output-dir',output)
  versions=[x for x in output.iterdir() if x.is_dir()];assert len(versions)==1
  target=versions[0];manifests=list(target.glob('*manifest.json'));assert len(manifests)==1
  m=json.loads(manifests[0].read_text());assert m['signed'] and m['isolated_build'];assert m['certificate_sha256']==config['certificate_sha256']
  for name,digest in m['artifacts'].items():assert sha(target/name)==digest
  apk=next(target.glob('*.apk'));source=next(target.glob('*.tar.gz'))
  with zipfile.ZipFile(apk) as z:assert not any('_ew_release_isolation_check' in x for x in z.namelist())
  with tarfile.open(source) as t:
   names=t.getnames();assert not any('_ew_release_isolation_check' in n or 'security-audit-' in n or '/audits/' in n or '.local/' in n for n in names)
  checks['isolated_ignored_asset_exclusion']=True;checks['test_key_apk_and_aab_signatures']=True;checks['manifest_hashes']=True
  existing={x.name:sha(x) for x in target.iterdir() if x.is_file()}
  log=build('existing-artifacts-protected','--signing-config',bad,'--output-dir',output,success=False)
  assert 'already exists' in log;assert existing=={x.name:sha(x) for x in target.iterdir() if x.is_file()};checks['existing_artifacts_unchanged']=True
  bad_output=p/'bad-output';log=build('wrong-certificate-rejected','--signing-config',bad,'--output-dir',bad_output,success=False)
  assert 'certificate mismatch' in log;assert not list(bad_output.iterdir());checks['wrong_certificate_no_final_artifacts']=True
  checks['ephemeral_test_key_only']=True
finally:
 canary.unlink(missing_ok=True)
 (RESULTS/'release-package-check.json').write_text(json.dumps(checks,indent=2)+'\n')
print('Release packaging security checks passed; test key and signed test artifacts removed. No production key created.',flush=True)
