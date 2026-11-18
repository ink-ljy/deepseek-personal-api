import ai.onnxruntime.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Bundled 0.024B Chinese BGE encoder, INT8 CPU inference; no network or API credentials. */
public final class ApiLocalEmbedding {
    static final String ID="local:bge-small-zh-v1.5:7999e1d:int8-v1";
    static OrtEnvironment environment;static OrtSession session;static Map<String,Integer> vocab;
    static synchronized void init()throws Exception{
        if(session!=null)return;
        JSONObject manifest=new JSONObject(readText("personal-memory/model-manifest.json"));String sha=manifest.getString("sha256");File model=new File(ApiSupport.app.getFilesDir(),"memory-model-"+sha+".onnx");
        if(!model.exists()||model.length()!=manifest.getLong("quantized_bytes")){
            AtomicFile output=new AtomicFile(model);FileOutputStream out=output.startWrite();MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=ApiSupport.app.getAssets().open("personal-memory/bge-small-zh-int8.onnx")){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){digest.update(b,0,n);out.write(b,0,n);}if(!hex(digest.digest()).equals(sha))throw new IOException("本机记忆模型校验失败，请重新安装完整 APK。");output.finishWrite(out);}catch(Exception e){output.failWrite(out);throw e;}
        }
        Map<String,Integer> words=new HashMap<>();try(BufferedReader reader=new BufferedReader(new InputStreamReader(ApiSupport.app.getAssets().open("personal-memory/vocab.txt"),StandardCharsets.UTF_8))){String line;int id=0;while((line=reader.readLine())!=null)words.put(line,id++);}
        OrtEnvironment env=OrtEnvironment.getEnvironment();env.setTelemetry(false);
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);session=env.createSession(model.getAbsolutePath(),options);}
        vocab=words;environment=env;
    }
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    static String readText(String name)throws Exception{try(InputStream in=ApiSupport.app.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    static boolean cjk(int cp){return(cp>=0x4e00&&cp<=0x9fff)||(cp>=0x3400&&cp<=0x4dbf)||(cp>=0x20000&&cp<=0x2fa1f)||(cp>=0xf900&&cp<=0xfaff);}
    static boolean punctuation(int cp){int t=Character.getType(cp);return(cp>=33&&cp<=47)||(cp>=58&&cp<=64)||(cp>=91&&cp<=96)||(cp>=123&&cp<=126)||t==Character.CONNECTOR_PUNCTUATION||t==Character.DASH_PUNCTUATION||t==Character.START_PUNCTUATION||t==Character.END_PUNCTUATION||t==Character.OTHER_PUNCTUATION||t==Character.INITIAL_QUOTE_PUNCTUATION||t==Character.FINAL_QUOTE_PUNCTUATION;}
    static List<Integer> tokens(String text)throws Exception{
        if(vocab==null)init();List<String> basic=new ArrayList<>();StringBuilder word=new StringBuilder();
        // Pinned model tokenizer: do_lower_case=false, strip_accents=null (therefore preserve accents).
        String normalized=text;
        for(int i=0;i<normalized.length();){int cp=normalized.codePointAt(i);i+=Character.charCount(cp);int type=Character.getType(cp);
            if(cp==0||cp==0xfffd||(Character.isISOControl(cp)&&cp!='\t'&&cp!='\n'&&cp!='\r')||type==Character.FORMAT)continue;
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)||cjk(cp)||punctuation(cp)){if(word.length()>0){basic.add(word.toString());word.setLength(0);}if(cjk(cp)||punctuation(cp))basic.add(new String(Character.toChars(cp)));}else word.appendCodePoint(cp);
        }if(word.length()>0)basic.add(word.toString());List<Integer> ids=new ArrayList<>();int unknown=vocab.get("[UNK]");
        for(String token:basic){if(token.codePointCount(0,token.length())>100){ids.add(unknown);continue;}int start=0;List<Integer> parts=new ArrayList<>();boolean bad=false;
            while(start<token.length()){int end=token.length();Integer value=null;while(start<end){String sub=(start==0?"":"##")+token.substring(start,end);value=vocab.get(sub);if(value!=null)break;end=token.offsetByCodePoints(end,-1);}if(value==null){bad=true;break;}parts.add(value);start=end;}if(bad)ids.add(unknown);else ids.addAll(parts);
        }return ids;
    }
    static synchronized JSONArray embed(String text,boolean query)throws Exception{
        try{init();List<Integer> ids=tokens((query?"为这个句子生成表示以用于检索相关文章：":"")+text);double[] sum=new double[512];int windows=0;
            // Encode all document windows, rather than silently dropping text after 512 tokens.
            for(int start=0;start<Math.max(1,ids.size());start+=448){int count=Math.min(510,ids.size()-start);long[][] input=new long[1][count+2],mask=new long[1][count+2],types=new long[1][count+2];input[0][0]=vocab.get("[CLS]");for(int i=0;i<count;i++)input[0][i+1]=ids.get(start+i);input[0][count+1]=vocab.get("[SEP]");Arrays.fill(mask[0],1);
                try(OnnxTensor a=OnnxTensor.createTensor(environment,input);OnnxTensor b=OnnxTensor.createTensor(environment,mask);OnnxTensor c=OnnxTensor.createTensor(environment,types)){
                    Map<String,OnnxTensor> inputs=new HashMap<>();inputs.put("input_ids",a);inputs.put("attention_mask",b);inputs.put("token_type_ids",c);
                    try(OrtSession.Result output=session.run(inputs)){float[][] values=(float[][])output.get(0).getValue();if(values[0].length!=sum.length)throw new IOException("本机记忆模型维度不匹配。");for(int i=0;i<sum.length;i++)sum[i]+=values[0][i];windows++;}
                }if(start+count>=ids.size())break;
            }double norm=0;for(double x:sum)norm+=x*x;norm=Math.sqrt(norm);if(norm==0||windows==0)throw new IOException("本机模型未生成有效向量。");JSONArray result=new JSONArray();for(double x:sum)result.put(x/norm);return result;
        }catch(LinkageError e){throw new IOException("本机记忆模型运行库无法加载，请使用完整的 arm64 安装包。");}
    }
}
