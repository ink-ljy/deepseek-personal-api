import android.net.Uri;
import android.util.AtomicFile;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    static void stage(String value){ApiSupport.prefs().edit().putString("last_upload_status",value).putLong("last_upload_at",System.currentTimeMillis()).apply();}
    /** Handle the selected image at the original attachment-model entry, including old chat -> API. */
    public static Object uploadImage(Object nativeFile,Object model,String modelKey,Object continuation)throws Exception{
        if(!ApiSupport.enabled()||nativeFile==null||model==null)return null;
        int kind=((Number)ApiSupport.field(nativeFile,"d")).intValue();if(kind!=2&&kind!=3)return null;
        Object callback=ApiSupport.make("j72",model,modelKey,0);
        new Thread(()->{try{
            JSONObject file=uploadSync(nativeFile,callback);Object codec=ApiSupport.field(ApiSupport.cls("o56"),"a");Object parsed=ApiSupport.call(codec,"b",ApiSupport.field(ApiSupport.cls("js"),"a"),file.getJSONObject("native").toString());
            ApiSupport.call(model,"m",parsed,modelKey);
        }catch(Exception e){try{ApiSupport.call(model,"l",ApiSupport.make("p12",4),modelKey);}catch(Exception ignored){}ApiSupport.notice(uploadError(e));}
            finally{try{ApiSupport.call(continuation,"h",ApiSupport.unit());}catch(Exception ignored){}}
        },"PersonalApiSelectedImage").start();
        return ApiSupport.field(ApiSupport.cls("qd3"),"a");
    }
    static String uploadError(Exception e){String result=ApiSupport.userError(e,"图片上传失败，请重新选择；可在上下文空间查看最近上传阶段。");stage("失败 · "+result);return result;}
    public static Object upload(Object nativeFile,Object progress,Object continuation)throws Exception{
        if(!ApiSupport.enabled())return null;
        new Thread(()->{Object result;try{JSONObject file=uploadSync(nativeFile,progress);Object codec=ApiSupport.field(ApiSupport.cls("o56"),"a");Object parsed=ApiSupport.call(codec,"b",ApiSupport.field(ApiSupport.cls("js"),"a"),file.getJSONObject("native").toString());result=ApiSupport.make("yv7",null,parsed);}catch(Exception e){ApiSupport.notice(uploadError(e));try{result=ApiSupport.make("bw7",e);}catch(Exception fatal){return;}}
            try{ApiSupport.call(continuation,"h",result);}catch(Exception ignored){}
        },"PersonalApiFileUpload").start();
        return ApiSupport.field(ApiSupport.cls("qd3"),"a");
    }
    static final class Prepared {
        File file;String name,mime;boolean image;int width,height;
    }
    static void progress(Object callback,float value)throws Exception{if(callback!=null)ApiSupport.call(callback,"j",value);}
    /** Inspect actual image bytes, including extensionless gallery/HEIC inputs. Keep previews locally. */
    static Prepared prepare(Object nativeFile)throws Exception {
        Prepared p=new Prepared();p.name=(String)ApiSupport.field(nativeFile,"a");Uri uri=(Uri)ApiSupport.field(nativeFile,"b");
        long declared=((Number)ApiSupport.field(nativeFile,"c")).longValue();
        if(declared>100L*1024*1024)throw new IOException("文件超过个人版单文件 100 MB 上限。");
        File dir=new File(ApiSupport.app.getFilesDir(),"personal_api_attachments");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法保存所选附件。");
        p.file=File.createTempFile("upload-",".bin",dir);
        try{
            try(InputStream in=ApiSupport.app.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(p.file)){
                if(in==null)throw new IOException("无法读取所选文件，请重新选择。");
                byte[] buffer=new byte[8192];int n;long total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("文件超过个人版单文件 100 MB 上限。");out.write(buffer,0,n);}
            }
            if(p.file.length()==0)throw new IOException("所选文件为空，请重新选择。");
            try{p.mime=ApiSupport.app.getContentResolver().getType(uri);}catch(Exception ignored){}
            String ext=p.name.contains(".")?p.name.substring(p.name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(p.file.getAbsolutePath(),bounds);
            int kind=((Number)ApiSupport.field(nativeFile,"d")).intValue();
            p.image=bounds.outWidth>0||(p.mime!=null&&p.mime.startsWith("image/"))||kind==2||kind==3||Arrays.asList("jpg","jpeg","png","webp","gif","bmp","heic","heif","avif","tif","tiff","jfif").contains(ext);
            if(p.image){
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("手机无法解码这张图片，请选择 JPEG、PNG、GIF 或 WebP 格式。");
                p.mime=bounds.outMimeType;p.width=bounds.outWidth;p.height=bounds.outHeight;
                if(!Arrays.asList("image/jpeg","image/png","image/gif","image/webp").contains(p.mime)||p.width>8192||p.height>8192){
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                    while(Math.max(p.width,p.height)/options.inSampleSize>8192||(long)(p.width/options.inSampleSize)*(p.height/options.inSampleSize)>24000000)options.inSampleSize*=2;
                    Bitmap bitmap=BitmapFactory.decodeFile(p.file.getAbsolutePath(),options);if(bitmap==null)throw new IOException("图片转换失败，请换成 JPEG 或 PNG 后重试。");
                    File converted=File.createTempFile("image-",".png",dir);
                    try{try(OutputStream out=new FileOutputStream(converted)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片转换失败。");}p.width=bitmap.getWidth();p.height=bitmap.getHeight();}
                    catch(Exception e){converted.delete();throw e;}finally{bitmap.recycle();}
                    p.file.delete();p.file=converted;p.mime="image/png";p.name=(p.name.contains(".")?p.name.substring(0,p.name.lastIndexOf('.')):p.name)+".png";
                }else if(!Arrays.asList("jpg","jpeg","png","webp","gif").contains(ext))p.name+="."+(p.mime.equals("image/jpeg")?"jpg":p.mime.substring(6));
                if(p.file.length()>64L*1024*1024)throw new IOException("图片超过官方 API 的 64 MiB 上限，请压缩后重试。");
            }else if(p.mime==null){p.mime=URLConnection.guessContentTypeFromName(p.name);}
            if(p.mime==null)p.mime="application/octet-stream";
            return p;
        }catch(Exception e){p.file.delete();throw e;}
    }
    static JSONObject uploadSync(Object nativeFile)throws Exception{return uploadSync(nativeFile,null);}
    static JSONObject uploadSync(Object nativeFile,Object callback)throws Exception{
        stage("读取所选文件");progress(callback,0f);Prepared p=prepare(nativeFile);boolean stored=false;
        String name=p.name;long length=p.file.length();boolean image=p.image;
        if(ApiProfiles.active().custom){try{
            if(!image)throw new IOException("兼容接口暂不支持文档，请粘贴文字或切回 DeepSeek 官方接口。");if(length>16L*1024*1024)throw new IOException("兼容接口内联图片上限 16 MiB，请压缩后重试。");
            String id="local-image-"+UUID.randomUUID();double now=System.currentTimeMillis()/1000.0;JSONObject meta=ApiSupport.obj("id",id,"status","SUCCESS","file_name",name,"file_size",length,"inserted_at",now,"updated_at",now,"previewable",true,"is_image",true,"signed_path",Uri.fromFile(p.file).toString(),"width",p.width,"height",p.height);
            JSONObject file=ApiSupport.obj("api",ApiSupport.obj("id",id),"native",meta,"is_image",true,"mime",p.mime,"local_only",true);store(file);stored=true;progress(callback,1f);stage("图片已准备，将随消息发送到自定义接口");return file;
        }finally{if(!stored)p.file.delete();}}
        String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        String purpose=Arrays.asList("pdf","doc","docx","ppt","pptx").contains(ext)?"rich_layout_recognition":"user_data";
        String boundary="----PersonalDeepSeek"+UUID.randomUUID().toString().replace("-","");HttpURLConnection c=ApiSupport.connection("/anthropic/v1/files","POST",ApiSupport.key());
        try{
            stage("连接 API 文件服务");
            c.setDoOutput(true);c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);c.setRequestProperty("anthropic-beta","files-api-2025-04-14");
            String safeName=name.replace("\r","").replace("\n","").replace("\\","_").replace("\"","_");
            byte[] prefix=((purpose!=null?"--"+boundary+"\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"+purpose+"\r\n":"")+"--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+safeName+"\"\r\nContent-Type: "+p.mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(length+prefix.length+suffix.length);
            try(OutputStream out=c.getOutputStream();InputStream in=new FileInputStream(p.file)){
                stage("正在发送文件");
                if(in==null)throw new IOException("无法读取所选文件，请重新选择。");
                out.write(prefix);
                byte[] buffer=new byte[8192];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;out.write(buffer,0,n);progress(callback,(float)total/length*.95f);}out.write(suffix);
            }
            stage("等待 API 上传确认");int code=c.getResponseCode();if(code!=200&&code!=201)throw new ApiSupport.ApiFailure(code);JSONObject api=new JSONObject(new String(ApiSupport.readAll(c.getInputStream()),StandardCharsets.UTF_8));double now=System.currentTimeMillis()/1000.0;
            JSONObject nativeMeta=ApiSupport.obj("id",api.getString("id"),"status","SUCCESS","file_name",name,"file_size",length,"inserted_at",now,"updated_at",now,"previewable",image,"is_image",image,"signed_path",Uri.fromFile(p.file).toString());
            if(image){nativeMeta.put("width",p.width);nativeMeta.put("height",p.height);}
            JSONObject file=ApiSupport.obj("api",api,"native",nativeMeta,"is_image",image,"mime",p.mime);store(file);stored=true;progress(callback,1f);stage("API 已确认上传完成");return file;
        }finally{c.disconnect();if(!stored)p.file.delete();}
    }
    static JSONArray idsFromNative(JSONObject nativeMessage)throws Exception{
        JSONArray ids=new JSONArray(),fragments=nativeMessage.optJSONArray("fragments");if(fragments==null)return ids;
        for(int i=0;i<fragments.length();i++){JSONArray fs=fragments.getJSONObject(i).optJSONArray("files");if(fs!=null)for(int j=0;j<fs.length();j++)ids.put(fs.getJSONObject(j).getString("id"));}return ids;
    }
    static JSONArray content(String prompt,JSONArray ids)throws Exception{
        JSONArray blocks=new JSONArray();if(!prompt.isEmpty())blocks.put(ApiSupport.obj("type","text","text",prompt));
        if(ids!=null)for(int i=0;i<ids.length();i++){String id=ids.getString(i);JSONObject f=find(id);if(f==null)throw new IOException("该附件来自原版聊天服务，请重新选择文件以上传到 API。");
            if(f.optBoolean("local_only")&&!ApiProfiles.active().custom){String data=dataUrl(id);int comma=data.indexOf(',');blocks.put(ApiSupport.obj("type","image","source",ApiSupport.obj("type","base64","media_type",data.substring(5,data.indexOf(';')),"data",data.substring(comma+1))));}
            else blocks.put(ApiSupport.obj("type",f.getBoolean("is_image")?"image":"document","source",ApiSupport.obj("type","file","file_id",id)));}
        return blocks;
    }
    static String dataUrl(String id)throws Exception{
        JSONObject f=find(id);if(f==null||!f.optBoolean("is_image"))throw new IOException("该图片不在本机，请重新选择。");Uri uri=Uri.parse(f.getJSONObject("native").getString("signed_path"));if(!"file".equals(uri.getScheme()))throw new IOException("该图片需要重新选择。");File file=new File(uri.getPath());
        if(!file.exists()||file.length()>16L*1024*1024)throw new IOException("本机图片不存在或超过兼容接口 16 MiB 上限，请重新选择。");BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);String mime=f.optString("mime",options.outMimeType);if(mime==null)throw new IOException("无法识别图片格式。");return "data:"+mime+";base64,"+android.util.Base64.encodeToString(ApiSupport.readAll(new FileInputStream(file)),android.util.Base64.NO_WRAP);
    }
    static JSONArray nativeFiles(JSONArray ids)throws Exception{JSONArray fs=new JSONArray();if(ids!=null)for(int i=0;i<ids.length();i++){JSONObject f=find(ids.getString(i));if(f!=null)fs.put(f.getJSONObject("native"));}return fs;}
}
