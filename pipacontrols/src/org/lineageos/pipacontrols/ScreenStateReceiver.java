/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.lineageos.pipacontrols.stylus.PenUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles SCREEN_ON to re-apply pen mode after the display turns back on.
 */
public class ScreenStateReceiver extends BroadcastReceiver {

    private static final String TAG = "PipaControls";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;

        logInfo("Screen on received — updating pen state");

        try {
            if (!PenUtils.isSetup()) {
                logInfo("PenUtils not initialized — running full setup");
                PenUtils.setup(context);
            } else {
                PenUtils.updatePenState();
            }
        } catch (Exception e) {
            logError("Error handling screen on: " + e.getMessage());
        }
    }

    private void logDebug(String message) { Log.d(TAG, ts() + message); }
    private void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
