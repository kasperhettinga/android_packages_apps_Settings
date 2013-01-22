package com.android.settings.cyanogenmod;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

import com.android.settings.R;

public class AutoBrightnessCustomizeDialog extends AlertDialog
        implements DialogInterface.OnClickListener {
    private static final String TAG = "AutoBrightnessCustomizeDialog";

    private TextView mSensorLevel;
    private TextView mBrightnessLevel;
    private ListView mConfigList;

    private SensorManager mSensorManager;
    private Sensor mLightSensor;

    private static class SettingRow {
        int luxFrom;
        int luxTo;
        int backlight;
        public SettingRow(int luxFrom, int luxTo, int backlight) {
            this.luxFrom = luxFrom;
            this.luxTo = luxTo;
            this.backlight = backlight;
        }
    };

    private SettingRowAdapter mAdapter;
    private boolean mIsDefault;

    private SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final int lux = Math.round(event.values[0]);
            mSensorLevel.setText(getContext().getString(R.string.light_sensor_current_value, lux));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public AutoBrightnessCustomizeDialog(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final Context context = getContext();
        View view = getLayoutInflater().inflate(R.layout.dialog_auto_brightness_levels, null);
        setView(view);
        setTitle(R.string.auto_brightness_dialog_title);
        setCancelable(true);

        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setButton(DialogInterface.BUTTON_NEUTRAL,
                context.getString(R.string.auto_brightness_reset_button), this);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this);

        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        mSensorLevel = (TextView) view.findViewById(R.id.light_sensor_value);
        mBrightnessLevel = (TextView) view.findViewById(R.id.current_brightness);

        mConfigList = (ListView) view.findViewById(android.R.id.list);
        mAdapter = new SettingRowAdapter(context, new ArrayList<SettingRow>());
        mConfigList.setAdapter(mAdapter);
    }

    @Override
    protected void onStart() {
        updateSettings(false);

        super.onStart();

        mSensorManager.registerListener(mLightSensorListener,
                mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        Button neutralButton = getButton(DialogInterface.BUTTON_NEUTRAL);
        neutralButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSettings(true);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(mLightSensorListener, mLightSensor);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            putSettings();
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            cancel();
        }
    }

    private void updateSettings(boolean forceDefault) {
        int[] lux = null, values = null;

        if (!forceDefault) {
            lux = fetchItems(Settings.System.AUTO_BRIGHTNESS_LUX);
            values = fetchItems(Settings.System.AUTO_BRIGHTNESS_BACKLIGHT);
        }

        if (lux != null && values != null && lux.length != values.length - 1) {
            Log.e(TAG, "Found invalid backlight settings, ignoring");
            values = null;
        }

        if (lux == null || values == null) {
            final Resources res = getContext().getResources();
            lux = res.getIntArray(com.android.internal.R.array.config_autoBrightnessLevels);
            values = res.getIntArray(com.android.internal.R.array.config_autoBrightnessLcdBacklightValues);
            mIsDefault = true;
        } else {
            mIsDefault = false;
        }

        mAdapter.initFromSettings(lux, values);
    }

    private void showSettings(final int position) {
        final SettingRow row = mAdapter.getItem(position);
        final View v = getLayoutInflater().inflate(R.layout.auto_brightness_row_config, null);
        final EditText luxInput = (EditText) v.findViewById(R.id.ambient);
        final SeekBar backlightBar = (SeekBar) v.findViewById(R.id.backlight);
        final TextView backlightText = (TextView) v.findViewById(R.id.backlight_value);

        final AlertDialog d = new AlertDialog.Builder(getContext())
            .setTitle(R.string.auto_brightness_level_dialog_title)
            .setCancelable(true)
            .setView(v)
            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        int newLux = Integer.valueOf(luxInput.getText().toString());
                        int newBacklight = (backlightBar.getProgress() + 50) / 100;
                        mAdapter.updateRow(position, newLux, newBacklight);
                    } catch (NumberFormatException e) {
                        //ignored
                    }
                }
            })
            .setNeutralButton(R.string.auto_brightness_remove_button,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    mAdapter.removeRow(position);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .create();

        backlightBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean mIsDragging = false;

            private void updateBrightness(float brightness) {
                final Window window = d.getWindow();
                final WindowManager.LayoutParams params = window.getAttributes();
                params.screenBrightness = brightness;
                window.setAttributes(params);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mIsDragging) {
                    final float brightness = (float) progress / seekBar.getMax();
                    updateBrightness(brightness);
                }

                final int percent = Math.round((float) progress / PowerManager.BRIGHTNESS_ON);
                backlightText.setText(getContext().getString(
                        R.string.auto_brightness_brightness_format, percent));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                final float brightness = (float) seekBar.getProgress() / seekBar.getMax();
                updateBrightness(brightness);
                mIsDragging = true;
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
                mIsDragging = false;
            }
        });

        luxInput.setText(String.valueOf(row.luxFrom));
        luxInput.setEnabled(position != 0);
        backlightBar.setProgress(row.backlight * 100);

        d.show();
    }

    private void putSettings() {
        int[] lux = null, values = null;

        if (!mIsDefault) {
            lux = mAdapter.getLuxValues();
            values = mAdapter.getBacklightValues();
        }

        putItems(Settings.System.AUTO_BRIGHTNESS_LUX, lux);
        putItems(Settings.System.AUTO_BRIGHTNESS_BACKLIGHT, values);
    }

    private int[] fetchItems(String setting) {
        String value = Settings.System.getString(getContext().getContentResolver(), setting);
        if (value != null) {
            String[] values = value.split(",");
            if (values != null && values.length != 0) {
                int[] result = new int[values.length];
                int i;

                for (i = 0; i < values.length; i++) {
                    try {
                        result[i] = Integer.valueOf(values[i]);
                    } catch (NumberFormatException e) {
                        break;
                    }
                }
                if (i == values.length) {
                    return result;
                }
            }
        }

        return null;
    }

    private void putItems(String setting, int[] values) {
        String value = null;
        if (values != null) {
            final StringBuilder builder = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(values[i]);
            }
            value = builder.toString();
        }
        Settings.System.putString(getContext().getContentResolver(), setting, value);
    }

    private class SettingRowAdapter extends ArrayAdapter<SettingRow> {
        private View mAddView;

        public SettingRowAdapter(Context context, ArrayList<SettingRow> rows) {
            super(context, 0, rows);
            setNotifyOnChange(false);
        }

        private boolean isAddItem(int position) {
            return position == getCount() - 1;
        }

        private boolean isLastItem(int position) {
            return position == super.getCount() - 1;
        }

        public void initFromSettings(int[] lux, int[] values) {
            ArrayList<SettingRow> settings = new ArrayList<SettingRow>(values.length);
            for (int i = 0; i < lux.length; i++) {
                settings.add(new SettingRow(i == 0 ? 0 : lux[i - 1], lux[i], values[i]));
            }
            settings.add(new SettingRow(lux[lux.length - 1], Integer.MAX_VALUE, values[values.length - 1]));

            clear();
            addAll(settings);
            notifyDataSetChanged();
        }

        public int[] getLuxValues() {
            int count = super.getCount();
            int[] lux = new int[count - 1];

            for (int i = 0; i < count - 1; i++) {
                lux[i] = getItem(i).luxTo;
            }

            return lux;
        }

        public int[] getBacklightValues() {
            int count = super.getCount();
            int[] values = new int[count];

            for (int i = 0; i < count; i++) {
                values[i] = getItem(i).backlight;
            }
            return values;
        }

        public void addRow() {
            SettingRow lastRow = mAdapter.getItem(super.getCount() - 1);
            SettingRow newRow = new SettingRow(0, 0, lastRow.backlight);
            add(newRow);
            sanitizeValuesAndNotify();
        }

        public void removeRow(int position) {
            if (super.getCount() <= 1) {
                return;
            }

            remove(getItem(position));
            sanitizeValuesAndNotify();
        }

        public void updateRow(final int position, int newLuxFrom, int newBacklight) {
            final SettingRow row = getItem(position);

            if (row.backlight == newBacklight && (position == 0 || row.luxFrom == newLuxFrom)) {
                return;
            }

            row.backlight = newBacklight;
            if (position != 0) {
                row.luxFrom = newLuxFrom;
            }

            sanitizeValuesAndNotify();
        }

        public void sanitizeValuesAndNotify() {
            final int count = super.getCount();

            getItem(0).luxFrom = 0;
            for (int i = 1; i < count; i++) {
                SettingRow lastRow = getItem(i - 1);
                SettingRow thisRow = getItem(i);

                thisRow.luxFrom = Math.max(lastRow.luxFrom + 1, thisRow.luxFrom);
                thisRow.backlight = Math.max(lastRow.backlight, thisRow.backlight);
                lastRow.luxTo = thisRow.luxFrom;
            }
            getItem(count - 1).luxTo = Integer.MAX_VALUE;

            mIsDefault = false;
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return super.getCount() + 1;
        }

        @Override
        public SettingRow getItem(int position) {
            if (isAddItem(position)) {
                return null;
            }
            return super.getItem(position);
        }

        @Override
        public int getItemViewType(int position) {
            return isAddItem(position) ? 1 : 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (isAddItem(position)) {
                if (mAddView == null) {
                    mAddView = getLayoutInflater().inflate(
                            R.layout.auto_brightness_list_item_add, parent, false);
                    mAddView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            addRow();
                        }
                    });
                }
                return mAddView;
            }

            Holder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.auto_brightness_list_item, parent, false);
                holder = new Holder();
                holder.lux = (TextView) convertView.findViewById(R.id.lux);
                holder.backlight = (TextView) convertView.findViewById(R.id.backlight);
                holder.settingsButton = convertView.findViewById(R.id.settings);
                holder.settingsButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int item = (Integer) v.getTag();
                        showSettings(item);
                    }
                });
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }

            SettingRow row = (SettingRow) getItem(position);

            final String to = row.luxTo == Integer.MAX_VALUE ? "\u221e" : String.valueOf(row.luxTo);
            holder.lux.setText(getContext().getString(R.string.auto_brightness_level_format,
                    String.valueOf(row.luxFrom), to));

            int backlight = Math.round((float) row.backlight * 100F / PowerManager.BRIGHTNESS_ON);
            holder.backlight.setText(getContext().getString(R.string.auto_brightness_brightness_format,
                    String.valueOf(backlight)));

            holder.settingsButton.setTag(Integer.valueOf(position));

            return convertView;
        }

        private class Holder {
            TextView lux;
            TextView backlight;
            View settingsButton;
        };
    };
}
