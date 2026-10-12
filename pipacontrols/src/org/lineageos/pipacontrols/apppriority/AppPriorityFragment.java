/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.apppriority;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

public class AppPriorityFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AppPriority";

    private ListPreference mPriorityPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.app_priority);

        mPriorityPref = findPreference(AppPriorityUtils.PREF_KEY);
        if (mPriorityPref == null) return;

        if (!AppPriorityUtils.isSupported()) {
            mPriorityPref.setEnabled(false);
            mPriorityPref.setSummary(R.string.app_priority_not_supported);
            return;
        }

        mPriorityPref.setOnPreferenceChangeListener(this);
        updateSummary(mPriorityPref.getValue());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String level = (String) newValue;
        new Thread(() -> AppPriorityUtils.apply(level), "app-priority-apply").start();
        updateSummary(level);
        Log.i(TAG, "Priority changed to: " + level);
        return true;
    }

    private void updateSummary(String level) {
        if (mPriorityPref == null || level == null) return;
        CharSequence entry = mPriorityPref.getEntries()[mPriorityPref.findIndexOfValue(level)];
        // Append inline warning only when High is selected
        if (AppPriorityUtils.LEVEL_HIGH.equals(level)) {
            mPriorityPref.setSummary(entry + " — " + getString(R.string.app_priority_high_warning_inline));
        } else {
            mPriorityPref.setSummary(entry);
        }
    }
}
