import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Adapts public Messages API SSE to the original app's native ready/delta/finish protocol. */
public final class ApiBridge {
    private static final Map<String,JSONObject> sessions=new ConcurrentHashMap<>();
    private static final Map<String,Run> running=new ConcurrentHashMap<>();
    private static final Object storeLock=new Object();

    public static Object pow() {
        if(!ApiSupport.enabled())return null;
        try{return ApiSupport.make("ua8","personal-api-mode",null);}catch(Exception e){throw new IllegalStateException(e);}
    }
    static File file(String id) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:hash)b.append(String.format(Locale.ROOT,"%02x",x));
        File dir=new File(ApiSupport.app.getFilesDir(),"personal_api_sessions");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法保存 API 会话");return new File(dir,b+".json");
    }
    static JSONObject session(String id) throws Exception {
        synchronized(storeLock){JSONObject existing=sessions.get(id);if(existing!=null)return existing;File f=file(id);JSONObject s;
            if(f.exists())s=new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8));
            else s=ApiSupport.obj("id",id,"title","新对话","inserted_at",System.currentTimeMillis()/1000.0,"updated_at",System.currentTimeMillis()/1000.0,"messages",new JSONObject(),"owned",false);
            sessions.put(id,s);return s;
        }
    }
    static void save(JSONObject s) throws Exception {
        synchronized(storeLock){AtomicFile f=new AtomicFile(file(s.getString("id")));FileOutputStream stream=f.startWrite();try{stream.write(s.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}catch(Exception e){f.failWrite(stream);throw e;}}
    }
    /** Captures the exact native branch and attachment metadata before a request, without changing the UI. */
    public static void cacheSession(Object nativeSession) {
        if(!ApiSupport.enabled())return;
        try{synchronized(storeLock){String id=(String)ApiSupport.field(nativeSession,"a");JSONObject s=session(id);JSONObject records=s.getJSONObject("messages");
            s.put("inserted_at",ApiSupport.field(nativeSession,"b"));s.put("updated_at",ApiSupport.field(nativeSession,"c"));
            s.put("title",ApiSupport.call(ApiSupport.field(nativeSession,"g"),"getValue"));
            Map<?,?> nativeMessages=(Map<?,?>)ApiSupport.field(nativeSession,"f");
            Object codec=ApiSupport.field(ApiSupport.cls("o56"),"a"),serializer=ApiSupport.field(ApiSupport.cls("gy"),"a");
            for(Object m:nativeMessages.values()){
                int mid=((Number)ApiSupport.call(m,"w")).intValue();if(mid<0)continue;
                JSONObject record=records.optJSONObject(Integer.toString(mid));if(record==null)record=new JSONObject();
                Object staticMessage=ApiSupport.call(m,"a");String json=(String)ApiSupport.call(codec,"c",serializer,staticMessage);
                record.put("native",new JSONObject(json));records.put(Integer.toString(mid),record);
            }
        }}catch(Exception e){ApiSupport.notice("API 上下文读取失败，请新建对话后重试。");}
    }
    public static Object intercept(Object request) throws Exception {
        if(ApiSupport.app==null)return null;
        String url=ApiSupport.field(request,"a").toString();Uri uri=Uri.parse(url);
        if(!"chat.deepseek.com".equals(uri.getHost()))return null;
        String path=uri.getPath();boolean enabled=ApiSupport.enabled();
        if(path.equals("/api/v0/chat/history_messages")){
            String id=uri.getQueryParameter("chat_session_id");if(id==null)return null;
            JSONObject s=session(id);if(!s.optBoolean("owned"))return null;
            return jsonResponse(request,history(s));
        }
        if(path.equals("/api/v0/chat_session/fetch_page"))return sessionPage(request,uri);
        if(!enabled)return null;
        if(path.equals("/api/v0/chat_session/create")){
            JSONObject s=session(UUID.randomUUID().toString());s.put("owned",true);save(s);
            return jsonResponse(request,envelope(ApiSupport.obj("chat_session",metadata(s),"ttl_seconds",86400)));
        }
        if(path.startsWith("/api/v0/chat_session/")){
            JSONObject data=body(request);String sid=data.optString("chat_session_id");
            if(!sid.isEmpty()){
                JSONObject s=session(sid);if(s.optBoolean("owned")){
                    if(path.endsWith("/update_title")){s.put("title",data.getString("title"));s.put("updated_at",System.currentTimeMillis()/1000.0);save(s);return jsonResponse(request,envelope(ApiSupport.obj("title",s.getString("title"),"chat_session_updated_at",s.getDouble("updated_at"))));}
                    if(path.endsWith("/update_pinned")){s.put("pinned",data.optBoolean("pinned"));save(s);return jsonResponse(request,envelope(ApiSupport.obj("chat_session",metadata(s),"pinned",s.optBoolean("pinned"))));}
                    if(path.endsWith("/delete")){s.put("deleted",true);save(s);return jsonResponse(request,envelope(new JSONObject()));}
                }
            }
            if(path.endsWith("/delete_all")){for(JSONObject s:localSessions()){s.put("deleted",true);save(s);}return null;}
        }
        if(path.equals("/api/v0/chat/stop_stream")){
            JSONObject data=body(request);Run r=running.get(data.optString("chat_session_id"));if(r!=null)r.stop();
            return jsonResponse(request,envelope(new JSONObject()));
        }
        boolean chat=path.equals("/api/v0/chat/completion")||path.equals("/api/v0/chat/edit_message")||path.equals("/api/v0/chat/regenerate")||path.equals("/api/v0/chat/continue")||path.equals("/api/v0/chat/resume_stream");
        if(!chat)return null;
        JSONObject data=body(request);String id=data.getString("chat_session_id");JSONObject s=session(id);
        Run run=new Run(id,path,data,s);Run old=running.putIfAbsent(id,run);
        if(old!=null)throw new IOException("当前对话正在生成，请先停止后重试。");
        try{
            PipedInputStream input=new PipedInputStream(131072);PipedOutputStream output=new PipedOutputStream(input);run.output=output;
            Object response=response(request,input,"text/event-stream; charset=utf-8",run);
            new Thread(run,"PersonalApiStream").start();return response;
        }catch(Exception e){running.remove(id,run);throw e;}
    }
    static JSONObject body(Object request) throws Exception {
        Object body=ApiSupport.field(request,"d");if(body==null)return new JSONObject();
        if(body.getClass().getName().equals("x89")){byte[] bytes=(byte[])ApiSupport.field(body,"c");int length=((Number)ApiSupport.field(body,"b")).intValue();return new JSONObject(new String(bytes,0,length,StandardCharsets.UTF_8));}
        throw new IOException("API 模式暂时无法读取此请求类型。");
    }
    static JSONObject envelope(JSONObject data) throws Exception {return ApiSupport.obj("code",0,"msg","","data",ApiSupport.obj("biz_code",0,"biz_msg","","biz_data",data));}
    static JSONObject history(JSONObject s) throws Exception {
        synchronized(storeLock){JSONObject records=s.getJSONObject("messages");List<Integer> ids=new ArrayList<>();Iterator<String> names=records.keys();while(names.hasNext())ids.add(Integer.valueOf(names.next()));Collections.sort(ids);
            JSONArray messages=new JSONArray();int max=0;for(int id:ids){JSONObject m=records.getJSONObject(Integer.toString(id)).optJSONObject("native");if(m!=null){messages.put(m);max=Math.max(max,id);}}
            JSONObject meta=ApiSupport.obj("id",s.getString("id"),"title",s.optString("title","新对话"),"version",max,"current_message_id",s.optInt("current_message_id",max),"inserted_at",s.optDouble("inserted_at"),"updated_at",s.optDouble("updated_at"),"pinned",false,"model_type","deepseek-flash","is_empty",messages.length()==0);
            return envelope(ApiSupport.obj("chat_session",meta,"chat_messages",messages,"cache_control","MERGE"));
        }
    }
    static JSONObject metadata(JSONObject s)throws Exception{
        int max=s.optInt("current_message_id",0);
        return ApiSupport.obj("id",s.getString("id"),"title",s.optString("title","新对话"),"version",max,"current_message_id",max>0?max:null,"inserted_at",s.optDouble("inserted_at"),"updated_at",s.optDouble("updated_at"),"pinned",s.optBoolean("pinned"),"model_type","deepseek-flash","is_empty",s.getJSONObject("messages").length()==0);
    }
    static List<JSONObject> localSessions()throws Exception{
        synchronized(storeLock){File dir=new File(ApiSupport.app.getFilesDir(),"personal_api_sessions");File[] all=dir.listFiles((d,name)->name.endsWith(".json"));if(all!=null)for(File f:all){try{JSONObject s=new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8));sessions.putIfAbsent(s.getString("id"),s);}catch(Exception ignored){}}
            List<JSONObject> result=new ArrayList<>();for(JSONObject s:sessions.values())if(s.optBoolean("owned"))result.add(s);return result;}
    }
    static Object sessionPage(Object request,Uri uri)throws Exception{
        List<JSONObject> locals=localSessions();if(locals.isEmpty())return null;
        JSONObject root=null;HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(ApiSupport.field(request,"a").toString()).openConnection();c.setRequestMethod("GET");c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(5000);
            String[] headers=(String[])ApiSupport.field(ApiSupport.field(request,"c"),"a");for(int i=0;i<headers.length;i+=2){String name=headers[i];if(!name.equalsIgnoreCase("Host")&&!name.equalsIgnoreCase("Content-Length")&&!name.equalsIgnoreCase("Accept-Encoding")&&!name.equalsIgnoreCase("Connection"))c.setRequestProperty(name,headers[i+1]);}
            if(c.getResponseCode()==200)root=new JSONObject(new String(ApiSupport.readAll(c.getInputStream()),StandardCharsets.UTF_8));
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
        if(root==null||root.optInt("code",-1)!=0||root.optJSONObject("data")==null||root.getJSONObject("data").optInt("biz_code",-1)!=0)root=envelope(ApiSupport.obj("chat_sessions",new JSONArray(),"has_more",false));
        JSONObject data=root.getJSONObject("data").getJSONObject("biz_data");JSONArray server=data.optJSONArray("chat_sessions");Map<String,JSONObject> combined=new HashMap<>();
        if(server!=null)for(int i=0;i<server.length();i++){JSONObject m=server.getJSONObject(i);combined.put(m.getString("id"),m);}
        double cursor=Double.POSITIVE_INFINITY;String value=uri.getQueryParameter("lte_cursor.updated_at");if(value!=null)try{cursor=Double.parseDouble(value);}catch(NumberFormatException ignored){}
        for(JSONObject s:locals){String id=s.getString("id");if(s.optBoolean("deleted")){combined.remove(id);continue;}if(s.optDouble("updated_at")<=cursor)combined.put(id,metadata(s));}
        List<JSONObject> ordered=new ArrayList<>(combined.values());ordered.sort((a,b)->{int pin=Boolean.compare(b.optBoolean("pinned"),a.optBoolean("pinned"));return pin!=0?pin:Double.compare(b.optDouble("updated_at"),a.optDouble("updated_at"));});
        JSONArray list=new JSONArray();for(JSONObject m:ordered)list.put(m);data.put("chat_sessions",list);return jsonResponse(request,root);
    }
    static Object jsonResponse(Object request,JSONObject data) throws Exception {return response(request,new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8)),"application/json; charset=utf-8",null);}
    static Object response(Object request,InputStream input,String contentType,Run run) throws Exception {
        Class<?> source=ApiSupport.cls("cea");Object timeout=ApiSupport.field(ApiSupport.cls("u2b"),"d");
        Object adapter=java.lang.reflect.Proxy.newProxyInstance(source.getClassLoader(),new Class[]{source},(proxy,method,args)->{
            switch(method.getName()){
                case "U":long count=((Number)args[0]).longValue();if(count==0)return 0L;byte[] bytes=new byte[(int)Math.min(count,8192)];int n=input.read(bytes);if(n==-1)return -1L;ApiSupport.call(args[1],"E",n,bytes);return (long)n;
                case "d":return timeout;
                case "close":if(run!=null)run.stop();input.close();return null;
                default:return ApiUi.proxyObject(proxy,method,args);
            }
        });
        Object buffered=ApiSupport.make("i09",adapter),body=ApiSupport.make("y09",contentType,-1L,buffered),builder=ApiSupport.make("ly0");
        ApiSupport.set(builder,"e",request);ApiSupport.set(builder,"f",ApiSupport.cls("dw8").getEnumConstants()[1]);ApiSupport.set(builder,"a",200);ApiSupport.set(builder,"b","OK");ApiSupport.set(builder,"i",body);
        ApiSupport.call(ApiSupport.field(builder,"h"),"D","Content-Type",contentType);ApiSupport.call(ApiSupport.field(builder,"h"),"D","Cache-Control","no-store");
        ApiSupport.set(builder,"c",System.currentTimeMillis());ApiSupport.set(builder,"d",System.currentTimeMillis());return ApiSupport.call(builder,"a");
    }
    static JSONObject nativeMessage(int id,Integer parent,String role,String status,JSONArray fragments,JSONObject req) throws Exception {
        return ApiSupport.obj("message_id",id,"parent_id",parent,"role",role,"inserted_at",System.currentTimeMillis()/1000.0,"status",status,
            "thinking_enabled",req.optBoolean("thinking_enabled"),"search_enabled",req.optBoolean("search_enabled"),"fragments",fragments);
    }
    static String text(JSONObject m) {
        JSONArray fs=m.optJSONArray("fragments");if(fs==null)return "";StringBuilder b=new StringBuilder();for(int i=0;i<fs.length();i++){JSONObject f=fs.optJSONObject(i);if(f!=null&&(f.optString("type").equals("REQUEST")||f.optString("type").equals("RESPONSE")))b.append(f.optString("content"));}return b.toString();
    }
    static JSONArray context(JSONObject s,Integer parent) throws Exception {
        JSONObject records=s.getJSONObject("messages");List<JSONObject> chain=new ArrayList<>();Set<Integer> seen=new HashSet<>();Integer cursor=parent;
        while(cursor!=null&&seen.add(cursor)){
            JSONObject r=records.optJSONObject(Integer.toString(cursor));if(r==null)throw new IOException("此分支的上下文尚未载入，请重新打开会话或新建对话。");
            JSONObject m=r.getJSONObject("native");String role=m.optString("role");
            if(role.equals("USER")||role.equals("ASSISTANT")){
                Object content=r.optJSONArray("api_content");if(content==null)content=text(m);
                if(!(content instanceof String)||!((String)content).isEmpty())chain.add(ApiSupport.obj("role",role.toLowerCase(Locale.ROOT),"content",content));
            }
            cursor=m.isNull("parent_id")?null:m.getInt("parent_id");
        }
        Collections.reverse(chain);JSONArray result=new JSONArray();for(JSONObject m:chain)result.put(m);return result;
    }
    static final class Run implements Runnable {
        final String id,path;final JSONObject request,session;volatile boolean cancelled;volatile HttpURLConnection connection;PipedOutputStream output;
        JSONObject assistant;int userId,answerId;Integer parent;JSONArray blocks=new JSONArray();JSONArray fragments=new JSONArray();Map<Integer,JSONObject> apiBlocks=new TreeMap<>();Map<Integer,StringBuilder> inputs=new HashMap<>();
        Map<Integer,Integer> fragmentIndexes=new HashMap<>();String title;boolean complete,refusal;long lastEmit;
        Run(String id,String path,JSONObject request,JSONObject session){this.id=id;this.path=path;this.request=request;this.session=session;}
        void stop(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        void event(String name,JSONObject data) throws Exception {if(name!=null)output.write(("event: "+name+"\n").getBytes(StandardCharsets.UTF_8));output.write(("data: "+data+"\n\n").getBytes(StandardCharsets.UTF_8));output.flush();}
        void delta(String path,Object value) throws Exception {event(null,ApiSupport.obj("p",path,"o","SET","v",value));}
        void emitFragments(boolean force) throws Exception {long now=System.currentTimeMillis();if(force||now-lastEmit>=80){delta("response/fragments",fragments);lastEmit=now;}}
        void prepare() throws Exception {
            synchronized(storeLock){JSONObject records=session.getJSONObject("messages");
                parent=request.isNull("parent_message_id")?null:request.optInt("parent_message_id");if(parent!=null&&parent<=0)parent=null;
                String prompt=request.optString("prompt","");JSONArray files=request.optJSONArray("ref_file_ids");
                if(path.endsWith("/regenerate")||path.endsWith("/continue")){
                    int mid=request.optInt("message_id",request.optInt("parent_message_id",session.optInt("current_message_id")));
                    JSONObject record=records.optJSONObject(Integer.toString(mid));if(record==null)throw new IOException("请重新打开会话后再试。");JSONObject m=record.getJSONObject("native");
                    if(m.optString("role").equals("ASSISTANT")){JSONObject ur=records.optJSONObject(Integer.toString(m.getInt("parent_id")));if(ur==null)throw new IOException("没有找到原问题。");m=ur.getJSONObject("native");}
                    prompt=text(m);parent=m.isNull("parent_id")?null:m.getInt("parent_id");files=ApiFiles.idsFromNative(m);
                }else if(path.endsWith("/edit_message")){
                    JSONObject old=records.optJSONObject(Integer.toString(request.optInt("message_id")));if(old!=null){JSONObject m=old.getJSONObject("native");parent=m.isNull("parent_id")?null:m.getInt("parent_id");}
                }else if(path.endsWith("/resume_stream"))throw new IOException("API 请求已中断，请使用原页面的重新生成按钮重试。");
                if(prompt.isEmpty()&&request.optJSONArray("ref_file_ids")==null)throw new IOException("请输入消息内容。");
                Object userContent=files!=null&&files.length()>0?ApiFiles.content(prompt,files):prompt;
                blocks=context(session,parent);blocks.put(ApiSupport.obj("role","user","content",userContent));
                int max=0;Iterator<String> names=records.keys();while(names.hasNext())max=Math.max(max,Integer.parseInt(names.next()));max=Math.max(max,parent==null?0:parent);userId=max+1;answerId=max+2;
                JSONArray uf=new JSONArray().put(ApiSupport.obj("type","REQUEST","id",1,"content",prompt));
                if(files!=null&&files.length()>0)uf.put(ApiSupport.obj("type","FILE","id",2,"files",ApiFiles.nativeFiles(files)));
                JSONObject user=nativeMessage(userId,parent,"USER","FINISHED",uf,request);JSONObject userRecord=ApiSupport.obj("native",user);if(userContent instanceof JSONArray)userRecord.put("api_content",userContent);records.put(Integer.toString(userId),userRecord);
                assistant=nativeMessage(answerId,userId,"ASSISTANT","WIP",fragments,request);session.put("owned",true);session.put("current_message_id",answerId);session.put("updated_at",System.currentTimeMillis()/1000.0);
                if(max==0){String clean=prompt.replace('\n',' ');title=clean.substring(0,Math.min(20,clean.length()));}
                records.put(Integer.toString(answerId),ApiSupport.obj("native",assistant));save(session);
                event("ready",ApiSupport.obj("request_message_id",userId,"response_message_id",answerId,"model_type","deepseek-flash","request_content",prompt));
                event(null,ApiSupport.obj("v",ApiSupport.obj("response",assistant)));
            }
        }
        public void run(){
            String failure=null;
            try{
                prepare();if(cancelled)return;JSONObject payload=ApiSupport.obj("model","deepseek-flash","max_tokens",16384,"thinking",ApiSupport.obj("type",request.optBoolean("thinking_enabled")?"enabled":"disabled"),"messages",blocks,"stream",true,"safety_check",ApiSupport.obj("level","high"),"title",ApiSupport.obj("enabled",true));
                if(request.optBoolean("search_enabled"))payload.put("tools",new JSONArray().put(ApiSupport.obj("type","deepseek_web_search","name","deepseek_web_search")).put(ApiSupport.obj("type","deepseek_read_url","name","deepseek_read_url")));
                connection=ApiSupport.connection("/anthropic/v1/messages","POST",ApiSupport.key());connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","text/event-stream");connection.setRequestProperty("anthropic-beta","files-api-2025-04-14");
                try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
                int code=connection.getResponseCode();if(code!=200)throw new IOException(ApiSupport.error(code));
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){String line;StringBuilder data=new StringBuilder();
                    while(!cancelled&&(line=reader.readLine())!=null){if(line.isEmpty()){if(data.length()>0){consume(new JSONObject(data.toString()));data.setLength(0);}if(complete)break;}else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}}
                }
                if(!complete&&!cancelled)throw new IOException("API 流式连接意外中断，请重新生成。");
            }catch(Exception e){if(!cancelled)failure=e.getMessage()==null?"API 请求失败，请检查网络后重试。":e.getMessage();}
            finally{
                try{
                    if(assistant!=null){if(refusal){fragments=new JSONArray().put(ApiSupport.obj("type","RESPONSE","id",1,"content","抱歉，当前问题无法回答。"));}
                        assistant.put("fragments",fragments);assistant.put("status",failure==null?(cancelled?"INCOMPLETE":"FINISHED"):"FAILED");
                        if(!cancelled){emitFragments(true);delta("response/status",assistant.getString("status"));}
                        JSONArray raw=new JSONArray();for(JSONObject b:apiBlocks.values())raw.put(b);
                        synchronized(storeLock){JSONObject rec=ApiSupport.obj("native",assistant);if(complete&&!refusal)rec.put("api_content",raw);session.getJSONObject("messages").put(Integer.toString(answerId),rec);if(title!=null&&!title.isEmpty())session.put("title",title);session.put("updated_at",System.currentTimeMillis()/1000.0);save(session);}
                        if(title!=null&&!cancelled)event("title",ApiSupport.obj("title",title));
                    }
                    if(failure!=null){event("toast",ApiSupport.obj("type","error","content",failure));ApiSupport.notice(failure);}
                    if(!cancelled)event("finish",new JSONObject());
                }catch(Exception ignored){}finally{HttpURLConnection c=connection;if(c!=null)c.disconnect();try{if(output!=null)output.close();}catch(IOException ignored){}running.remove(id,this);}
            }
        }
        void consume(JSONObject e) throws Exception {
            String type=e.optString("type");int index=e.optInt("index");
            if(type.equals("error"))throw new IOException("DeepSeek API 流式请求失败，请稍后重试。");
            if(type.equals("content_block_start")){
                JSONObject block=e.getJSONObject("content_block");apiBlocks.put(index,block);String bt=block.optString("type");JSONObject f=null;
                if(bt.equals("thinking"))f=ApiSupport.obj("type","THINK","id",fragments.length()+1,"content",block.optString("thinking"));
                else if(bt.equals("text"))f=ApiSupport.obj("type","RESPONSE","id",fragments.length()+1,"content",block.optString("text"));
                else if(bt.equals("server_tool_use")){f=ApiSupport.obj("type","SEARCH","id",fragments.length()+1,"status","WIP","content",block.optString("name").contains("read")?"正在阅读网页":"正在搜索网页");}
                else if(bt.equals("web_search_tool_result")){JSONArray results=searchResults(block.optJSONArray("content"));f=ApiSupport.obj("type","SEARCH","id",fragments.length()+1,"status","FINISHED","results",results,"content","搜索完成");finishTools();}
                else if(bt.equals("web_fetch_tool_result")){finishTools();}
                if(f!=null){fragmentIndexes.put(index,fragments.length());fragments.put(f);emitFragments(true);}
            }else if(type.equals("content_block_delta")){
                JSONObject block=apiBlocks.get(index);if(block==null)return;JSONObject delta=e.getJSONObject("delta");String dt=delta.optString("type");
                if(dt.equals("text_delta")||dt.equals("thinking_delta")){String field=dt.equals("text_delta")?"text":"thinking";String value=block.optString(field)+delta.optString(field);block.put(field,value);Integer fi=fragmentIndexes.get(index);if(fi!=null)fragments.getJSONObject(fi).put("content",value);emitFragments(false);}
                else if(dt.equals("signature_delta"))block.put("signature",block.optString("signature")+delta.optString("signature"));
                else if(dt.equals("input_json_delta"))inputs.computeIfAbsent(index,x->new StringBuilder()).append(delta.optString("partial_json"));
                else if(dt.equals("citations_delta")){JSONArray citations=block.optJSONArray("citations");if(citations==null){citations=new JSONArray();block.put("citations",citations);}if(delta.has("citation"))citations.put(delta.get("citation"));}
            }else if(type.equals("content_block_stop")){StringBuilder input=inputs.remove(index);if(input!=null&&input.length()>0)apiBlocks.get(index).put("input",new JSONObject(input.toString()));}
            else if(type.equals("message_delta")){JSONObject delta=e.optJSONObject("delta");if(delta!=null){if(delta.has("title")&&!delta.isNull("title"))title=delta.getString("title");refusal="refusal".equals(delta.optString("stop_reason"));}}
            else if(type.equals("message_stop")){complete=true;finishTools();}
        }
        void finishTools()throws Exception{for(int i=0;i<fragments.length();i++){JSONObject f=fragments.getJSONObject(i);if(f.optString("type").equals("SEARCH"))f.put("status","FINISHED");}}
        JSONArray searchResults(JSONArray content)throws Exception{JSONArray results=new JSONArray();if(content==null)return results;for(int i=0;i<content.length();i++){JSONObject r=content.optJSONObject(i);if(r!=null&&r.optString("type").equals("web_search_result")){JSONObject n=ApiSupport.obj("url",r.optString("url"),"title",r.optString("title"),"snippet",r.optString("snippet"),"cite_index",results.length()+1);if(r.has("site_name"))n.put("site_name",r.get("site_name"));if(r.has("site_icon"))n.put("site_icon",r.get("site_icon"));results.put(n);}}return results;}
    }
}
