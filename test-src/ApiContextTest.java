package dev.local.deepseekapitest;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Deterministic usage and compression fixtures; never sends a paid API request. */
public final class ApiContextTest {
    static JSONObject next,lastPayload;static int requests;
    static void transport()throws Exception{URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new HttpURLConnection(u){
        ByteArrayOutputStream body=new ByteArrayOutputStream();public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}public OutputStream getOutputStream(){return body;}public int getResponseCode(){return 200;}
        public InputStream getInputStream()throws IOException{try{requests++;lastPayload=new JSONObject(new String(body.toByteArray(),StandardCharsets.UTF_8));return new ByteArrayInputStream(next.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IOException(e);}}
    };}}:null);}
    static JSONObject seed(ApiSelfTest t,String sid,int turns)throws Exception{
        JSONObject s=(JSONObject)t.call(t.c("ApiBridge"),"session",sid);s.put("owned",true);JSONObject records=s.getJSONObject("messages");StringBuilder large=new StringBuilder();for(int n=0;n<350;n++)large.append("Remember confirmed fact ").append(n).append(". ");
        for(int i=1;i<=turns*2;i++){JSONArray fragments=new JSONArray().put(t.json("type",i%2==1?"REQUEST":"RESPONSE","id",1,"content",i<=2?large.toString():"turn "+i));
            JSONObject m=(JSONObject)t.call(t.c("ApiBridge"),"nativeMessage",i,i==1?null:i-1,i%2==1?"USER":"ASSISTANT","FINISHED",fragments,new JSONObject());JSONObject rec=t.json("native",m);
            if(i%2==0)rec.put("api_content",new JSONArray().put(t.json("type","text","text",i<=2?large.toString():"raw answer "+i)));records.put(Integer.toString(i),rec);}
        s.put("current_message_id",turns*2);t.call(t.c("ApiBridge"),"save",s);t.call(t.c("ApiContext"),"select",sid);return s;
    }
    static JSONObject summary(ApiSelfTest t,String text,String stop)throws Exception{return t.json("content",new JSONArray().put(t.json("type","text","text",text)),"stop_reason",stop,"usage",t.json("input_tokens",1800,"output_tokens",50,"cache_read_input_tokens",700));}
    public static void run(ApiSelfTest t)throws Exception{
        transport();t.call(t.c("ApiSupport"),"saveKey","local-context-fixture-no-network");String sid="context-fixture-"+System.nanoTime();JSONObject s=seed(t,sid,3);
        try{
            String original=s.getJSONObject("messages").toString();JSONArray full=(JSONArray)t.call(t.c("ApiBridge"),"context",s,6),effective=(JSONArray)t.call(t.c("ApiContext"),"messages",s,6);
            t.check(full.toString().equals(effective.toString())&&full.length()==6,"uncompressed context keeps exact multi-turn raw prefix");
            JSONObject merged=new JSONObject();t.call(t.c("ApiContext"),"mergeUsage",merged,t.json("input_tokens",200,"cache_read_input_tokens",800,"output_tokens",0));t.call(t.c("ApiContext"),"mergeUsage",merged,t.json("output_tokens",20));t.call(t.c("ApiContext"),"mergeUsage",merged,t.json("output_tokens",35));
            t.check(merged.getInt("output_tokens")==35,"cumulative usage deltas overwrite instead of summing");t.check(((Number)t.call(t.c("ApiContext"),"totalInput",merged)).longValue()==1000,"Anthropic input total includes cache-hit tokens");
            s.getJSONObject("messages").getJSONObject("6").put("api_usage",merged);original=s.getJSONObject("messages").toString();JSONObject stats=(JSONObject)t.call(t.c("ApiContext"),"stats",s);
            t.check(stats.getInt("input_tokens")==1000&&stats.getDouble("cache_percent")==80,"cache percentage uses actual API usage");t.check(stats.getLong("estimated_tokens")>0&&stats.getLong("limit")==1000000,"context space is explicitly estimated against documented 1M limit");
            next=summary(t,"Confirmed historical facts and goals from first turn.","end_turn");t.call(t.c("ApiContext"),"compress",sid);JSONObject cp=s.getJSONObject("context_checkpoint");
            t.check(requests==1&&!lastPayload.getBoolean("stream")&&lastPayload.getString("model").equals("deepseek-flash"),"compression uses one explicit public Messages API request");
            t.check(cp.getInt("anchor")==2,"compression anchors after older complete assistant turn");JSONArray reduced=(JSONArray)t.call(t.c("ApiContext"),"messages",s,6);
            t.check(reduced.length()==4&&reduced.getJSONObject(0).getString("content").equals("turn 3"),"compression keeps last two complete turns verbatim");
            t.check(s.getJSONObject("messages").toString().equals(original)&&s.getJSONObject("messages").length()==6,"compression preserves all history including recorded usage");
            t.check(((String)t.call(t.c("ApiContext"),"summary",s,6)).contains("Confirmed"),"summary is applied to descendants of its anchor");
            t.check(t.call(t.c("ApiContext"),"summary",s,1)==null&&((JSONArray)t.call(t.c("ApiContext"),"messages",s,1)).length()==1,"summary is excluded from unrelated earlier branches");
            JSONObject request=t.json("chat_session_id",sid,"parent_message_id",6,"prompt","continue","ref_file_ids",new JSONArray());Object run=t.make("ApiBridge$Run",sid,"/api/v0/chat/completion",request,s);t.call(run,"prepare");
            t.check(((JSONArray)t.field(run,"blocks")).length()==5&&String.valueOf(t.field(run,"contextSummary")).contains("Confirmed"),"real chat adapter sends summary plus preserved recent messages");
            t.call(run,"consume",t.json("type","message_start","message",t.json("model","deepseek-flash","usage",t.json("input_tokens",100,"cache_read_input_tokens",900,"output_tokens",0))));
            t.call(run,"consume",t.json("type","message_delta","usage",t.json("output_tokens",40)));t.call(run,"checkpoint",(Object)null);
            JSONObject rec=s.getJSONObject("messages").getJSONObject(Integer.toString((Integer)t.field(run,"answerId")));
            t.check(rec.getJSONObject("api_usage").getInt("output_tokens")==40&&rec.getJSONObject("api_usage").getInt("cache_read_input_tokens")==900,"real SSE parser persists usage in answer record");
            s.put("current_message_id",6);String before=s.getJSONObject("messages").toString();t.call(t.c("ApiContext"),"restore",sid);
            t.check(!s.has("context_checkpoint")&&s.getJSONObject("messages").toString().equals(before),"restore removes only summary and preserves all original messages");
            next=summary(t,"truncated","max_tokens");boolean failed=false;try{t.call(t.c("ApiContext"),"compress",sid);}catch(IOException expected){failed=true;}
            t.check(failed&&!s.has("context_checkpoint")&&!((Boolean)t.call(t.c("ApiContext"),"busy",sid)),"truncated summary is rejected atomically and releases compression lock");
            next=summary(t,"Confirmed first turn facts.","end_turn");t.call(t.c("ApiContext"),"compress",sid);String cpBefore=s.getJSONObject("context_checkpoint").toString();failed=false;
            try{t.call(t.c("ApiContext"),"compress",sid);}catch(IOException expected){failed=true;}t.check(failed&&cpBefore.equals(s.getJSONObject("context_checkpoint").toString()),"repeated compression without new older turns preserves previous summary");
            s.put("deleted",true);t.call(t.c("ApiBridge"),"save",s);
        }finally{Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"remove","key");t.call(editor,"putBoolean","enabled",false);t.call(editor,"commit");}
    }
}
