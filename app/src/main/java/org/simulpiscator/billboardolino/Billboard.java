package org.simulpiscator.billboardolino;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

import static android.content.Context.ALARM_SERVICE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;

class Billboard {

    private static final String TAG = "bbl:Billboard";

    static final String KEY_ACT_ON_SLEEP = "act_on_sleep";
    static final String KEY_ACT_ON_SHUTDOWN = "act_on_shutdown";
    static final String KEY_SYNC_URL = "sync_url";
    static final String KEY_ENABLE_JAVASCRIPT = "enable_javascript";
    static final String KEY_CONNECTIVITY_TIMEOUT = "connectivity_timeout";
    static final String KEY_ENABLE_WIFI = "enable_wifi";
    static final String KEY_IMAGE_ORIENTATION = "image_orientation";
    static final String KEY_WAVEFORM_MODE = "waveform_mode";
    static final String KEY_TRANSITION_IMAGE = "transition_image";
    static final String KEY_MODIFY_SYSTEM = "modify_system";

    static final String KEY_SYNC_NOW = "sync_now";
    static final String KEY_SYNC_AUTO = "sync_auto";
    static final String KEY_SYNC_OFFSET = "sync_offset";
    static final String KEY_SYNC_INTERVAL = "sync_interval";
    static final String KEY_ALARM_TYPE = "alarm_type";

    static final String EXTRA_MANUAL_SYNC = BuildConfig.APPLICATION_ID + ".MANUAL_SYNC";
    static final String EXTRA_PREFS = BuildConfig.APPLICATION_ID + ".SYNC_PREFS";
    static final String ACTION_UPDATE_PREFS = EXTRA_PREFS;

    private static class SyncError extends Exception {
        SyncError(String what) { super(what); }
    }
    private Context mContext;
    private Intent mIntent;

    private static final int sTimeResolutionMs = 100;

    Billboard(Context context) {
        this(context, null);
    }

    Billboard(Context context, Intent intent) {
        mContext = context;
        mIntent = intent;
    }

    boolean isInstalled() {
        try {
            Resources r = getResources();
            File    p1 = resolvePath(r.getString(R.string.original_image_path)),
                    p2 = resolvePath(r.getString(R.string.target_image_path_installed));
            return p1.getCanonicalPath().equals(p2.getCanonicalPath());
        }
        catch(IOException e) {
            return false;
        }
    }

