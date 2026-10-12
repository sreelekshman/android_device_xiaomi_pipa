/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.touch;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class BoostDurationFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                   SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "BoostDuration";

    private ListPreference mPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.boost_duration);

        mPref = findPreference(BoostDurationUtils.PREF_KEY);
        if (mPref == null) return;

        if (!BoostDurationUtils.isSupported()) {
            mPref.setEnabled(false);
            mPref.setSummary(R.string.boost_duration_not_supported);
            return;
        }

        mPref.setOnPreferenceChangeListener(this);
        updateSummary(mPref.getValue());
        syncEnabledState();
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        syncEnabledState();
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (TouchBoostUtils.PREF_KEY.equals(key)) syncEnabledState();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String duration = (String) newValue;
        new Thread(() -> {
            BoostDurationUtils.apply(duration);
            // Changing duration from UI implicitly enables Touch Boost
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit().putBoolean(TouchBoostUtils.PREF_KEY, true).apply();
        }, "boost-duration-apply").start();
        updateSummary(duration);
        Log.i(TAG, "Boost duration changed to: " + duration + "ms");
        return true;
    }

    private void syncEnabledState() {
        if (mPref == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPref.setEnabled(prefs.getBoolean(TouchBoostUtils.PREF_KEY, true));
    }

    private void updateSummary(String duration) {
        if (mPref == null || duration == null) return;
        int idx = mPref.findIndexOfValue(duration);
        if (idx >= 0) mPref.setSummary(mPref.getEntries()[idx]);
    }
}
