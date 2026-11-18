import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

/** Match the original cu7/eh navigation: 1/5-width slide and staggered 105/195 ms fades. */
public final class ApiPageDialog extends Dialog {
    private View page,underlay;
    private float underlayAlpha,underlayX,offset;
    private AnimatorSet animation;
    private boolean closing;
    public ApiPageDialog(Context context) { super(context,android.R.style.Theme_DeviceDefault_Light_NoActionBar); }
    private boolean animatePage() { return android.os.Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled(); }
    private void restoreUnderlay(){if(underlay!=null){underlay.setAlpha(underlayAlpha);underlay.setTranslationX(underlayX);underlay=null;}}
    private static ObjectAnimator slide(View view,float to){ObjectAnimator a=ObjectAnimator.ofFloat(view,View.TRANSLATION_X,view.getTranslationX(),to);a.setDuration(300);a.setInterpolator(new PathInterpolator(.4f,0f,.2f,1f));return a;}
    private static ObjectAnimator fade(View view,float to,boolean entering){ObjectAnimator a=ObjectAnimator.ofFloat(view,View.ALPHA,view.getAlpha(),to);a.setDuration(entering?195:105);a.setStartDelay(entering?105:0);a.setInterpolator(entering?new PathInterpolator(0f,0f,.2f,1f):new PathInterpolator(.4f,0f,1f,1f));return a;}
    private void enter(){
        if(page==null||closing)return;
        offset=(int)(page.getWidth()/5);page.setTranslationX(offset);
        animation=new AnimatorSet();
        if(underlay!=null)animation.playTogether(slide(page,0f),fade(page,1f,true),slide(underlay,underlayX-offset),fade(underlay,0f,false));
        else animation.playTogether(slide(page,0f),fade(page,1f,true));
        animation.start();
    }
    @Override public void show() {
        Window window=getWindow();
        if(window!=null){window.setWindowAnimations(0);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        page=findViewById(android.R.id.content);
        Context context=getContext();
        if(context instanceof android.content.ContextWrapper){while(context instanceof android.content.ContextWrapper&&!(context instanceof Activity))context=((android.content.ContextWrapper)context).getBaseContext();}
        if(context instanceof Activity){underlay=((Activity)context).findViewById(android.R.id.content);if(underlay!=null){underlayAlpha=underlay.getAlpha();underlayX=underlay.getTranslationX();}}
        if(page!=null)page.setAlpha(animatePage()?0f:1f);
        super.show();closing=false;
        if(window!=null)window.setLayout(-1,-1);
        if(page!=null&&animatePage())page.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){page.getViewTreeObserver().removeOnPreDrawListener(this);enter();return true;}});
    }
    @Override public void dismiss() {
        if(closing)return;
        if(!isShowing()||page==null||!animatePage()){restoreUnderlay();super.dismiss();return;}
        closing=true;if(animation!=null){animation.removeAllListeners();animation.cancel();}
        animation=new AnimatorSet();
        if(underlay!=null)animation.playTogether(slide(page,offset),fade(page,0f,false),slide(underlay,underlayX),fade(underlay,underlayAlpha,true));
        else animation.playTogether(slide(page,offset),fade(page,0f,false));
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){animation=null;restoreUnderlay();ApiPageDialog.super.dismiss();}});
        animation.start();
    }
    @Override protected void onStop(){if(animation!=null){animation.removeAllListeners();animation.cancel();animation=null;}restoreUnderlay();super.onStop();}
}
