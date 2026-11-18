from pathlib import Path
import hashlib, json, os, secrets, shutil, subprocess, zipfile

ROOT=Path(__file__).resolve().parent
DECODED=ROOT/'original-decoded'
SDK=Path(os.environ.get('ANDROID_SDK_ROOT',r'C:\Android\sdk'))
ORIGINAL=Path(os.environ.get('DEEPSEEK_ORIGINAL_APK',str(Path.home()/'Downloads/deepseek.apk')))
BT=SDK/'build-tools/36.0.0'
ANDROID=SDK/'platforms/android-36/android.jar'

def run(args):
    result=subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    output=result.stdout.decode('utf-8',errors='replace')
    if result.returncode:
        print(output)
        raise RuntimeError('Command failed: '+Path(str(args[0])).name)
    print(output[-2500:] if len(output)>2500 else output)

def patch(relative, transform):
    target=DECODED/relative
    baseline=ROOT/'patch-baseline'/relative
    baseline.parent.mkdir(parents=True,exist_ok=True)
    if not baseline.exists():shutil.copy2(target,baseline)
    source=baseline.read_text(encoding='utf-8')
    changed=transform(source)
    assert changed!=source,relative
    if target.read_text(encoding='utf-8')!=changed:target.write_text(changed,encoding='utf-8')

def add_after(source, needle, addition):
    assert source.count(needle)==1,(needle,source.count(needle))
    return source.replace(needle,needle+'\n'+addition,1)

def method_entry(source, signature, addition):
    begin=source.index(signature);end=source.index('\n',source.index('.locals ',begin))
    return source[:end]+ '\n\n'+addition+source[end:]

patch('smali/com/deepseek/chat/MainActivity.smali',lambda s:add_after(s,
    'invoke-super {p0, p1}, Lsu4;->onCreate(Landroid/os/Bundle;)V',
    '    invoke-static {p0}, LApiSupport;->init(Landroid/content/Context;)V'))

def settings(source):
    start=source.index('.method public static final l(')
    needle='invoke-static/range {v16 .. v29}, Lpy9;->b(Lem7;Lvy4;ZZJLlz4;Llz4;Llz4;FLlz4;Lh25;II)V'
    first=source.index(needle,start);second=source.index(needle,first+len(needle));end=second+len(needle)
    source=source[:end]+'\n\n    invoke-static {v8}, LApiUi;->row(Ljava/lang/Object;)V'+source[end:]
    about=source.index('sget-object v26, Lnl9;->I:Lf43;',start)
    end=source.index(needle,about)+len(needle)
    return source[:end]+'\n\n    invoke-static {v8}, LApiUi;->aboutRow(Ljava/lang/Object;)V'+source[end:]
patch('smali/sz6.smali',settings)
patch('smali_classes3/x09.smali',lambda s:method_entry(s,'.method public final b(Lw89;)Lwa9;', '''    invoke-static/range {p1 .. p1}, LApiBridge;->intercept(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :personal_api_original_request
    check-cast v0, Lwa9;
    return-object v0
    :personal_api_original_request'''))
def capture_context(source):
    begin=source.index('.method public constructor <init>(Ljd3;Lat;')
    end=source.index('.end method',begin)
    needle='invoke-direct {p0}, Ljava/lang/Object;-><init>()V'
    assert needle in source[begin:end]
    part=source[begin:end].replace(needle,needle+'\n    invoke-static/range {p2 .. p2}, LApiBridge;->cacheSession(Ljava/lang/Object;)V',1)
    return source[:begin]+part+source[end:]
patch('smali/xv1.smali',capture_context)
patch('smali/wf2.smali',lambda s:method_entry(s,'.method public final j(Loc3;)Ljava/io/Serializable;', '''    invoke-static {}, LApiBridge;->pow()Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :personal_api_original_pow
    check-cast v0, Ljava/io/Serializable;
    return-object v0
    :personal_api_original_pow'''))
patch('smali/ny1.smali',lambda s:method_entry(s,'.method public final a(Lui4;ZLjava/lang/String;Lxy4;Lnc3;)Ljava/lang/Object;', '''    move-object/from16 v0, p1
    move-object/from16 v1, p4
    move-object/from16 v2, p5
    invoke-static {v0, v1, v2}, LApiFiles;->upload(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :personal_api_original_upload
    return-object v0
    :personal_api_original_upload'''))
patch('smali/edb.smali',lambda s:method_entry(s,'.method public final a(Lqcb;Lncb;)Lddb;', '''    invoke-static/range {p1 .. p1}, LApiSpeech;->request(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lqcb;
    move-object/from16 p1, v0'''))
patch('smali/it1.smali',lambda s:method_entry(s,'.method public final j(Ljbb;Ljava/lang/String;Lul4;JLnc3;)Ljava/lang/Object;', '''    invoke-static {p1}, LApiSpeech;->flow(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    if-eqz v0, :personal_api_original_tts
    return-object v0
    :personal_api_original_tts'''))
