package org.simulpiscator.billboardolino;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import static android.graphics.Color.WHITE;
import static org.simulpiscator.billboardolino.Hardware.putDeviceToSleep;

public class WakeupActivity extends Activity {

    private static String TAG = "bbl:WakeupAct";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        View view = new View(this);
        view.setBackgroundColor(WHITE);
        setContentView(view);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) // close immediately after display
            finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AlarmReceiver.releaseAlarmWakeLock();
        if(isFinishing())
            putDeviceToSleep();
    }
}
