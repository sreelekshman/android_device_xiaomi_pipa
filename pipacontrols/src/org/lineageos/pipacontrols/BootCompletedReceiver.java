/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.pipacontrols.apppriority.AppPriorityUtils;
import org.lineageos.pipacontrols.keyboard.KeyboardUtils;
import org.lineageos.pipacontrols.performance.cpufreqcap.CpuFreqCapUtils;
import org.lineageos.pipacontrols.performance.cpugovernor.CpuGovernorUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.CoreMigrationUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.CpuRampSpeedUtils;
import org.lineageos.pipacontrols.performance.cpuscheduler.PreferBigCoresUtils;
import org.lineageos.pipacontrols.performance.gpufreqcap.GpuFreqCapUtils;
import org.lineageos.pipacontrols.performance.gpugovernor.GpuGovernorUtils;
import org.lineageos.pipacontrols.stylus.PenUtils;
import org.lineageos.pipacontrols.touch.BigCoresTouchUtils;
import org.lineageos.pipacontrols.touch.BoostDurationUtils;
import org.lineageos.pipacontrols.touch.TouchBoostUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "PipaControls";
    private static final long INIT_DELAY_MS = 3000;

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) return;

        Log.i(TAG, ts() + "Boot completed — init in " + INIT_DELAY_MS + "ms");

        final Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> new Thread(() -> initPeripherals(appContext), "pipa-boot-init").start(),
                INIT_DELAY_MS);
    }

    private static void initPeripherals(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean stylusEnabled   = prefs.getBoolean("stylus_mode_key", true);
        boolean forceRecognize  = prefs.getBoolean("force_recognize_stylus_key", true);
        boolean keyboardEnabled = prefs.getBoolean("keyboard_mode_key", false);

        if (keyboardEnabled) {
            try { KeyboardUtils.setup(context); } catch (Exception e) {
                Log.e(TAG, ts() + "Keyboard init failed: " + e.getMessage()); }
        }

        if (stylusEnabled || forceRecognize) {
            try { PenUtils.setup(context); } catch (Exception e) {
                Log.e(TAG, ts() + "Pen init failed: " + e.getMessage()); }
        }

        try { CpuGovernorUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "CPU governor restore failed: " + e.getMessage()); }

        try { CpuFreqCapUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "CPU freq cap restore failed: " + e.getMessage()); }

        try { GpuGovernorUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "GPU governor restore failed: " + e.getMessage()); }

        try { GpuFreqCapUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "GPU freq cap restore failed: " + e.getMessage()); }

        try { PreferBigCoresUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "Prefer big cores restore failed: " + e.getMessage()); }

        try { CoreMigrationUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "Core migration restore failed: " + e.getMessage()); }

        try { CpuRampSpeedUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "CPU ramp speed restore failed: " + e.getMessage()); }

        // Touch Boost restore handles the input_boost_ms write using saved duration
        try { TouchBoostUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "Touch boost restore failed: " + e.getMessage()); }

        try { BigCoresTouchUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "Big cores on touch restore failed: " + e.getMessage()); }

        // App priority — cgroup resets to 0 on every reboot
        try { AppPriorityUtils.restore(context); } catch (Exception e) {
            Log.e(TAG, ts() + "App priority restore failed: " + e.getMessage()); }
    }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
