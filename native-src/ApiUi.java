import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

public final class ApiUi {
    public static final String ATTRIBUTION="基于 DeepSeek 原版客户端，使用 DSH 与 CODEX 共同修改完成";
    public static String footer(String original){return ATTRIBUTION+"\n"+original;}
    /** Uses the original Compose settings row, original data icon and original arrow. */
    public static void row(Object composer) {
        settingsRow(composer,"额度管理",false);
    }
    public static void aboutRow(Object composer) {
        settingsRow(composer,"关于 DeepSeek API",true);
    }
    static void settingsRow(Object composer,String caption,boolean about) {
        try {
            Object context = ApiSupport.call(composer, "j", ApiSupport.field(ApiSupport.cls("fe"), "b"));
            Class<?> clickType=ApiSupport.cls("vy4"), contentType=ApiSupport.cls("lz4");
            Object click=Proxy.newProxyInstance(clickType.getClassLoader(), new Class[]{clickType}, (proxy,m,args)->{
                if(m.getName().equals("v")) { if(about)openAbout((Context)context);else open((Context)context); return ApiSupport.unit(); }
                return proxyObject(proxy,m,args);
            });
            Object label=Proxy.newProxyInstance(contentType.getClassLoader(), new Class[]{contentType}, (proxy,m,args)->{
                if(m.getName().equals("q")) {
                    ApiSupport.call(ApiSupport.cls("mza"),"b",caption,null,0L,null,0L,null,0L,null,0L,0,false,1,0,null,null,args[0],0,0,262142);
                    return ApiSupport.unit();
                }
                return proxyObject(proxy,m,args);
            });
            ApiSupport.call(ApiSupport.cls("py9"), "b",null,click,false,false,0L,
                ApiSupport.field(ApiSupport.cls("nl9"),about?"D":"i"), null,
                ApiSupport.field(ApiSupport.cls("nl9"),"j"),0f,label,composer,0x30c30000,0x15d);
        } catch(Exception e) { throw new IllegalStateException("个人 API 设置入口初始化失败", e); }
    }
    static Object proxyObject(Object p,Method m,Object[] a) {
        if(m.getName().equals("hashCode"))return System.identityHashCode(p);
        if(m.getName().equals("equals"))return p==a[0];
        if(m.getName().equals("toString"))return "PersonalApiSettings";
        return null;
    }
    static int dp(Context c,float v) { return (int)(c.getResources().getDisplayMetrics().density*v+.5f); }
    static GradientDrawable background(int color,int radius,Context c) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d; }
    static TextView text(Context c,String s,int size,int color) { TextView t=new TextView(c); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(c,3),1); return t; }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static void gap(LinearLayout l,int h) { View v=new View(l.getContext()); l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),h))); }
    static Button button(Context c,String title,int fg,int bg) { Button b=new Button(c); b.setText(title); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(fg); b.setMinHeight(dp(c,48)); b.setBackground(background(bg,16,c));b.setStateListAnimator(null);b.setElevation(0);b.setPadding(dp(c,16),0,dp(c,16),0); return b; }
    public static void open(Context originalContext) {
        try { openNative(originalContext); } catch(Exception e) { throw new IllegalStateException("额度管理界面初始化失败",e); }
    }
    static void openNative(Context originalContext) throws Exception {
        Activity activity=ApiSupport.activity.get(); if(activity==null) return;
        Context c=activity;
        boolean dark=(c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        int bg=dark?0xff111111:0xfff7f7f7, card=dark?0xff202020:Color.WHITE, fg=dark?0xffeeeeee:0xff141414, muted=dark?0xffaaaaaa:0xff999999, blue=0xff4d6bfe;
        Dialog d=new ApiPageDialog(c);
        LinearLayout root=column(c); root.setBackgroundColor(bg);
        ApiNativeViews.owners(root,activity);
        root.setOnApplyWindowInsetsListener((view,insets)->{int bottom=android.os.Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.systemBars()).bottom:insets.getSystemWindowInsetBottom();view.setPadding(0,0,0,bottom);return insets;});
        root.addView(ApiNativeViews.header(activity,d::dismiss),new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(c); scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false); LinearLayout content=column(c); content.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,24)); scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView section=text(c,"API 账号",14,muted);section.setPadding(dp(c,16),0,0,dp(c,12));content.addView(section);
        Switch mode=new Switch(c);mode.setChecked(ApiSupport.enabled());
        FrameLayout modeCard=new FrameLayout(c);modeCard.setBackground(background(card,16,c));modeCard.setClipToOutline(true);
        View modeView=ApiNativeViews.create(activity,ApiNativeViews.toggle(mode::isChecked,mode::setChecked));modeCard.addView(modeView,new FrameLayout.LayoutParams(-1,-1));content.addView(modeCard,new LinearLayout.LayoutParams(-1,dp(c,48)));
        TextView description=text(c,"填写你自己的 API Key，费用由该 Key 所属账号支付。",14,muted);description.setPadding(dp(c,16),dp(c,8),dp(c,16),0);content.addView(description);gap(content,28);
        LinearLayout keyCard=column(c);keyCard.setBackground(background(card,16,c));keyCard.setClipToOutline(true);content.addView(keyCard);
        LinearLayout keyInput=column(c);keyInput.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,8));keyCard.addView(keyInput);
        EditText key=new EditText(c);key.setTextSize(15);key.setSingleLine();key.setTextColor(fg);key.setHintTextColor(muted);key.setHint(ApiSupport.key().isEmpty()?"输入 DeepSeek API Key":"已保存 API Key（输入新 Key 可替换）");
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setTypeface(Typeface.DEFAULT);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);key.setSaveEnabled(false);key.setPadding(dp(c,12),0,dp(c,12),0);key.setBackground(background(bg,10,c));keyInput.addView(key,new LinearLayout.LayoutParams(-1,dp(c,44)));
        Runnable[] saveAction={()->{}};View save=ApiNativeViews.action(activity,"验证并保存",()->saveAction[0].run(),false,card);keyCard.addView(save,new LinearLayout.LayoutParams(-1,dp(c,48)));
        TextView status=text(c,"",14,muted);status.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,12));keyCard.addView(status);status.setVisibility(View.GONE);
        gap(content,28);TextView balanceHeading=text(c,"API 余额",14,muted);balanceHeading.setPadding(dp(c,16),0,0,dp(c,12));content.addView(balanceHeading);
        LinearLayout balanceCard=column(c);balanceCard.setBackground(background(card,16,c));balanceCard.setClipToOutline(true);content.addView(balanceCard);
        LinearLayout balanceInfo=column(c);balanceInfo.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,12));balanceCard.addView(balanceInfo);
        TextView balance=text(c,ApiSupport.key().isEmpty()?"配置 API Key 后查看余额":"正在查询…",24,fg);balanceInfo.addView(balance);
        TextView details=text(c,"",14,muted);balanceInfo.addView(details);
        Runnable[] refreshAction={()->{}};View refresh=ApiNativeViews.action(activity,"刷新余额",()->refreshAction[0].run(),false,card);balanceCard.addView(refresh,new LinearLayout.LayoutParams(-1,dp(c,48)));
        AtomicInteger request=new AtomicInteger(); final boolean[] busy={false};
        Runnable load=()->{
            String saved=ApiSupport.key();int generation=request.incrementAndGet();
            if(saved.isEmpty()) { balance.setTextSize(16);balance.setText("尚未配置 API Key");details.setText("");details.setVisibility(View.GONE);refresh.setEnabled(true);return; }
            refresh.setEnabled(false);balance.setTextSize(16);balance.setText("查询中…");details.setText("");details.setVisibility(View.GONE);
            new Thread(()->{
                String total,info;
                try { JSONObject data=ApiSupport.get("/user/balance",saved);JSONArray infos=data.getJSONArray("balance_infos");StringBuilder t=new StringBuilder(),i=new StringBuilder();
                    for(int n=0;n<infos.length();n++){JSONObject b=infos.getJSONObject(n);String currency=b.getString("currency");if(n>0){t.append("\n");i.append("\n");} t.append(currency.equals("CNY")?"¥":currency.equals("USD")?"$":currency+" ").append(b.getString("total_balance"));i.append("充值余额 ").append(b.getString("topped_up_balance")).append(" · 赠金 ").append(b.getString("granted_balance")).append(" ").append(currency);}
                    if(!data.getBoolean("is_available"))i.append("\n当前没有可用余额，请前往官方平台查看或充值。");total=t.length()==0?"暂无余额数据":t.toString();info=i.toString();
                }catch(Exception e){total="查询失败";info=ApiSupport.userError(e,"余额查询失败，请检查网络后重试。");}
                String t=total,i=info; activity.runOnUiThread(()->{if(!d.isShowing()||request.get()!=generation)return;balance.setTextSize(t.startsWith("¥")||t.startsWith("$")?24:16);balance.setText(t);details.setText(i);details.setVisibility(i.isEmpty()?View.GONE:View.VISIBLE);refresh.setEnabled(true);});
            },"PersonalApiBalance").start();
        };
        refreshAction[0]=load;
        mode.setOnCheckedChangeListener((b,checked)->{if(checked&&ApiSupport.key().isEmpty()){mode.setChecked(false);status.setVisibility(View.VISIBLE);status.setText("请先输入并验证 API Key。");}else{ApiSupport.prefs().edit().putBoolean("enabled",checked).apply();}try{ApiNativeViews.update(modeView,ApiNativeViews.toggle(mode::isChecked,mode::setChecked));}catch(Exception e){throw new IllegalStateException(e);}});
        saveAction[0]=()->{String value=key.getText().toString().trim();if(value.isEmpty()||busy[0])return;busy[0]=true;save.setEnabled(false);status.setVisibility(View.VISIBLE);status.setText("正在验证…");
            new Thread(()->{String error=null;try{ApiSupport.get("/models",value);ApiSupport.saveKey(value);}catch(Exception e){error=ApiSupport.userError(e,"验证失败，请检查网络后重试。");}String result=error;
                activity.runOnUiThread(()->{busy[0]=false;if(!d.isShowing())return;save.setEnabled(true);if(result!=null){status.setText(result);}else{key.setText("");key.setHint("已保存 API Key（输入新 Key 可替换）");status.setText("验证成功，已启用 API 额度。");mode.setChecked(true);load.run();}});
            },"PersonalApiKeyCheck").start();};
        gap(content,28);View topup=ApiNativeViews.action(activity,"前往官方 API 平台充值",()->openOfficial(c,"https://platform.deepseek.com/top_up"),true,card);content.addView(topup,new LinearLayout.LayoutParams(-1,dp(c,48)));
        gap(content,28);View manage=ApiNativeViews.action(activity,"创建 / 管理 API Key",()->openOfficial(c,"https://platform.deepseek.com/api_keys"),true,card);content.addView(manage,new LinearLayout.LayoutParams(-1,dp(c,48)));
        gap(content,8);TextView notice=text(c,"充值直接进入你在官方平台登录的账号，请确认它与 API Key 属于同一账号。返回后点击刷新余额。",14,muted);notice.setPadding(dp(c,16),0,dp(c,16),0);content.addView(notice);
        gap(content,28);View clear=ApiNativeViews.action(activity,"清除本机 API Key",()->new AlertDialog.Builder(c).setTitle("清除 API Key").setMessage("清除本机保存的 Key 并关闭 API 模式，聊天记录会保留。").setNegativeButton("取消",null).setPositiveButton("清除",(dialog,which)->{ApiSupport.prefs().edit().remove("key").putBoolean("enabled",false).apply();mode.setChecked(false);key.setText("");key.setHint("输入 DeepSeek API Key");status.setVisibility(View.GONE);load.run();}).show(),false,card);content.addView(clear,new LinearLayout.LayoutParams(-1,dp(c,48)));
        d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setStatusBarColor(bg);w.setNavigationBarColor(bg);if(!dark)w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        d.setOnDismissListener(v->request.incrementAndGet());d.show();if(w!=null)w.setLayout(-1,-1);root.requestApplyInsets();root.setFocusableInTouchMode(true);root.requestFocus();load.run();
    }
    static void openOfficial(Context c,String url){try{c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){ApiSupport.notice("请安装或启用系统浏览器后重试。");}}
    public static void openAbout(Context context){
        try{
            Activity activity=ApiSupport.activity.get();if(activity==null)return;
            Context c=activity;boolean dark=(c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            int bg=dark?0xff111111:0xfff7f7f7,card=dark?0xff202020:Color.WHITE,fg=dark?0xffeeeeee:0xff141414,muted=dark?0xffaaaaaa:0xff999999;
            Dialog d=new ApiPageDialog(c);
            LinearLayout root=column(c);root.setBackgroundColor(bg);ApiNativeViews.owners(root,activity);
            root.setOnApplyWindowInsetsListener((v,insets)->{int bottom=android.os.Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.systemBars()).bottom:insets.getSystemWindowInsetBottom();v.setPadding(0,0,0,bottom);return insets;});
            root.addView(ApiNativeViews.header(activity,"关于 DeepSeek API",d::dismiss),new LinearLayout.LayoutParams(-1,-2));
            ScrollView scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=column(c);body.setPadding(dp(c,16),dp(c,28),dp(c,16),dp(c,24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            ImageView logo=new ImageView(c);logo.setImageDrawable(c.getPackageManager().getApplicationIcon(c.getApplicationInfo()));LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(c,64),dp(c,64));logoSize.gravity=Gravity.CENTER_HORIZONTAL;body.addView(logo,logoSize);gap(body,16);
            TextView name=text(c,"DeepSeek API",20,fg);name.setGravity(Gravity.CENTER);body.addView(name);gap(body,8);TextView version=text(c,"2.8.0 · 个人 API 版 UI5",14,muted);version.setGravity(Gravity.CENTER);body.addView(version);gap(body,32);
            LinearLayout cardView=column(c);cardView.setPadding(dp(c,16),dp(c,16),dp(c,16),dp(c,16));cardView.setBackground(background(card,16,c));body.addView(cardView);
            cardView.addView(text(c,"原应用：DeepSeek",16,fg));gap(cardView,12);
            cardView.addView(text(c,"基于 DeepSeek 原版应用重构，支持使用个人 API 额度（个人修改版，非官方发布）。",16,fg));
            gap(cardView,12);cardView.addView(text(c,ATTRIBUTION,16,fg));
            gap(body,12);TextView note=text(c,"API 额度由你自己的开放平台账号提供，充值在 DeepSeek 官方 API 平台完成。API 消息保存在本机，Key 使用 Android 系统密钥库加密。",14,muted);note.setPadding(dp(c,16),0,dp(c,16),0);body.addView(note);
            d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setStatusBarColor(bg);w.setNavigationBarColor(bg);if(!dark)w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}d.show();if(w!=null)w.setLayout(-1,-1);root.requestApplyInsets();
        }catch(Exception e){throw new IllegalStateException("关于页面初始化失败",e);}
    }
}
