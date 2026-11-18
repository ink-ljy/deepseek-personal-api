"""Reproducible local BGE export from pinned official safetensors; no remote Python code."""
from pathlib import Path
import hashlib,json,os,zipfile,sys
sys.path.insert(0,str(Path(__file__).resolve().parent/'local-model/export-deps'))
os.environ.setdefault('HTTPS_PROXY','http://127.0.0.1:7897')
import requests
ROOT=Path(__file__).resolve().parent
REV='7999e1d3359715c523056ef9478215996d62a620'
DEST=ROOT/'local-model';DEST.mkdir(exist_ok=True)
MODEL=DEST/'source';MODEL.mkdir(exist_ok=True)
def fetch(url,path,sha=None):
    if path.exists() and (sha is None or hashlib.sha256(path.read_bytes()).hexdigest()==sha):return
    tmp=path.with_suffix(path.suffix+'.part')
    with requests.get(url,timeout=(20,120),stream=True)as r:
        r.raise_for_status()
        with tmp.open('wb')as f:
            for chunk in r.iter_content(1024*1024):f.write(chunk)
    if sha and hashlib.sha256(tmp.read_bytes()).hexdigest()!=sha:raise RuntimeError('Download hash mismatch: '+path.name)
    tmp.replace(path);print('downloaded',path.name,path.stat().st_size,flush=True)
meta=requests.get(f'https://huggingface.co/api/models/BAAI/bge-small-zh-v1.5/revision/{REV}?blobs=true',timeout=30).json()
for name in ['config.json','tokenizer_config.json','special_tokens_map.json','vocab.txt','tokenizer.json','model.safetensors','README.md']:
    info=next(x for x in meta['siblings']if x['rfilename']==name);sha=info.get('lfs',{}).get('sha256')
    fetch(f'https://huggingface.co/BAAI/bge-small-zh-v1.5/resolve/{REV}/{name}',MODEL/name,sha)
version='1.24.3'
url=f'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/{version}/onnxruntime-android-{version}.aar'
aar=DEST/'onnxruntime-android.aar';fetch(url,aar)
with zipfile.ZipFile(aar)as z:
    for name in ['classes.jar','jni/arm64-v8a/libonnxruntime.so','jni/arm64-v8a/libonnxruntime4j_jni.so','jni/x86_64/libonnxruntime.so','jni/x86_64/libonnxruntime4j_jni.so']:
        path=DEST/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(name))
import torch,transformers,onnxruntime as ort,numpy as np
from transformers import AutoModel,AutoTokenizer
from onnxruntime.quantization import quantize_dynamic,QuantType
torch.set_num_threads(4)
tokenizer=AutoTokenizer.from_pretrained(MODEL,local_files_only=True)
model=AutoModel.from_pretrained(MODEL,local_files_only=True,attn_implementation='eager').eval()
class Encoder(torch.nn.Module):
    def __init__(self):super().__init__();self.model=model
    def forward(self,input_ids,attention_mask,token_type_ids):
        x=self.model(input_ids=input_ids,attention_mask=attention_mask,token_type_ids=token_type_ids).last_hidden_state[:,0]
        return torch.nn.functional.normalize(x,p=2,dim=1)
encoder=Encoder().eval()
fp=DEST/'bge-small-zh-fp32.onnx';quant=DEST/'bge-small-zh-int8.onnx'
inputs=tokenizer('本机向量记忆验证',return_tensors='pt')
if not fp.exists():
    torch.onnx.export(encoder,tuple(inputs[k]for k in ['input_ids','attention_mask','token_type_ids']),str(fp),input_names=['input_ids','attention_mask','token_type_ids'],output_names=['embedding'],dynamic_axes={k:{0:'batch',1:'sequence'}for k in ['input_ids','attention_mask','token_type_ids']}|{'embedding':{0:'batch'}},opset_version=17,dynamo=False)
if not quant.exists():quantize_dynamic(str(fp),str(quant),weight_type=QuantType.QInt8,op_types_to_quantize=['MatMul','Gather'],per_channel=True)
session=ort.InferenceSession(str(quant),providers=['CPUExecutionProvider'])
texts=['我更喜欢用中文回答，简短一点。','为这个句子生成表示以用于检索相关文章：请记住我的语言和回复风格偏好。','火箭发动机推进剂混合比与燃烧稳定性。']
encoded=tokenizer(texts,padding=True,truncation=True,max_length=512,return_tensors='np')
vec=session.run(None,dict(encoded))[0]
with torch.no_grad():ref=encoder(**{k:torch.tensor(v)for k,v in encoded.items()}).numpy()
similarity=(vec*ref).sum(axis=1)/(np.linalg.norm(vec,axis=1)*np.linalg.norm(ref,axis=1))
assert min(similarity)>.97,similarity
assert np.dot(vec[0],vec[1])>np.dot(vec[0],vec[2]),'Semantic sanity failed'
report={'model':'BAAI/bge-small-zh-v1.5','revision':REV,'license':'MIT','parameters':sum(p.numel()for p in model.parameters()),'dimensions':int(vec.shape[1]),'max_tokens':512,'quantization':'dynamic INT8 MatMul and Gather','onnxruntime_android':version,'quantized_bytes':quant.stat().st_size,'sha256':hashlib.sha256(quant.read_bytes()).hexdigest(),'vocab_sha256':hashlib.sha256((MODEL/'vocab.txt').read_bytes()).hexdigest(),'fp32_int8_cosine':similarity.tolist(),'semantic_related':float(np.dot(vec[0],vec[1])),'semantic_unrelated':float(np.dot(vec[0],vec[2])),'environment':{'torch':torch.__version__,'transformers':transformers.__version__,'onnxruntime':ort.__version__}}
report['per_channel']=True
report['runtime_aar_sha256']=hashlib.sha256(aar.read_bytes()).hexdigest()
(DEST/'model-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
cases=['你好，世界！记住我喜欢简短中文回答。','ABC abc API DeepSeek café iPhone 14.5 🚀','我更喜欢用中文回答，简短一点。','为这个句子生成表示以用于检索相关文章：请记住我的语言和回复风格偏好。','火箭发动机推进剂混合比与燃烧稳定性。']
fixtures=[]
for text in cases:
    sample=tokenizer(text,return_tensors='np');v=session.run(None,dict(sample))[0][0]
    fixtures.append({'text':text,'tokens':tokenizer.encode(text,add_special_tokens=False),'vector':v.tolist()})
(DEST/'tokenizer-fixtures.json').write_text(json.dumps(fixtures,ensure_ascii=False),encoding='utf-8')
if not (DEST/'THIRD-PARTY-NOTICES.txt').exists():
    import importlib.metadata as metadata
    package=metadata.distribution('onnxruntime')
    license_path=next(package.locate_file(f)for f in package.files if str(f).endswith('/LICENSE'))
    flag=requests.get('https://raw.githubusercontent.com/FlagOpen/FlagEmbedding/master/LICENSE',timeout=30);flag.raise_for_status()
    (DEST/'THIRD-PARTY-NOTICES.txt').write_text(f'BAAI/bge-small-zh-v1.5 (MIT), revision {REV}\nhttps://huggingface.co/BAAI/bge-small-zh-v1.5\nConverted and INT8 quantized locally from official safetensors.\n\n'+flag.text+'\n\nONNX Runtime Android '+version+' (MIT)\nhttps://github.com/microsoft/onnxruntime\n\n'+license_path.read_text(encoding='utf-8'),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
