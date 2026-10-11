/*
 * Copyright (C) 2023-2026 The LineageOS Project
 * Copyright (C) 2025-2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.hardware.input.InputManager.InputDeviceListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.InputDevice;

import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for Xiaomi stylus operations.
 * Automatically caps display refresh rate and activates touch pen mode (18)
 * only while the pen is in active use (detached from the magnetic dock).
 * Restores 144 Hz display refresh rate and normal touch mode (2) when docked or idle.
 */
public class PenUtils {

    private static final String TAG = "XiaomiPenUtils";
    private static final boolean DEBUG = SystemProperties.getBoolean(
            "persist.xiaomi.peripherals.debug", true);

    // Xiaomi pen hardware identifiers
    private static final int PEN_VENDOR_ID  = 6421;
    private static final int PEN_PRODUCT_ID = 19841;

    // Preference keys
    public static final String FORCE_STYLUS_KEY        = "force_recognize_stylus_key";
    public static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    // Static singletons
    private static InputManager      mInputManager;
    private static SharedPreferences mPreferences;
    private static RefreshUtils      mRefreshUtils;
    private static Handler           mHandler;
    private static Context           mContext;

    // State
    private static boolean mPenModeActive      = false;
    private static boolean mIsPenConnected     = false;
    private static boolean mIsPenAttached      = false;
    private static boolean mForceRecognize     = false;
    private static boolean mListenerRegistered = false;
    private static boolean mIsSetup            = false;
    private static String  mCurrentRefreshMode = "dynamic";

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /** @return true if setup() has been called and state is valid. */
    public static boolean isSetup() {
        return mIsSetup;
    }

    /**
     * Initialize all resources. Called from BootCompletedReceiver (and from
     * ScreenStateReceiver if the process was killed and restarted).
     */
    public static synchronized void setup(Context context) {
        mContext      = context.getApplicationContext();
        mInputManager = (InputManager) mContext.getSystemService(Context.INPUT_SERVICE);
        mPreferences  = PreferenceManager.getDefaultSharedPreferences(mContext);
        mRefreshUtils = new RefreshUtils(mContext);
        mHandler      = new Handler(Looper.getMainLooper());
        mIsSetup      = true;

        mCurrentRefreshMode = mPreferences.getString(STYLUS_REFRESH_RATE_KEY, "dynamic");
        mForceRecognize     = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);

        registerInputDeviceListener();

