package dev.local.deepseekapitest;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Deterministic transport fixtures; never packaged with production code. No live API key needed. */
public final class ApiMediaTest {
    static final List<FakeConnection> requests=new CopyOnWriteArrayList<>();
    static int failCode;static volatile boolean slowSpeech;static CountDownLatch speechReading;
    public static final class NativeStop {public String c;public int d;NativeStop(String sid,int mid){c=sid;d=mid;}}
    static class FakeConnection extends HttpURLConnection {
        ByteArrayOutputStream body=new ByteArrayOutputStream();volatile boolean closed;
        FakeConnection(URL url){super(url);requests.add(this);}
        public void connect(){}public void disconnect(){closed=true;}public boolean usingProxy(){return false;}
        String urlPath(){return url.getPath();}
        public OutputStream getOutputStream(){return body;}
        public int getResponseCode(){return failCode==0?200:failCode;}
        public String getContentType(){return url.getPath().endsWith("/tts")?"audio/pcm":"application/json";}
        public long getContentLengthLong(){return url.getPath().endsWith("/tts")?4800: -1;}
        public InputStream getInputStream()throws IOException{
            if(failCode!=0)throw new IOException("fixture HTTP failure");
            if(url.getPath().endsWith("/tts")){
                if(slowSpeech)return new InputStream(){public int read()throws IOException{speechReading.countDown();long deadline=System.currentTimeMillis()+5000;while(!closed&&System.currentTimeMillis()<deadline){try{Thread.sleep(10);}catch(InterruptedException e){throw new IOException(e);}}throw new IOException("fixture stream disconnected");}};
                return new ByteArrayInputStream(new byte[4800]);
            }
            if(url.getPath().equals("/anthropic/v1/files"))return new ByteArrayInputStream(("{\"id\":\"file-api-fixture-"+requests.size()+"\",\"type\":\"file\",\"filename\":\"fixture\"}").getBytes(StandardCharsets.UTF_8));
            throw new IOException("Unexpected endpoint "+url.getPath());
        }
    }
    static void run(ApiSelfTest t)throws Exception {
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new FakeConnection(u);}}:null);
        t.call(t.c("ApiSupport"),"saveKey","local-fixture-not-a-real-api-key");
        try{
            // Exercise actual ny1 hook, image progress and original AppChatFile serializer.
            File image=new File(t.getTargetContext().getFilesDir(),"gallery-no-extension");Bitmap bmp=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.BLUE);try(OutputStream out=new FileOutputStream(image)){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();
            List<Float> progress=new CopyOnWriteArrayList<>();Object cb=t.proxy("xy4",(p,m,a)->{if(m.getName().equals("j"))progress.add((Float)a[0]);return t.field(t.c("qjb"),"a");});
            CountDownLatch uploaded=new CountDownLatch(1);Object[] uploadResult={null};
            Object continuation=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){uploadResult[0]=a[0];uploaded.countDown();}return m.getName().equals("t")?t.field(t.c("x94"),"a"):null;});
            Object nativeFile=t.make("ui4",image.getName(),Uri.fromFile(image),image.length(),2);
            // A minimal ny1 object with null unused dependencies verifies the production smali entry.
            Object uploader=t.make("ny1",t.field(t.c("qz6"),"a"),null,null,null);
            Object suspend=t.call(uploader,"a",nativeFile,false,"fixture",cb,continuation);
            t.check(suspend==t.field(t.c("qd3"),"a"),"real ny1 upload hook suspends original coroutine");
            t.check(uploaded.await(20,TimeUnit.SECONDS),"image upload completes original continuation");
            t.check(uploadResult[0].getClass().getName().equals("yv7"),"image upload returns original native success");
            t.check(progress.size()>=3&&progress.get(0)==0f&&progress.get(progress.size()-1)==1f,"image progress moves from 0 to 100 percent");
            Object attachment=t.field(uploadResult[0],"b");String id=(String)t.field(attachment,"a");
            t.check((Boolean)t.field(attachment,"k"),"extensionless gallery image classified as image");
            t.check(((Number)t.field(attachment,"m")).intValue()==40&&((Number)t.field(attachment,"n")).intValue()==30,"native image dimensions are preserved");
            t.check(new File(Uri.parse((String)t.field(attachment,"j")).getPath()).isFile(),"native image preview survives original URI grant");
            String multipart=requests.get(0).body.toString("UTF-8");t.check(!multipart.contains("name=\"purpose\""),"image multipart omits document parsing purpose");
            t.check(multipart.contains("Content-Type: image/png"),"multipart sends actual PNG MIME");
            t.check("files-api-2025-04-14".equals(requests.get(0).getRequestProperty("anthropic-beta")),"Files API beta header is present");
            JSONArray content=(JSONArray)t.call(t.c("ApiFiles"),"content","Look",new JSONArray().put(id));t.check(content.getJSONObject(1).getString("type").equals("image")&&content.getJSONObject(1).getJSONObject("source").getString("file_id").equals(id),"chat sends image source file_id to Messages API");
            // A BMP must become supported PNG rather than being sent as unsupported BMP.
            File bmpFile=new File(t.getTargetContext().getFilesDir(),"bitmap.bmp");byte[] header=new byte[58];header[0]=66;header[1]=77;header[2]=58;header[10]=54;header[14]=40;header[18]=1;header[22]=1;header[26]=1;header[28]=24;header[34]=4;header[54]=(byte)255;try(OutputStream out=new FileOutputStream(bmpFile)){out.write(header);}
            Object prepared=t.call(t.c("ApiFiles"),"prepare",t.make("ui4",bmpFile.getName(),Uri.fromFile(bmpFile),bmpFile.length(),2));
            java.lang.reflect.Field mime=prepared.getClass().getDeclaredField("mime");mime.setAccessible(true);t.check("image/png".equals(mime.get(prepared)),"unsupported BMP converted to public API PNG");
            // Native it1 hook returns original Flow and PCM audio events.
            String sid="media-fixture-session";JSONObject session=(JSONObject)t.call(t.c("ApiBridge"),"session",sid);session.put("owned",true);
            JSONObject msg=t.json("message_id",2,"role","ASSISTANT","status","FINISHED","fragments",new JSONArray().put(t.json("type","THINK","content","DO NOT READ THIS")).put(t.json("type","RESPONSE","content","你好，这是朗读验证。")));
            session.getJSONObject("messages").put("2",t.json("native",msg));
            Object request=t.make("qcb",sid,2,"manual","opus");Object pcmRequest=t.call(t.c("ApiSpeech"),"request",request);t.check(t.field(pcmRequest,"d").equals("pcm"),"API TTS forces original 24 kHz PCM playback format");
            Object flow=t.call(t.make("it1",new Object[]{null}),"j",pcmRequest,"pcm",null,20000L,continuation);t.check(t.c("ul4").isInstance(flow),"real it1 TTS hook returns original Kotlin Flow");
            CountDownLatch finished=new CountDownLatch(1);Object[] finish={null};List<Object> events=new CopyOnWriteArrayList<>();
            Object collector=t.proxy("vl4",(p,m,a)->{if(m.getName().equals("a"))events.add(a[0]);return t.field(t.c("qjb"),"a");});
            Object end=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){finish[0]=a[0];finished.countDown();}return m.getName().equals("t")?t.field(t.c("x94"),"a"):null;});
            t.call(flow,"b",collector,end);t.check(finished.await(20,TimeUnit.SECONDS),"TTS flow completes native coroutine");
            t.check(finish[0]==t.field(t.c("qjb"),"a"),"TTS completes successfully");
            t.check(events.get(0).getClass().getName().equals("ubb")&&events.get(events.size()-1).getClass().getName().equals("rbb"),"original ready and finish audio events emitted");
            int bytes=0,sequence=0;for(Object e:events)if(e.getClass().getName().equals("nbb")){t.check(t.field(e,"b").equals("pcm"),"audio event uses original PCM format");byte[] packet=(byte[])t.field(e,"a");t.check(java.nio.ByteBuffer.wrap(packet).getInt()==sequence++,"audio packet carries original big-endian sequence header");bytes+=packet.length-4;}t.check(bytes==4800,"native audio pipeline receives all PCM bytes");
            FakeConnection speech=requests.get(requests.size()-1);JSONObject payload=new JSONObject(speech.body.toString("UTF-8"));t.check(speech.urlPath().equals("/v1/tools/tts"),"speech uses official public TTS endpoint");
            t.check(payload.getString("text").equals("你好，这是朗读验证。")&&!payload.toString().contains("DO NOT READ THIS"),"only answer body is synthesized, thinking excluded");
            // Preserve native failure semantics and prevent success after truncated PCM.
            failCode=402;CountDownLatch failed=new CountDownLatch(1);Object[] error={null};
            Object failureEnd=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){error[0]=a[0];failed.countDown();}return m.getName().equals("t")?t.field(t.c("x94"),"a"):null;});
            t.call(flow,"b",collector,failureEnd);t.check(failed.await(10,TimeUnit.SECONDS)&&error[0].getClass().getName().equals("cb9"),"HTTP 402 resumes native flow with failure");failCode=0;
            boolean truncated=false;try{t.call(t.c("ApiSpeech"),"pump",new ByteArrayInputStream(new byte[3]),3L,collector,end);}catch(IOException e){truncated=true;}t.check(truncated,"truncated odd-byte PCM is rejected");
            Object job=t.proxy("d26",(p,m,a)->m.getName().equals("e")?false:null);
            Object context=t.proxy("hd3",(p,m,a)->m.getName().equals("W")?job:null);
            CountDownLatch cancelled=new CountDownLatch(1);Object[] cancel={null};int before=requests.size();
            Object stop=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){cancel[0]=a[0];cancelled.countDown();}return m.getName().equals("t")?context:null;});
            t.call(flow,"b",collector,stop);t.check(cancelled.await(10,TimeUnit.SECONDS)&&cancel[0].getClass().getName().equals("cb9")&&requests.size()==before,"cancelled native playback does not start a billed API request");
            // Original ddb stop is a channel message, not always a parent coroutine cancellation.
            slowSpeech=true;speechReading=new CountDownLatch(1);CountDownLatch stopped=new CountDownLatch(1);Object[] stoppedResult={null};
            Object stoppedEnd=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){stoppedResult[0]=a[0];stopped.countDown();}return m.getName().equals("t")?t.field(t.c("x94"),"a"):null;});
            t.call(flow,"b",collector,stoppedEnd);t.check(speechReading.await(5,TimeUnit.SECONDS),"TTS stop fixture reaches active HTTP audio stream");
            FakeConnection liveSpeech=requests.get(requests.size()-1);t.call(t.c("ApiSpeech"),"stop",new NativeStop(sid,2));
            t.check(stopped.await(5,TimeUnit.SECONDS)&&liveSpeech.closed&&stoppedResult[0].getClass().getName().equals("cb9"),"native session stop disconnects active TTS HTTP stream");slowSpeech=false;
            // Upload failure returns a native error, never 100% success.
            failCode=413;CountDownLatch badUpload=new CountDownLatch(1);Object[] badResult={null};progress.clear();
            Object badEnd=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){badResult[0]=a[0];badUpload.countDown();}return null;});
            t.call(uploader,"a",nativeFile,false,"fixture",cb,badEnd);t.check(badUpload.await(10,TimeUnit.SECONDS)&&badResult[0].getClass().getName().equals("bw7")&&!progress.contains(1f),"upload failure resumes original error state without false completion");failCode=0;
            // Disabled API mode must leave all original handlers available.
            Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"putBoolean","enabled",false);t.call(editor,"commit");
            t.check(t.call(t.c("ApiSpeech"),"flow",request)==null,"API disabled preserves original voice route");
        }finally{Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"clear");t.call(editor,"commit");}
    }
}
