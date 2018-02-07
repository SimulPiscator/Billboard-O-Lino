package org.simulpiscator.billboardolino;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SHUTDOWN;
import static org.simulpiscator.billboardolino.Billboard.EXTRA_MANUAL_SYNC;
import static org.simulpiscator.billboardolino.Billboard.EXTRA_PREFS;

public class SleepScreenReceiver extends BroadcastReceiver {
    static final String TAG = "bbl:SleepScreenReceiver";

    static private Bitmap sMainBitmap;
    static private EInkFb.WaveformMode sWaveformMode = EInkFb.WaveformMode.INIT;
    static private boolean sActOnSleep = false;
    static private boolean sActOnShutdown = false;
    static private boolean sUpdateInProgress = false;
    static private final Object sMutex = new Object();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();
        new Thread() {
            @Override
            public void run() {
                try {
                    handleIntent(context, intent, pm);
                } catch (Exception error) {
                    Log.e(TAG, String.format("%s: %s: %s", intent.getAction(), error.getClass(), error.getMessage()));
                }
                wl.release();
            }
        }.start();
    }

    private void handleIntent(Context context, Intent intent, PowerManager pm) {
        boolean doDisplay = false;
        if(intent.hasExtra(EXTRA_PREFS)) {
            boolean manualSync = intent.getBooleanExtra(EXTRA_MANUAL_SYNC, false);
            Billboard s = new Billboard(context, intent);
            Log.d(TAG, "updating preferences");
            synchronized (sMutex) {
                sWaveformMode = s.getWaveformMode();
                sActOnSleep = s.getActOnSleep();
                sActOnShutdown = s.getActOnShutdown();
            }
            if(intent.getAction() == null) { // alarm intent
                boolean mayUpdate = false,
                        needUpdate = manualSync;
                synchronized (sMutex) {
                    needUpdate = needUpdate || sActOnSleep;
                    needUpdate = needUpdate || sActOnShutdown;
                    if(needUpdate && !sUpdateInProgress) {
                        sUpdateInProgress = true;
                        mayUpdate = true;
                    }
                }
                if(!mayUpdate) {
                    Log.d(TAG, "update in progress, ignoring request");
                } else if(needUpdate) {
                    Log.d(TAG, manualSync ? "manual update" : "scheduled update");
                    Bitmap b = s.renderImageFromURL(manualSync);
                    synchronized (sMutex) {
                        sUpdateInProgress = false;
                        sMainBitmap = b;
                        doDisplay = sActOnSleep && !pm.isScreenOn();
                    }
                }
            }
        }
        else if(intent.getAction() != null) {
            Log.d(TAG, intent.getAction());
            synchronized (sMutex) {
                if (intent.getAction().equals(ACTION_SCREEN_OFF))
                    doDisplay = sActOnSleep;
                else if (intent.getAction().equals(ACTION_SHUTDOWN))
                    doDisplay = sActOnShutdown;
            }
        }
        synchronized (sMutex) {
            if(doDisplay && sMainBitmap != null) {
                Log.d(TAG, "displaying image");
                EInkFb fb = new EInkFb();
                fb.putBitmap(sMainBitmap);
                fb.refreshSync(sWaveformMode);
                fb.close();
            }
        }
    }
}
