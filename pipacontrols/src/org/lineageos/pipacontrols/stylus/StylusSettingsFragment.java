/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2025-2026 nullpointer1101
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Settings fragment for stylus/pen configuration.
 */
public class StylusSettingsFragment extends SettingsBasePreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "XiaomiStylusSettings";

    private static final String FORCE_RECOGNIZE_KEY     = "force_recognize_stylus_key";
    private static final String FOOTER_KEY              = "footer_key";
    private static final String STYLUS_REFRESH_RATE_KEY = "stylus_refresh_rate_key";

    private static final String RATE_DYNAMIC = "dynamic";
    private static final String RATE_60      = "60";
    private static final String RATE_120     = "120";

    private SharedPreferences        mPrefs;
    private SwitchPreferenceCompat   mForceRecognizePref;
    private SwitchPreferenceCompat   mChargingNotifPref;
    private ListPreference           mRefreshRatePref;
    private FooterPreference     mFooterPref;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Per-key re-entry guard
    private String mHandlingKey = null;

    // -----------------------------------------------------------------------
    // Tile sync receiver — keeps fragment in sync when QS tile is toggled
    // -----------------------------------------------------------------------

    private final BroadcastReceiver mTileChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logDebug("Tile-change broadcast — refreshing UI");
            mHandler.post(() -> refreshUI());
        }
    };

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.stylus_settings);

        mPrefs              = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mForceRecognizePref = findPreference(FORCE_RECOGNIZE_KEY);
        mChargingNotifPref  = findPreference(PenChargingService.PREF_STYLUS_CHARGING_NOTIF);
        mRefreshRatePref    = findPreference(STYLUS_REFRESH_RATE_KEY);
        mFooterPref         = findPreference(FOOTER_KEY);

        refreshUI();
        logInfo("Stylus settings created");
    }

    @Override
    public void onResume() {
        super.onResume();
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                mTileChangeReceiver,
                new IntentFilter(StylusTileService.ACTION_STYLUS_CHANGED));
        refreshUI();
        logInfo("Stylus settings resumed");
    }

    @Override
    public void onPause() {
        super.onPause();
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        try {
            LocalBroadcastManager.getInstance(requireContext())
                    .unregisterReceiver(mTileChangeReceiver);
        } catch (IllegalArgumentException ignored) { /* not registered */ }
        logInfo("Stylus settings paused");
    }

    // -----------------------------------------------------------------------
    // SharedPreferences callback
    // -----------------------------------------------------------------------

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key != null && key.equals(mHandlingKey)) {
            logDebug("Skipping guarded callback for key: " + key);
            return;
        }

        mHandlingKey = key;
        try {
            if (FORCE_RECOGNIZE_KEY.equals(key)) {
                boolean enabled = prefs.getBoolean(key, false);
                logInfo("Force recognize → " + enabled);
                PenUtils.onForceRecognizeChanged(enabled);
                notifyTile();

            } else if (STYLUS_REFRESH_RATE_KEY.equals(key)) {
                String rate = prefs.getString(key, RATE_DYNAMIC);
                logInfo("Refresh rate → " + rate);
                PenUtils.setRefreshRateMode(rate);
                notifyTile();
            }

            refreshUI();

        } finally {
            mHandler.post(() -> {
                if (key != null && key.equals(mHandlingKey)) mHandlingKey = null;
            });
        }
    }

    // -----------------------------------------------------------------------
    // Tile notification — dual channel
    // -----------------------------------------------------------------------

    private void notifyTile() {
        LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(new Intent(StylusTileService.ACTION_STYLUS_CHANGED));
        TileService.requestListeningState(requireContext(),
                new ComponentName(requireContext(), StylusTileService.class));
    }

    // -----------------------------------------------------------------------
    // UI refresh
    // -----------------------------------------------------------------------

    private void refreshUI() {
        final boolean forceRecog  = mPrefs.getBoolean(FORCE_RECOGNIZE_KEY, false);
        final String  refreshRate = mPrefs.getString(STYLUS_REFRESH_RATE_KEY, RATE_DYNAMIC);

        if (mForceRecognizePref != null && mForceRecognizePref.isChecked() != forceRecog) {
            mForceRecognizePref.setChecked(forceRecog);
        }
        if (mChargingNotifPref != null) {
            final boolean notifEnabled = mPrefs.getBoolean(PenChargingService.PREF_STYLUS_CHARGING_NOTIF, true);
            if (mChargingNotifPref.isChecked() != notifEnabled) {
                mChargingNotifPref.setChecked(notifEnabled);
            }
        }
        if (mRefreshRatePref != null) {
            String current = mRefreshRatePref.getValue();
            if (current == null || !current.equals(refreshRate)) {
                mRefreshRatePref.setValue(refreshRate);
            }
        }

        updateFooter(forceRecog, refreshRate);
    }

    private void updateFooter(boolean forceRecog, String refreshRate) {
        if (mFooterPref == null) return;

        final boolean penInUse     = PenUtils.isPenInUse();
        final boolean penConnected = PenUtils.isPenConnected();
        final boolean penAttached  = PenUtils.isPenAttached();

        final String statusText;
        if (penInUse) {
            statusText = getString(R.string.stylus_status_active) + " (" + getRateLabel(refreshRate) + " • In use)";
        } else if (penAttached) {
            statusText = getString(R.string.stylus_status_inactive) + " (Docked / Charging • 144Hz)";
        } else if (penConnected) {
            statusText = getString(R.string.stylus_status_inactive) + " (Connected • 144Hz)";
        } else if (forceRecog) {
            statusText = getString(R.string.stylus_status_inactive) + " (Force Mode Ready • 144Hz)";
        } else {
            statusText = getString(R.string.stylus_status_inactive) + " (No Pen • 144Hz)";
        }

        mFooterPref.setTitle(
                getString(R.string.stylus_more_info)
                        + "\n\n"
                        + getString(R.string.stylus_footer_status, statusText)
                        + "\n"
                        + getString(R.string.stylus_footer_refresh_rate,
                                getRateLabel(refreshRate)
                                + " (" + (penInUse ? "Active" : "When in use") + ")"));
    }

    private String getRateLabel(String value) {
        switch (value) {
            case RATE_60:  return getString(R.string.refresh_rate_60hz);
            case RATE_120: return getString(R.string.refresh_rate_120hz);
            default:       return getString(R.string.refresh_rate_dynamic);
        }
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------

    private void logDebug(String msg) { Log.d(TAG, ts() + msg); }
    private void logInfo(String msg)  { Log.i(TAG, ts() + msg); }

    private static String ts() {
        return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] ";
    }
}
