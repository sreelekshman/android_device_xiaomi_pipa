/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.lid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for lid/smart cover configuration.
 */
public class LidSettingsFragment extends SettingsBasePreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG     = "XiaomiLidSettings";
    private static final String LID_KEY = "lid_switch_key";

    private SharedPreferences mLidPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.lid_settings);
            mLidPreference = PreferenceManager.getDefaultSharedPreferences(getContext());
            SwitchPreferenceCompat switchPreference = findPreference(LID_KEY);
            if (switchPreference != null) {
                switchPreference.setChecked(mLidPreference.getBoolean(LID_KEY, true));
                switchPreference.setEnabled(true);
            } else {
                logError("Could not find lid switch preference");
            }
            logInfo("Lid settings fragment created");
        } catch (Exception e) {
            logError("Error creating lid settings: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mLidPreference.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mLidPreference.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (LID_KEY.equals(key)) {
            try {
                boolean newStatus = prefs.getBoolean(key, true);
                logInfo("Lid preference changed to: " + newStatus);
                Settings.Global.putInt(getActivity().getContentResolver(),
                        "lid_behavior", newStatus ? 1 : 0);
            } catch (Exception e) {
                logError("Error handling preference change: " + e.getMessage());
            }
        }
    }

    private void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}