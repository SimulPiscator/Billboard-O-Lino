package org.simulpiscator.billboardolino;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import java.text.MessageFormat;

class DurationPreference extends DialogPreference {

    public static final String XMLNS = "http://schema.durationpref.org";
    public static final String ATTRIBUTE_MAX_VALUE = "maxValue";

    private static final int FACTOR = 0, UNIT = 1, PICKER = 2;
    private static final int[][] sElements = {
            { 60, R.array.unit_second, R.id.secondsPicker },
            { 60, R.array.unit_minute, R.id.minutesPicker },
            { 24, R.array.unit_hour, R.id.hoursPicker },
            { Integer.MAX_VALUE, R.array.unit_day, R.id.daysPicker },
    };

    private static final int sDefaultValue = 0;
    private final Context mContext;
    private View mView;
    private int mValue = sDefaultValue, mMaxValue = Integer.MAX_VALUE;

    public DurationPreference(Context context) { this(context, null); }

    public DurationPreference(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public DurationPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        if(attrs != null)
            mMaxValue = attrs.getAttributeIntValue(XMLNS, ATTRIBUTE_MAX_VALUE, mMaxValue);
        mValue = getPersistedInt(sDefaultValue);
        mValue = Math.min(mValue, mMaxValue);
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        if(value != mValue && callChangeListener(value)) {
            mValue = value;
            persistInt(mValue);
            notifyChanged();
        }
    }

    public String getValueAsString() {
        return getValueAsString(mValue);
    }

    public String getValueAsString(int value) {
        return getValueAsString(value, mContext);
    }

    public static String getValueAsString(int value, Context context) {
        String s = "";
        for(int[] element : sElements) {
            int count = value % element[FACTOR];
            value /= element[FACTOR];
            if(context != null) {
                if(count > 0)
                    s = formatElement(element[UNIT], count, context) + " " + s;
            }
            else if(element[FACTOR] <= 60)
                s = String.format(":%02d", count) + s;
            else
                s = Integer.toString(count) + " days " + s.substring(1);
        }
        return s.trim();
    }

    private static String formatElement(int unit_id, int count, Context context) {
        Resources r = context.getResources();
        String[] unit = r.getStringArray(unit_id);
        int index = Math.min(unit.length - 1, count);
        return MessageFormat.format(unit[index], count);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mView = view;

        int value = Math.min(mMaxValue, mValue);
        int factor = 1;
        for(int[] element : sElements) {
            NumberPicker picker = (NumberPicker)view.findViewById(element[PICKER]);
            if(mMaxValue/factor > 0) {
                picker.setMinValue(0);
                picker.setMaxValue(Math.min(mMaxValue/factor, element[FACTOR]-1));
                picker.setValue(value/factor % element[FACTOR]);
                if(picker.getMaxValue() > 999)
                    picker.setWrapSelectorWheel(false);
            }
            else {
                View parent = (View)picker.getParent();
                parent.setVisibility(View.GONE);
            }
            factor *= element[FACTOR];
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            int value = 0;
            int factor = 1;
            for(int[] element : sElements) {
                NumberPicker picker = (NumberPicker)mView.findViewById(element[PICKER]);
                if(picker != null) {
                    picker.clearFocus();
                    value += picker.getValue()*factor;
                }
                factor *= element[FACTOR];
            }
            setValue(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, sDefaultValue);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        int value = mValue;
        if(defaultValue != null)
            value = (Integer)defaultValue;
        if(restorePersistedValue)
            mValue = getPersistedInt(value);
        else
            setValue(value);
    }
}
