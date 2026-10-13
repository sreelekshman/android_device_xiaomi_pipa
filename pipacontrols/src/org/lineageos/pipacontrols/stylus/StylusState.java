/*
 * SPDX-FileCopyrightText: 2026 Mufasa
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.Context;
import android.graphics.Color;

import org.lineageos.pipacontrols.R;

/**
 * UI State Model for the Material You Stylus Status Chip.
 */
public enum StylusState {
    CHARGING(3500L),
    FULLY_CHARGED(4000L),
    CONNECTED(2800L),
    DISCONNECTED(2500L),
    LOW_BATTERY(4500L),
    CRITICAL_BATTERY(5000L),
    UNKNOWN(3500L);

    private final long mTimeoutMs;

    StylusState(long timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    public long getTimeoutMs() {
        return mTimeoutMs;
    }

    public String getTitle(Context context, String customName) {
        return (customName != null && !customName.isEmpty())
                ? customName
                : context.getString(R.string.stylus_title);
    }

    public String getSubtitle(Context context) {
        switch (this) {
            case FULLY_CHARGED:
                return context.getString(R.string.stylus_card_sub_charged);
            case CHARGING:
                return context.getString(R.string.stylus_card_sub_charging);
            case LOW_BATTERY:
                return context.getString(R.string.stylus_card_sub_low_battery);
            case CRITICAL_BATTERY:
                return context.getString(R.string.stylus_card_sub_critical_battery);
            case DISCONNECTED:
                return context.getString(R.string.stylus_card_sub_disconnected);
            case CONNECTED:
                return context.getString(R.string.stylus_card_sub_connected);
            case UNKNOWN:
            default:
                return context.getString(R.string.pen_charging_notification_content_unknown);
        }
    }

    public int getAccentColor(Context context) {
        if (context == null) return getAccentColor();
        switch (this) {
            case FULLY_CHARGED:
            case CHARGING:
                return context.getColor(R.color.stylus_accent_charging);
            case LOW_BATTERY:
                return context.getColor(R.color.stylus_accent_warning);
            case CRITICAL_BATTERY:
                return context.getColor(R.color.stylus_accent_critical);
            case CONNECTED:
                return context.getColor(R.color.stylus_accent_connected);
            case DISCONNECTED:
            case UNKNOWN:
            default:
                return context.getColor(R.color.stylus_accent_neutral);
        }
    }

    public int getAccentColor() {
        switch (this) {
            case FULLY_CHARGED:
            case CHARGING:
                return Color.parseColor("#4C662B"); // Material Green Accent
            case LOW_BATTERY:
                return Color.parseColor("#775A0B"); // Material Yellow/Orange Accent
            case CRITICAL_BATTERY:
                return Color.parseColor("#BA1A1A"); // Material Error Red Accent
            case CONNECTED:
                return Color.parseColor("#2E6088"); // Material Blue Accent
            case DISCONNECTED:
            case UNKNOWN:
            default:
                return Color.parseColor("#73777F"); // Material Neutral Variant
        }
    }
}
