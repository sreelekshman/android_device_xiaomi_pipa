/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.keyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.SystemProperties;
import android.util.Log;
import android.view.InputDevice;

import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for Xiaomi keyboard operations.
 * Works in conjunction with the native xiaomi-keyboard service.
 */
public class KeyboardUtils {

    private static final String TAG = "XiaomiKeyboard";
    private static final boolean DEBUG = SystemProperties.getBoolean(
            "persist.xiaomi.keyboard.debug", false);
    private static final String KEYBOARD_MODE_KEY = "keyboard_mode_key";

    private static final int KEYBOARD_VENDOR_ID  = 5593;
    private static final int KEYBOARD_PRODUCT_ID = 163;

    private static InputManager mInputManager;
    private static boolean mLastEnabledState = false;

    /**
     * Initialize the keyboard utilities and set the initial device state.
     */
    public static void setup(Context context) {
        logInfo("Initializing Xiaomi keyboard framework integration");
        try {
            if (mInputManager == null) {
                mInputManager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean keyboardEnabled = prefs.getBoolean(KEYBOARD_MODE_KEY, false);
            if (!keyboardEnabled) {
                logInfo("Keyboard monitoring disabled by user — skipping setup");
                return;
            }
            // Set initial state based on whether the keyboard is physically present.
            boolean connected = isXiaomiKeyboardConnected();
            logInfo("Keyboard " + (connected ? "found" : "not found")
                    + " on boot — setting initial state: " + connected);
            setKeyboardEnabled(connected);
        } catch (Exception e) {
            logError("Error setting up keyboard utils: " + e.getMessage());
        }
    }

    public static void setKeyboardMonitoringEnabled(Context context, boolean enabled) {
        logInfo("Setting keyboard monitoring: " + enabled);
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean(KEYBOARD_MODE_KEY, enabled).apply();

            Intent intent = new Intent("org.lineageos.pipacontrols.KEYBOARD_MODE_CHANGED");
            intent.putExtra("enabled", enabled);
            context.sendBroadcast(intent);

            if (!enabled) setKeyboardEnabled(false);
        } catch (Exception e) {
            logError("Error setting keyboard monitoring: " + e.getMessage());
        }
    }

    public static boolean setKeyboardEnabled(boolean enabled) {
        if (enabled == mLastEnabledState) {
            logDebug("Keyboard already in requested state: " + enabled);
            return true;
        }
        logInfo("Setting keyboard enabled: " + enabled);
        boolean success = false;
        try {
            if (mInputManager == null) {
                logError("InputManager not initialized");
                return false;
            }
            boolean deviceFound = false;
            for (int id : mInputManager.getInputDeviceIds()) {
                if (isDeviceXiaomiKeyboard(id)) {
                    deviceFound = true;
                    if (enabled) mInputManager.enableInputDevice(id);
                    else         mInputManager.disableInputDevice(id);
                    mLastEnabledState = enabled;
                    success = true;
                }
            }
            if (!deviceFound) logInfo("Xiaomi keyboard not found in input devices");
        } catch (Exception e) {
            logError("Error changing keyboard state: " + e.getMessage());
        }
        return success;
    }

    /** Returns true if the Xiaomi keyboard is currently present in the input device list. */
    private static boolean isXiaomiKeyboardConnected() {
        if (mInputManager == null) return false;
        for (int id : mInputManager.getInputDeviceIds()) {
            if (isDeviceXiaomiKeyboard(id)) return true;
        }
        return false;
    }

    private static boolean isDeviceXiaomiKeyboard(int id) {
        try {
            InputDevice device = mInputManager.getInputDevice(id);
            if (device == null) return false;
            return device.getVendorId() == KEYBOARD_VENDOR_ID
                    && device.getProductId() == KEYBOARD_PRODUCT_ID;
        } catch (Exception e) {
            logError("Error checking device: " + e.getMessage());
            return false;
        }
    }

    private static void logDebug(String message) {
        if (DEBUG) Log.d(TAG, ts() + message);
    }
    private static void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private static void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
