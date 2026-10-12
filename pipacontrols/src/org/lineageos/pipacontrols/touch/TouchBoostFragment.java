/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.touch;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class TouchBoostFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "TouchBoost";

    private SwitchPreferenceCompat mPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.touch_boost);

        mPref = findPreference(TouchBoostUtils.PREF_KEY);
        if (mPref == null) return;

        if (!TouchBoostUtils.isSupported()) {
            mPref.setEnabled(false);
            mPref.setSummary(R.string.touch_boost_not_supported);
            return;
        }

        mPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        new Thread(() -> TouchBoostUtils.apply(enabled, requireContext()),
                "touch-boost-apply").start();
        Log.i(TAG, "Touch boost: " + enabled);
        return true;
    }
}
