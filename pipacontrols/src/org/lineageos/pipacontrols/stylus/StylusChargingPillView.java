/*
 * SPDX-FileCopyrightText: 2026 Mufasa
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.lineageos.pipacontrols.R;

/**
 * Material You / Monet Stylus Status Chip View.
 * Uses native Android views with dynamic Monet system palette colors,
 * standard typography inheriting current system fonts, and elevation shadow.
 */
public class StylusChargingPillView extends FrameLayout {

    public enum StylusPopupState {
        CHARGING,
        FULLY_CHARGED,
        CONNECTED,
        DISCONNECTED,
        LOW_BATTERY,
        CRITICAL_BATTERY,
        UNKNOWN
    }

    public static class StylusPopupModel {
        public final String title;
        public final String subtitle;
        public final Integer batteryPercent;
        public final StylusPopupState state;

        public StylusPopupModel(String title, String subtitle, Integer batteryPercent, StylusPopupState state) {
            this.title = title;
            this.subtitle = subtitle;
            this.batteryPercent = batteryPercent;
            this.state = state;
        }
    }

    private View mIconContainer;
    private ImageView mIconView;
    private TextView mTitleView;
    private TextView mSubtitleView;
    private TextView mBatteryPercentView;

    private StylusPopupModel mModel;

    public StylusChargingPillView(Context context) {
        this(context, null);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StylusChargingPillView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureViews();
    }

    private void ensureViews() {
        if (mTitleView != null) return;
        mIconContainer = findViewById(R.id.stylus_icon_container);
        mIconView = findViewById(R.id.stylus_icon);
        mTitleView = findViewById(R.id.stylus_title);
        mSubtitleView = findViewById(R.id.stylus_subtitle);
        mBatteryPercentView = findViewById(R.id.stylus_battery_percent);
    }

    public void setModel(StylusPopupModel model) {
        mModel = model;
        ensureViews();
        if (mTitleView == null || model == null) return;

        Context context = getContext();

        // 1. Stylus Name and Status Text
        mTitleView.setText(model.title);
        mSubtitleView.setText(model.subtitle);

        // 2. Battery Percentage on the side
        if (model.batteryPercent != null && model.batteryPercent >= 0) {
            mBatteryPercentView.setVisibility(View.VISIBLE);
            mBatteryPercentView.setText(model.batteryPercent + "%");
        } else {
            mBatteryPercentView.setVisibility(View.GONE);
        }

        // 3. Status tints & Icon
        if (mIconContainer != null) {
            mIconContainer.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.stylus_icon_container_bg)));
        }
        if (mIconView != null) {
            mIconView.setImageResource(R.drawable.ic_stylus);
            mIconView.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.stylus_icon_tint)));
        }

        // 4. Subtitle color adaptation for warnings
        if (mSubtitleView != null) {
            if (model.state == StylusPopupState.CRITICAL_BATTERY) {
                mSubtitleView.setTextColor(context.getColor(R.color.stylus_accent_critical));
            } else if (model.state == StylusPopupState.LOW_BATTERY) {
                mSubtitleView.setTextColor(context.getColor(R.color.stylus_accent_warning));
            } else {
                mSubtitleView.setTextColor(context.getColor(R.color.stylus_text_secondary));
            }
        }
    }
}
