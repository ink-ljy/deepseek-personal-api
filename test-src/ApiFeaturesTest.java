package dev.local.deepseekapitest;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Custom chat/embedding endpoint isolation and live local memory integration fixtures. */
public final class ApiFeaturesTest {
    static List<Conn> requests=new CopyOnWriteArrayList<>();static String response;static volatile boolean embeddingFail;
    static class Conn extends HttpURLConnection{
        ByteArrayOutputStream body=new ByteArrayOutputStream();Conn(URL u){super(u);requests.add(this);}URL address(){return url;}public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public OutputStream getOutputStream(){return body;}public int getResponseCode(){return embeddingFail&&url.getPath().endsWith("embeddings")?503:200;}
        public InputStream getInputStream()throws IOException{String s=response;try{if(url.getPath().endsWith("embeddings")){JSONObject p=new JSONObject(body.toString("UTF-8"));boolean rocket=p.getString("input").contains("rocket");s="{\"data\":[{\"embedding\":"+(rocket?"[0,1,0]":"[1,0,0]")+"}]}";}}catch(Exception e){throw new IOException(e);}return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));}
    }
    static void pref(ApiSelfTest t,String name,Object value)throws Exception{Object ed=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(ed,value instanceof Boolean?"putBoolean":"putString",name,value);t.call(ed,"commit");}
    static void idle(ApiSelfTest t)throws Exception{Set<?> jobs=(Set<?>)t.field(t.c("ApiMemory"),"pending");long end=System.currentTimeMillis()+15000;while(!jobs.isEmpty()&&System.currentTimeMillis()<end)Thread.sleep(20);if(!jobs.isEmpty())throw new AssertionError("Memory jobs did not finish");}
    static String sse(ApiSelfTest t)throws Exception{return "data: "+t.json("model","custom-fixture","choices",new JSONArray().put(t.json("delta",t.json("reasoning_content","think"))))+"\n\n"+"data: "+t.json("choices",new JSONArray().put(t.json("delta",t.json("content","fixture answer"),"finish_reason","stop")))+"\n\n"+"data: "+t.json("choices",new JSONArray(),"usage",t.json("prompt_tokens",1000,"completion_tokens",20,"prompt_tokens_details",t.json("cached_tokens",800)))+"\n\ndata: [DONE]\n\n";}
    static void run(ApiSelfTest t)throws Exception{
        URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Conn(u);}}:null);
        pref(t,"memory_auto",false);pref(t,"memory_enabled",false);t.call(t.c("ApiMemory"),"clear");t.call(t.c("ApiSupport"),"saveKey","official-fixture-only");
        try{
            t.check(t.call(t.c("ApiProfiles"),"base","https://CHAT.example/v1/").equals("https://chat.example/v1"),"custom base URL is canonicalized without losing v1 path");
            boolean rejected=true;for(String s:new String[]{"http://example.com/v1","https://user:pass@example.com","https://example.com?key=x","https://example.com#x","https://example.com\nInjected"})try{t.call(t.c("ApiProfiles"),"base",s);rejected=false;}catch(IOException expected){}t.check(rejected,"unsafe or ambiguous base URLs are rejected");
            t.call(t.c("ApiProfiles"),"saveChat","https://chat.example/v1","chat-fixture-only","custom-model",128000L,8192);Object cfg=t.call(t.c("ApiProfiles"),"active");t.check(t.field(cfg,"custom").equals(true)&&t.field(cfg,"model").equals("custom-model"),"custom chat profile becomes active with model and token limits");
            t.check(t.call(t.c("ApiSupport"),"key").equals("official-fixture-only")&&t.field(cfg,"key").equals("chat-fixture-only"),"official and custom chat keys stay in separate encrypted slots");
            boolean blocked=false;try{t.call(t.c("ApiProfiles"),"saveChat","https://other.example/v1","","new",64000L,4096);}catch(IOException e){blocked=true;}t.check(blocked&&t.field(t.call(t.c("ApiProfiles"),"active"),"base").equals("https://chat.example/v1"),"changed endpoint cannot reuse an existing key and failed save is atomic");
            pref(t,"persona","Respond in Chinese and call me Captain.");pref(t,"memory_enabled",true);
            t.call(t.c("ApiMemory"),"configure","https://vector.example/v1","vector-fixture-only","embed-model");
            String id=(String)t.call(t.c("ApiMemory"),"put",null,"I enjoy bicycle rides.","old-session");idle(t);
            String hits=(String)t.call(t.c("ApiMemory"),"retrieve","bike hobby","new-session");t.check(hits.contains("bicycle"),"separate embedding endpoint creates vectors and retrieves cross-session memory");
            t.check(!((String)t.call(t.c("ApiMemory"),"retrieve","bike hobby","old-session")).contains("bicycle"),"current-session memory is excluded from duplicate retrieval");
            t.check(!((String)t.call(t.c("ApiMemory"),"retrieve","rocket engineering","new-session")).contains("bicycle"),"cosine threshold excludes unrelated memory fixture");
            t.call(t.c("ApiMemory"),"put",id,"I enjoy mountain biking.","old-session");idle(t);hits=(String)t.call(t.c("ApiMemory"),"retrieve","bike hobby","new-session");t.check(hits.contains("mountain")&&!hits.contains("bicycle rides"),"editing memory replaces both text and vector index");
            String sid="custom-feature-"+System.nanoTime();response=sse(t);int before=requests.size();JSONObject req=ApiStreamTest.req(t,sid);req.put("prompt","bike hobby");
            try(ApiStreamTest.Client client=new ApiStreamTest.Client(t,"completion",req)){client.finish();t.check(client.text().equals("fixture answer")&&client.status().equals("FINISHED"),"custom OpenAI SSE maps into original native answer and terminal status");}
            ApiStreamTest.ended(t,sid);Conn chat=null;for(int i=before;i<requests.size();i++)if(requests.get(i).address().getHost().equals("chat.example"))chat=requests.get(i);if(chat==null)throw new AssertionError("Missing custom request");JSONObject body=new JSONObject(chat.body.toString("UTF-8"));JSONArray messages=body.getJSONArray("messages");
            t.check(chat.address().getPath().equals("/v1/chat/completions")&&chat.getRequestProperty("Authorization").equals("Bearer chat-fixture-only"),"chat request uses only the configured endpoint key and model route");
            t.check(messages.getJSONObject(0).getString("content").contains("Captain")&&!body.has("thinking")&&!body.has("tools"),"persona is system content and DeepSeek-specific extensions are omitted for compatible providers");
            t.check(messages.getJSONObject(1).toString().contains("mountain biking"),"retrieved memory is attached to current user message");JSONObject session=(JSONObject)t.call(t.c("ApiBridge"),"session",sid);String nativePrompt=(String)t.call(t.c("ApiBridge"),"text",session.getJSONObject("messages").getJSONObject("1").getJSONObject("native"));
            t.check(nativePrompt.equals("bike hobby")&&!nativePrompt.contains("mountain"),"native chat bubble preserves the original user text");JSONObject u=session.getJSONObject("messages").getJSONObject("2").getJSONObject("api_usage");t.check(u.getInt("input_tokens")==200&&u.getInt("cache_read_input_tokens")==800&&u.getInt("output_tokens")==20,"OpenAI usage is normalized without counting cache hits twice");
            JSONArray prefix=(JSONArray)t.call(t.c("ApiContext"),"messages",session,2);t.call(t.c("ApiMemory"),"put",id,"Changed memory after first request.","old-session");idle(t);t.check(prefix.toString().equals(((JSONArray)t.call(t.c("ApiContext"),"messages",session,2)).toString()),"memory edits do not rewrite prior message prefixes");
            t.call(t.c("ApiMemory"),"delete",id);t.check(!((String)t.call(t.c("ApiMemory"),"retrieve","bike hobby","new-session")).contains("Changed"),"deleted memory is excluded from subsequent retrieval");
            File image=new File(t.getTargetContext().getFilesDir(),"custom-image.png");Bitmap bmp=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888);try(OutputStream out=new FileOutputStream(image)){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();before=requests.size();JSONObject file=(JSONObject)t.call(t.c("ApiFiles"),"uploadSync",t.make("ui4",image.getName(),Uri.fromFile(image),image.length(),2));
            t.check(file.optBoolean("local_only")&&requests.size()==before,"custom image is prepared locally without sending key to DeepSeek Files API");JSONArray content=(JSONArray)t.call(t.c("ApiFiles"),"content","look",new JSONArray().put(file.getJSONObject("api").getString("id")));JSONArray mapped=(JSONArray)t.call(t.c("ApiProfiles"),"messages",new JSONArray().put(t.json("role","user","content",content)));t.check(mapped.getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/png;base64,"),"custom image bytes are encoded in compatible image_url content");
            pref(t,"memory_auto",true);t.call(t.c("ApiMemory"),"clear");response=sse(t);String auto="auto-memory-"+System.nanoTime();try(ApiStreamTest.Client client=new ApiStreamTest.Client(t,"completion",ApiStreamTest.req(t,auto))){client.finish();}ApiStreamTest.ended(t,auto);idle(t);JSONArray rows=(JSONArray)t.call(t.c("ApiMemory"),"list","");t.check(rows.length()>0&&rows.getJSONObject(0).getString("text").contains("fixture answer")&&!rows.getJSONObject(0).getString("text").contains("think"),"completed API round auto-updates memory without storing reasoning");
            for(Conn c:requests)t.check(c.address().getHost().equals("chat.example")?c.getRequestProperty("Authorization").equals("Bearer chat-fixture-only"):c.address().getHost().equals("vector.example")&&c.getRequestProperty("Authorization").equals("Bearer vector-fixture-only"),"endpoint-scoped credential fixture "+requests.indexOf(c));
            embeddingFail=true;String fallback=(String)t.call(t.c("ApiMemory"),"retrieve","bike hobby","other-session");t.check(fallback.isEmpty(),"embedding failure degrades to ordinary chat without leaking provider response");embeddingFail=false;
            t.call(t.c("ApiProfiles"),"official");t.check(t.field(t.call(t.c("ApiProfiles"),"active"),"key").equals("official-fixture-only"),"switching back restores official key instead of custom key");
        }finally{idle(t);t.call(t.c("ApiMemory"),"clear");Object ed=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");for(String key:new String[]{"key","chat_key","embedding_key","persona"})t.call(ed,"remove",key);t.call(ed,"putBoolean","enabled",false);t.call(ed,"putBoolean","custom_active",false);t.call(ed,"putBoolean","embedding_remote",false);t.call(ed,"putBoolean","memory_auto",false);t.call(ed,"commit");}
    }
}
