/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeyboardSettingsFragment extends SettingsBasePreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG               = "XiaomiKeyboardSettings";
    private static final String KEYBOARD_MODE_KEY = "keyboard_mode_key";
    private static final String KEYBOARD_FOOTER_KEY = "keyboard_footer_key";

    private SharedPreferences      mKeyboardPreference;
    private MainSwitchPreference   mKeyboardModePref;
    private FooterPreference       mFooterPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            addPreferencesFromResource(R.xml.keyboard_settings);
            Context context = getContext();
            mKeyboardPreference = PreferenceManager.getDefaultSharedPreferences(context);
            mKeyboardModePref   = findPreference(KEYBOARD_MODE_KEY);
            mFooterPref         = findPreference(KEYBOARD_FOOTER_KEY);
            refreshUI();
            logInfo("Keyboard settings created");
        } catch (Exception e) {
            logError("Error creating preferences: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mKeyboardPreference.registerOnSharedPreferenceChangeListener(this);
        refreshUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        mKeyboardPreference.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (KEYBOARD_MODE_KEY.equals(key)) {
            try {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                logInfo("Keyboard mode changed to: " + enabled);
                KeyboardUtils.setKeyboardMonitoringEnabled(getContext(), enabled);
                refreshUI();
            } catch (Exception e) {
                logError("Error handling preference change: " + e.getMessage());
            }
        }
    }

    private void refreshUI() {
        try {
            boolean keyboardModeEnabled = mKeyboardPreference.getBoolean(KEYBOARD_MODE_KEY, false);
            if (mKeyboardModePref != null
                    && mKeyboardModePref.isChecked() != keyboardModeEnabled) {
                mKeyboardModePref.setChecked(keyboardModeEnabled);
            }
            updateFooterInfo();
        } catch (Exception e) {
            logError("Error refreshing UI: " + e.getMessage());
        }
    }

    private void updateFooterInfo() {
        if (mFooterPref == null) return;
        try {
            boolean keyboardModeEnabled = mKeyboardPreference.getBoolean(KEYBOARD_MODE_KEY, false);
            String statusText = keyboardModeEnabled
                    ? getString(R.string.keyboard_status_enabled)
                    : getString(R.string.keyboard_status_disabled);
            mFooterPref.setTitle(
                    getString(R.string.keyboard_more_info)
                            + "\n\n"
                            + getString(R.string.keyboard_footer_status, statusText));
        } catch (Exception e) {
            logError("Error updating footer: " + e.getMessage());
        }
    }

    private void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
