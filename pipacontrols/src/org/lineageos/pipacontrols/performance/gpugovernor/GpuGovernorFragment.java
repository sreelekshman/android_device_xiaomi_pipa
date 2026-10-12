/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.performance.gpugovernor;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class GpuGovernorFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "GpuGovernor";

    private ListPreference mGovPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.gpu_governor);

        mGovPref = findPreference(GpuGovernorUtils.PREF_KEY);
        if (mGovPref == null) return;

        if (!GpuGovernorUtils.isSupported()) {
            mGovPref.setEnabled(false);
            mGovPref.setSummary(R.string.gpu_governor_not_supported);
            return;
        }

        mGovPref.setOnPreferenceChangeListener(this);
        updateSummary(mGovPref.getValue());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String governor = (String) newValue;
        new Thread(() -> GpuGovernorUtils.apply(governor), "gpu-gov-apply").start();
        updateSummary(governor);
        Log.i(TAG, "GPU governor changed to: " + governor);
        return true;
    }

    private void updateSummary(String governor) {
        if (mGovPref == null || governor == null) return;
        int idx = mGovPref.findIndexOfValue(governor);
        if (idx >= 0) mGovPref.setSummary(mGovPref.getEntries()[idx]);
    }
}
