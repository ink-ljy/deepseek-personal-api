import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import org.json.*;

/** Small original-Compose top-bar action and an original-style context details page. */
public final class ApiContextUi {
    static Object revision;static int version;
    static final Handler main=new Handler(Looper.getMainLooper());
    static synchronized Object state()throws Exception{if(revision==null)revision=ApiSupport.make("sb8",0);return revision;}
    static void changed(){main.post(()->{try{ApiSupport.call(state(),"g",++version);}catch(Exception ignored){}});}
    public static void badge(Object composer){
        try{
            ApiSupport.call(composer,"h0",146736799);
            ApiSupport.call(state(),"getValue");
            if(ApiSupport.enabled()){
                JSONObject stats=current();double percent=stats==null?0:stats.optDouble("percent");String amount=stats==null||!stats.optBoolean("owned")?"—":percent>0&&percent<1?"<1%":String.format(Locale.ROOT,"%.0f%%",percent);
                Object click=ApiNativeViews.callback("vy4",(p,m,a)->{if(m.getName().equals("v")){open();return ApiSupport.unit();}return ApiUi.proxyObject(p,m,a);});
                float fraction=stats==null?0f:(float)Math.min(1,stats.optDouble("percent")/100);
                Object label=ApiNativeViews.content(c->badgeContent(c,amount,fraction));
                Object modifier=ApiSupport.call(ApiSupport.cls("s9a"),"p",ApiSupport.field(ApiSupport.cls("bm7"),"a"),60f,44f);
                Object semantics=ApiNativeViews.callback("xy4",(p,m,a)->{if(m.getName().equals("j")){ApiSupport.call(ApiSupport.cls("bt9"),"f",a[0],"上下文空间，估算 "+amount);return ApiSupport.unit();}return ApiUi.proxyObject(p,m,a);});
                modifier=ApiSupport.call(ApiSupport.cls("ss9"),"b",modifier,false,semantics);
                ApiSupport.call(ApiSupport.cls("f11"),"H",click,modifier,false,null,null,null,null,label,composer,0,252);
            }
            ApiSupport.call(composer,"q",false);
        }catch(Exception e){throw new IllegalStateException("上下文入口初始化失败",e);}
    }
    static void badgeContent(Object c,String amount,float fraction)throws Exception{
        Object modifier=ApiSupport.field(ApiSupport.cls("bm7"),"a"),z=ApiSupport.cls("zrc"),f=ApiSupport.cls("f57");
        Object layout=ApiSupport.call(ApiSupport.cls("ne9"),"a",ApiSupport.make("i00",4f,true,ApiSupport.make("qc",23)),ApiSupport.field(z,"m"),c,54);
        long key=((Number)ApiSupport.call(ApiSupport.cls("nl9"),"H",c)).longValue();Object locals=ApiSupport.call(c,"l"),materialized=ApiSupport.call(ApiSupport.cls("thb"),"a0",c,modifier);
        ApiSupport.call(c,"l0");if((Boolean)ApiSupport.field(c,"S"))ApiSupport.call(c,"k",ApiSupport.field(ApiSupport.cls("zi6"),"T0"));else ApiSupport.call(c,"u0");
        ApiSupport.call(f,"w",ApiSupport.field(z,"A"),c,layout);ApiSupport.call(f,"w",ApiSupport.field(z,"z"),c,locals);ApiSupport.call(f,"w",ApiSupport.field(z,"B"),c,Long.hashCode(key));ApiSupport.call(f,"v",c);ApiSupport.call(f,"w",ApiSupport.field(z,"y"),c,materialized);
        Object value=ApiSupport.make("rb8",fraction),supplier=ApiSupport.make("ww",0,0,ApiSupport.cls("tga"),value,"value","getValue()Ljava/lang/Object;");
        Object size=ApiSupport.call(ApiSupport.cls("s9a"),"p",modifier,12f,12f);
        ApiSupport.call(ApiSupport.cls("ds8"),"b",supplier,size,0xff99999900000000L,1.5f,0xffdedede00000000L,1,0f,c,0);
        long sp=0x100000000L|(Float.floatToIntBits(12f)&0xffffffffL);
        ApiSupport.call(ApiSupport.cls("mza"),"b",amount,null,0xff88888800000000L,null,sp,null,0L,null,0L,0,false,1,0,null,null,c,0,0,262142&~16&~4);ApiSupport.call(c,"q",true);
    }
    static JSONObject current(){try{return ApiContext.currentStats();}catch(Exception e){return null;}}
    static String number(long v){return String.format(Locale.ROOT,"%,d",v);}
    public static void open(){try{openPage();}catch(Exception e){ApiSupport.notice("上下文面板打开失败，请重新进入聊天。");}}
    static void openPage()throws Exception{
        Activity a=ApiSupport.activity.get();if(a==null)return;boolean dark=(a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int bg=dark?0xff111111:0xfff7f7f7,card=dark?0xff202020:Color.WHITE,fg=dark?0xffeeeeee:0xff141414,muted=dark?0xffaaaaaa:0xff999999;
        String selected=ApiContext.current;Dialog d=new ApiPageDialog(a);LinearLayout root=ApiUi.column(a);root.setBackgroundColor(bg);ApiNativeViews.owners(root,a);
        root.setOnApplyWindowInsetsListener((v,insets)->{int b=android.os.Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.systemBars()).bottom:insets.getSystemWindowInsetBottom();v.setPadding(0,0,0,b);return insets;});
        root.addView(ApiNativeViews.header(a,"上下文空间",d::dismiss),new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=ApiUi.column(a);content.setPadding(ApiUi.dp(a,16),ApiUi.dp(a,12),ApiUi.dp(a,16),ApiUi.dp(a,24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView model=ApiUi.text(a,"DeepSeek V4.1 Flash 1017 · API",16,fg);model.setPadding(ApiUi.dp(a,16),0,0,ApiUi.dp(a,12));content.addView(model);
        LinearLayout info=ApiUi.column(a);info.setPadding(ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16));info.setBackground(ApiUi.background(card,16,a));content.addView(info);
        TextView space=ApiUi.text(a,"",24,fg);info.addView(space);ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);info.addView(progress,new LinearLayout.LayoutParams(-1,ApiUi.dp(a,12)));
        TextView metrics=ApiUi.text(a,"",14,muted);info.addView(metrics);ApiUi.gap(content,28);
        TextView usage=ApiUi.text(a,"",15,fg);usage.setPadding(ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16));usage.setBackground(ApiUi.background(card,16,a));content.addView(usage);ApiUi.gap(content,28);
        TextView status=ApiUi.text(a,"",14,muted);status.setPadding(ApiUi.dp(a,16),0,ApiUi.dp(a,16),ApiUi.dp(a,12));
        Runnable[] compressAction={()->{}},restoreAction={()->{}};
        View compress=ApiNativeViews.action(a,"压缩较早上下文",()->compressAction[0].run(),true,card);content.addView(compress,new LinearLayout.LayoutParams(-1,ApiUi.dp(a,48)));ApiUi.gap(content,8);
        View restore=ApiNativeViews.action(a,"恢复完整上下文",()->restoreAction[0].run(),false,card);content.addView(restore,new LinearLayout.LayoutParams(-1,ApiUi.dp(a,48)));ApiUi.gap(content,8);content.addView(status);
        TextView notice=ApiUi.text(a,"空间按文本估算，附件和工具的 token 以接口统计为准；预留 16,384 token 供回答。缓存命中显示最近一次请求的真实统计，不代表剩余额度。\n\n压缩会调用一次 API 生成摘要，保留最近 2 轮原文及全部本地聊天记录。摘要可能遗漏细节，可随时恢复完整上下文。改变摘要、深度思考或搜索设置可能降低下一次缓存命中。",14,muted);notice.setPadding(ApiUi.dp(a,16),0,ApiUi.dp(a,16),0);content.addView(notice);ApiUi.gap(content,20);
        TextView summary=ApiUi.text(a,"",14,muted);summary.setTextIsSelectable(true);summary.setPadding(ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16),ApiUi.dp(a,16));content.addView(summary);
        final boolean[] working={false};Runnable refresh=()->{try{JSONObject s=selected==null?null:ApiContext.stats(ApiBridge.session(selected));boolean valid=s!=null&&s.optBoolean("owned");
            if(!valid){space.setText("暂无 API 上下文");metrics.setText("使用 API 发送消息后显示当前分支用量。");usage.setText("缓存命中 —");progress.setProgress(0);compress.setEnabled(false);restore.setEnabled(false);return;}
            long used=s.getLong("estimated_tokens"),limit=s.getLong("limit");space.setText(String.format(Locale.ROOT,"%.1f%% 已使用",s.getDouble("percent")));progress.setProgress((int)Math.min(1000,used*1000/limit));
            metrics.setText("约 "+number(used)+" / 1M token\n本次上下文 "+s.getInt("messages")+" 条 · 完整分支 "+s.getInt("full_messages")+" 条"+(s.optBoolean("compressed")?"\n已启用压缩摘要":"\n使用完整上下文"));
            JSONObject u=s.optJSONObject("usage");usage.setText(u==null?"最近请求用量 —\n收到接口 usage 后显示实际统计。":"最近请求 · API 实际统计\n输入 "+number(s.getLong("input_tokens"))+" token · 输出 "+number(u.optLong("output_tokens"))+" token\n缓存命中 "+(s.optBoolean("cache_known")?String.format(Locale.ROOT,"%.1f%% · %s token",s.getDouble("cache_percent"),number(u.optLong("cache_read_input_tokens"))):"—（接口未返回）"));
            compress.setEnabled(!working[0]&&!s.optBoolean("busy")&&s.optInt("messages")>=6);restore.setEnabled(!working[0]&&!s.optBoolean("busy")&&s.optBoolean("compressed"));summary.setText(s.optBoolean("compressed")?"当前历史摘要\n\n"+s.optString("summary"):"");
        }catch(Exception e){status.setText("上下文统计读取失败，请重新进入此页面。");}};
        compressAction[0]=()->{if(working[0])return;working[0]=true;status.setText("正在压缩较早历史…");refresh.run();new Thread(()->{String result;try{ApiContext.compress(selected);result="压缩完成，原聊天记录已保留。";}catch(Exception e){result=e instanceof java.io.IOException?e.getMessage():"压缩失败，已保留原上下文。";}String message=result;a.runOnUiThread(()->{working[0]=false;if(d.isShowing()){status.setText(message);refresh.run();}});},"PersonalApiCompress").start();};
        restoreAction[0]=()->{try{ApiContext.restore(selected);status.setText("已恢复完整上下文。");refresh.run();}catch(Exception e){status.setText(e.getMessage());}};
        Runnable poll=new Runnable(){public void run(){if(d.isShowing()){refresh.run();main.postDelayed(this,1000);}}};
        d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setStatusBarColor(bg);w.setNavigationBarColor(bg);if(!dark)w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
        d.setOnDismissListener(v->main.removeCallbacks(poll));d.show();root.requestApplyInsets();poll.run();
    }
}
