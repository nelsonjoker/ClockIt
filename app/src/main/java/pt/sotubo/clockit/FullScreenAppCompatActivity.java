package pt.sotubo.clockit;

import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

public abstract class FullScreenAppCompatActivity extends AppCompatActivity {


    public static final String TAG = FullScreenAppCompatActivity.class.getName();

    /*
        1. Set Device owner and lock task/pin screen
        2. Set as home intent
        3. Disable power off button/ give a way to turn off the device - not possible
        4. Disable volume botton if required
        5. stop screen to turn off, or lock
     */

    final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            //| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {


        super.onCreate(savedInstanceState, persistentState);
        //PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        //wl.acquire();

    }

    @Override
    public void setContentView(View view) {
        /* Set the app into full screen mode */
        //getWindow().getDecorView().setSystemUiVisibility(flags);
        super.setContentView(view);
    }

    @Override
    public void setContentView(int layoutResID) {
        /* Set the app into full screen mode */
        //getWindow().getDecorView().setSystemUiVisibility(flags);
        super.setContentView(layoutResID);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        //android.provider.Settings.System.putInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS,10);



    }

    protected void setBrightnessLow() { setBrightness(0.001f);}
    protected void setBrightnessHigh() { setBrightness(1.0f);}

    protected void setBrightness(float b){
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAGS_CHANGED);
    }

}
