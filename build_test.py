from pathlib import Path
import subprocess,zipfile
ROOT=Path(__file__).resolve().parent
BT=Path(r'C:\Android\sdk\build-tools\36.0.0')
ANDROID=Path(r'C:\Android\sdk\platforms\android-36\android.jar')
def run(args):
 r=subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 print(r.stdout.decode('utf-8',errors='replace')[-2000:]);r.check_returncode()
classes=ROOT/'test-classes';classes.mkdir(exist_ok=True)
dex=ROOT/'test-dex';dex.mkdir(exist_ok=True)
run(['javac','-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',ANDROID,'-d',classes,*sorted((ROOT/'test-src').glob('*.java'))])
run([BT/'d8.bat','--release','--min-api','24','--lib',ANDROID,'--output',dex,*[p for p in classes.rglob('*.class') if p.parent!=classes]])
apk=ROOT/'test-unsigned.apk'
run([BT/'aapt2.exe','link','-I',ANDROID,'--manifest',ROOT/'test-src/AndroidManifest.xml','-o',apk])
with zipfile.ZipFile(apk,'a',compression=zipfile.ZIP_DEFLATED)as z:z.write(dex/'classes.dex','classes.dex')
aligned=ROOT/'test-aligned.apk';run([BT/'zipalign.exe','-f','4',apk,aligned])
run([BT/'apksigner.bat','sign','--ks',ROOT/'.signing/personal.p12','--ks-key-alias','personal-api','--ks-pass','file:'+str(ROOT/'.signing/password.txt'),'--out',ROOT/'native-api-test.apk',aligned])
