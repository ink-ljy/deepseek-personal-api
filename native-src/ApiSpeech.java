import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.*;
import org.json.*;

/** Public TTS PCM -> original Flow<TtsEvent> -> original playback UI/audio engine. */
public final class ApiSpeech {
    static final ConcurrentHashMap<String,Run> running=new ConcurrentHashMap<>();
    static final java.util.Map<Object,Boolean> stopped=java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    static String id(Object request)throws Exception{return ApiSupport.call(request,"a")+":"+ApiSupport.call(request,"b");}
    static final class Run implements Runnable {
        final String id;final Object request,collector,continuation;volatile HttpURLConnection connection;
        Run(String id,Object request,Object collector,Object continuation){this.id=id;this.request=request;this.collector=collector;this.continuation=continuation;}
        void stop(){stopped.put(continuation,true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        public void run(){Object result;
            try{stream(request,collector,continuation,this);result=ApiSupport.unit();}
            catch(Exception e){try{result=ApiSupport.make("cb9",e);}catch(Exception fatal){return;}}
            finally{running.remove(id,this);stopped.remove(continuation);}
            try{ApiSupport.call(continuation,"h",result);}catch(Exception ignored){}
        }
    }
    /** Native stop uses a control channel, so it must also cancel this HTTP transport explicitly. */
    public static void stop(Object nativeSession)throws Exception {
        String id=ApiSupport.field(nativeSession,"c")+":"+ApiSupport.field(nativeSession,"d");Run run=running.get(id);if(run!=null)run.stop();
    }
    static boolean owns(Object request)throws Exception {
        return ApiSupport.enabled()&&ApiBridge.session((String)ApiSupport.call(request,"a")).optBoolean("owned");
    }
    public static Object request(Object request)throws Exception {
        if(!owns(request))return request;
        return ApiSupport.make("qcb",ApiSupport.call(request,"a"),ApiSupport.call(request,"b"),ApiSupport.field(request,"c"),"pcm");
    }
    public static Object flow(Object request)throws Exception {
        if(!owns(request))return null;
        Class<?> type=ApiSupport.cls("ul4");
        return Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(!m.getName().equals("b"))return ApiUi.proxyObject(p,m,a);
            Object collector=a[0],continuation=a[1];
            Run run=new Run(id(request),request,collector,continuation);Run old=running.put(run.id,run);if(old!=null)old.stop();new Thread(run,"PersonalApiTts").start();
            return ApiSupport.field(ApiSupport.cls("qd3"),"a");
        });
    }
    static boolean active(Object continuation)throws Exception {
        if(stopped.containsKey(continuation))return false;
        Object context=ApiSupport.call(continuation,"t");
        Object job=ApiSupport.call(context,"W",ApiSupport.field(ApiSupport.cls("mac"),"s"));
        return job==null||(Boolean)ApiSupport.call(job,"e");
    }
    static void checkActive(Object continuation)throws Exception {
        if(!active(continuation))throw new CancellationException("朗读已停止");
    }
    static void emit(Object collector,Object event,Object parent)throws Exception {
        checkActive(parent);CountDownLatch latch=new CountDownLatch(1);Object[] resumed={null};
        Class<?> type=ApiSupport.cls("nc3");Object callback=Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(m.getName().equals("t"))return ApiSupport.call(parent,"t");
            if(m.getName().equals("h")){resumed[0]=a[0];latch.countDown();return null;}
            return ApiUi.proxyObject(p,m,a);
        });
        Object value=ApiSupport.call(collector,"a",event,callback);
        if(value==ApiSupport.field(ApiSupport.cls("qd3"),"a")){
            while(!latch.await(100,TimeUnit.MILLISECONDS))checkActive(parent);
            if(resumed[0]!=null&&resumed[0].getClass().getName().equals("cb9"))throw (Exception)ApiSupport.field(resumed[0],"a");
        }
    }
    static String text(Object request,Object continuation)throws Exception {
        String sid=(String)ApiSupport.call(request,"a");int id=((Number)ApiSupport.call(request,"b")).intValue();
        long deadline=System.currentTimeMillis()+120000;
        while(true){
            checkActive(continuation);
            JSONObject s=ApiBridge.session(sid);JSONArray messages=ApiBridge.history(s).getJSONObject("data").getJSONObject("biz_data").getJSONArray("chat_messages");
            for(int i=0;i<messages.length();i++){
                JSONObject msg=messages.getJSONObject(i);if(msg.optInt("message_id")!=id)continue;
                if(!"WIP".equals(msg.optString("status"))){String text=ApiBridge.text(msg);if(text.trim().isEmpty())throw new IOException("该回答没有可朗读的正文。");return text;}
            }
            if(!"auto".equals(ApiSupport.field(request,"c"))||System.currentTimeMillis()>deadline)throw new IOException("回答尚未完成，请生成完成后再点朗读。");
            Thread.sleep(100);
        }
    }
    static JSONObject payload(String text)throws Exception {
        long count=0;for(int n=0;n<text.length();){int cp=text.codePointAt(n);count+=Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN?2:1;n+=Character.charCount(cp);}
        if(count>10000)throw new IOException("这条回答超过语音接口的 10000 计费字符限制。");
        String voice=ApiSupport.prefs().getString("tts_voice","stella");
        return ApiSupport.obj("text",text,"voice_id",voice.equals("tide")?"tide":"stella","format","pcm","filter_markdown",true);
    }
    static void stream(Object request,Object collector,Object continuation,Run run)throws Exception {
        byte[] body=payload(text(request,continuation)).toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=ApiSupport.connection("/v1/tools/tts","POST",ApiSupport.key());
        run.connection=connection;checkActive(continuation);
        ScheduledExecutorService watcher=Executors.newSingleThreadScheduledExecutor();
        watcher.scheduleAtFixedRate(()->{try{if(!active(continuation))connection.disconnect();}catch(Exception e){connection.disconnect();}},100,100,TimeUnit.MILLISECONDS);
        try{
            connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","audio/pcm");
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int code=connection.getResponseCode();if(code!=200)throw new IOException(ApiSupport.error(code));
            String type=connection.getContentType();if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("json"))throw new IOException("语音接口未返回音频，请稍后重试。");
            emit(collector,ApiSupport.make("ubb",1,"personal-api-tts"),continuation);
            try(InputStream input=connection.getInputStream()){pump(input,connection.getContentLengthLong(),collector,continuation);}
            emit(collector,ApiSupport.make("rbb",1,ApiSupport.make("mbb",0)),continuation);
        }finally{watcher.shutdownNow();connection.disconnect();}
    }
    static void pump(InputStream input,long expected,Object collector,Object continuation)throws Exception {
        byte[] buffer=new byte[8192];int pending=-1,n,sequence=0;long total=0;
        while((n=input.read(buffer,pending>=0?1:0,pending>=0?8191:8192))!=-1){
            checkActive(continuation);total+=n;if(pending>=0){buffer[0]=(byte)pending;n++;pending=-1;}
            if((n&1)!=0){pending=buffer[n-1]&255;n--;}
            if(n>0){
                // Original TTSStreamPlayer expects a 4-byte big-endian sequence before PCM.
                byte[] packet=new byte[n+4];packet[0]=(byte)(sequence>>>24);packet[1]=(byte)(sequence>>>16);packet[2]=(byte)(sequence>>>8);packet[3]=(byte)sequence;sequence++;
                System.arraycopy(buffer,0,packet,4,n);emit(collector,ApiSupport.make("nbb","pcm",packet),continuation);
            }
        }
        if(total==0||pending>=0||(expected>=0&&total!=expected))throw new IOException("语音音频不完整，请重新朗读。");
    }
}