patch('smali/ddb.smali',lambda s:method_entry(s,'.method public final c(Ljava/lang/String;)V', '''    invoke-static {p0}, LApiSpeech;->stop(Ljava/lang/Object;)V'''))

def personal_footer(source):
    # Remove the original app's filing numbers, retaining its AI-generated-content notice.
    lines=source.splitlines(keepends=True)
    matches=[i for i,line in enumerate(lines) if 'const-string v9,' in line and 'Beijing-DeepseekChat-202404280016' in line]
    assert len(matches)==1
    lines[matches[0]]='    const-string v9, ""\n'
    source=''.join(lines)
    start=source.index('.method public static final x(Lyob;Lem7;Lh25;I)V')
    marker='invoke-static {v3, v1}, Li77;->C(ILh25;)Ljava/lang/String;'
    pos=source.index(marker,start)
    pos=source.index('move-result-object v3',pos)+len('move-result-object v3')
    return source[:pos]+'\n\n    invoke-static {v3}, LApiUi;->footer(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v3'+source[pos:]
patch('smali_classes3/u48.smali',personal_footer)

classes=ROOT/'native-classes';classes.mkdir(exist_ok=True)
run(['javac','-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',ANDROID,'-d',classes,*sorted((ROOT/'native-src').glob('*.java'))])
dex=ROOT/'native-dex';dex.mkdir(exist_ok=True)
run([BT/'d8.bat','--release','--min-api','24','--lib',ANDROID,'--output',dex,*sorted(classes.glob('*.class'))])
compiled=ROOT/'smali-dex';compiled.mkdir(exist_ok=True)
for folder,name in [('smali','classes.dex'),('smali_classes3','classes3.dex')]:
    target=compiled/name
    cache=DECODED/'build/apk'/name
    newest=max(p.stat().st_mtime for p in (DECODED/folder).rglob('*.smali'))
    if cache.exists() and cache.stat().st_mtime>=newest and (not target.exists() or cache.stat().st_mtime>target.stat().st_mtime):shutil.copy2(cache,target)
    if not target.exists() or target.stat().st_mtime<newest:
        bytecode=ROOT/'bytecode-build';bytecode.mkdir(exist_ok=True)
        for source_folder in ['smali','smali_classes3']:
            linked=bytecode/source_folder
            if not linked.exists():shutil.copytree(DECODED/source_folder,linked,copy_function=os.link)
        shutil.copy2(DECODED/'apktool.yml',bytecode/'apktool.yml')
        with zipfile.ZipFile(ORIGINAL)as original:
            for raw_name in ['AndroidManifest.xml','resources.arsc']:(bytecode/raw_name).write_bytes(original.read(raw_name))
        run(['java','-jar',ROOT/'apktool.jar','b','--no-apk','-a',BT/'aapt2.exe',bytecode])
        shutil.copy2(bytecode/'build/apk'/name,target)
combined=ROOT/'native-combined-unsigned.apk'
with zipfile.ZipFile(ORIGINAL) as source,zipfile.ZipFile(combined,'w',compression=zipfile.ZIP_DEFLATED) as target:
    for item in source.infolist():
        if item.filename.startswith('META-INF/') and (item.filename.endswith(('.RSA','.SF','.MF','.DSA','.EC'))):continue
        replacement=compiled/item.filename
        target.writestr(item,replacement.read_bytes() if item.filename in ['classes.dex','classes3.dex'] else source.read(item.filename))
    target.write(dex/'classes.dex','classes4.dex')
aligned=ROOT/'native-aligned-unsigned.apk'
run([BT/'zipalign.exe','-P','16','-f','4',combined,aligned])
signing=ROOT/'.signing';signing.mkdir(exist_ok=True)
password=signing/'password.txt';keystore=signing/'personal.p12'
if not password.exists():password.write_text(secrets.token_urlsafe(32),encoding='ascii')
if not keystore.exists():run(['keytool','-genkeypair','-keystore',keystore,'-storetype','PKCS12','-storepass:file',password,'-alias','personal-api','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Personal API Edition,O=Local Personal Build,C=CN'])
output=ROOT/'DeepSeek-2.8.0-PersonalAPI.apk'
run([BT/'apksigner.bat','sign','--ks',keystore,'--ks-key-alias','personal-api','--ks-pass','file:'+str(password),'--out',output,aligned])
run([BT/'apksigner.bat','verify','--verbose',output])
run([BT/'zipalign.exe','-c','-P','16','4',output])
evidence={'input':ORIGINAL.name,'input_sha256':hashlib.sha256(ORIGINAL.read_bytes()).hexdigest(),'output':output.name,'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'patches':['MainActivity initialization','original settings and about rows','original Compose header and settings controls','OkHttp request adapter','native file adapter with progress','native branch context capture','API mode skips chat-service-specific PoW','public TTS request normalization','public TTS native event Flow','native playback stop cancels public TTS'],'key_embedded':False}
(ROOT/'build-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print('BUILT',output,output.stat().st_size)
