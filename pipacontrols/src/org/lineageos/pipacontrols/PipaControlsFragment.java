/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.apppriority.AppPriorityActivity;
import org.lineageos.pipacontrols.apppriority.AppPriorityUtils;
import org.lineageos.pipacontrols.bypasscharging.BypassChargingActivity;
import org.lineageos.pipacontrols.bypasscharging.BypassChargingUtils;
import org.lineageos.pipacontrols.keyboard.KeyboardSettingsActivity;
import org.lineageos.pipacontrols.lid.LidSettingsActivity;
import org.lineageos.pipacontrols.performance.PerformanceResetUtils;
import org.lineageos.pipacontrols.performance.cpufreqcap.CpuFreqCapActivity;
import org.lineageos.pipacontrols.performance.cpufreqcap.CpuFreqCapUtils;
import org.lineageos.pipacontrols.performance.cpugovernor.CpuGovernorActivity;
import org.lineageos.pipacontrols.performance.cpugovernor.CpuGovernorUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.CoreMigrationActivity;
import org.lineageos.pipacontrols.performance.cpuscheduler.CoreMigrationUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.CpuRampSpeedActivity;
import org.lineageos.pipacontrols.performance.cpuscheduler.CpuRampSpeedUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.PreferBigCoresActivity;
import org.lineageos.pipacontrols.performance.cpuscheduler.PreferBigCoresUtils;
import org.lineageos.pipacontrols.performance.gpufreqcap.GpuFreqCapActivity;
import org.lineageos.pipacontrols.performance.gpufreqcap.GpuFreqCapUtils;
import org.lineageos.pipacontrols.performance.gpugovernor.GpuGovernorActivity;
import org.lineageos.pipacontrols.performance.gpugovernor.GpuGovernorUtils;
import org.lineageos.pipacontrols.refreshrate.RefreshActivity;
import org.lineageos.pipacontrols.refreshrate.RefreshUtils;
import org.lineageos.pipacontrols.saturation.SaturationActivity;
import org.lineageos.pipacontrols.stylus.StylusSettingsActivity;
import org.lineageos.pipacontrols.touch.BigCoresTouchActivity;
import org.lineageos.pipacontrols.touch.BigCoresTouchUtils;
import org.lineageos.pipacontrols.touch.BoostDurationActivity;
import org.lineageos.pipacontrols.touch.BoostDurationUtils;
import org.lineageos.pipacontrols.touch.TouchBoostActivity;
import org.lineageos.pipacontrols.touch.TouchBoostUtils;

public class PipaControlsFragment extends SettingsBasePreferenceFragment {

    private static final String TAG = "PipaControls";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.pipa_controls);

        wireActivity("pipa_stylus",            StylusSettingsActivity.class);
        wireActivity("pipa_keyboard",          KeyboardSettingsActivity.class);
        wireActivity("pipa_lid",               LidSettingsActivity.class);
        wireActivity("pipa_bypass_charging",   BypassChargingActivity.class);
        wireActivity("pipa_saturation",        SaturationActivity.class);
        wireActivity("pipa_refresh_rate",      RefreshActivity.class);
        wireActivity("pipa_app_priority",      AppPriorityActivity.class);
        wireActivity("pipa_cpu_governor",      CpuGovernorActivity.class);
        wireActivity("pipa_cpu_freq_cap",      CpuFreqCapActivity.class);
        wireActivity("pipa_gpu_governor",      GpuGovernorActivity.class);
        wireActivity("pipa_gpu_freq_cap",      GpuFreqCapActivity.class);
        wireActivity("pipa_prefer_big_cores",  PreferBigCoresActivity.class);
        wireActivity("pipa_core_migration",    CoreMigrationActivity.class);
        wireActivity("pipa_cpu_ramp_speed",    CpuRampSpeedActivity.class);
        wireActivity("pipa_touch_boost",       TouchBoostActivity.class);
        wireActivity("pipa_big_cores_touch",   BigCoresTouchActivity.class);
        wireActivity("pipa_boost_duration",    BoostDurationActivity.class);

        checkSupported("pipa_bypass_charging",  BypassChargingUtils.isSupported(),  R.string.bypass_charging_not_supported);
        checkSupported("pipa_app_priority",     AppPriorityUtils.isSupported(),     R.string.app_priority_not_supported);
        checkSupported("pipa_cpu_governor",     CpuGovernorUtils.isSupported(),     R.string.cpu_governor_not_supported);
        checkSupported("pipa_cpu_freq_cap",     CpuFreqCapUtils.isSupported(),      R.string.cpu_freq_cap_not_supported);
        checkSupported("pipa_gpu_governor",     GpuGovernorUtils.isSupported(),     R.string.gpu_governor_not_supported);
        checkSupported("pipa_gpu_freq_cap",     GpuFreqCapUtils.isSupported(),      R.string.gpu_freq_cap_not_supported);
        checkSupported("pipa_prefer_big_cores", PreferBigCoresUtils.isSupported(),  R.string.prefer_big_cores_not_supported);
        checkSupported("pipa_core_migration",   CoreMigrationUtils.isSupported(),   R.string.core_migration_not_supported);
        checkSupported("pipa_cpu_ramp_speed",   CpuRampSpeedUtils.isSupported(),    R.string.cpu_ramp_speed_not_supported);
        checkSupported("pipa_touch_boost",      TouchBoostUtils.isSupported(),      R.string.touch_boost_not_supported);
        checkSupported("pipa_big_cores_touch",  BigCoresTouchUtils.isSupported(),   R.string.big_cores_touch_not_supported);
        checkSupported("pipa_boost_duration",   BoostDurationUtils.isSupported(),   R.string.boost_duration_not_supported);

        Preference resetPref = findPreference("pipa_reset_all");
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(p -> {
                showResetDialog();
                return true;
            });
        }

        RefreshUtils.startService(requireContext());
        Log.i(TAG, "Pipa Controls fragment created");
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBypassChargingSummary();
    }

    private void updateBypassChargingSummary() {
        Preference pref = findPreference("pipa_bypass_charging");
        if (pref != null && BypassChargingUtils.isSupported()) {
            pref.setSummary(BypassChargingUtils.getCurrentHardwareState()
                    ? R.string.bypass_charging_status_on
                    : R.string.bypass_charging_summary);
        }
    }

    private void checkSupported(String key, boolean supported, int fallbackSummary) {
        if (supported) return;
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setEnabled(false);
            pref.setSummary(fallbackSummary);
        }
    }

    private void showResetDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_all_dialog_title)
                .setMessage(R.string.reset_all_dialog_message)
                .setPositiveButton(R.string.reset_all_confirm, (d, w) -> {
                    new Thread(() -> {
                        PerformanceResetUtils.resetAll(requireContext());
                        requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                R.string.reset_all_done, Toast.LENGTH_SHORT).show());
                    }, "pipa-reset").start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void wireActivity(String key, Class<?> activityClass) {
        Preference pref = findPreference(key);
        if (pref == null) {
            Log.w(TAG, "Preference not found: " + key);
            return;
        }
        pref.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(requireContext(), activityClass));
            return true;
        });
    }
}
