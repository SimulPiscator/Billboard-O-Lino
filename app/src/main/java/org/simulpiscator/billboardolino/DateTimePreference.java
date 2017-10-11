package org.simulpiscator.billboardolino;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

class DateTimePreference extends DialogPreference {
    private DatePicker mDatePicker;
    private TimePicker mTimePicker;
    private long mValue = 0;

    public DateTimePreference(Context context) {
        this(context, null);
    }

    public DateTimePreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DateTimePreference(Context context, AttributeSet attrs,
                          int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public long getValue() {
        return mValue;
    }
    public void setValue(long value) {
        if(value != mValue && callChangeListener(value)) {
            mValue = value;
            persistLong(mValue);
            notifyChanged();
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mDatePicker = (DatePicker) view.findViewById(R.id.datePicker);
        mDatePicker.setMinDate(0);
        mDatePicker.setMaxDate(Long.MAX_VALUE);
        mTimePicker = (TimePicker) view.findViewById(R.id.timePicker);
        mTimePicker.setIs24HourView(true);

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(mValue);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        mDatePicker.init(year, month, day, null);
        mTimePicker.setCurrentHour(hour);
        mTimePicker.setCurrentMinute(min);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            int day = mDatePicker.getDayOfMonth();
            int month = mDatePicker.getMonth();
            int year = mDatePicker.getYear();
            int hour = mTimePicker.getCurrentHour();
            int min = mTimePicker.getCurrentMinute();
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, min, 0);
            setValue(calendar.getTimeInMillis());
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        String value = a.getString(index);
        if(value != null && !value.isEmpty())
            return Long.parseLong(value);
        return 0L;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        long value = mValue;
        if(defaultValue != null)
            value = (Long)defaultValue;
        if(restorePersistedValue)
            mValue = getPersistedLong(value);
        else
            setValue(value);
    }
}