        logInfo("Setup complete — force=" + mForceRecognize + ", rate=" + mCurrentRefreshMode);
        updatePenState();
    }

    // -----------------------------------------------------------------------
    // InputDeviceListener management
    // -----------------------------------------------------------------------

    private static void registerInputDeviceListener() {
        if (mListenerRegistered || mInputManager == null) return;
        mInputManager.registerInputDeviceListener(mInputDeviceListener, mHandler);
        mListenerRegistered = true;
        logInfo("InputDeviceListener registered");
    }

    private static void unregisterInputDeviceListener() {
        if (!mListenerRegistered || mInputManager == null) return;
        mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
        mListenerRegistered = false;
        logInfo("InputDeviceListener unregistered");
    }

    // -----------------------------------------------------------------------
    // Core State Evaluation
    // -----------------------------------------------------------------------

    /**
     * Determines whether the pen is actively in use and switches digitizer/refresh rate.
     * The pen is in use iff:
     * 1. It is detached from the magnetic wireless charging dock (!mIsPenAttached), AND
     * 2. Either connected via Bluetooth (mIsPenConnected) OR Force Recognize is enabled (mForceRecognize).
     */
    public static synchronized void updatePenState() {
        if (!mIsSetup) {
            logError("updatePenState called before setup");
            return;
        }
        updatePenStateInternal(PenChargingManager.isPenPhysicallyAttached());
    }

    private static void updatePenStateInternal(boolean isAttached) {
        mForceRecognize = mPreferences.getBoolean(FORCE_STYLUS_KEY, false);
        mIsPenConnected = checkPenConnected();
        mIsPenAttached  = isAttached;

        boolean isPenInUse = (mIsPenConnected || mForceRecognize) && !mIsPenAttached;

        logInfo("updatePenState — connected=" + mIsPenConnected
                + ", attached=" + mIsPenAttached
                + ", force=" + mForceRecognize
                + " → inUse=" + isPenInUse
                + " (wasActive=" + mPenModeActive + ")");

        if (isPenInUse) {
            enablePenHardware();
        } else {
            disablePenHardware();
        }
    }

    private static void enablePenHardware() {
        if (mPenModeActive) {
            logDebug("Pen mode already enabled");
            // Re-apply refresh rate mode in case it changed
            if (mRefreshUtils != null) {
                mRefreshUtils.capRefreshRate(mCurrentRefreshMode);
            }
            return;
        }
        logInfo("Enabling pen mode (digitizer 18, refresh cap: " + mCurrentRefreshMode + ")");
        mPenModeActive = true;
        SystemProperties.set("persist.vendor.parts.pen", "18");
        if (mRefreshUtils != null) {
            mRefreshUtils.capRefreshRate(mCurrentRefreshMode);
        }
    }

    private static void disablePenHardware() {
        if (!mPenModeActive) {
            logDebug("Pen mode already disabled");
            return;
        }
        logInfo("Disabling pen mode (digitizer 2, restoring 144Hz)");
        mPenModeActive = false;
        SystemProperties.set("persist.vendor.parts.pen", "2");
        if (mRefreshUtils != null) {
            mRefreshUtils.restoreRefreshRate();
        }
    }

    // -----------------------------------------------------------------------
    // State-change callbacks
    // -----------------------------------------------------------------------

    /**
     * Called by PenChargingService when the stylus is physically attached or detached from the dock.
     */
    public static synchronized void onPenAttachmentChanged(boolean isAttached) {
        logInfo("Pen attachment changed → " + (isAttached ? "DOCKED" : "DETACHED"));
        mIsPenAttached = isAttached;
        if (mIsSetup) {
            updatePenStateInternal(isAttached);
        }
    }

    /**
     * Called when the user toggles "Force recognize stylus".
     */
    public static synchronized void onForceRecognizeChanged(boolean enabled) {
        logInfo("Force recognize changed → " + enabled);
        mForceRecognize = enabled;
        if (mIsSetup) {
            updatePenState();
        }
    }

    /**
     * Called when the user selects a new refresh rate profile ("dynamic", "60", "120").
     */
    public static synchronized void setRefreshRateMode(String mode) {
        logInfo("Refresh rate mode changed → " + mode);
        mCurrentRefreshMode = mode;
        if (mPenModeActive && mRefreshUtils != null) {
            mRefreshUtils.capRefreshRate(mode);
        }
    }

    // -----------------------------------------------------------------------
    // InputDeviceListener
    // -----------------------------------------------------------------------

    private static final InputDeviceListener mInputDeviceListener = new InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int id) {
            logDebug("Input device added: " + id);
            if (isDeviceXiaomiPen(id)) {
                logInfo("Xiaomi pen connected via input subsystem");
                mHandler.postDelayed(() -> updatePenState(), 100);
            }
        }

        @Override
        public void onInputDeviceRemoved(int id) {
            logDebug("Input device removed: " + id);
            mHandler.postDelayed(() -> updatePenState(), 100);
        }

        @Override
        public void onInputDeviceChanged(int id) {
            logDebug("Input device changed: " + id);
            if (isDeviceXiaomiPen(id)) {
                mHandler.postDelayed(() -> updatePenState(), 100);
            }
        }
    };

    // -----------------------------------------------------------------------
    // Device identification
    // -----------------------------------------------------------------------

    private static boolean checkPenConnected() {
        if (mInputManager == null) return false;
        try {
            for (int id : mInputManager.getInputDeviceIds()) {
                if (isDeviceXiaomiPen(id)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logError("Error checking connected devices: " + e.getMessage());
        }
        return false;
    }

    private static boolean isDeviceXiaomiPen(int id) {
        try {
            InputDevice device = mInputManager.getInputDevice(id);
            if (device == null) return false;
            boolean isPen = device.getVendorId() == PEN_VENDOR_ID
                    && device.getProductId() == PEN_PRODUCT_ID;
            if (isPen) logDebug("Found Xiaomi pen: " + device.getName());
            return isPen;
        } catch (Exception e) {
            logError("Error checking device: " + e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Public state accessors
    // -----------------------------------------------------------------------

    public static boolean isPenInUse()            { return mPenModeActive; }
    public static boolean isPenModeEnabled()      { return mPenModeActive; }
    public static boolean isPenConnected()        { return mIsPenConnected; }
    public static boolean isPenAttached()         { return mIsPenAttached; }
    public static boolean isForceRecognize()      { return mForceRecognize; }
    public static String  getCurrentRefreshMode() { return mCurrentRefreshMode; }

    public static synchronized void cleanup() {
        unregisterInputDeviceListener();
        if (mRefreshUtils != null) mRefreshUtils.cleanup();
        mIsSetup = false;
        logInfo("PenUtils cleaned up");
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------

    private static void logDebug(String message) {
        if (DEBUG) Log.d(TAG, ts() + message);
    }
    private static void logInfo(String message)  { Log.i(TAG, ts() + message); }
    private static void logError(String message) { Log.e(TAG, ts() + message); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
