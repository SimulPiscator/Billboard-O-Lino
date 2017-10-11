package org.simulpiscator.billboardolino;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.caverock.androidsvg.SVG;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF;

class ImageSync {

    private static final String TAG = "bbl:ImageSync";

    static final String KEY_SYNC_URL = "sync_url";
    static final String KEY_SYNC_TIMEOUT = "sync_timeout";
    static final String KEY_BACKGROUND = "background";
    static final String KEY_ENABLE_WIFI = "enable_wifi";
    static final String KEY_PUSHY_MODE = "pushy_mode";
    static final String KEY_NARCOLEPSY_MODE = "narcolepsy_mode";

    static final String KEY_INSTALL = "install";
    static final String KEY_SYNC_NOW = "sync_now";
    static final String KEY_SYNC_AUTO = "sync_auto";
    static final String KEY_SYNC_OFFSET = "sync_offset";
    static final String KEY_SYNC_INTERVAL = "sync_interval";
    static final String KEY_ALARM_TYPE = "alarm_type";

    static final String EXTRA_MANUAL_SYNC = BuildConfig.APPLICATION_ID + ".MANUAL_SYNC";
    static final String EXTRA_SLEEP_WHEN_DONE = BuildConfig.APPLICATION_ID + ".SLEEP_WHEN_DONE";
    static final String EXTRA_PREFS = BuildConfig.APPLICATION_ID + ".SYNC_PREFS";

    private static final String RT_KEY_IMAGE_HASH = "image_hash";


    private class RuntimeStorage {
        RuntimeStorage putLong(String key, long value) {
            return putString(key, Long.toString(value));
        }
        long getLong(String key, long defval) {
            return Long.parseLong(getString(key, Long.toString(defval)));
        }
        RuntimeStorage putString(String key, String value) {
            try {
                final FileOutputStream os = mContext.openFileOutput(key, MODE_PRIVATE);
                PrintWriter wr = new PrintWriter(os);
                wr.print(value);
                wr.close();
                os.close();
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
            }
            return this;
        }
        String getString(String key, String defval) {
            try {
                final FileInputStream is = mContext.openFileInput(key);
                byte[] data = new byte[1024];
                int bytesread = is.read(data);
                is.close();
                return new String(data, 0, bytesread);
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
            }
            return defval;
        }
    }

    private static class SyncError extends Exception {
        SyncError(String what) { super(what); }
    }

    private Context mContext;
    private Intent mIntent;
    private SyncTask mSyncTask = null;
    private RuntimeStorage mRtStorage = new RuntimeStorage();

    private static final int sTimeResolutionMs = 100;

    ImageSync(Context context) {
        this(context, null);
    }

    ImageSync(Context context, Intent intent) {
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
                log("installing");
                script += getRawResource(R.raw.script_install);
            }
            else {
                log("uninstalling");
                script += getRawResource(R.raw.script_uninstall);
            }
            try {
                RootScript rs = new RootScript(script);
                if (rs.execute(filesdir) == 0)
                    isInstalled = isInstalled();
                else if (!rs.errorOutput().isEmpty())
                    throw new Exception(rs.errorOutput().trim());
                else
                    throw new Exception("installation failed");
            } catch(Exception e) {
                log(e.getMessage());
                Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        return isInstalled;
    }

    void updateAlarms() {
        scheduleAlarm(Long.MAX_VALUE);
        scheduleAlarm(-1);
    }

