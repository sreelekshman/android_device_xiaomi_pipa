/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2025-2026 nullpointer1101
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RefreshUtils {
    private static final String TAG = "XiaomiRefreshUtils";
    private static final boolean DEBUG = true;

    public static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";
    public static final String KEY_MIN_REFRESH_RATE  = "min_refresh_rate";
    private static final String PREF_FILE_NAME       = "pen_refresh_prefs";

    private static final String KEY_IS_CAPPED        = "is_capped";
    private static final String KEY_SAVED_PEAK_RATE  = "saved_peak_rate";
    private static final String KEY_SAVED_MIN_RATE   = "saved_min_rate";

    // Pipa panel standard refresh rates
    public static final float PANEL_DEFAULT_PEAK_RATE = 144f;
    public static final float PANEL_DEFAULT_MIN_RATE  = 60f;
    public static final float PEN_MAX_RATE            = 120f;
    public static final float PEN_MIN_RATE            = 60f;

    private final Context mContext;
    private final SharedPreferences mSharedPrefs;
    private volatile boolean mIsCapped = false;

    public RefreshUtils(Context context) {
        mContext = context.getApplicationContext();
        mSharedPrefs = mContext.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
        mIsCapped = mSharedPrefs.getBoolean(KEY_IS_CAPPED, false);
        logInfo("RefreshUtils initialized (initially capped=" + mIsCapped + ")");
    }

    public boolean isCapped() {
        return mIsCapped;
    }

    public synchronized void capRefreshRate(String mode) {
        float minRate;
        float maxRate;
        switch (mode) {
            case "60":
                minRate = PEN_MIN_RATE;
                maxRate = PEN_MIN_RATE;
                break;
            case "120":
                minRate = PEN_MAX_RATE;
                maxRate = PEN_MAX_RATE;
                break;
            case "dynamic":
            default:
                minRate = PEN_MIN_RATE;
                maxRate = PEN_MAX_RATE;
                break;
        }
        applyCap(minRate, maxRate, mode);
    }

    public synchronized void setPenRefreshRate() {
        capRefreshRate("dynamic");
    }

    public synchronized void setFixedRefreshRate(float fixedRate) {
        capRefreshRate(String.valueOf((int) fixedRate));
    }

    private synchronized void applyCap(float minRate, float maxRate, String modeName) {
        if (!mIsCapped) {
            float currentPeak = Settings.System.getFloat(
                    mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, PANEL_DEFAULT_PEAK_RATE);
            float currentMin = Settings.System.getFloat(
                    mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, PANEL_DEFAULT_MIN_RATE);

            // If currentPeak is already capped or invalid, fall back to panel default (144Hz)
            float savedPeak = (currentPeak > PEN_MAX_RATE) ? currentPeak : PANEL_DEFAULT_PEAK_RATE;
            float savedMin = (currentMin > 0f) ? currentMin : PANEL_DEFAULT_MIN_RATE;

            mSharedPrefs.edit()
                    .putFloat(KEY_SAVED_PEAK_RATE, savedPeak)
                    .putFloat(KEY_SAVED_MIN_RATE, savedMin)
                    .putBoolean(KEY_IS_CAPPED, true)
                    .apply();

            logInfo("Saving pre-cap rates: min=" + savedMin + "Hz, peak=" + savedPeak + "Hz");
        }

        mIsCapped = true;
        try {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, minRate);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxRate);
            logInfo("Capped refresh rate for pen mode (" + modeName + "): " + minRate + "-" + maxRate + "Hz");
        } catch (Exception e) {
            logError("Failed to apply pen refresh rate cap: " + e.getMessage());
        }
    }

    public synchronized void restoreRefreshRate() {
        if (!mIsCapped) {
            logDebug("Not currently capped, skipping restore");
            return;
        }

        float savedPeak = mSharedPrefs.getFloat(KEY_SAVED_PEAK_RATE, PANEL_DEFAULT_PEAK_RATE);
        float savedMin = mSharedPrefs.getFloat(KEY_SAVED_MIN_RATE, PANEL_DEFAULT_MIN_RATE);

        // Safety fallback: if saved peak was <= 120Hz, always restore to 144Hz panel rate
        if (savedPeak <= PEN_MAX_RATE) {
            savedPeak = PANEL_DEFAULT_PEAK_RATE;
        }

        mSharedPrefs.edit()
                .putBoolean(KEY_IS_CAPPED, false)
                .apply();
        mIsCapped = false;

        try {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, savedMin);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, savedPeak);
            logInfo("Restored refresh rate: min=" + savedMin + "Hz, peak=" + savedPeak + "Hz");
        } catch (Exception e) {
            logError("Failed to restore refresh rate: " + e.getMessage());
        }
    }

    public synchronized void setDefaultRefreshRate() {
        restoreRefreshRate();
    }

    public void cleanup() {
        // No background listeners to unregister
    }

    private void logDebug(String message) {
        if (DEBUG) Log.d(TAG, ts() + message);
    }

    private void logInfo(String message) {
        Log.i(TAG, ts() + message);
    }

    private void logError(String message) {
        Log.e(TAG, ts() + message);
    }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
