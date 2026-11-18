import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Hosts the supplied APK's actual Compose settings components in the quota screen. */
public final class ApiNativeViews {
    interface Content { void draw(Object composer) throws Exception; }
    static final AtomicInteger ids=new AtomicInteger(146736700);

    static Object callback(String type, java.lang.reflect.InvocationHandler handler)throws Exception {
        Class<?> c=ApiSupport.cls(type);
        return Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},handler);
    }
    static Object content(Content body)throws Exception {
        Object lambda=callback("lz4",(p,m,a)->{
            if(m.getName().equals("q")){body.draw(a[0]);return ApiSupport.unit();}
            return ApiUi.proxyObject(p,m,a);
        });
        return ApiSupport.make("f43",lambda,true,ids.incrementAndGet());
    }
    static Object themed(int stage,Content body)throws Exception {
        return content(composer->{
            if(stage==0)ApiSupport.call(ApiSupport.cls("oe6"),"a",null,themed(1,body),composer,48);
            else if(stage==1)ApiSupport.call(ApiSupport.cls("f73"),"a",6,themed(2,body),composer);
            else if(stage==2)ApiSupport.call(ApiSupport.cls("xv"),"a",6,themed(3,body),composer);
            else if(stage==3)ApiSupport.call(ApiSupport.cls("g1b"),"a",false,null,themed(4,body),composer,384,3);
            else body.draw(composer);
        });
    }
    static View create(Activity activity,Content body)throws Exception {
        View view=(View)ApiSupport.make("androidx.compose.ui.platform.ComposeView",activity);
        // A Dialog is a separate view tree; propagate the real Activity owners explicitly.
        owners(view,activity);
        update(view,body);
        return view;
    }
    static void owners(View view,Activity activity){
        view.setTag(0x7f0800f7,activity);
        view.setTag(0x7f0800fa,activity);
        view.setTag(0x7f0800fb,activity);
    }
    static void update(View view,Content body)throws Exception {ApiSupport.call(view,"setContent",themed(0,body));}
    static Object label(String title)throws Exception {
        return content(composer->ApiSupport.call(ApiSupport.cls("mza"),"b",title,null,0L,null,0L,null,0L,null,0L,0,false,1,0,null,null,composer,0,0,262142));
    }
    static View header(Activity activity,Runnable dismiss)throws Exception {
        return header(activity,"额度管理",dismiss);
    }
    static View header(Activity activity,String title,Runnable dismiss)throws Exception {
        return create(activity,composer->{
            Object click=callback("vy4",(p,m,a)->{if(m.getName().equals("v")){dismiss.run();return ApiSupport.unit();}return ApiUi.proxyObject(p,m,a);});
            ApiSupport.call(ApiSupport.cls("x4b"),"b",null,click,null,label(title),composer,0,1);
        });
    }
    static View action(Activity activity,String title,Runnable action,boolean arrow,int background)throws Exception {
        FrameLayout container=new FrameLayout(activity);
        container.setBackground(ApiUi.background(background,16,activity));container.setClipToOutline(true);
        View view=create(activity,composer->{
            Object click=callback("vy4",(p,m,a)->{if(m.getName().equals("v")){if(container.isEnabled())action.run();return ApiSupport.unit();}return ApiUi.proxyObject(p,m,a);});
            ApiSupport.call(ApiSupport.cls("py9"),"b",null,click,false,false,0L,null,null,arrow?ApiSupport.field(ApiSupport.cls("nl9"),"j"):null,0f,label(title),composer,0,0x17d);
        });
        container.addView(view,new FrameLayout.LayoutParams(-1,-1));
        return container;
    }
    static Content toggle(Supplier<Boolean> checked,Consumer<Boolean> change)throws Exception {
        return composer->{
            Object scheme=ApiSupport.call(composer,"j",ApiSupport.field(ApiSupport.cls("g1b"),"c"));
            Object changed=callback("xy4",(p,m,a)->{if(m.getName().equals("j")){change.accept((Boolean)a[0]);return ApiSupport.unit();}return ApiUi.proxyObject(p,m,a);});
            // Same trailing content lambda and green/white switch used by Data Controls.
            Object toggle=ApiSupport.make("tq",checked.get(),scheme,"使用 API 额度",changed,21,false);
            Object trailing=content(c->ApiSupport.call(toggle,"q",c,0));
            ApiSupport.call(ApiSupport.cls("py9"),"b",null,null,false,false,0L,null,trailing,null,0f,label("使用 API 额度"),composer,0,447);
        };
    }
}
