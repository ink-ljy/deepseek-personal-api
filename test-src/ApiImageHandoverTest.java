package dev.local.deepseekapitest;
import android.content.ContentValues;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** System MediaStore content URI + original attachment coordinator + existing cloud-history branch. */
public final class ApiImageHandoverTest {
    static final List<Connection> requests=new CopyOnWriteArrayList<>();static int fileCode=200;
    static class Connection extends HttpURLConnection {
        ByteArrayOutputStream body=new ByteArrayOutputStream();Connection(URL u){super(u);requests.add(this);}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}public OutputStream getOutputStream(){return body;}public int getResponseCode(){return url.getPath().endsWith("/files")?fileCode:200;}
        public InputStream getInputStream()throws IOException{String data;
            if(url.getPath().endsWith("/files"))data="{\"id\":\"file-api-handover-fixture-"+requests.size()+"\",\"type\":\"file\",\"size_bytes\":100,\"filename\":\"fixture.png\"}";
            else data="data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":100,\"cache_read_input_tokens\":400,\"output_tokens\":0}}}\n\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"Image received.\"}}\n\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":10}}\n\ndata: {\"type\":\"message_stop\"}\n\n";
            return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));}
    }
    static Object host(ApiSelfTest t)throws Exception{Class<?> type=Class.forName("sun.misc.Unsafe");Field field=type.getDeclaredField("theUnsafe");field.setAccessible(true);return type.getMethod("allocateInstance",Class.class).invoke(field.get(null),t.c("p82"));}
    static Object model(ApiSelfTest t,Uri uri,String key)throws Exception{Object m=t.make("y12","fixture.png",100L,2,"old-cloud-session",null,null,null,"fixture-image-"+System.nanoTime());t.call(m,"l",t.make("u12",0f),key);return m;}
    static Object continuation(ApiSelfTest t,Object host,Object model,String key,CountDownLatch done,Object[] result)throws Exception{
        Object end=t.proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){result[0]=a[0];done.countDown();return null;}if(m.getName().equals("t"))return t.field(t.c("x94"),"a");return null;});
        Object coroutine=t.make("lv",model,null,host,"old-cloud-session",key,end,7);Field state=coroutine.getClass().getField("f");state.setInt(coroutine,1);return coroutine;
    }
    public static void run(ApiSelfTest t)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
        t.call(t.c("ApiSupport"),"saveKey","local-handover-fixture-only");Uri uri=null;
        try{
            ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"handover-fixture-"+System.nanoTime()+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PersonalApiTest");
            uri=t.getTargetContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);Bitmap bitmap=Bitmap.createBitmap(30,20,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN);try(OutputStream out=t.getTargetContext().getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            t.check(uri!=null&&uri.getScheme().equals("content"),"system MediaStore image is supplied as a content URI");
            String sid="old-cloud-handover-"+System.nanoTime(),key="old-native-model";JSONObject session=ApiContextTest.seed(t,sid,1);session.put("owned",false);String oldMessages=session.getJSONObject("messages").toString();
            Object host=host(t),model=model(t,uri,key),file=t.make("ui4","fixture.png",uri,100L,2);CountDownLatch done=new CountDownLatch(1);Object[] result={null};
            Object suspended=t.call(host,"y",file,model,key,continuation(t,host,model,key,done,result));
            t.check(suspended==t.field(t.c("qd3"),"a"),"original image coordinator enters API route without old service dependencies");
            t.check(done.await(15,TimeUnit.SECONDS)&&result[0]==t.field(t.c("qjb"),"a"),"original image coroutine resumes after public API upload");
            Object attachment=t.call(model,"j",key);t.check(attachment!=null&&t.field(attachment,"a").toString().startsWith("file-api-"),"old native model key receives the new public API image ID");
            t.check(t.field(attachment,"b").toString().equals("SUCCESS")&&((Boolean)t.field(attachment,"k")),"original image chip reaches native SUCCESS with image metadata");
            t.check(!session.optBoolean("owned")&&oldMessages.equals(session.getJSONObject("messages").toString()),"image can upload before the old cloud conversation first enters API mode");
            String multipart=requests.get(0).body.toString("UTF-8");t.check(multipart.contains("name=\"purpose\"")&&multipart.indexOf("user_data")<multipart.indexOf("name=\"file\""),"image upload explicitly sends user_data before file bytes");
            String id=(String)t.field(attachment,"a");JSONObject req=t.json("chat_session_id",sid,"parent_message_id",2,"prompt","Read the image and continue our old chat.","ref_file_ids",new JSONArray().put(id));
            ApiStreamTest.Client chat=new ApiStreamTest.Client(t,"completion",req);chat.finish();chat.close();JSONObject payload=new JSONObject(requests.get(1).body.toString("UTF-8"));JSONArray messages=payload.getJSONArray("messages");
            t.check(messages.length()==3&&messages.getJSONObject(0).toString().equals(((JSONArray)t.call(t.c("ApiBridge"),"context",session,2)).getJSONObject(0).toString()),"first API request retains old cloud-history branch without opening another chat");
            t.check(messages.getJSONObject(2).getJSONArray("content").getJSONObject(1).getJSONObject("source").getString("file_id").equals(id),"new image is included in the same existing-session API request");
            JSONObject next=t.json("chat_session_id",sid,"parent_message_id",session.getInt("current_message_id"),"prompt","Continue.","ref_file_ids",new JSONArray());ApiStreamTest.Client second=new ApiStreamTest.Client(t,"completion",next);second.finish();second.close();JSONArray secondMessages=new JSONObject(requests.get(2).body.toString("UTF-8")).getJSONArray("messages");
            boolean stable=true;for(int i=0;i<messages.length();i++)stable&=messages.getJSONObject(i).toString().equals(secondMessages.getJSONObject(i).toString());t.check(stable&&secondMessages.length()==5,"second API turn preserves the first request prefix including image reference");
            fileCode=413;Object failedModel=model(t,uri,key);CountDownLatch failed=new CountDownLatch(1);Object[] failedResult={null};t.call(host,"y",file,failedModel,key,continuation(t,host,failedModel,key,failed,failedResult));
            t.check(failed.await(15,TimeUnit.SECONDS)&&t.call(failedModel,"g",key).getClass().getName().equals("p12"),"upload failure leaves zero-progress state and becomes original native error state");
            String status=(String)t.call(t.call(t.c("ApiSupport"),"prefs"),"getString","last_upload_status","");t.check(!status.isEmpty()&&!status.contains("fixture-only")&&!status.contains(uri.toString()),"upload-stage diagnostic contains no key or photo URI");
        }finally{if(uri!=null)t.getTargetContext().getContentResolver().delete(uri,null,null);Object editor=t.call(t.call(t.c("ApiSupport"),"prefs"),"edit");t.call(editor,"clear");t.call(editor,"commit");}
    }
}
