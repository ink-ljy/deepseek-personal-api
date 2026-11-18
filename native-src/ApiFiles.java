import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Keeps the app's native picker and file chips, but uploads their bytes to the public API. */
public final class ApiFiles {
    private static JSONObject files;
    private static synchronized JSONObject index()throws Exception{
        if(files==null){File f=new File(ApiSupport.app.getFilesDir(),"personal_api_files.json");files=f.exists()?new JSONObject(new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8)):new JSONObject();}return files;
    }
    static synchronized JSONObject find(String id)throws Exception{return index().optJSONObject(id);}
    static synchronized void store(JSONObject file)throws Exception{
        index().put(file.getJSONObject("api").getString("id"),file);AtomicFile f=new AtomicFile(new File(ApiSupport.app.getFilesDir(),"personal_api_files.json"));FileOutputStream out=f.startWrite();try{out.write(files.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
    }
    public static Object upload(Object nativeFile,Object progress,Object continuation)throws Exception{
        if(!ApiSupport.enabled())return null;
        new Thread(()->{Object result;try{JSONObject file=uploadSync(nativeFile);Object codec=ApiSupport.field(ApiSupport.cls("o56"),"a");Object parsed=ApiSupport.call(codec,"b",ApiSupport.field(ApiSupport.cls("js"),"a"),file.getJSONObject("native").toString());result=ApiSupport.make("yv7",null,parsed);}catch(Exception e){ApiSupport.notice(e.getMessage()==null?"API 文件上传失败":e.getMessage());try{result=ApiSupport.make("bw7",e);}catch(Exception fatal){return;}}
            try{ApiSupport.call(continuation,"h",result);}catch(Exception ignored){}
        },"PersonalApiFileUpload").start();
        return ApiSupport.field(ApiSupport.cls("qd3"),"a");
    }
    static JSONObject uploadSync(Object nativeFile)throws Exception{
        String name=(String)ApiSupport.field(nativeFile,"a");Uri uri=(Uri)ApiSupport.field(nativeFile,"b");long length=((Number)ApiSupport.field(nativeFile,"c")).longValue();
        if(length>100L*1024*1024)throw new IOException("文件超过个人版单文件 100 MB 上限。");
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        boolean image=Arrays.asList("jpg","jpeg","png","webp","gif","bmp").contains(ext);String purpose=image?null:Arrays.asList("pdf","doc","docx","ppt","pptx").contains(ext)?"rich_layout_recognition":"user_data";
        String boundary="----PersonalDeepSeek"+UUID.randomUUID().toString().replace("-","");HttpURLConnection c=ApiSupport.connection("/anthropic/v1/files","POST",ApiSupport.key());
        try{
            c.setDoOutput(true);c.setChunkedStreamingMode(8192);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setRequestProperty("anthropic-beta","files-api-2025-04-14");
            String safeName=name.replace("\r","").replace("\n","").replace("\\","_").replace("\"","_");
            String mime=ApiSupport.app.getContentResolver().getType(uri);if(mime==null)mime=URLConnection.guessContentTypeFromName(name);if(mime==null)mime=image?"image/"+(ext.equals("jpg")?"jpeg":ext):"application/octet-stream";
            try(OutputStream out=c.getOutputStream();InputStream in=ApiSupport.app.getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("无法读取所选文件，请重新选择。");
                if(purpose!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"+purpose+"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+safeName+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("文件超过个人版单文件 100 MB 上限。");out.write(buffer,0,n);}out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int code=c.getResponseCode();if(code!=200&&code!=201)throw new IOException(ApiSupport.error(code));JSONObject api=new JSONObject(new String(ApiSupport.readAll(c.getInputStream()),StandardCharsets.UTF_8));double now=System.currentTimeMillis()/1000.0;
            JSONObject nativeMeta=ApiSupport.obj("id",api.getString("id"),"status","SUCCESS","file_name",name,"file_size",length,"inserted_at",now,"updated_at",now,"previewable",false,"is_image",image,"signed_path",uri.toString());
            JSONObject file=ApiSupport.obj("api",api,"native",nativeMeta,"is_image",image);store(file);return file;
        }finally{c.disconnect();}
    }
    static JSONArray idsFromNative(JSONObject nativeMessage)throws Exception{
        JSONArray ids=new JSONArray(),fragments=nativeMessage.optJSONArray("fragments");if(fragments==null)return ids;
        for(int i=0;i<fragments.length();i++){JSONArray fs=fragments.getJSONObject(i).optJSONArray("files");if(fs!=null)for(int j=0;j<fs.length();j++)ids.put(fs.getJSONObject(j).getString("id"));}return ids;
    }
    static JSONArray content(String prompt,JSONArray ids)throws Exception{
        JSONArray blocks=new JSONArray();if(!prompt.isEmpty())blocks.put(ApiSupport.obj("type","text","text",prompt));
        if(ids!=null)for(int i=0;i<ids.length();i++){String id=ids.getString(i);JSONObject f=find(id);if(f==null)throw new IOException("该附件来自原版聊天服务，请重新选择文件以上传到 API。");
            blocks.put(ApiSupport.obj("type",f.getBoolean("is_image")?"image":"document","source",ApiSupport.obj("type","file","file_id",id)));}
        return blocks;
    }
    static JSONArray nativeFiles(JSONArray ids)throws Exception{JSONArray fs=new JSONArray();if(ids!=null)for(int i=0;i<ids.length();i++){JSONObject f=find(ids.getString(i));if(f!=null)fs.put(f.getJSONObject("native"));}return fs;}
}
