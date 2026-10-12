/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.performance.gpufreqcap;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class GpuFreqCapFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "GpuFreqCap";

    private ListPreference mCapPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.gpu_freq_cap);

        mCapPref = findPreference(GpuFreqCapUtils.PREF_KEY);
        if (mCapPref == null) return;

        if (!GpuFreqCapUtils.isSupported()) {
            mCapPref.setEnabled(false);
            mCapPref.setSummary(R.string.gpu_freq_cap_not_supported);
            return;
        }

        mCapPref.setOnPreferenceChangeListener(this);
        updateSummary(mCapPref.getValue());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String freq = (String) newValue;
        new Thread(() -> GpuFreqCapUtils.apply(freq), "gpu-freq-cap-apply").start();
        updateSummary(freq);
        Log.i(TAG, "GPU freq cap changed to: " + freq);
        return true;
    }

    private void updateSummary(String freq) {
        if (mCapPref == null || freq == null) return;
        int idx = mCapPref.findIndexOfValue(freq);
        if (idx >= 0) mCapPref.setSummary(mCapPref.getEntries()[idx]);
    }
}
