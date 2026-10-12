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

public class BigCoresTouchFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "BigCoresOnTouch";

    private SwitchPreferenceCompat mPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.big_cores_touch);

        mPref = findPreference(BigCoresTouchUtils.PREF_KEY);
        if (mPref == null) return;

        if (!BigCoresTouchUtils.isSupported()) {
            mPref.setEnabled(false);
            mPref.setSummary(R.string.big_cores_touch_not_supported);
            return;
        }

        mPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean enabled = (Boolean) newValue;
        new Thread(() -> BigCoresTouchUtils.apply(enabled), "big-cores-touch-apply").start();
        Log.i(TAG, "Big cores on touch: " + enabled);
        return true;
    }
}
