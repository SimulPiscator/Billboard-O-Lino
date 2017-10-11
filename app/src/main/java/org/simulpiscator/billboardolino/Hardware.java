package org.simulpiscator.billboardolino;

import android.util.Log;

class Hardware {
    static final String TAG = "bbl:Hardware";

    static void putDeviceToSleep() {
        RootScript rs = new RootScript("input keyevent 26");
        try {
            Log.d(TAG, "initiating sleep");
            rs.execute(1000);
        } catch (Exception e) {
            Log.e(TAG, "putDeviceToSleep(): " + e.getMessage());
        }
    }
}
