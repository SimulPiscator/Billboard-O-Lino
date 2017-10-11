package org.simulpiscator.billboardolino;

import android.content.*;
import android.util.Log;

import static android.content.Intent.ACTION_BOOT_COMPLETED;

public class PowerBroadcastReceiver extends android.content.BroadcastReceiver {

    static private String TAG = "bbl:PowerBroadcastRcv";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, intent.getAction());
        if(intent.getAction().equals(ACTION_BOOT_COMPLETED))
            new ImageSync(context).updateAlarms();
    }
}

