/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.performance.cpuscheduler;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class PreferBigCoresFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "PreferBigCores";

    private SwitchPreferenceCompat mPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.prefer_big_cores);

        mPref = findPreference(PreferBigCoresUtils.PREF_KEY);
        if (mPref == null) return;

        if (!PreferBigCoresUtils.isSupported()) {
            mPref.setEnabled(false);
            mPref.setSummary(R.string.prefer_big_cores_not_supported);
            return;
        }

        mPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        new Thread(() -> PreferBigCoresUtils.apply(enabled), "prefer-big-cores-apply").start();
        Log.i(TAG, "Prefer big cores: " + enabled);
        return true;
    }
}
