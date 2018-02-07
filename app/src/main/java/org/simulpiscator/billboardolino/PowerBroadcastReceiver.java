package org.simulpiscator.billboardolino;

import android.content.*;
import android.util.Log;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

public class PowerBroadcastReceiver extends android.content.BroadcastReceiver {

    static private String TAG = "bbl:PowerBroadcastRcv";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, intent.getAction());
        if(intent.getAction().equals(ACTION_BOOT_COMPLETED)) {
            Intent serviceIntent = new Intent(context, SleepScreenService.class);
            context.startService(serviceIntent);
            Billboard sync = new Billboard(context);
            sync.updateAlarms();
            sync.scheduleAlarm(System.currentTimeMillis());
            // may not load all content very first time (?)
            sync.scheduleAlarm(System.currentTimeMillis() + 1000);
        }
    }
}

