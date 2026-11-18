package dev.local.deepseekapitest;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real local ONNX inference and tokenizer parity; no API or synthetic embedding transport. */
public final class ApiLocalTest {
    static void run(ApiSelfTest t)throws Exception{
        JSONArray fixtures=new JSONArray(t.read(new FileInputStream("/data/local/tmp/local-tokenizer-fixtures.json")));long began=System.currentTimeMillis();
        List<JSONArray> vectors=new ArrayList<>();for(int i=0;i<fixtures.length();i++){JSONObject f=fixtures.getJSONObject(i);List<?> tokens=(List<?>)t.call(t.c("ApiLocalEmbedding"),"tokens",f.getString("text"));JSONArray expected=f.getJSONArray("tokens");boolean same=tokens.size()==expected.length();for(int n=0;same&&n<tokens.size();n++)same=((Number)tokens.get(n)).intValue()==expected.getInt(n);t.check(same,"Android WordPiece matches pinned reference case "+i);
            JSONArray vector=(JSONArray)t.call(t.c("ApiLocalEmbedding"),"embed",f.getString("text"),false);vectors.add(vector);double cos=((Number)t.call(t.c("ApiMemory"),"cosine",vector,f.getJSONArray("vector"))).doubleValue();t.check(vector.length()==512&&cos>.995,"Android INT8 encoder agrees with desktop ONNX reference case "+i);
        }
        double related=((Number)t.call(t.c("ApiMemory"),"cosine",vectors.get(2),vectors.get(3))).doubleValue(),unrelated=((Number)t.call(t.c("ApiMemory"),"cosine",vectors.get(2),vectors.get(4))).doubleValue();t.check(related>unrelated,"real Chinese semantic retrieval ranks preference memory above rocket engineering");
        ApiFeaturesTest.pref(t,"embedding_remote",false);ApiFeaturesTest.pref(t,"memory_enabled",true);t.call(t.c("ApiMemory"),"clear");String id=(String)t.call(t.c("ApiMemory"),"put",null,"我喜欢中文回答，语气简短，称呼我为小李。","source-local");ApiFeaturesTest.idle(t);String found=(String)t.call(t.c("ApiMemory"),"retrieve","我的语言和称呼偏好是什么？","new-local");t.check(found.contains("小李"),"default offline memory creates and retrieves real vectors without any endpoint key");t.call(t.c("ApiMemory"),"delete",id);t.check(((String)t.call(t.c("ApiMemory"),"retrieve","我的称呼是什么？","new-local")).isEmpty(),"deleting offline memory removes it from subsequent retrieval");
        android.os.Bundle status=new android.os.Bundle();status.putString("stream","LOCAL_INFERENCE_MS "+(System.currentTimeMillis()-began)+"\n");t.sendStatus(0,status);t.call(t.c("ApiMemory"),"clear");
    }
}
