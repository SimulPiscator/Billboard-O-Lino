package org.simulpiscator.billboardolino;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.TextView;

import static org.simulpiscator.billboardolino.Hardware.putDeviceToSleep;

public class SyncActivity extends Activity {

    static final String TAG = "bbl:SyncAct";
    private static final int sCloseDelayMs = 3000;
    private TextView mLogView = null;
    private boolean mSleepWhenDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );
        setContentView(R.layout.sync_activity);
        mLogView = (TextView) findViewById(R.id.logView);
        mLogView.setText("");

        Intent intent = getIntent();
        mSleepWhenDone = intent.getBooleanExtra(ImageSync.EXTRA_SLEEP_WHEN_DONE, false);
        boolean isManualSync = intent.getBooleanExtra(ImageSync.EXTRA_MANUAL_SYNC, false);
        new ImageSync(this, intent).sync(isManualSync);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AlarmReceiver.releaseAlarmWakeLock();
        if(isFinishing() && mSleepWhenDone)
            putDeviceToSleep();
    }

    public void displayMessage(String s) {
        mLogView.append(s + "\n");
    }

    public void done() {
        new Thread() {
            @Override
            public void run() {
                SystemClock.sleep(sCloseDelayMs);
                finish();
            }
        }.start();
    }
}
