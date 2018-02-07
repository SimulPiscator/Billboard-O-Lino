package org.simulpiscator.billboardolino;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SHUTDOWN;

public class SleepScreenService extends Service {
    static final String TAG = "bbl:SleepScreenService";

    private SleepScreenReceiver mReceiver = new SleepScreenReceiver();

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        registerReceiver(mReceiver, new IntentFilter(ACTION_SCREEN_OFF));
        registerReceiver(mReceiver, new IntentFilter(ACTION_SHUTDOWN));
        registerReceiver(mReceiver, new IntentFilter(Billboard.ACTION_UPDATE_PREFS));
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
