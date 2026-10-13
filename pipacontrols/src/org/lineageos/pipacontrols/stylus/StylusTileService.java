/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2025-2026 nullpointer1101
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import org.lineageos.pipacontrols.R;

/**
 * Quick Settings tile for Stylus / Force Recognize.
 */
public class StylusTileService extends TileService {

    private static final String TAG = "XiaomiStylusTile";

    static final String ACTION_STYLUS_CHANGED =
            "org.lineageos.pipacontrols.STYLUS_MODE_CHANGED";

    private static final String FORCE_RECOGNIZE_KEY     = "force_recognize_stylus_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mDelayedUpdateRunnable = () -> {
        boolean forceActive = PreferenceManager
                .getDefaultSharedPreferences(StylusTileService.this)
                .getBoolean(FORCE_RECOGNIZE_KEY, false);
        Log.d(TAG, "Delayed retry — forceActive=" + forceActive);
        updateTile(forceActive);
    };

    // -----------------------------------------------------------------------
    // Sync receiver — updates tile while QS panel is open
    // -----------------------------------------------------------------------

    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_STYLUS_CHANGED.equals(intent.getAction())) return;
            boolean forceActive = PreferenceManager
                    .getDefaultSharedPreferences(context)
                    .getBoolean(FORCE_RECOGNIZE_KEY, false);
            Log.d(TAG, "LocalBroadcast — forceActive=" + forceActive);
            updateTile(forceActive);
        }
    };

    // -----------------------------------------------------------------------
    // TileService lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onStartListening() {
        super.onStartListening();

        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mSettingsReceiver, new IntentFilter(ACTION_STYLUS_CHANGED));

        boolean forceActive = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(FORCE_RECOGNIZE_KEY, false);

        Log.d(TAG, "onStartListening — forceActive=" + forceActive
                + ", tile=" + (getQsTile() != null ? "ok" : "null"));

        updateTile(forceActive);

        // 350 ms retry for cold-boot window where getQsTile() is transiently null.
        mHandler.removeCallbacks(mDelayedUpdateRunnable);
        mHandler.postDelayed(mDelayedUpdateRunnable, 350);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mHandler.removeCallbacks(mDelayedUpdateRunnable);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSettingsReceiver);
    }

    // -----------------------------------------------------------------------
    // User interaction
    // -----------------------------------------------------------------------

    @Override
    public void onClick() {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "onClick — tile is null");
            return;
        }

        final boolean currentForce = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(FORCE_RECOGNIZE_KEY, false);
        final boolean newForce = !currentForce;
        Log.d(TAG, "onClick — toggle forceActive to " + newForce);

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(FORCE_RECOGNIZE_KEY, newForce)
                .apply();

        PenUtils.onForceRecognizeChanged(newForce);
        updateTile(newForce);

        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_STYLUS_CHANGED));
    }

    // -----------------------------------------------------------------------
    // Tile rendering
    // -----------------------------------------------------------------------

    private void updateTile(boolean forceActive) {
        final Tile tile = getQsTile();
        if (tile == null) {
            Log.w(TAG, "updateTile — tile is null");
            return;
        }

        boolean inUse = PenUtils.isPenInUse();
        boolean isAttached = PenUtils.isPenAttached();
        String rate = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(STYLUS_REFRESH_RATE_KEY, "dynamic");

        boolean isActiveTile = inUse || forceActive;

        tile.setState(isActiveTile ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this,
                isActiveTile ? R.drawable.ic_stylus_tile
                             : R.drawable.ic_stylus_tile_off));

        if (inUse) {
            tile.setSubtitle("In use • " + rateLabel(rate));
        } else if (isAttached) {
            tile.setSubtitle("Docked • 144 Hz");
        } else if (forceActive) {
            tile.setSubtitle("Force ready");
        } else {
            tile.setSubtitle("144 Hz");
        }

        tile.updateTile();
    }

    private static String rateLabel(String rate) {
        switch (rate) {
            case "60":  return "60 Hz";
            case "120": return "120 Hz";
            default:    return "Dynamic";
        }
    }
}