    private String getRawResource(int id) {
        InputStream is = getResources().openRawResource(id);
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    boolean install(Boolean value) {
        boolean isInstalled = isInstalled();
        if(value != isInstalled && RootScript.rootAvailable()) {
            String filesdir = mContext.getApplicationContext().getFilesDir().getAbsolutePath();
            String script = getRawResource(R.raw.script_common);
            if(value) {
                Log.d(TAG, "modifying system partition");
                script += getRawResource(R.raw.script_install);
            }
            else {
                Log.d(TAG, "restoring original state");
                script += getRawResource(R.raw.script_uninstall);
            }
            try {
                RootScript rs = new RootScript(script);
                if (rs.execute(filesdir) == 0)
                    isInstalled = isInstalled();
                else if (!rs.errorOutput().isEmpty())
                    throw new Exception(rs.errorOutput().trim());
                else
                    throw new Exception("system modification failed");
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
                Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            if(value && isInstalled) {
                saveTransitionImage(getPreferences().getString(KEY_TRANSITION_IMAGE));
            }
        }
        return isInstalled;
    }

    void publishPrefs() {
        Intent intent = new Intent(mContext, SleepScreenReceiver.class);
        intent.putExtra(EXTRA_PREFS, getPreferences());
        intent.setAction(ACTION_UPDATE_PREFS);
        mContext.sendBroadcast(intent);
    }

    void updateAlarms() {
        scheduleAlarm(Long.MAX_VALUE);
        scheduleAlarm(-1);
    }

    void manualSync() {
        scheduleAlarm(-2);
    }

    void scheduleAlarm(long when) {
        if(when == -1)
            when = nextSyncTime();
        Bundle prefs = getPreferences();

        Intent intent = new Intent(mContext, SleepScreenReceiver.class);
        intent.putExtra(EXTRA_PREFS, prefs);
        PendingIntent autoSyncIntent = PendingIntent.getBroadcast(mContext, 1,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        intent.putExtra(EXTRA_MANUAL_SYNC, true);
        PendingIntent manualSyncIntent = PendingIntent.getBroadcast(mContext, 2,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
        if(when == Long.MAX_VALUE) {
            am.cancel(autoSyncIntent);
            Log.d(TAG, "auto sync canceled");
        }
        else if(when >= 0){
            int type = Integer.parseInt(prefs.getString(KEY_ALARM_TYPE, "4"));
            long interval = getSyncIntervalMs();
            boolean repeat = interval > 0;
            if(repeat) {
                try {
                    am.setRepeating(type, when, interval, autoSyncIntent);
                } catch(Exception e) {
                    type = AlarmManager.RTC_WAKEUP;
                    am.setRepeating(type, when, interval, autoSyncIntent);
                }
                Log.d(TAG, "auto sync scheduled for " + timestampToString(when)
                        + ", repeated every " + durationToString(interval));
            }
            else {
                try {
                    am.set(type, when, autoSyncIntent);
                } catch (Exception e) {
                    type = AlarmManager.RTC_WAKEUP;
                    am.set(type, when, autoSyncIntent);
                }
                Log.d(TAG, "auto sync scheduled for " + timestampToString(when) + ", no repeat");
            }
        }
        else if(when == -2) {
            am.set(AlarmManager.RTC, System.currentTimeMillis(), manualSyncIntent);
            Log.d(TAG, "manual sync scheduled");
        }
    }

    void resetSyncState() {
        updateAlarms();
    }

    long nextSyncTime() {
        Bundle prefs = getPreferences();
        if(!prefs.getBoolean(KEY_SYNC_AUTO, false))
            return -1;

        long    now = System.currentTimeMillis(),
                syncoffset = prefs.getLong(KEY_SYNC_OFFSET, 0);
        long    syncinterval = getSyncIntervalMs(),
                timeout = getConnectivityTimeoutMs();
        long minInterval = timeout + 1000;
        if(syncinterval < minInterval)
            syncinterval = minInterval;
        return calculateNextSync(syncoffset, syncinterval, now);
    }

    Bundle getPreferences() {
        if(mIntent == null)
            return getPreferencesAsBundle(mContext);
        return mIntent.getBundleExtra(EXTRA_PREFS);
    }

    private static Bundle getPreferencesAsBundle(Context context) {
        Bundle b = new Bundle();
        final Map<String, ?> prefs = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        for(Map.Entry<String, ?> p : prefs.entrySet()) {
            if(p.getValue() instanceof Boolean)
                b.putBoolean(p.getKey(), (Boolean)p.getValue());
            else if(p.getValue() instanceof Long)
                b.putLong(p.getKey(), (Long)p.getValue());
            else if(p.getValue() instanceof Integer)
                b.putInt(p.getKey(), (Integer)p.getValue());
            else
                b.putString(p.getKey(), p.getValue().toString());
        }
        return b;
    }

    private static void showToast(final Context context, final String message, final int duration) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(), message, duration).show();
            }
        });
    }

    private Resources getResources() {
        return mContext.getApplicationContext().getResources();
    }

    private File resolvePath(String path) {
        if(!path.startsWith("/"))
            path = mContext.getApplicationContext().getFilesDir() + "/" + path;
        return new File(path);
    }

    long getConnectivityTimeoutMs() {
        return getPreferences().getInt(KEY_CONNECTIVITY_TIMEOUT, 5)*1000;
    }
    long getSyncIntervalMs() {
        long syncinterval = getPreferences().getInt(KEY_SYNC_INTERVAL, 0) * 1000;
        syncinterval = Math.max(syncinterval, getConnectivityTimeoutMs() + 1000);
        return syncinterval;
    }
    EInkFb.WaveformMode getWaveformMode() {
        String mode = getPreferences().getString(KEY_WAVEFORM_MODE, "");
        if(mode.equals("MODE_INIT"))
            return EInkFb.WaveformMode.INIT;
        if(mode.equals("MODE_DU"))
            return EInkFb.WaveformMode.DU;
        if(mode.equals("MODE_GC16"))
            return EInkFb.WaveformMode.GC16;
        if(mode.equals("MODE_GC4"))
            return EInkFb.WaveformMode.GC4;
        if(mode.equals("MODE_A2"))
            return EInkFb.WaveformMode.A2;
        return EInkFb.WaveformMode.GC16;
    }
    boolean getActOnSleep() {
        return getPreferences().getBoolean(KEY_ACT_ON_SLEEP, false);
    }
    boolean getActOnShutdown() {
        return getPreferences().getBoolean(KEY_ACT_ON_SHUTDOWN, false);
    }

    private Bitmap renderTransitionImage(String imgDesc) {
        if (imgDesc.startsWith("blank_")) {
            int y = Integer.parseInt(imgDesc.substring(6));
            y = 255 * y / 100;
            int color = 0xff000000 | y << 16 | y << 8 | y;
            Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.setPixel(0, 0, color);
            return b;
        }
        return null;
    }

    boolean saveTransitionImage(String imgDesc) {
        boolean ok = false;
        try {
            String filename = getResources().getString(R.string.transition_image_location);
            Bitmap b = renderTransitionImage(imgDesc);
            b.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(filename));
            ok = true;
        } catch(IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return ok;
    }

    private long calculateNextSync(long offset, long interval, long now) {
        if(offset >= now)
            return offset;
        return offset + ((now - offset + interval-1)/interval)*interval;
    }

    Bitmap renderImageFromURL(boolean interactive) {
        Bundle prefs = getPreferences();
        String orientation = prefs.getString(KEY_IMAGE_ORIENTATION, "0");
        int or = Integer.parseInt(orientation);
        int width = 0, height = 0;
        EInkFb fb = new EInkFb();
        boolean flip = false, transpose = false;
        for (int i = 0; i < 4; ++i) {
            if ((fb.getOrientation().value + i) % 4 == or) {
                if (i % 2 == 0) {
                    width = fb.getWidth();
                    height = fb.getHeight();
                } else {
                    width = fb.getHeight();
                    height = fb.getWidth();
                    transpose = true;
                }
                if (i >= 2)
                    flip = true;
                break;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, fb.getConfig());
        fb.close();

        Context appContext = mContext.getApplicationContext();
        String error = "";
        WifiManager.WifiLock wifiLock = null;
        boolean disableWifi = false;

        WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        try {
            Log.d(TAG,"synchronization started");
            boolean enablewifi = prefs.getBoolean(KEY_ENABLE_WIFI, true);
            if (enablewifi && !wm.isWifiEnabled()) {
                if (wm.setWifiEnabled(true)) {
                    disableWifi = true;
                    Log.d(TAG, "enabled wifi");
                } else {
                    throw new SyncError(appContext.getString(R.string.message_wifi_enable_failed));
                }
            }

            Log.d(TAG, "waiting for connection");
            long timeoutMs = getConnectivityTimeoutMs();
            long abortTime = SystemClock.uptimeMillis() + timeoutMs + sTimeResolutionMs;

            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            while ((info == null || !info.isConnected()) && SystemClock.uptimeMillis() < abortTime) {
                SystemClock.sleep(sTimeResolutionMs);
                info = cm.getActiveNetworkInfo();
            }
            if (info == null || !info.isConnected()) {
                throw new SyncError(String.format(appContext.getString(R.string.message_no_connection), timeoutMs / 1000));
            }
            Log.d(TAG, "connected");
            wifiLock = wm.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, TAG);
            wifiLock.acquire();

            URL url = new URL(prefs.getString(KEY_SYNC_URL, ""));
            boolean js = prefs.getBoolean(KEY_ENABLE_JAVASCRIPT, false);
            WebRenderer renderer = new WebRenderer(appContext, url.toString(), width, height, js);
            error = renderer.getError();
            if (error.isEmpty())
                renderer.render(new Canvas(bitmap));
        } catch (SyncError e) {
            error = e.getMessage();
        } catch (Exception e) {
            error = e.getClass() + ": " + e.getMessage();
        } finally {
            if (wifiLock != null)
                wifiLock.release();
            if (disableWifi) {
                wm.setWifiEnabled(false);
                Log.d(TAG,"disabled wifi");
            }
        }
        if (error.isEmpty())
            Log.d(TAG, "done");
        else
            Log.e(TAG, "failed: " + error);

        if(interactive) {
            if (error.isEmpty())
                showToast(mContext, appContext.getString(R.string.message_synchronization_succeeded), Toast.LENGTH_SHORT);
            else
                showToast(mContext, appContext.getString(R.string.message_synchronization_failed) + "\n" + error, Toast.LENGTH_LONG);
        }
        if(!error.isEmpty())
            return null;
        if(!flip && !transpose)
            return bitmap;
        Matrix matrix = new Matrix();
        if(transpose)
            matrix.preRotate(90);
        if(flip)
            matrix.preScale(-1, -1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }

    private static String timestampToString(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(ts);
    }

    private static String durationToString(long d) {
        return DurationPreference.getValueAsString((int)d/1000, null);
    }

}
