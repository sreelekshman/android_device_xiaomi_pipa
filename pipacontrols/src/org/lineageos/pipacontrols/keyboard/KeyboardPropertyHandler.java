/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.keyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles keyboard mode changes from settings and controls the native
 * keyboard service via system properties / init.rc triggers.
 */
public class KeyboardPropertyHandler extends BroadcastReceiver {

    private static final String TAG    = "XiaomiKeyboardProperty";
    private static final String ACTION = "org.lineageos.pipacontrols.KEYBOARD_MODE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        boolean enabled = intent.getBooleanExtra("enabled", false);
        logInfo("Keyboard mode change requested: " + enabled);
        try {
            SystemProperties.set("persist.vendor.parts.keyboard", enabled ? "1" : "0");
            logInfo("Set persist.vendor.parts.keyboard=" + (enabled ? "1" : "0"));
        } catch (Exception e) {
            logError("Failed to set keyboard property: " + e.getMessage());
        }
    }

    private void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
