/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.performance.cpuscheduler;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class CpuRampSpeedFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "CpuRampSpeed";

    private ListPreference mPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.cpu_ramp_speed);

        mPref = findPreference(CpuRampSpeedUtils.PREF_KEY);
        if (mPref == null) return;

        if (!CpuRampSpeedUtils.isSupported()) {
            mPref.setEnabled(false);
            mPref.setSummary(R.string.cpu_ramp_speed_not_supported);
            return;
        }

        mPref.setOnPreferenceChangeListener(this);
        updateSummary(mPref.getValue());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String preset = (String) newValue;
        new Thread(() -> CpuRampSpeedUtils.apply(preset), "cpu-ramp-speed-apply").start();
        updateSummary(preset);
        Log.i(TAG, "CPU ramp speed changed to: " + preset);
        return true;
    }

    private void updateSummary(String preset) {
        if (mPref == null || preset == null) return;
        int idx = mPref.findIndexOfValue(preset);
        if (idx >= 0) mPref.setSummary(mPref.getEntries()[idx]);
    }
}