    void scheduleAlarm(long when) {
        boolean isManualSync = (when >= 0);
        if(when == -1)
            when = nextSyncTime();
        Bundle prefs = getPreferences();

        Intent intent = new Intent(mContext, AlarmReceiver.class);
        intent.putExtra(EXTRA_MANUAL_SYNC, true);
        intent.putExtra(EXTRA_PREFS, prefs);
        PendingIntent manualSyncIntent = PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        intent = new Intent(mContext, AlarmReceiver.class);
        intent.putExtra(EXTRA_MANUAL_SYNC, false);
        intent.putExtra(EXTRA_PREFS, prefs);
        PendingIntent autoSyncIntent = PendingIntent.getBroadcast(mContext, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
        if(when == Long.MAX_VALUE) {
            am.cancel(autoSyncIntent);
            log("auto sync canceled");
        }
        else if(when >= 0){
            int type = AlarmManager.RTC_WAKEUP;
            if(isManualSync) {
                am.set(type, when, manualSyncIntent);
                log("manual sync scheduled for " + timestampToString(when));
            }
            else {
                type = Integer.parseInt(prefs.getString(KEY_ALARM_TYPE, "4"));
                long interval = getSyncInterval();
                boolean repeat = interval > 0;
                if(repeat) {
                    try {
                        am.setRepeating(type, when, interval, autoSyncIntent);
                    } catch(Exception e) {
                        type = AlarmManager.RTC_WAKEUP;
                        am.setRepeating(type, when, interval, autoSyncIntent);
                    }
                    log("auto sync scheduled for " + timestampToString(when)
                            + ", repeated every " + durationToString(interval));
                }
                else {
                    try {
                        am.set(type, when, autoSyncIntent);
                    } catch (Exception e) {
                        type = AlarmManager.RTC_WAKEUP;
                        am.set(type, when, autoSyncIntent);
                    }
                    log("auto sync scheduled for " + timestampToString(when) + ", no repeat");
                }
            }

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
        long    syncinterval = getSyncInterval(),
                timeout = getTimeout();
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

    private Resources getResources() {
        return mContext.getApplicationContext().getResources();
    }

    private File resolvePath(String path) {
        if(!path.startsWith("/"))
            path = mContext.getApplicationContext().getFilesDir() + "/" + path;
        return new File(path);
    }

    private long getTimeout() {
        return getPreferences().getInt(KEY_SYNC_TIMEOUT, 0) * 1000;
    }
    private long getSyncInterval() {
        long syncinterval = getPreferences().getInt(KEY_SYNC_INTERVAL, 0) * 1000;
        syncinterval = Math.max(syncinterval, getTimeout() + 1000);
        return syncinterval;
    }

    private long calculateNextSync(long offset, long interval, long now) {
        if(offset >= now)
            return offset;
        return offset + ((now - offset + interval-1)/interval)*interval;
    }

    boolean sync(boolean isManualSync) {
        boolean needsWakeup = false;
        try {
            if(mContext instanceof SyncActivity) {
                mSyncTask = new SyncTask();
                mSyncTask.execute(isManualSync);
            }
            else {
                needsWakeup = doSync(isManualSync);
                postUpdate();
            }
        }
        catch(Exception error) {
            Log.e(TAG, String.format("%s: %s", error.getClass(), error.getMessage()));
        }
        return needsWakeup;
    }

    private boolean doSync(boolean isManualSync) {
        Bundle prefs = getPreferences();
        Context appContext = mContext.getApplicationContext();
        String error = "";
        WifiManager.WifiLock wifiLock = null;
        boolean disableWifi = false;
        boolean changed = true;

        WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        try {
            log((isManualSync ? "manual" : "automatic") + " synchronization started");
            boolean enablewifi = prefs.getBoolean(KEY_ENABLE_WIFI, true);

            if(enablewifi && !wm.isWifiEnabled()) {
                if(wm.setWifiEnabled(true)) {
                    disableWifi = true;
                    log("enabled wifi");
                }
                else {
                    throw new SyncError("failed to enable wifi");
                }
            }
            log("waiting for connection");
            long timeoutMs = getTimeout();
            long abortTime = SystemClock.uptimeMillis() + timeoutMs + sTimeResolutionMs;

            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            while ((info == null || !info.isConnected()) && SystemClock.uptimeMillis() < abortTime) {
                SystemClock.sleep(sTimeResolutionMs);
                info = cm.getActiveNetworkInfo();
            }
            if (info == null || !info.isConnected()) {
                throw new SyncError(String.format((Locale)null, "no internet connection within %d seconds", timeoutMs/1000));
            }

            wifiLock = wm.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, TAG);
            wifiLock.acquire();

            URL url = new URL(prefs.getString(KEY_SYNC_URL, ""));
            log("getting " + url);
            URLConnection connection = null;
            String contentType = null;
            int contentLength = -1;
            byte data[] = null;
            while(contentType == null && contentLength < 0 && SystemClock.uptimeMillis() < abortTime) {
                try {
                    connection = url.openConnection();
                    connection.setConnectTimeout((int)timeoutMs);
                    connection.setReadTimeout((int)timeoutMs);
                    connection.connect();
                    contentType = connection.getContentType();
                    contentLength = connection.getContentLength();
                } catch(SocketTimeoutException e) {
                    error = e.getMessage();
                    log(error);
                } catch(Exception e) {
                    error = e.getMessage();
                    log(error);
                    SystemClock.sleep(sTimeResolutionMs);
                }
            }
            Bitmap downloadedBitmap = null;
            SVG downloadedSvg = null;
            if (contentLength > 0) {
                error = "";
                data = new byte[contentLength];
                try {
                    InputStream download = connection.getInputStream();
                    int remaining = contentLength, pos = 0;
                    while(remaining > 0) {
                        int read = download.read(data, pos, remaining);
                        if(read < 0)
                            throw new EOFException("unexpected eof");
                        pos += read;
                        remaining -= read;
                    }
                    download.close();
                } catch (FileNotFoundException e) {
                    // 404 response will end up here
                    data = null;
                    error = "file not found: " + e.getMessage();
                    log(error);
                } catch (Exception e) {
                    data = null;
                    error = e.getMessage();
                    log(error);
                }
            }
            if (data == null) {
                log("could not read data from " + url);
            } else {
                String oldHash = mRtStorage.getString(RT_KEY_IMAGE_HASH, "");
                String newHash = getDataHash(data);
                changed = newHash.isEmpty() || !newHash.equals(oldHash);
                if (!changed) {
                    log("no change in remote content");
                } else {
                    log("remote content changed, updating");
                    mRtStorage.putString(RT_KEY_IMAGE_HASH, newHash);

                    downloadedBitmap = BitmapFactory.decodeByteArray(data, 0, contentLength);
                    if (downloadedBitmap == null && contentType != null) {
                        if (contentType.toLowerCase().startsWith("image/svg")) {
                            downloadedSvg = SVG.getFromInputStream(new ByteArrayInputStream(data));
                        }
                    }
                    if (downloadedBitmap == null && downloadedSvg == null)
                        throw new SyncError("could not decode image from " + url);
                }
            }
            if(changed) {
                boolean isInstalled = isInstalled();
                int templatepath_id = isInstalled ? R.array.template_path_installed : R.array.template_path;
                String[] templatepath = getResources().getStringArray(templatepath_id);
                HashMap<String, File> templates = new HashMap<String, File>();
                String filesdir = appContext.getFilesDir().getCanonicalPath() + "/";
                for (String entry : templatepath) {
                    if(!entry.startsWith("/"))
                        entry = filesdir + entry;
                    boolean isdir = entry.endsWith("/");

                    Process proc = Runtime.getRuntime().exec("ls " + entry);
                    if(proc.waitFor() == 0) {
                        InputStreamReader isr = new InputStreamReader(proc.getInputStream());
                        BufferedReader r = new BufferedReader(isr);
                        String s = r.readLine();
                        while(s != null) {
                            if(!s.isEmpty()) {
                                File f = new File(isdir ? entry + s : s);
                                templates.put(f.getName(), f);
                            }
                            s = r.readLine();
                        }
                    }
                }
                File targetdir = resolvePath(getResources().getString(
                        isInstalled ? R.string.target_image_path_installed : R.string.target_image_path
                ));
                if(!targetdir.exists() && !targetdir.mkdirs())
                    throw new SyncError("target directory \"" + targetdir + "\" could not be created");

                List<File> pictures = new ArrayList<File>();
                for (File file : templates.values()) {
                    if(file.exists() && !file.isDirectory()) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                        if (opts.outMimeType != null && opts.outHeight * opts.outWidth > 0)
                            pictures.add(file);
                    }
                }

                int fillcolor = Integer.parseInt(prefs.getString(KEY_BACKGROUND, "-1"));
                for (int i = 0; i < pictures.size(); ++i) {
                    File picture = pictures.get(i);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inMutable = true;
                    opts.inScaled = false;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bitmap = BitmapFactory.decodeFile(picture.getAbsolutePath(), opts);
                    Canvas c = new Canvas(bitmap);
                    if(fillcolor >= 0) {
                        c.drawARGB(0xff, fillcolor, fillcolor, fillcolor);
                    }
                    if (downloadedSvg != null) {
                        downloadedSvg.renderToCanvas(c);
                    } else if (downloadedBitmap != null) {
                        Rect src = new Rect(0, 0, downloadedBitmap.getWidth(), downloadedBitmap.getHeight());
                        Rect dest = new Rect(0, 0, c.getWidth(), c.getHeight());
                        c.drawBitmap(downloadedBitmap, src, dest, null);
                    } else if (!error.isEmpty()) {
                        Paint paint = new Paint();
                        paint.setTextSize(20);
                        paint.setTextAlign(Paint.Align.CENTER);
                        paint.setColor(Color.BLACK);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setFlags(Paint.ANTI_ALIAS_FLAG);
                        String text = error;
                        float maxWidth = c.getWidth() - 2*paint.getTextSize();
                        float ypos = c.getHeight()/2;
                        c.clipRect(0, ypos, c.getWidth(), ypos + 3*paint.getTextSize());
                        c.drawColor(Color.WHITE);
                        ypos += 2*paint.getTextSize();
                        boolean done = false;
                        while(!done) {
                            int count = paint.breakText(text, true, maxWidth, null);
                            String s = text.substring(0, count);
                            c.drawText(s, c.getWidth()/2, ypos, paint);
                            ypos += paint.getTextSize();
                            text = text.substring(count);
                            done = text.isEmpty() || s.isEmpty();
                        }
                    }
                    String name = picture.getName();
                    int dotpos = name.lastIndexOf('.');
                    if(dotpos > 0)
                      name = name.substring(0, dotpos);
                    name += ".jpg"; // tolino accepts jpg extension only
                    FileOutputStream output = new FileOutputStream(targetdir + "/" + name);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                    log("rendered " + picture.getName());
                }
            }
        }
        catch(SyncError e) {
            error = e.getMessage();
        }
        catch(Exception e) {
            error = e.getClass() + ": " + e.getMessage();
        }
        finally {
            if(wifiLock != null)
                wifiLock.release();
            if(disableWifi) {
                wm.setWifiEnabled(false);
                log("disabled wifi");
            }
        }
        if(error.isEmpty())
            log("\ndone");
        else
            log("\nfailed: " + error);
        return changed;
    }

    private class SyncTask extends AsyncTask<Boolean, String, Boolean> {
        // Asynchronous execution required to move network activity away from GUI thread
        void log(String s) {
            publishProgress(s);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if(mContext instanceof SyncActivity)
                ((SyncActivity)mContext).displayMessage(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean v) {
            super.onPostExecute(v);
            postUpdate();
        }

        @Override
        protected Boolean doInBackground(Boolean... args) {
            return doSync(args[0]);
        }
    }

    private static String timestampToString(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(ts);
    }

    private static String durationToString(long d) {
        return DurationPreference.getValueAsString((int)d/1000, null);
    }

    private void log(String msg) {
        boolean lf = false;
        if(msg.startsWith("\n")) {
            lf = true;
            msg = msg.substring(1);
        }
        String s = timestampToString(System.currentTimeMillis()) + ": " + msg;
        Log.d(TAG, s);
        if(mSyncTask != null) {
            if(lf)
                mSyncTask.log("");
            mSyncTask.log(msg);
        }
    }

    private void postUpdate() {
        if(mContext instanceof SyncActivity)
            ((SyncActivity)mContext).done();
        mSyncTask = null;
    }

    private String getDataHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            return Base64.encodeToString(hash, Base64.DEFAULT);
        } catch (Exception e) {
            log("getDataHash(): " + e.getClass() + ": " + e.getMessage());
        }
        return "";
    }
}
