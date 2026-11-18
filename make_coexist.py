from pathlib import Path
import hashlib,json,struct,subprocess,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parent
BT=Path(r'C:\Android\sdk\build-tools\36.0.0')
OLD='com.deepseek.chat';NEW='com.deepseek.chat.personalapi'
ANDROID='{http://schemas.android.com/apk/res/android}'

def length8(data,pos):
 first=data[pos];pos+=1
 if first&128:return ((first&127)<<8)|data[pos],pos+1
 return first,pos
def length16(data,pos):
 first=struct.unpack_from('<H',data,pos)[0];pos+=2
 if first&0x8000:return ((first&0x7fff)<<16)|struct.unpack_from('<H',data,pos)[0],pos+2
 return first,pos
def len8(n):return bytes([n])if n<128 else bytes([0x80|(n>>8),n&255])
def len16(n):return struct.pack('<H',n)if n<0x8000 else struct.pack('<HH',0x8000|(n>>16),n&0xffff)

def rewrite_manifest(data,mapping):
 chunks=[];strings=None;label_index=None;pos=8
 while pos<len(data):
  typ,header_size,size=struct.unpack_from('<HHI',data,pos);chunk=bytearray(data[pos:pos+size])
  if typ==1:
   count,style_count,flags,start,style_start=struct.unpack_from('<IIIII',chunk,8)
   offsets=struct.unpack_from('<'+'I'*count,chunk,header_size)
   old_strings=[]
   for off in offsets:
    p=start+off
    if flags&0x100:
     _,p=length8(chunk,p);n,p=length8(chunk,p);old_strings.append(bytes(chunk[p:p+n]).decode('utf-8'))
    else:n,p=length16(chunk,p);old_strings.append(bytes(chunk[p:p+n*2]).decode('utf-16le'))
   strings=[mapping.get(s,s)for s in old_strings];label_index=len(strings);strings.append('DeepSeek API')
   raw=bytearray();new_offsets=[]
   for s in strings:
    new_offsets.append(len(raw));units=len(s.encode('utf-16le'))//2
    if flags&0x100:
     encoded=s.encode('utf-8');raw.extend(len8(units)+len8(len(encoded))+encoded+b'\0')
    else:raw.extend(len16(units)+s.encode('utf-16le')+b'\0\0')
   while len(raw)%4:raw.append(0)
   styles_offsets=chunk[header_size+count*4:header_size+(count+style_count)*4]
   styles=chunk[style_start:]if style_start else b''
   strings_start=header_size+(len(strings)+style_count)*4
   styles_start=strings_start+len(raw)if styles else 0
   rebuilt=bytearray(chunk[:header_size]);struct.pack_into('<IIIII',rebuilt,8,len(strings),style_count,flags&~1,strings_start,styles_start)
   rebuilt.extend(struct.pack('<'+'I'*len(new_offsets),*new_offsets));rebuilt.extend(styles_offsets);rebuilt.extend(raw);rebuilt.extend(styles)
   struct.pack_into('<I',rebuilt,4,len(rebuilt));chunk=rebuilt
  elif typ==0x0102 and strings is not None:
   element=strings[struct.unpack_from('<I',chunk,20)[0]]
   attr_start,attr_size,attr_count=struct.unpack_from('<HHH',chunk,24);base=16+attr_start
   attrs={}
   for i in range(attr_count):
    p=base+i*attr_size;_,name,raw=struct.unpack_from('<III',chunk,p)
    attrs[strings[name]]=(p,raw,chunk[p+15],struct.unpack_from('<I',chunk,p+16)[0])
   is_main=element=='application' or(element=='activity'and'name'in attrs and attrs['name'][2]==3 and strings[attrs['name'][3]]=='com.deepseek.chat.MainActivity')
   if is_main and'label'in attrs:
    p=attrs['label'][0];struct.pack_into('<I',chunk,p+8,label_index);chunk[p+15]=3;struct.pack_into('<I',chunk,p+16,label_index)
  chunks.append(bytes(chunk));pos+=size
 body=b''.join(chunks);return data[:4]+struct.pack('<I',len(body)+8)+body

tree=ET.parse(ROOT/'original-decoded/AndroidManifest.xml')
mapping={OLD:NEW}
for node in tree.iter():
 for key,value in node.attrib.items():
  if key in [ANDROID+'authorities',ANDROID+'permission',ANDROID+'readPermission',ANDROID+'writePermission']or(node.tag in ['permission','uses-permission']and key==ANDROID+'name'):
   if value.startswith(OLD+'.'):mapping[value]=NEW+value[len(OLD):]
input_apk=ROOT/'DeepSeek-2.8.0-PersonalAPI.apk';unsigned=ROOT/'coexist-unsigned.apk'
with zipfile.ZipFile(input_apk)as source,zipfile.ZipFile(unsigned,'w',compression=zipfile.ZIP_DEFLATED)as target:
 for item in source.infolist():
  if item.filename.startswith('META-INF/')and item.filename.endswith(('.RSA','.SF','.MF','.DSA','.EC')):continue
  value=source.read(item.filename)
  if item.filename=='AndroidManifest.xml':value=rewrite_manifest(value,mapping)
  target.writestr(item,value)
def run(args):
 r=subprocess.run([str(x)for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT);print(r.stdout.decode('utf-8',errors='replace')[-1600:]);r.check_returncode()
aligned=ROOT/'coexist-aligned.apk';output=ROOT/'DeepSeek-2.8.0-PersonalAPI-Coexist.apk'
run([BT/'zipalign.exe','-P','16','-f','4',unsigned,aligned])
run([BT/'apksigner.bat','sign','--ks',ROOT/'.signing/personal.p12','--ks-key-alias','personal-api','--ks-pass','file:'+str(ROOT/'.signing/password.txt'),'--out',output,aligned])
run([BT/'apksigner.bat','verify','--verbose',output]);run([BT/'aapt2.exe','dump','badging',output])
print('COEXIST',output,hashlib.sha256(output.read_bytes()).hexdigest())
