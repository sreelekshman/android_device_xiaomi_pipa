/*
 * SPDX-FileCopyrightText: 2026 Mufasa
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;

import org.lineageos.pipacontrols.R;

/**
 * Controller managing the Material You Stylus Status Chip overlay.
 * Handles WindowManager injection, Material 3 animations, in-place state transitions,
 * and auto-dismissal timeouts.
 */
public class StylusPillController {

    private static final String TAG = "StylusPillController";

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private StylusChargingPillView mPillView;
    private boolean mIsDismissing = false;

    private final Runnable mDismissRunnable = this::hidePill;

    public StylusPillController(Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Shows or updates the floating charging pill for a given StylusState.
     *
     * @param state        Current state (CHARGING, FULLY_CHARGED, CONNECTED, DISCONNECTED, etc.)
     * @param batteryLevel Battery percentage (0-100, or -1 if unknown)
     * @param customName   Device name, or null for default
     */
    public void showPopup(StylusState state, int batteryLevel, String customName) {
        mMainHandler.post(() -> {
            mMainHandler.removeCallbacks(mDismissRunnable);

            StylusChargingPillView.StylusPopupState popupState = mapState(state);
            String title = state.getTitle(mContext, customName);
            String subtitle = state.getSubtitle(mContext);
            Integer percent = (batteryLevel >= 0 && batteryLevel <= 100) ? batteryLevel : null;

            StylusChargingPillView.StylusPopupModel model =
                    new StylusChargingPillView.StylusPopupModel(title, subtitle, percent, popupState);

            // If a dismissal was in progress, cancel it immediately and re-use view
            if (mPillView != null) {
                mPillView.animate().cancel();
                mIsDismissing = false;

                if (mPillView.isAttachedToWindow()) {
                    // Update model in-place with subtle Material attention pulse
                    mPillView.setModel(model);
                    mPillView.setAlpha(1f);
                    mPillView.setTranslationY(0f);
                    mPillView.animate()
                            .scaleX(1.02f)
                            .scaleY(1.02f)
                            .setDuration(120)
                            .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                            .withEndAction(() -> {
                                if (mPillView != null) {
                                    mPillView.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(160)
                                            .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                                            .start();
                                }
                            })
                            .start();

                    announceAccessibility(title, subtitle, percent);
                    mMainHandler.postDelayed(mDismissRunnable, state.getTimeoutMs());
                    return;
                }
            }

            LayoutInflater inflater = LayoutInflater.from(mContext);
            View inflated = inflater.inflate(R.layout.stylus_charging_pill, null);
            StylusChargingPillView view;
            if (inflated instanceof StylusChargingPillView) {
                view = (StylusChargingPillView) inflated;
            } else {
                view = inflated.findViewById(R.id.stylus_charging_pill);
                if (view == null) {
                    view = new StylusChargingPillView(mContext);
                }
            }
            view.setModel(model);

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
            );
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            layoutParams.y = mContext.getResources().getDimensionPixelSize(R.dimen.stylus_card_top_margin);
            layoutParams.alpha = 1.0f;
            layoutParams.windowAnimations = 0;

            // Animate entrance: subtle slide down with Material Emphasized motion
            view.setAlpha(0f);
            view.setTranslationY(-dpToPx(16));
            view.setScaleX(0.96f);
            view.setScaleY(0.96f);

            try {
                mWindowManager.addView(view, layoutParams);
                mPillView = view;
                mIsDismissing = false;

                view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                        .start();

                announceAccessibility(title, subtitle, percent);
                mMainHandler.postDelayed(mDismissRunnable, state.getTimeoutMs());
            } catch (Exception e) {
                Log.e(TAG, "Failed to add stylus pill view to WindowManager", e);
                mPillView = null;
            }
        });
    }

    /**
     * Backward-compatible helper method.
     */
    public void showPill(int batteryLevel, boolean isFullyCharged) {
        StylusState state;
        if (isFullyCharged || batteryLevel >= 100) {
            state = StylusState.FULLY_CHARGED;
        } else if (batteryLevel >= 0) {
            state = StylusState.CHARGING;
        } else {
            state = StylusState.UNKNOWN;
        }
        showPopup(state, batteryLevel, null);
    }

    private StylusChargingPillView.StylusPopupState mapState(StylusState state) {
        if (state == null) return StylusChargingPillView.StylusPopupState.UNKNOWN;
        switch (state) {
            case CHARGING:
                return StylusChargingPillView.StylusPopupState.CHARGING;
            case FULLY_CHARGED:
                return StylusChargingPillView.StylusPopupState.FULLY_CHARGED;
            case CONNECTED:
                return StylusChargingPillView.StylusPopupState.CONNECTED;
            case DISCONNECTED:
                return StylusChargingPillView.StylusPopupState.DISCONNECTED;
            case LOW_BATTERY:
                return StylusChargingPillView.StylusPopupState.LOW_BATTERY;
            case CRITICAL_BATTERY:
                return StylusChargingPillView.StylusPopupState.CRITICAL_BATTERY;
            case UNKNOWN:
            default:
                return StylusChargingPillView.StylusPopupState.UNKNOWN;
        }
    }

    private void announceAccessibility(String title, String subtitle, Integer percent) {
        if (mPillView != null) {
            String battery = (percent != null && percent >= 0) ? (percent + "%") : "";
            mPillView.announceForAccessibility(title + ", " + subtitle + " " + battery);
        }
    }

    /**
     * Smoothly animates out and removes the pill view from WindowManager.
     */
    public void hidePill() {
        mMainHandler.post(() -> {
            StylusChargingPillView view = mPillView;
            if (view == null || mIsDismissing) return;
            mIsDismissing = true;

            view.animate()
                    .alpha(0f)
                    .translationY(-dpToPx(12))
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(200)
                    .setInterpolator(new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            try {
                                if (view.isAttachedToWindow()) {
                                    mWindowManager.removeViewImmediate(view);
                                }
                            } catch (Exception ignored) {}
                            if (mPillView == view) {
                                mPillView = null;
                            }
                            mIsDismissing = false;
                        }
                    })
                    .start();
        });
    }

    private float dpToPx(float dp) {
        return dp * mContext.getResources().getDisplayMetrics().density;
    }
}
