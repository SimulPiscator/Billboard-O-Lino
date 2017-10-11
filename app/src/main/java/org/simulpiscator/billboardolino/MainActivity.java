package org.simulpiscator.billboardolino;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.TwoStatePreference;
import android.widget.Toast;

import java.net.URL;
import java.text.MessageFormat;

public class MainActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "bbl:MainAct";
    private static final int sNextSyncFieldUpdateIntervalMs = 2000;

    private Preference mSyncAuto;
    private ImageSync mSync = new ImageSync(this);
    private CountDownTimer mTimer = new CountDownTimer(Long.MAX_VALUE, sNextSyncFieldUpdateIntervalMs) {
        @Override
        public void onTick(long millisUntilFinished) { updateNextSyncField(); }
        @Override
        public void onFinish() {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        PreferenceGroup group = this.getPreferenceScreen();
        mSyncAuto = group.findPreference(ImageSync.KEY_SYNC_AUTO);
        mSync.updateAlarms();

        for (int i = 0; i < group.getPreferenceCount(); ++i) {
            Preference pref = group.getPreference(i);
            if (pref instanceof DialogPreference) {
                DialogPreference dp = (DialogPreference)pref;
                if(dp.getDialogTitle().equals(""))
                    dp.setDialogTitle(dp.getTitle());
            }
            if(pref instanceof TwoStatePreference) {
                TwoStatePreference p = (TwoStatePreference) pref;
                if(pref.getKey().equals(ImageSync.KEY_INSTALL)) {
                    p.setChecked(mSync.isInstalled());
                    p.setEnabled(RootScript.rootAvailable());
                }
                else if(pref.getKey().equals(ImageSync.KEY_NARCOLEPSY_MODE)) {
                    p.setEnabled(RootScript.rootAvailable());
                }
            }
            else if (pref instanceof EditTextPreference)
                onEditTextPreferenceChange((EditTextPreference)pref, null);
            else if (pref instanceof ListPreference)
                onListPreferenceChange((ListPreference)pref, null);
            else if (pref instanceof DateTimePreference)
                onDateTimePreferenceChange((DateTimePreference)pref, null);
            else if (pref instanceof DurationPreference)
                onDurationPreferenceChange((DurationPreference) pref, null);
            pref.setOnPreferenceChangeListener(this);
            pref.setOnPreferenceClickListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimer.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTimer.cancel();
        getPreferenceScreen().getEditor().commit();
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object value) {
        boolean ok = true;
        try {
            // validation
            if(pref.getKey().equals(ImageSync.KEY_INSTALL))
                ok = onInstallPreferenceChange(pref, (Boolean) value);
            else if(pref.getKey().equals(ImageSync.KEY_SYNC_URL))
                new URL((String)value);
            else if(pref.getKey().equals(ImageSync.KEY_NARCOLEPSY_MODE))
                ok = onSleepAfterWakeupPreferenceChange(pref, (Boolean) value);
            // state updates
            if (pref instanceof EditTextPreference)
                ok = onEditTextPreferenceChange((EditTextPreference) pref, (String) value);
            else if (pref instanceof ListPreference)
                onListPreferenceChange((ListPreference)pref, (String)value);
            else if(pref instanceof DateTimePreference)
                ok = onDateTimePreferenceChange((DateTimePreference) pref, (Long) value);
            else if(pref instanceof DurationPreference)
                ok = onDurationPreferenceChange((DurationPreference) pref, (Integer) value);
            if(ok && value != null) {
                for (final String s : new String[]{
                        ImageSync.KEY_ALARM_TYPE, ImageSync.KEY_SYNC_OFFSET, ImageSync.KEY_SYNC_AUTO,
                        ImageSync.KEY_SYNC_INTERVAL, ImageSync.KEY_SYNC_URL, ImageSync.KEY_INSTALL
                }) {
                    if (pref.getKey().equals(s)) {
                        SharedPreferences.Editor editor = pref.getEditor();
                        if (value instanceof Boolean)
                            editor.putBoolean(pref.getKey(), (Boolean) value);
                        else if (value instanceof Integer)
                            editor.putInt(pref.getKey(), (Integer) value);
                        else if (value instanceof Long)
                            editor.putLong(pref.getKey(), (Long) value);
                        else if (value instanceof String)
                            editor.putString(pref.getKey(), (String) value);
                        editor.commit();
                        mSync.resetSyncState();
                        updateNextSyncField();
                        break;
                    }
                }
            }

        } catch (Exception error) {
            ok = false;
            Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
        return ok;
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref.getKey().equals(ImageSync.KEY_SYNC_NOW)) {
            mSync.scheduleAlarm(System.currentTimeMillis());
        }
        return true;
    }


    private boolean onEditTextPreferenceChange(EditTextPreference pref, String value) {
        if(value == null)
            value = pref.getText();
        pref.setSummary(formatEscape(value));
        return true;
    }

    private boolean onListPreferenceChange(ListPreference pref, String value) {
        if(value == null)
            value = pref.getValue();
        int idx = pref.findIndexOfValue(value);
        final CharSequence[] entries = pref.getEntries();
        pref.setSummary(formatEscape(entries[idx].toString()));
        return true;
    }

    private boolean onDateTimePreferenceChange(DateTimePreference pref, Long value) {
        if(value == null)
            value = pref.getValue();
        String summary = MessageFormat.format(getString(R.string.summary_datetime), value);
        pref.setSummary(formatEscape(summary));
        return true;
    }

    private boolean onDurationPreferenceChange(DurationPreference pref, Integer value) {
        if(value == null)
            value = pref.getValue();
        String summary = pref.getValueAsString(value);
        if(summary.isEmpty())
            summary = getString(R.string.duration_none);
        pref.setSummary(formatEscape(summary));
        return true;
    }

    private boolean onInstallPreferenceChange(Preference pref, Boolean value) {
        return value == mSync.install(value);
    }

    private boolean onSleepAfterWakeupPreferenceChange(Preference pref, Boolean value) {
        if(value && !pref.getSharedPreferences().getBoolean(pref.getKey(), false))
            return RootScript.askForRoot(10000);
        return true;
    }

    private static String formatEscape(final String s) {
        return s.replace("%", "%%");
    }

    private void updateNextSyncField() {
        String summary;
        long nextSync = mSync.nextSyncTime();
        if(nextSync > 0)
            summary = MessageFormat.format(getString(R.string.sync_next), nextSync);
        else
            summary = getString(R.string.sync_none);
        mSyncAuto.setSummary(formatEscape(summary));
    }
}
