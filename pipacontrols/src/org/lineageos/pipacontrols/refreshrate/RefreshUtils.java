/*
 * Copyright (C) 2020 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.refreshrate;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.UserHandle;

import android.provider.Settings;
import androidx.preference.PreferenceManager;

public final class RefreshUtils {

    private static final String REFRESH_CONTROL = "refresh_control";

    private static float defaultMaxRate;
    private static final String KEY_PEAK_REFRESH_RATE = "peak_refresh_rate";

    private Context mContext;
    protected static boolean isAppInList = false;

    protected static final int STATE_DEFAULT  = 0;
    protected static final int STATE_STANDARD = 1;
    protected static final int STATE_HIGH     = 2;
    protected static final int STATE_EXTREME  = 3;
    protected static final int STATE_ULTRA    = 4;

    private static final float REFRESH_STATE_DEFAULT  = 144f;
    private static final float REFRESH_STATE_STANDARD = 60f;
    private static final float REFRESH_STATE_HIGH     = 90f;
    private static final float REFRESH_STATE_EXTREME  = 120f;
    private static final float REFRESH_STATE_ULTRA    = 144f;

    private static final String REFRESH_STANDARD = "refresh.standard=";
    private static final String REFRESH_HIGH     = "refresh.high=";
    private static final String REFRESH_EXTREME  = "refresh.extreme=";
    private static final String REFRESH_ULTRA    = "refresh.ultra=";

    private SharedPreferences mSharedPrefs;

    protected RefreshUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new Intent(context, RefreshService.class),
                UserHandle.CURRENT);
    }

    private void writeValue(String profiles) {
        mSharedPrefs.edit().putString(REFRESH_CONTROL, profiles).apply();
    }

    protected void getOldRate() {
        if (org.lineageos.pipacontrols.stylus.PenUtils.isPenInUse()) {
            return;
        }
        defaultMaxRate = Settings.System.getFloat(mContext.getContentResolver(),
                KEY_PEAK_REFRESH_RATE, REFRESH_STATE_DEFAULT);
    }

    private String getValue() {
        String value = mSharedPrefs.getString(REFRESH_CONTROL, null);

        if (value == null || value.isEmpty()) {
            value = REFRESH_STANDARD + ":" + REFRESH_HIGH + ":" + REFRESH_EXTREME + ":" + REFRESH_ULTRA;
            writeValue(value);
        }
        return value;
    }

    protected void writePackage(String packageName, int mode) {
        String value = getValue();
        value = value.replace(packageName + ",", "");
        String[] modes = value.split(":");
        String finalString;

        switch (mode) {
            case STATE_STANDARD:
                modes[0] = modes[0] + packageName + ",";
                break;
            case STATE_HIGH:
                modes[1] = modes[1] + packageName + ",";
                break;
            case STATE_EXTREME:
                modes[2] = modes[2] + packageName + ",";
                break;
            case STATE_ULTRA:
                modes[3] = modes[3] + packageName + ",";
                break;
        }

        finalString = modes[0] + ":" + modes[1] + ":" + modes[2] + ":" + modes[3];
        writeValue(finalString);
    }

    protected int getStateForPackage(String packageName) {
        String value = getValue();
        String[] modes = value.split(":");
        int state = STATE_DEFAULT;
        if (modes[0].contains(packageName + ",")) {
            state = STATE_STANDARD;
        } else if (modes[1].contains(packageName + ",")) {
            state = STATE_HIGH;
        } else if (modes[2].contains(packageName + ",")) {
            state = STATE_EXTREME;
        } else if (modes[3].contains(packageName + ",")) {
            state = STATE_ULTRA;
        }
        return state;
    }

    protected void setRefreshRate(String packageName) {
        String value = getValue();
        float maxrate = defaultMaxRate;
        isAppInList = false;

        if (value != null) {
            String[] modes = value.split(":");
            if (modes[0].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_STANDARD; isAppInList = true;
            } else if (modes[1].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_HIGH;     isAppInList = true;
            } else if (modes[2].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_EXTREME;  isAppInList = true;
            } else if (modes[3].contains(packageName + ",")) {
                maxrate = REFRESH_STATE_ULTRA;    isAppInList = true;
            }
        }
        if (org.lineageos.pipacontrols.stylus.PenUtils.isPenInUse()) {
            if (!isAppInList) {
                return;
            }
            if (maxrate > 120f) {
                maxrate = 120f;
            }
        }

        Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, maxrate);
    }
}
