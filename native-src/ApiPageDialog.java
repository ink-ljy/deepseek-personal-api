import android.app.Dialog;
import android.content.Context;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Fade the entire added page, including system Back, without replacing native controls. */
public final class ApiPageDialog extends Dialog {
    private View page;
    private boolean closing;
    public ApiPageDialog(Context context) { super(context,android.R.style.Theme_DeviceDefault_Light_NoActionBar); }
    private boolean animatePage() { return android.os.Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled(); }
    @Override public void show() {
        Window window=getWindow();
        if(window!=null){window.setWindowAnimations(0);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        page=findViewById(android.R.id.content);
        if(page!=null)page.setAlpha(animatePage()?0f:1f);
        super.show();closing=false;
        if(page!=null)page.animate().alpha(1f).setDuration(220).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }
    @Override public void dismiss() {
        if(closing)return;
        if(!isShowing()||page==null||!animatePage()){super.dismiss();return;}
        closing=true;page.animate().cancel();
        page.animate().alpha(0f).setDuration(180).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(()->ApiPageDialog.super.dismiss()).start();
    }
}
