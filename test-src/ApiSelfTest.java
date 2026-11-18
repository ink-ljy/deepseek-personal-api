package dev.local.deepseekapitest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Test-only instrumentation. This class is never included in the delivered APK. */
public final class ApiSelfTest extends Instrumentation {
    Bundle args; ClassLoader loader; int checks;JSONArray testAttachments=new JSONArray();
    public void onCreate(Bundle arguments){args=arguments;start();}
    Class<?> c(String name)throws Exception{return loader.loadClass(name);}
    Object field(Object o,String name)throws Exception{Class<?> type=o instanceof Class?(Class<?>)o:o.getClass();Field f;try{f=type.getField(name);}catch(NoSuchFieldException e){f=type.getDeclaredField(name);}f.setAccessible(true);return f.get(o instanceof Class?null:o);}
    Object call(Object o,String name,Object...values)throws Exception{
        Class<?> type=o instanceof Class?(Class<?>)o:o.getClass();List<Method> methods=new ArrayList<>(Arrays.asList(type.getMethods()));methods.addAll(Arrays.asList(type.getDeclaredMethods()));
        for(Method m:methods)if(m.getName().equals(name)&&m.getParameterTypes().length==values.length){try{m.setAccessible(true);return m.invoke(o instanceof Class?null:o,values);}catch(IllegalArgumentException ignored){}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw(Exception)t;throw e;}}
        throw new NoSuchMethodException(name);
    }
    Object make(String name,Object...values)throws Exception{for(Constructor<?> x:c(name).getDeclaredConstructors())if(x.getParameterTypes().length==values.length){try{x.setAccessible(true);return x.newInstance(values);}catch(IllegalArgumentException ignored){}}throw new NoSuchMethodException(name);}
    void check(boolean condition,String name){if(!condition)throw new AssertionError(name);checks++;Bundle status=new Bundle();status.putString("stream","PASS "+name+"\n");sendStatus(0,status);}
    String read(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return new String(b.toByteArray(),StandardCharsets.UTF_8);}
    Object req(JSONObject json)throws Exception{return request("https://chat.deepseek.com/api/v0/chat/completion","POST",json);}
    Object request(String address,String method,JSONObject json)throws Exception{
        Object builder=make("ck5");call(builder,"d",null,address);Object url=call(builder,"a");byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);
        return make("w89",url,method,make("ia5",(Object)new String[]{"Content-Type","application/json"}),method.equals("GET")?null:make("x89",null,bytes.length,bytes),new HashMap<>());
    }
    JSONObject json(Object...xs)throws Exception{JSONObject j=new JSONObject();for(int i=0;i<xs.length;i+=2)j.put((String)xs[i],xs[i+1]==null?JSONObject.NULL:xs[i+1]);return j;}
    Object proxy(String type,InvocationHandler h)throws Exception{return java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{c(type)},h);}
    Object compose(int stage)throws Exception{
        Object callback=proxy("lz4",(p,m,a)->{
            if(!m.getName().equals("q"))return m.getName().equals("hashCode")?System.identityHashCode(p):m.getName().equals("equals")?p==a[0]:"NativeSettingsTest";
            Object composer=a[0];
            if(stage==0)call(c("oe6"),"a",null,compose(1),composer,48);
            else if(stage==1)call(c("f73"),"a",6,compose(2),composer);
            else if(stage==2)call(c("xv"),"a",6,compose(3),composer);
            else if(stage==3)call(c("g1b"),"a",false,null,compose(4),composer,384,3);
            else{
                Object noop=proxy("vy4",(p2,m2,a2)->field(c("qjb"),"a"));
                Object click=proxy("xy4",(p2,m2,a2)->field(c("qjb"),"a"));
                Object padding=proxy("m98",(p2,m2,a2)->0f);
                Object modifier=call(c("s9a"),"c",field(c("bm7"),"a"),1f);
                if(args.getString("mode","").equals("footer")){call(c("u48"),"x",field(field(c("gz5"),"c"),"b"),modifier,composer,0);return field(c("qjb"),"a");}
                if(args.getString("mode","").equals("contextui")){if(stage==4){Object reporter=make("by1",make("ev",null,0));Object provided=call(field(c("cy1"),"a"),"a",reporter);call(c("z7c"),"f",provided,compose(5),composer,0);}else call(c("fc1"),"e",null,null,null,null,noop,noop,null,composer,0,126);return field(c("qjb"),"a");}
                Object[] values=new Object[22];values[0]=click;values[1]=false;for(int i=2;i<=11;i++)values[i]=noop;
                values[12]=false;values[13]=noop;values[14]=proxy("yob",(p2,m2,a2)->"");values[15]=false;values[16]=false;values[17]=padding;values[18]=modifier;values[19]=composer;values[20]=0;values[21]=0;
                call(c("sz6"),"l",values);
            }
            return field(c("qjb"),"a");
        });
        return make("f43",callback,true,1837541827+stage);
    }
    String nativeChat(boolean thinking,boolean search,String sessionId,String prompt,Integer parent)throws Exception{
        JSONObject request=json("chat_session_id",sessionId,"parent_message_id",parent,"prompt",prompt,"thinking_enabled",thinking,"search_enabled",search,"ref_file_ids",testAttachments,"preempt",false,"shumei_device_id","test");
        Object response=call(c("ApiBridge"),"intercept",req(request));check(response!=null,"native HTTP adapter returns original response type");check(response.getClass().getName().equals("wa9"),"response class is original OkHttp Response");
        Object source=call(field(response,"g"),"d0");InputStream input=(InputStream)call(source,"g0");BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        Object codec=field(c("o56"),"a"),deltaSerializer=field(c("du3"),"a"),state=make("oqc",12);boolean first=true,finished=false,sawThinking=false;int deltas=0;String event=null,line;StringBuilder data=new StringBuilder();
        while((line=reader.readLine())!=null){
            if(line.startsWith("event:")){event=line.substring(6).trim();continue;}if(line.startsWith("data:")){data.append(line.substring(5).trim());continue;}if(!line.isEmpty()||data.length()==0)continue;
            JSONObject packet=new JSONObject(data.toString());
            if(event==null){Object parsed=call(codec,"b",deltaSerializer,packet.toString());if(first){Object root=call(state,"Q",field(parsed,"c"));check(root!=null,"initial delta parsed by original native message serializer");first=false;}else{boolean applied=(Boolean)call(state,"F",parsed,null);check(applied,"native delta applied: "+packet.optString("p"));}deltas++;}
            else if(event.equals("ready")){call(codec,"b",field(c("fv1"),"a"),packet.toString());check(packet.has("response_message_id"),"original ready event parsed");}
            else if(event.equals("toast")){throw new IOException("Bridge toast: "+packet.optString("content"));}
            else if(event.equals("finish")){finished=true;}
            data.setLength(0);event=null;
        }
        input.close();check(finished,"stream reached native finish event");check(deltas>1,"stream produced multiple native updates");
        Object root=field(state,"c"),message=field(root,"a");String answer=(String)call(message,"j");check(!answer.isEmpty(),"original message model contains assistant text");
        check("FINISHED".equals(call(message,"F")),"original message status is FINISHED");
        Object snapshot=call(c("ApiBridge"),"session",sessionId);JSONObject hist=(JSONObject)call(c("ApiBridge"),"history",snapshot);JSONObject hd=hist.getJSONObject("data").getJSONObject("biz_data");
        call(codec,"b",field(c("iz1"),"a"),hd.toString());check(true,"API history parses with original native history serializer");
        JSONObject last=hd.getJSONArray("chat_messages").getJSONObject(hd.getJSONArray("chat_messages").length()-1);JSONArray fs=last.getJSONArray("fragments");for(int i=0;i<fs.length();i++)if(fs.getJSONObject(i).optString("type").equals("THINK"))sawThinking=true;
        if(thinking)check(sawThinking,"deep thinking converted to original THINK fragment");
        if(search){boolean results=false;for(int i=0;i<fs.length();i++)if(fs.getJSONObject(i).optString("type").equals("SEARCH")&&fs.getJSONObject(i).optJSONArray("results")!=null)results=true;check(results,"search converted to original SEARCH results");}
        Bundle status=new Bundle();status.putString("stream","ANSWER "+answer.substring(0,Math.min(answer.length(),300))+"\n");sendStatus(0,status);return answer;
    }
    public void onStart(){Bundle result=new Bundle();try{
        loader=getTargetContext().getClassLoader();call(c("ApiSupport"),"init",getTargetContext());
        String mode=args.getString("mode","protocol");
        if(!mode.equals("local")&&!mode.equals("features")){Object ed=call(call(c("ApiSupport"),"prefs"),"edit");call(ed,"putBoolean","memory_auto",false);call(ed,"putBoolean","memory_enabled",false);call(ed,"putString","persona","");call(ed,"commit");}
        if(mode.equals("media")){ApiMediaTest.run(this);}
        else if(mode.equals("features")){ApiFeaturesTest.run(this);}
        else if(mode.equals("local")){ApiLocalTest.run(this);}
        else if(mode.equals("stream")){ApiStreamTest.run(this);}
        else if(mode.equals("context")){ApiContextTest.run(this);}
        else if(mode.equals("key")){ApiKeyTest.run(this);}
        else if(mode.equals("handover")){ApiImageHandoverTest.run(this);}
        else if(mode.equals("quota")||mode.equals("settings")||mode.equals("about")||mode.equals("footer")||mode.equals("contextui")||mode.equals("configui")){
            if(mode.equals("contextui")){ApiContextTest.transport();ApiContextTest.next=ApiContextTest.summary(this,"User goals, confirmed facts, exact numbers and remaining work are preserved in this test summary.","end_turn");call(c("ApiSupport"),"saveKey","local-ui-fixture-no-network");JSONObject seed=ApiContextTest.seed(this,"context-ui-"+System.nanoTime(),3);seed.getJSONObject("messages").getJSONObject("6").put("api_usage",json("input_tokens",900,"cache_read_input_tokens",5100,"output_tokens",1200));}
            Intent launch=getTargetContext().getPackageManager().getLaunchIntentForPackage("com.deepseek.chat");launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity activity=startActivitySync(launch);call(c("ApiSupport"),"init",activity);
            runOnMainSync(()->{try{if(mode.equals("configui"))call(c("ApiSettings"),"open");else if(mode.equals("quota"))call(c("ApiUi"),"open",activity);else if(mode.equals("about"))call(c("ApiUi"),"openAbout",activity);else{android.view.View view=(android.view.View)make("androidx.compose.ui.platform.ComposeView",activity);call(view,"setContent",compose(0));activity.setContentView(view);}}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(1000);check(true,"native "+mode+" screen opened");
            if(args.getString("hold","false").equals("true"))Thread.sleep(Long.parseLong(args.getString("hold_ms","45000")));
            if(mode.equals("contextui")){Object editor=call(call(c("ApiSupport"),"prefs"),"edit");call(editor,"remove","key");call(editor,"putBoolean","enabled",false);call(editor,"commit");}
        }else{
            String key=read(new FileInputStream("/data/local/tmp/deepseek-personal-api-test.key")).trim();call(c("ApiSupport"),"saveKey",key);check((Boolean)call(c("ApiSupport"),"enabled"),"API key stored and mode enabled");
            JSONObject balance=(JSONObject)call(c("ApiSupport"),"get","/user/balance",key);check(balance.getBoolean("is_available"),"native balance query succeeded");
            check(!(String.valueOf(call(call(c("ApiSupport"),"prefs"),"getAll"))).contains(key),"preferences contain encrypted key only");
            if(mode.equals("errors")){
                try{call(c("ApiSupport"),"saveKey","sk-deliberately-invalid-for-test");String sid="instrumentation-error-"+System.currentTimeMillis();JSONObject req=json("chat_session_id",sid,"parent_message_id",null,"prompt","Test request","ref_file_ids",new JSONArray());Object response=call(c("ApiBridge"),"intercept",req(req));String stream=read((InputStream)call(call(field(response,"g"),"d0"),"g0"));check(stream.contains("event: toast"),"invalid API key produces native error toast");check(stream.contains("event: finish"),"failed API request terminates native stream");check(!stream.contains("sk-deliberately-invalid-for-test"),"native error does not expose API key");}
                finally{call(c("ApiSupport"),"saveKey",key);}
            }
            else if(mode.equals("files")){
                File f=new File(getTargetContext().getFilesDir(),"test-attachment.txt");try(FileOutputStream out=new FileOutputStream(f)){out.write("The verification code in this file is FILE_BLUE_ORCHID.".getBytes(StandardCharsets.UTF_8));}
                Object nf=make("ui4",f.getName(),android.net.Uri.fromFile(f),f.length(),0);java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);Object[] outcome={null};
                Object continuation=proxy("nc3",(p,m,a)->{if(m.getName().equals("h")){outcome[0]=a[0];latch.countDown();}return null;});
                Object suspended=call(c("ApiFiles"),"upload",nf,null,continuation);check(suspended==field(c("qd3"),"a"),"native file upload uses coroutine suspension");
                check(latch.await(45,java.util.concurrent.TimeUnit.SECONDS),"native file upload resumed");check(outcome[0]!=null&&outcome[0].getClass().getName().equals("yv7"),"file upload returns native success result");Object file=field(outcome[0],"b");String fid=(String)field(file,"a");check(fid.startsWith("file-"),"file ID belongs to public API");
                testAttachments=new JSONArray().put(fid);String sid="instrumentation-file-"+System.currentTimeMillis();String answer=nativeChat(false,false,sid,"What verification code is written in the attached file? Reply with that code only.",null);check(answer.contains("FILE_BLUE_ORCHID"),"API reads file selected through native picker model");
            }
            else if(mode.equals("metadata")){
                Object response=call(c("ApiBridge"),"intercept",request("https://chat.deepseek.com/api/v0/chat_session/create","POST",new JSONObject()));JSONObject data=new JSONObject(read((InputStream)call(call(field(response,"g"),"d0"),"g0"))).getJSONObject("data").getJSONObject("biz_data");
                Object codec=field(c("o56"),"a");call(codec,"b",field(c("al2"),"a"),data.toString());check(true,"local API session creation parses with original serializer");String sid=data.getJSONObject("chat_session").getString("id");
                response=call(c("ApiBridge"),"intercept",request("https://chat.deepseek.com/api/v0/chat_session/fetch_page","GET",new JSONObject()));JSONObject page=new JSONObject(read((InputStream)call(call(field(response,"g"),"d0"),"g0"))).getJSONObject("data").getJSONObject("biz_data");call(codec,"b",field(c("tl2"),"a"),page.toString());check(true,"merged session list parses with original serializer");boolean found=false;JSONArray ss=page.getJSONArray("chat_sessions");for(int i=0;i<ss.length();i++)if(ss.getJSONObject(i).getString("id").equals(sid))found=true;check(found,"API session survives native list refresh");
                Object editor=call(call(c("ApiSupport"),"prefs"),"edit");call(call(editor,"putBoolean","enabled",false),"commit");
                try{response=call(c("ApiBridge"),"intercept",request("https://chat.deepseek.com/api/v0/chat_session/fetch_page","GET",new JSONObject()));check(response!=null,"local history remains available when API mode disabled");page=new JSONObject(read((InputStream)call(call(field(response,"g"),"d0"),"g0"))).getJSONObject("data").getJSONObject("biz_data");call(codec,"b",field(c("tl2"),"a"),page.toString());check(true,"disabled-mode history parses with original serializer");}
                finally{editor=call(call(c("ApiSupport"),"prefs"),"edit");call(call(editor,"putBoolean","enabled",true),"commit");}
            }
            else if(mode.equals("search"))nativeChat(false,true,"instrumentation-search-"+System.currentTimeMillis(),"Search the official DeepSeek API documentation for the default chatbot model name. Reply briefly and cite a source.",null);
            else if(mode.equals("thinking"))nativeChat(true,false,"instrumentation-thinking-"+System.currentTimeMillis(),"What is 17 plus 25? Reply briefly.",null);
            else{String sid="instrumentation-text-"+System.currentTimeMillis();nativeChat(false,false,sid,"Remember the code word LANTERN. Reply exactly API_OK.",null);JSONObject s=(JSONObject)call(c("ApiBridge"),"session",sid);String answer=nativeChat(false,false,sid,"What code word did I ask you to remember? Reply with that word only.",s.getInt("current_message_id"));check(answer.contains("LANTERN"),"second turn retains native branch context");}
        }
        result.putString("stream","SELF_TEST_PASS checks="+checks+"\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){StringWriter writer=new StringWriter();e.printStackTrace(new PrintWriter(writer));result.putString("stream","SELF_TEST_FAIL checks="+checks+"\n"+writer);finish(Activity.RESULT_CANCELED,result);}}
}
