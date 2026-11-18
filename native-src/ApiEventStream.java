import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** One generation, independently attachable native readers. Slow/closed readers never stop the API. */
public final class ApiEventStream {
    private String ready="",snapshot="",update="",title="",error="";
    private long version;
    private boolean terminal;
    static String frame(String name,JSONObject data){return (name==null?"":"event: "+name+"\n")+"data: "+data+"\n\n";}
    synchronized void event(String name,JSONObject data,JSONObject assistant)throws Exception{
        if("ready".equals(name))ready=frame(name,data);
        if(assistant!=null){
            snapshot=frame(null,ApiSupport.obj("v",ApiSupport.obj("response",assistant)));
            update=frame(null,ApiSupport.obj("p","response/fragments","o","SET","v",assistant.getJSONArray("fragments")))+frame(null,ApiSupport.obj("p","response/status","o","SET","v",assistant.getString("status")));
        }
        if("title".equals(name))title=frame(name,data);
        if("toast".equals(name))error=frame(name,data);
        if("finish".equals(name))terminal=true;
        version++;notifyAll();
    }
    InputStream open(){return new Reader();}
    private final class Reader extends InputStream {
        private ByteArrayInputStream pending;
        private long seen=-1;
        private boolean initial=true,ended;
        private volatile boolean closed;
        private byte[] next()throws IOException{
            synchronized(ApiEventStream.this){
                if(closed)throw new IOException("Native stream reader closed");
                if(ended)return null;
                if(seen==version||(initial&&ready.isEmpty()&&!terminal)){
                    try{ApiEventStream.this.wait(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Native stream reader interrupted");}
                }
                if(closed)throw new IOException("Native stream reader closed");
                String value;
                if(initial&&!ready.isEmpty()){value=ready+snapshot;initial=false;}
                else if(seen!=version)value=update;
                else {
                    // A valid native delta also keeps its stream monitor alive during slow tools/thinking.
                    value=snapshot.isEmpty()?": keep-alive\n\n":update;
                }
                seen=version;value+=title+error;
                if(terminal){value+=frame("finish",new JSONObject());ended=true;}
                return value.getBytes(StandardCharsets.UTF_8);
            }
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{
            if(bytes==null)throw new NullPointerException();if(offset<0||length<0||length>bytes.length-offset)throw new IndexOutOfBoundsException();if(length==0)return 0;
            while(true){
                if(closed)throw new IOException("Native stream reader closed");
                if(pending!=null){int n=pending.read(bytes,offset,length);if(n!=-1)return n;pending=null;}
                byte[] value=next();if(value==null)return -1;pending=new ByteArrayInputStream(value);
            }
        }
        @Override public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)==-1?-1:one[0]&255;}
        @Override public void close(){closed=true;synchronized(ApiEventStream.this){ApiEventStream.this.notifyAll();}}
    }
}
