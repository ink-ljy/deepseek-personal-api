import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Explicit OpenAI Chat Completions adapter; keys never follow redirects or cross endpoints. */
public final class ApiProfiles {
    static final class Config {
        final boolean custom;final String base,key,model;final long limit;final int output;
        Config(boolean c,String b,String k,String m,long l,int o){custom=c;base=b;key=k;model=m;limit=l;output=o;}
    }
    static Config active(){
        boolean c=ApiSupport.prefs().getBoolean("custom_active",false);
        return c?new Config(true,ApiSupport.prefs().getString("chat_base",""),ApiSupport.readSecret("chat_key"),ApiSupport.prefs().getString("chat_model",""),ApiSupport.prefs().getLong("chat_limit",128000),ApiSupport.prefs().getInt("chat_output",8192)):
            new Config(false,ApiSupport.ORIGIN,ApiSupport.key(),"deepseek-flash",ApiContext.LIMIT,(int)ApiContext.RESERVE);
    }
    static String base(String value)throws IOException{
        try{URI u=new URI(value.trim());String scheme=u.getScheme();
            if(!"https".equalsIgnoreCase(scheme)||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new Exception();
            String s=new URI("https",null,u.getHost().toLowerCase(Locale.ROOT),u.getPort(),u.getPath(),null,null).toASCIIString();
            while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;
        }catch(Exception e){throw new IOException("Base URL 请填写完整的 HTTPS 地址，不含账号、查询参数或 #。例如 https://example.com/v1");}
    }
    static String model(String value)throws IOException{String s=value.trim();if(s.isEmpty()||s.length()>200||s.matches(".*[\\p{Cntrl}].*"))throw new IOException("请填写有效的模型名称。");return s;}
    static void saveChat(String url,String token,String name,long limit,int output)throws Exception{
        String b=base(url),m=model(name);if(limit<4096||limit>10000000||output<256||output>limit/2)throw new IOException("上下文上限至少 4096，回答上限至少 256 且不超过上下文的一半。");
        boolean same=b.equals(ApiSupport.prefs().getString("chat_base",""));String stored;
        if(token.trim().isEmpty()){if(!same||ApiSupport.readSecret("chat_key").isEmpty())throw new IOException("地址变更后请重新输入该地址的 Key。");stored=ApiSupport.prefs().getString("chat_key","");}
        else stored=ApiSupport.encryptSecret(token);
        if(!ApiSupport.prefs().edit().putString("chat_base",b).putString("chat_key",stored).putString("chat_model",m).putLong("chat_limit",limit).putInt("chat_output",output).putBoolean("custom_active",true).putBoolean("enabled",true).commit())throw new IOException("配置保存失败。");
    }
    static void official(){ApiSupport.prefs().edit().putBoolean("custom_active",false).apply();}
    static HttpURLConnection connect(String url,String token,int timeout)throws Exception{
        String key=ApiSupport.normalizeKey(token);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(timeout);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");return c;
    }
    static void write(HttpURLConnection c,JSONObject body)throws Exception{byte[] b=body.toString().getBytes(StandardCharsets.UTF_8);if(b.length>48*1024*1024)throw new IOException("请求超过个人版 48 MiB 上限，请减少附件。");c.setFixedLengthStreamingMode(b.length);try(OutputStream out=c.getOutputStream()){out.write(b);}}
    static JSONObject post(String url,String key,JSONObject body,int timeout)throws Exception{HttpURLConnection c=connect(url,key,timeout);try{write(c,body);int code=c.getResponseCode();if(code!=200)throw new ApiSupport.ApiFailure(code);return new JSONObject(new String(ApiSupport.readAll(c.getInputStream()),StandardCharsets.UTF_8));}finally{c.disconnect();}}
    static String system(String summary){String persona=ApiSupport.prefs().getString("persona","").trim();return persona+(summary==null?"":(persona.isEmpty()?"":"\n\n")+ApiContext.system(summary));}
    static JSONArray messages(JSONArray source)throws Exception{
        JSONArray result=new JSONArray();for(int i=0;i<source.length();i++){
            JSONObject m=source.getJSONObject(i);String role=m.getString("role");Object content=m.get("content");JSONObject dest=ApiSupport.obj("role",role);
            if(content instanceof JSONArray){JSONArray parts=new JSONArray();StringBuilder text=new StringBuilder(),thinking=new StringBuilder();JSONArray blocks=(JSONArray)content;
                for(int j=0;j<blocks.length();j++){JSONObject b=blocks.getJSONObject(j);String type=b.optString("type");
                    if(type.equals("text")){parts.put(ApiSupport.obj("type","text","text",b.optString("text")));text.append(b.optString("text"));}
                    else if(type.equals("thinking"))thinking.append(b.optString("thinking"));
                    else if(type.equals("image")){JSONObject s=b.getJSONObject("source");String url;
                        if(s.optString("type").equals("file"))url=ApiFiles.dataUrl(s.getString("file_id"));
                        else if(s.optString("type").equals("base64"))url="data:"+s.getString("media_type")+";base64,"+s.getString("data");
                        else if(s.optString("type").equals("url"))url=s.getString("url");else throw new IOException("图片来源不受兼容接口支持。");
                        parts.put(ApiSupport.obj("type","image_url","image_url",ApiSupport.obj("url",url)));}
                    else if(type.equals("document"))throw new IOException("兼容接口暂不支持文档，请粘贴文字或切回 DeepSeek 官方接口。");
                }
                if(role.equals("assistant")){dest.put("content",text.toString());/* Provider-specific reasoning fields are deliberately not replayed across providers. */}
                else dest.put("content",parts);
            }else dest.put("content",content);result.put(dest);
        }return result;
    }
    static JSONObject payload(Config cfg,JSONArray source,String system,boolean stream,int max)throws Exception{
        JSONArray ms=new JSONArray();if(system!=null&&!system.isEmpty())ms.put(ApiSupport.obj("role","system","content",system));JSONArray converted=messages(source);for(int i=0;i<converted.length();i++)ms.put(converted.get(i));
        JSONObject body=ApiSupport.obj("model",cfg.model,"messages",ms,"stream",stream,"max_tokens",Math.min(max,cfg.output));
        if(stream&&ApiSupport.prefs().getBoolean("chat_usage",true))body.put("stream_options",ApiSupport.obj("include_usage",true));return body;
    }
    static JSONObject usage(JSONObject u)throws Exception{
        JSONObject result=new JSONObject();if(u==null)return result;long total=u.optLong("prompt_tokens");JSONObject details=u.optJSONObject("prompt_tokens_details");boolean known=(details!=null&&details.has("cached_tokens"))||u.has("prompt_cache_hit_tokens");long hit=details!=null?details.optLong("cached_tokens"):u.optLong("prompt_cache_hit_tokens");hit=Math.max(0,Math.min(total,hit));
        result.put("input_tokens",total-hit);result.put("output_tokens",u.optLong("completion_tokens"));if(known)result.put("cache_read_input_tokens",hit);return result;
    }
    static void stream(ApiBridge.Run run,Config cfg,String system)throws Exception{
        HttpURLConnection c=connect(cfg.base+"/chat/completions",cfg.key,300000);run.connection=c;c.setRequestProperty("Accept","text/event-stream");
        write(c,payload(cfg,run.blocks,system,true,cfg.output));if(run.cancelled)return;run.httpCode=c.getResponseCode();if(run.httpCode!=200)throw new ApiSupport.ApiFailure(run.httpCode);
        Set<Integer> started=new HashSet<>();boolean ended=false;StringBuilder data=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){String line;
            while(!run.cancelled&&(line=reader.readLine())!=null){if(line.startsWith("\ufeff"))line=line.substring(1);if(line.isEmpty()){
                if(data.length()>0){if(chunk(run,data.toString(),started)){ended=true;break;}data.setLength(0);if(System.currentTimeMillis()-run.lastSave>=1000)run.checkpoint(null);}
            }else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}}
            if(!run.cancelled&&!ended&&data.length()>0)ended=chunk(run,data.toString(),started);
        }
        if(!ended&&run.usage.has("finish_reason"))ended=true;
        if(!run.cancelled&&!ended)throw new IOException("兼容接口流式响应未正常结束。");if(ended)run.consume(ApiSupport.obj("type","message_stop"));
    }
    static boolean chunk(ApiBridge.Run run,String data,Set<Integer> started)throws Exception{
        if(data.equals("[DONE]"))return true;JSONObject event=new JSONObject(data);if(event.has("error"))throw new IOException("兼容接口返回错误。");
        ApiContext.mergeUsage(run.usage,usage(event.optJSONObject("usage")));if(event.has("model"))run.usage.put("model",event.getString("model"));
        JSONArray choices=event.optJSONArray("choices");if(choices==null||choices.length()==0)return false;JSONObject choice=choices.getJSONObject(0),delta=choice.optJSONObject("delta");
        if(delta!=null){if(delta.has("tool_calls"))throw new IOException("该兼容接口要求工具调用，当前客户端不支持此工具。");
            for(String field:new String[]{"reasoning_content","content"}){String text=delta.optString(field,"");if(text.isEmpty()||delta.isNull(field))continue;boolean thinking=field.equals("reasoning_content");int idx=thinking?0:1;String f=thinking?"thinking":"text";
                if(started.add(idx))run.consume(ApiSupport.obj("type","content_block_start","index",idx,"content_block",ApiSupport.obj("type",f,f,"")));
                run.consume(ApiSupport.obj("type","content_block_delta","index",idx,"delta",ApiSupport.obj("type",f+"_delta",f,text)));}
        }
        if(!choice.isNull("finish_reason")&&choice.has("finish_reason")){String reason=choice.getString("finish_reason");if(reason.equals("tool_calls")||reason.equals("function_call"))throw new IOException("当前兼容接口的工具调用尚不受支持。");if(reason.equals("content_filter"))run.refusal=true;run.usage.put("finish_reason",reason);}
        return false;
    }
    static JSONObject complete(JSONArray source,String system,int max)throws Exception{
        Config cfg=active();if(cfg.custom){JSONObject response=post(cfg.base+"/chat/completions",cfg.key,payload(cfg,source,system,false,max),300000);JSONObject choice=response.getJSONArray("choices").getJSONObject(0);if(!choice.optString("finish_reason").equals("stop"))throw new IOException("摘要未完整生成，已保留原上下文。");return ApiSupport.obj("stop_reason","end_turn","content",new JSONArray().put(ApiSupport.obj("type","text","text",choice.getJSONObject("message").optString("content"))),"usage",usage(response.optJSONObject("usage")));}
        JSONObject body=ApiSupport.obj("model",cfg.model,"max_tokens",max,"thinking",ApiSupport.obj("type","disabled"),"system",system,"messages",source,"stream",false);HttpURLConnection c=ApiSupport.connection("/anthropic/v1/messages","POST",cfg.key);
        try{c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("anthropic-beta","files-api-2025-04-14");c.setReadTimeout(300000);write(c,body);int code=c.getResponseCode();if(code!=200)throw new ApiSupport.ApiFailure(code);return new JSONObject(new String(ApiSupport.readAll(c.getInputStream()),StandardCharsets.UTF_8));}finally{c.disconnect();}
    }
}
