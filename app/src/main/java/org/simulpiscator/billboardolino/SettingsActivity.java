package org.simulpiscator.billboardolino;

import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "bbl:PrefsAct";
    private static final int sNextSyncFieldUpdateIntervalMs = 2000;

    private Preference mSyncAuto;
    private Billboard mBillboard = new Billboard(this);
    private CountDownTimer mTimer = new CountDownTimer(Long.MAX_VALUE, sNextSyncFieldUpdateIntervalMs) {
        @Override
        public void onTick(long millisUntilFinished) { updateNextSyncField(); }
        @Override
        public void onFinish() {}
    };
    static private void enumeratePreferences(PreferenceGroup group, List<Preference> outPrefs) {
        for(int i = 0; i < group.getPreferenceCount(); ++i) {
            Preference pref = group.getPreference(i);
            if(pref instanceof PreferenceGroup)
                enumeratePreferences((PreferenceGroup)pref, outPrefs);
            else
                outPrefs.add(pref);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SleepScreenService.class));
        addPreferencesFromResource(R.xml.preferences);
        List<Preference> prefs = new ArrayList<Preference>();
        enumeratePreferences(this.getPreferenceScreen(), prefs);
        for (Preference pref : prefs) {
            if(pref.getKey().equals(Billboard.KEY_SYNC_AUTO))
                mSyncAuto = pref;
            if (pref instanceof DialogPreference) {
                DialogPreference dp = (DialogPreference)pref;
                if(dp.getDialogTitle().equals(""))
                    dp.setDialogTitle(dp.getTitle());
            }
            if(pref instanceof TwoStatePreference) {
                TwoStatePreference p = (TwoStatePreference) pref;
                if(pref.getKey().equals(Billboard.KEY_MODIFY_SYSTEM)) {
                    p.setChecked(mBillboard.isInstalled());
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
        mBillboard.scheduleAlarm(System.currentTimeMillis());
        mBillboard.updateAlarms();
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
            if(pref.getKey().equals(Billboard.KEY_MODIFY_SYSTEM))
                ok = onInstallPreferenceChange(pref, (Boolean) value);
            else if(pref.getKey().equals(Billboard.KEY_SYNC_URL))
                new URL((String)value);
            else if(pref.getKey().equals(Billboard.KEY_TRANSITION_IMAGE))
                ok = mBillboard.saveTransitionImage((String)value);
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
                        Billboard.KEY_ACT_ON_SLEEP, Billboard.KEY_ACT_ON_SHUTDOWN,
                        Billboard.KEY_ALARM_TYPE, Billboard.KEY_SYNC_AUTO,
                        Billboard.KEY_SYNC_OFFSET, Billboard.KEY_SYNC_INTERVAL,
                        Billboard.KEY_SYNC_URL, Billboard.KEY_ENABLE_JAVASCRIPT,
                        Billboard.KEY_IMAGE_ORIENTATION, Billboard.KEY_WAVEFORM_MODE,
                        Billboard.KEY_ENABLE_WIFI, Billboard.KEY_CONNECTIVITY_TIMEOUT,
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
                        mBillboard.resetSyncState();
                        updateNextSyncField();
                        mBillboard.publishPrefs();
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
        if (pref.getKey().equals(Billboard.KEY_SYNC_NOW)) {
            mBillboard.manualSync();
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
        String entry = idx < 0 ? "?" : entries[idx].toString();
        pref.setSummary(formatEscape(entry));
        return true;
    }

    private boolean onDateTimePreferenceChange(DateTimePreference pref, Long value) {
        if(value == null)
            value = pref.getValue();
        String summary = MessageFormat.format(getString(R.string.datetime_summary), value);
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
        return value == mBillboard.install(value);
    }

    private static String formatEscape(final String s) {
        return s.replace("%", "%%");
    }

    private void updateNextSyncField() {
        String summary;
        long nextSync = mBillboard.nextSyncTime();
        if(nextSync > 0)
            summary = MessageFormat.format(getString(R.string.sync_next), nextSync);
        else
            summary = getString(R.string.sync_none);
        mSyncAuto.setSummary(formatEscape(summary));
    }
}
