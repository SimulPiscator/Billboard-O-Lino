package org.simulpiscator.billboardolino;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_USER_ACTION;

public class AlarmReceiver extends android.content.BroadcastReceiver {
    private static final String TAG = "bbl:AlarmRcv";

    private static boolean sSyncInProgress = false;
    private static PowerManager.WakeLock sAlarmWakeLock = null;
    private static void acquireAlarmWakeLock(Context context) {
        synchronized (AlarmReceiver.class) {
            if (sAlarmWakeLock != null)
                return;
            sAlarmWakeLock = newPartialWakeLock(context);
            sAlarmWakeLock.acquire();
        }
        Log.d(TAG, "alarm wake lock acquired");
    }

    public static void releaseAlarmWakeLock() {
        synchronized (AlarmReceiver.class) {
            if(sAlarmWakeLock == null)
                return;
            sAlarmWakeLock.release();
            sAlarmWakeLock = null;
        }
        Log.d(TAG, "alarm wake lock released");
    }
    private static PowerManager.WakeLock newPartialWakeLock(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult result = goAsync();
        final PowerManager.WakeLock wl = newPartialWakeLock(context);
        wl.acquire();
        new Thread() {
            @Override
            public void run() {
                try {
                    handleIntent(context, intent);
                }
                catch (Exception error) {
                    Log.e(TAG, String.format("%s: %s: %s", intent.getAction(), error.getClass(), error.getMessage()));
                }
                result.finish();
                wl.release();
            }
        }.start();
    }

    private void handleIntent(Context context, Intent intent) {

        if (intent.hasExtra(ImageSync.EXTRA_MANUAL_SYNC)) {
            synchronized (AlarmReceiver.class) {
                if(sSyncInProgress)
                    return;
                sSyncInProgress = true;
            }

            boolean isManualSync = intent.getBooleanExtra(ImageSync.EXTRA_MANUAL_SYNC, false);
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isScreenLocked = !pm.isScreenOn();
            ImageSync sync = new ImageSync(context, intent);
            Bundle prefs = sync.getPreferences();
            boolean pushyMode = prefs.getBoolean(ImageSync.KEY_PUSHY_MODE, false);
            boolean narcolepsyMode = prefs.getBoolean(ImageSync.KEY_NARCOLEPSY_MODE, false);

            Intent activityIntent = null;
            if (isManualSync || pushyMode) {
                // sync will be run from inside SyncActivity
                activityIntent = new Intent(context, SyncActivity.class);
                activityIntent.putExtra(ImageSync.EXTRA_MANUAL_SYNC, isManualSync);
            } else {
                // run sync now
                boolean contentChanged = sync.sync(isManualSync);
                if (isScreenLocked && contentChanged)
                    activityIntent = new Intent(context, WakeupActivity.class);
            }

            if (activityIntent != null) {
                activityIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_NO_USER_ACTION);
                activityIntent.putExtra(ImageSync.EXTRA_SLEEP_WHEN_DONE, isScreenLocked && narcolepsyMode);
                activityIntent.putExtra(ImageSync.EXTRA_PREFS, sync.getPreferences());
                acquireAlarmWakeLock(context);
                context.getApplicationContext().startActivity(activityIntent);
            }
            synchronized (AlarmReceiver.class) {
                sSyncInProgress = false;
            }
        }
    }
}
