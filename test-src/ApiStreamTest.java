package dev.local.deepseekapitest;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Controlled streaming transport. Exercises the shipped adapter and original native serializers. */
public final class ApiStreamTest {
    static final List<Connection> requests=new CopyOnWriteArrayList<>();
    static volatile int nextCode=200;
    static final String suffix="-"+System.nanoTime();
    static class Gate extends InputStream {
        final BlockingQueue<byte[]> chunks=new LinkedBlockingQueue<>();ByteArrayInputStream current;
        volatile boolean closed;boolean eof;
        void send(String text){chunks.add(text.getBytes(StandardCharsets.UTF_8));}
        void end(){chunks.add(new byte[0]);}
        @Override public int read(byte[] b,int off,int len)throws IOException{
            while(true){if(closed)throw new IOException("fixture disconnected");if(eof)return -1;
                if(current!=null){int n=current.read(b,off,len);if(n!=-1)return n;current=null;}
                byte[] next;try{next=chunks.poll(20,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}
                if(next==null)throw new IOException("fixture timed out");if(next.length==0){eof=true;return -1;}current=new ByteArrayInputStream(next);
            }
        }
        @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
        @Override public void close(){closed=true;end();}
    }
    static class Connection extends HttpURLConnection {
        final Gate input=new Gate();final ByteArrayOutputStream body=new ByteArrayOutputStream();final int code;volatile boolean closed;
        Connection(URL u){super(u);code=nextCode;nextCode=200;requests.add(this);}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed=true;input.close();}
        public OutputStream getOutputStream(){return body;}public InputStream getInputStream(){return input;}
        public int getResponseCode(){return code;}public String getContentType(){return "text/event-stream";}
        void event(JSONObject event){input.send("data: "+event+"\n\n");}
        void start(ApiSelfTest t,String value)throws Exception{event(t.json("type","content_block_start","index",0,"content_block",t.json("type","text","text",value)));}
        void append(ApiSelfTest t,String value)throws Exception{event(t.json("type","content_block_delta","index",0,"delta",t.json("type","text_delta","text",value)));}
        void finish(ApiSelfTest t,boolean finalBlank)throws Exception{input.send("data: "+t.json("type","message_stop")+(finalBlank?"\n\n":""));input.end();}
    }
    static class Client implements AutoCloseable {
        final ApiSelfTest t;final InputStream input;final BufferedReader reader;final Object state,codec;boolean initial=true,finished;int packets;String toast="";
        Client(ApiSelfTest t,String path,JSONObject data)throws Exception{
            this.t=t;Object request=t.request("https://chat.deepseek.com/api/v0/chat/"+path,"POST",data);
            Object response=t.call(t.c("ApiBridge"),"intercept",request);
            input=(InputStream)t.call(t.call(t.field(response,"g"),"d0"),"g0");reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
            state=t.make("oqc",12);codec=t.field(t.c("o56"),"a");
        }
        String packet()throws Exception{
            String event=null,line;StringBuilder data=new StringBuilder();
            while((line=reader.readLine())!=null){
                if(line.startsWith("event:"))event=line.substring(6).trim();else if(line.startsWith("data:"))data.append(line.substring(5).trim());
                else if(line.isEmpty()&&data.length()>0){
                    packets++;JSONObject value=new JSONObject(data.toString());
                    if(event==null){Object delta=t.call(codec,"b",t.field(t.c("du3"),"a"),value.toString());if(initial){t.call(state,"Q",t.field(delta,"c"));initial=false;}else t.call(state,"F",delta,null);}
                    else if(event.equals("ready"))t.call(codec,"b",t.field(t.c("fv1"),"a"),value.toString());
                    else if(event.equals("finish")){t.call(codec,"b",t.field(t.c("gy2"),"a"),value.toString());finished=true;}
                    else if(event.equals("toast"))toast=value.optString("content");
                    return event==null?"delta":event;
                }
            }return "EOF";
        }
        Object message()throws Exception{return t.field(t.field(state,"c"),"a");}
        String text()throws Exception{return (String)t.call(message(),"j");}
        String status()throws Exception{return (String)t.call(message(),"F");}
        void untilText(String text)throws Exception{long end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){if(!initial&&text().contains(text))return;if(packet().equals("EOF"))break;}throw new AssertionError("fixture answer not received");}
        void finish()throws Exception{long end=System.currentTimeMillis()+12000;while(!finished&&System.currentTimeMillis()<end)if(packet().equals("EOF"))break;if(!finished)throw new AssertionError("native finish missing");}
        public void close()throws IOException{reader.close();}
    }
    static JSONObject req(ApiSelfTest t,String sid)throws Exception{return t.json("chat_session_id",sid,"parent_message_id",null,"prompt","fixture prompt","thinking_enabled",true,"search_enabled",true,"ref_file_ids",new JSONArray());}
    static JSONObject resume(ApiSelfTest t,String sid)throws Exception{return t.json("chat_session_id",sid,"message_id",2,"interaction_type","text");}
    static Connection connection(int index)throws Exception{long end=System.currentTimeMillis()+8000;while(requests.size()<=index&&System.currentTimeMillis()<end)Thread.sleep(10);if(requests.size()<=index)throw new AssertionError("missing upstream request");return requests.get(index);}
    static JSONObject record(ApiSelfTest t,String sid)throws Exception{return ((JSONObject)t.call(t.c("ApiBridge"),"session",sid)).getJSONObject("messages").getJSONObject("2");}
    static void ended(ApiSelfTest t,String sid)throws Exception{
        java.lang.reflect.Field field=t.c("ApiBridge").getDeclaredField("running");field.setAccessible(true);Map<?,?> runs=(Map<?,?>)field.get(null);
        long end=System.currentTimeMillis()+8000;while(runs.containsKey(sid)&&System.currentTimeMillis()<end)Thread.sleep(10);if(runs.containsKey(sid))throw new AssertionError("generation did not finish");
    }
    static void run(ApiSelfTest t)throws Exception{
        URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
        t.call(t.c("ApiSupport"),"saveKey","local-stream-fixture-not-a-real-key");
        try{
            String sid="stream-detach"+suffix;int index=requests.size();Client a=new Client(t,"completion",req(t,sid));Connection c=connection(index);
            a.packet();a.packet();c.start(t,"partial");a.untilText("partial");a.close();
            t.check(!c.closed,"closing native page reader does not cancel upstream API generation");
            Client b=new Client(t,"resume_stream",resume(t,sid));b.untilText("partial");
            t.check(requests.size()==index+1,"active resume reattaches without another billed API request");
            t.check(b.text().equals("partial"),"resume restores original message snapshot without duplicate text");
            Thread.sleep(1100);c.append(t,"-saved");b.untilText("-saved");
            long savedEnd=System.currentTimeMillis()+3000;File file=(File)t.call(t.c("ApiBridge"),"file",sid);String saved="";
            while(System.currentTimeMillis()<savedEnd){saved=t.read(new FileInputStream(file));if(saved.contains("partial-saved"))break;Thread.sleep(20);}
            t.check(saved.contains("partial-saved"),"partial answer is checkpointed before generation finishes");
            b.close();char[] chars=new char[180000];Arrays.fill(chars,'x');String large=new String(chars);c.append(t,large);c.finish(t,true);ended(t,sid);
            t.check(record(t,sid).getJSONObject("native").getString("status").equals("FINISHED"),"detached long answer finishes without original pipe buffer backpressure");
            Client completed=new Client(t,"resume_stream",resume(t,sid));completed.finish();
            t.check(completed.text().equals("partial-saved"+large),"completed replay retains more than 128 KiB of answer content");
            t.check(requests.size()==index+1,"completed replay does not regenerate or bill again");completed.close();
            JSONObject history=(JSONObject)t.call(t.c("ApiBridge"),"history",t.call(t.c("ApiBridge"),"session",sid));
            t.call(t.field(t.c("o56"),"a"),"b",t.field(t.c("iz1"),"a"),history.getJSONObject("data").getJSONObject("biz_data").toString());
            t.check(history.getJSONObject("data").getJSONObject("biz_data").getJSONArray("chat_messages").length()==2,"reattach preserves exactly one user and one assistant message in native history");

            sid="stream-idle"+suffix;index=requests.size();a=new Client(t,"completion",req(t,sid));c=connection(index);a.packet();a.packet();long begin=System.currentTimeMillis();
            t.check(a.packet().equals("delta")&&a.status().equals("WIP")&&System.currentTimeMillis()-begin>=4000,"idle thinking emits a valid native keepalive delta");
            b=new Client(t,"resume_stream",resume(t,sid));a.close();c.start(t,"idle recovered");c.finish(t,false);b.finish();ended(t,sid);
            t.check(b.text().equals("idle recovered")&&b.status().equals("FINISHED"),"SSE final message_stop without a trailing blank line completes successfully");b.close();

            sid="stream-network-retry"+suffix;index=requests.size();a=new Client(t,"completion",req(t,sid));c=connection(index);c.start(t,"interrupted partial");c.input.end();a.finish();ended(t,sid);
            t.check(a.status().equals("FAILED")&&a.text().equals("interrupted partial"),"real upstream truncation preserves partial text and is not marked finished");a.close();
            b=new Client(t,"resume_stream",resume(t,sid));Connection retry=connection(index+1);retry.start(t,"replacement answer");retry.finish(t,true);b.finish();ended(t,sid);
            t.check(b.status().equals("FINISHED")&&b.text().equals("replacement answer"),"retry regenerates the failed answer instead of appending duplicate partial text");
            JSONObject payload=new JSONObject(retry.body.toString("UTF-8"));JSONArray messages=payload.getJSONArray("messages");
            t.check(messages.length()==1&&messages.getJSONObject(0).getString("content").equals("fixture prompt")&&payload.getJSONObject("thinking").getString("type").equals("enabled")&&payload.has("tools"),"retry keeps original prompt and thinking/search options");
            t.check(((JSONObject)t.call(t.c("ApiBridge"),"session",sid)).getJSONObject("messages").length()==2&&record(t,sid).getInt("api_retry_count")==1,"retry reuses existing native message IDs with a bounded retry counter");b.close();

            sid="stream-retry-limit"+suffix;index=requests.size();a=new Client(t,"completion",req(t,sid));c=connection(index);c.start(t,"first");c.input.end();a.finish();ended(t,sid);a.close();
            b=new Client(t,"resume_stream",resume(t,sid));c=connection(index+1);c.start(t,"second partial");c.input.end();b.finish();ended(t,sid);b.close();
            a=new Client(t,"resume_stream",resume(t,sid));a.finish();
            t.check(requests.size()==index+2&&a.text().equals("second partial")&&a.status().equals("FAILED"),"repeated recovery is capped and never loops billed generation requests");a.close();

            sid="stream-auth-error"+suffix;index=requests.size();nextCode=401;a=new Client(t,"completion",req(t,sid));connection(index);a.finish();ended(t,sid);a.close();
            b=new Client(t,"resume_stream",resume(t,sid));b.finish();
            t.check(requests.size()==index+1&&b.status().equals("FAILED"),"authentication failure is replayed without repeated API calls");b.close();

            sid="stream-explicit-stop"+suffix;index=requests.size();a=new Client(t,"completion",req(t,sid));c=connection(index);c.start(t,"stopped answer");a.untilText("stopped answer");
            t.call(t.c("ApiBridge"),"intercept",t.request("https://chat.deepseek.com/api/v0/chat/stop_stream","POST",t.json("chat_session_id",sid)));a.finish();ended(t,sid);a.close();
            b=new Client(t,"resume_stream",resume(t,sid));b.finish();
            t.check(c.closed&&b.status().equals("INCOMPLETE")&&b.text().equals("stopped answer"),"explicit Stop disconnects API and persists incomplete answer");
            t.check(requests.size()==index+1,"reopening a deliberately stopped answer never restarts generation");b.close();

            sid="stream-branch-attachment"+suffix;index=requests.size();String fid="file-api-stream-image";double now=System.currentTimeMillis()/1000.0;
            JSONObject meta=t.json("id",fid,"status","SUCCESS","file_name","fixture.png","file_size",100,"inserted_at",now,"updated_at",now,"previewable",true,"is_image",true,"signed_path","file:///fixture.png","width",1,"height",1);
            t.call(t.c("ApiFiles"),"store",t.json("api",t.json("id",fid),"native",meta,"is_image",true));
            JSONObject first=req(t,sid);first.put("ref_file_ids",new JSONArray().put(fid));a=new Client(t,"completion",first);c=connection(index);c.start(t,"previous answer");c.finish(t,true);a.finish();ended(t,sid);a.close();
            JSONObject follow=req(t,sid);follow.put("parent_message_id",2);follow.put("prompt","follow up");a=new Client(t,"completion",follow);c=connection(index+1);c.start(t,"partial follow up");c.input.end();a.finish();ended(t,sid);a.close();
            JSONObject recovery=resume(t,sid);recovery.put("message_id",4);b=new Client(t,"resume_stream",recovery);retry=connection(index+2);retry.start(t,"final follow up");retry.finish(t,true);b.finish();ended(t,sid);
            payload=new JSONObject(retry.body.toString("UTF-8"));messages=payload.getJSONArray("messages");
            t.check(messages.length()==3&&messages.getJSONObject(2).getString("content").equals("follow up"),"multi-turn retry follows the selected original parent branch");
            t.check(messages.getJSONObject(0).getJSONArray("content").getJSONObject(1).getJSONObject("source").getString("file_id").equals(fid),"retry preserves image file references in previous user context");
            t.check(messages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text").equals("previous answer"),"retry preserves complete prior assistant API content");
            t.check(((JSONObject)t.call(t.c("ApiBridge"),"session",sid)).getJSONObject("messages").length()==4&&b.text().equals("final follow up"),"multi-turn retry reuses the failed response ID without duplicating history");b.close();
        }finally{for(Connection c:requests)c.disconnect();Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"clear");t.call(editor,"commit");}
    }
}
