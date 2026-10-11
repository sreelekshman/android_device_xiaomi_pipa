/*
 * Copyright (C) 2026 Mufasa
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.stylus;

import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.view.InputDevice;

import java.util.Locale;
import java.util.Set;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.lineageos.pipacontrols.R;

/**
 * Persistent Background Service managing the iOS-inspired stylus charging experience:
 * 1. On Connect / Attach: Triggers charging popup + ongoing notification.
 * 2. On Detach / Remove: Triggers disconnected popup + cleans up notification.
 * 3. On 100% Full Charge: Triggers fully charged popup (once per cycle).
 * 4. On Significant Battery Update: Updates popup in-place without duplicating windows.
 * 5. On Low/Critical Battery: Alerts with warning popup.
 * 6. On Manual Broadcast: Handles ACTION_SHOW_STYLUS_POPUP for testing and status checks.
 */
public class PenChargingService extends Service {

    private static final String TAG = "PenChargingService";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_SHOW_STYLUS_POPUP =
            "org.lineageos.pipacontrols.ACTION_SHOW_STYLUS_POPUP";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_BATTERY_LEVEL = "battery_level";
    public static final String EXTRA_DEVICE_NAME = "device_name";

    private static final long POLL_INTERVAL_ACTIVE_MS = 600L; // 600ms fast polling while screen is on
    private static final long NINETY_NINE_TO_FULL_MS = 60000L; // 1 minute
    private static final int BATTERY_LEVEL_UNKNOWN = Integer.MIN_VALUE;
    private static final int LOW_BATTERY_THRESHOLD = 15;
    private static final int CRITICAL_BATTERY_THRESHOLD = 5;

    public static final String PREF_STYLUS_CHARGING_NOTIF = "stylus_charging_notification_key";

    private NotificationManager mNotificationManager;
    private PowerManager mPowerManager;
    private SharedPreferences mPreferences;
    private StylusPillController mPillController;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsMonitoring = false;
    private boolean mWasAttached = false;
    private boolean mWasCharging = false;
    private boolean mHasShown100PercentPopup = false;
    private int mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
    private Long mFirstSeen99AtMs = null;
    private String mStylusDeviceName = null;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsMonitoring) return;
            updateChargingStatus();
            mHandler.postDelayed(this, POLL_INTERVAL_ACTIVE_MS);
        }
    };

    private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (PenChargingManager.DEBUG) Log.d(TAG, "Screen turned ON, resuming active monitoring");
                startMonitoring();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (PenChargingManager.DEBUG) Log.d(TAG, "Screen turned OFF, pausing active monitoring");
                pauseMonitoring();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                handleBluetoothConnected(intent);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) ||
                       BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED.equals(action)) {
                updateChargingStatus();
            } else if (ACTION_SHOW_STYLUS_POPUP.equals(action)) {
                handleManualPopupRequest(intent);
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (PREF_STYLUS_CHARGING_NOTIF.equals(key)) {
                        boolean enabled = prefs.getBoolean(key, true);
                        if (!enabled) {
                            mPillController.hidePill();
                        }
                    }
                }
            };

    private boolean isPopupEnabled() {
        return mPreferences == null || mPreferences.getBoolean(PREF_STYLUS_CHARGING_NOTIF, true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);

        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }

        mPillController = new StylusPillController(this);
        registerReceivers();

        mWasAttached = PenChargingManager.isPenPhysicallyAttached();
        PenChargingManager.initDetectionState(mWasAttached);
        PenUtils.onPenAttachmentChanged(mWasAttached);

        if (mWasAttached) {
            PenChargingManager.PenChargingStatus initialStatus = PenChargingManager.getStatus(this);
            int initialLevel = (initialStatus != null && initialStatus.batteryLevel >= 0)
                    ? initialStatus.batteryLevel : -1;
            mLastBatteryLevel = initialLevel;
            mWasCharging = true;
            if (initialLevel >= 100) {
                mHasShown100PercentPopup = true;
            }
        }

        if (mPowerManager != null && mPowerManager.isInteractive()) {
            startMonitoring();
        }

        Log.i(TAG, "PenChargingService initialized (initially attached=" + mWasAttached + ")");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_STYLUS_POPUP.equals(intent.getAction())) {
            handleManualPopupRequest(intent);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        }
        try {
            unregisterReceiver(mStateReceiver);
        } catch (Exception ignored) {}
        Log.i(TAG, "PenChargingService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED);
        filter.addAction(ACTION_SHOW_STYLUS_POPUP);
        registerReceiver(mStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void startMonitoring() {
        if (!mIsMonitoring && PenChargingManager.isAvailable()) {
            mIsMonitoring = true;
            mHandler.post(mPollRunnable);
        }
    }

    private void pauseMonitoring() {
        if (mIsMonitoring) {
            mIsMonitoring = false;
            mHandler.removeCallbacks(mPollRunnable);
            mPillController.hidePill();
        }
    }

    private void stopMonitoring() {
        pauseMonitoring();
        hideNotification();
        PenChargingManager.resetDetectionState();
        mWasAttached = false;
        mWasCharging = false;
        mHasShown100PercentPopup = false;
        mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
        mFirstSeen99AtMs = null;
    }

    private void handleBluetoothConnected(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device != null) {
            String devName = device.getAlias() != null ? device.getAlias() : device.getName();
            if (devName != null) {
                String lower = devName.toLowerCase(Locale.ROOT);
                if (lower.contains("pen") || lower.contains("stylus")) {
                    mStylusDeviceName = devName;
                }
            }
        }
        String name = getStylusDeviceName();
        PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);

        // ACTION_ACL_CONNECTED fires for every Bluetooth device (earbuds,
        // watches, ...). Only react when the wireless charging hardware has
        // actually confirmed a stylus on the pill.
        if (status == null || !status.isConnected) {
            return;
        }

        int level = status.batteryLevel;
        if (status.isCharging) {
            mPillController.showPopup(level >= 100 ? StylusState.FULLY_CHARGED : StylusState.CHARGING, level, name);
        } else {
            mPillController.showPopup(StylusState.CONNECTED, level, name);
        }
    }

    private String getStylusDeviceName() {
        if (mStylusDeviceName != null && !mStylusDeviceName.isEmpty()) {
            return mStylusDeviceName;
        }
        mStylusDeviceName = queryBluetoothStylusName();
        if (mStylusDeviceName != null && !mStylusDeviceName.isEmpty()) {
            return mStylusDeviceName;
        }
        return getString(R.string.stylus_title);
    }

    private String queryBluetoothStylusName() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
                if (bondedDevices != null) {
                    for (BluetoothDevice device : bondedDevices) {
                        String name = device.getAlias() != null ? device.getAlias() : device.getName();
                        if (name != null) {
                            String lower = name.toLowerCase(Locale.ROOT);
                            if (lower.contains("pen") || lower.contains("stylus")) {
                                return name;
                            }
                        }
                    }
                    for (BluetoothDevice device : bondedDevices) {
                        if (device.getBluetoothClass() != null &&
                                device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL) {
                            String name = device.getAlias() != null ? device.getAlias() : device.getName();
                            if (name != null && !name.toLowerCase(Locale.ROOT).contains("keyboard")) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query Bluetooth stylus name", e);
        }

        try {
            InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) {
                for (int id : inputManager.getInputDeviceIds()) {
                    InputDevice dev = inputManager.getInputDevice(id);
                    if (dev != null && (dev.getSources() & InputDevice.SOURCE_STYLUS) != 0) {
                        String devName = dev.getName();
                        if (devName != null && !devName.isEmpty() && !devName.equalsIgnoreCase("pen")) {
                            return devName;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private void handleManualPopupRequest(Intent intent) {
        String stateStr = intent.getStringExtra(EXTRA_STATE);
        int batteryLevel = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1);
        String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = getStylusDeviceName();
        }

        StylusState state;
        if (stateStr != null) {
            try {
                state = StylusState.valueOf(stateStr.toUpperCase());
            } catch (Exception e) {
                state = StylusState.CHARGING;
            }
        } else {
            PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);
            if (status != null && status.isCharging) {
                state = status.batteryLevel >= 100 ? StylusState.FULLY_CHARGED : StylusState.CHARGING;
                batteryLevel = status.batteryLevel;
            } else if (status != null && status.isConnected) {
                state = StylusState.CONNECTED;
                batteryLevel = status.batteryLevel;
            } else {
                state = StylusState.DISCONNECTED;
            }
        }

        Log.i(TAG, "Handling manual popup request: state=" + state + ", level=" + batteryLevel);
        mPillController.showPopup(state, batteryLevel, deviceName);
    }

    private void updateChargingStatus() {
        PenChargingManager.PenChargingStatus status = PenChargingManager.getStatus(this);
        String penName = getStylusDeviceName();
        boolean isAttached = (status != null && status.isConnected);

        if (mWasAttached != isAttached) {
            mWasAttached = isAttached;
            PenUtils.onPenAttachmentChanged(isAttached);

            if (!isAttached) {
                // Stylus detached: warn if battery is low
                if (mLastBatteryLevel >= 0 && mLastBatteryLevel <= LOW_BATTERY_THRESHOLD && isPopupEnabled()) {
                    StylusState detachState = (mLastBatteryLevel <= CRITICAL_BATTERY_THRESHOLD)
                            ? StylusState.CRITICAL_BATTERY : StylusState.LOW_BATTERY;
                    mPillController.showPopup(detachState, mLastBatteryLevel, penName);
                }
                hideNotification();
                mWasCharging = false;
                mHasShown100PercentPopup = false;
                mLastBatteryLevel = BATTERY_LEVEL_UNKNOWN;
                mFirstSeen99AtMs = null;
                return;
            }
        }

        if (!isAttached) {
            return;
        }

        // Pen is actively connected and charging
        int rawLevel = -1;
        if (status.batteryLevel >= 0 && status.batteryLevel <= 100) {
            rawLevel = status.batteryLevel;
        }

        int displayLevel;
        if (rawLevel == 99) {
            long now = SystemClock.elapsedRealtime();
            if (mFirstSeen99AtMs == null) mFirstSeen99AtMs = now;
            if (now - mFirstSeen99AtMs >= NINETY_NINE_TO_FULL_MS) {
                displayLevel = 100;
            } else {
                displayLevel = 99;
            }
        } else {
            mFirstSeen99AtMs = null;
            displayLevel = rawLevel;
        }

        boolean justAttached = !mWasCharging;
        mWasCharging = true;

        if (justAttached) {
            Log.i(TAG, "Stylus attached -> floating charging chip (" +
                    (displayLevel >= 0 ? displayLevel + "%" : "unknown SoC") + ")");
            mLastBatteryLevel = displayLevel;
            if (isPopupEnabled()) {
                StylusState state = (displayLevel >= 100) ? StylusState.FULLY_CHARGED : StylusState.CHARGING;
                mPillController.showPopup(state, displayLevel, penName);
            }

            if (displayLevel >= 100) {
                mHasShown100PercentPopup = true;
            }
        } else {
            // Charging progress updates while remaining attached
            if (displayLevel < 100 && displayLevel >= 0) {
                mHasShown100PercentPopup = false;
            } else if (displayLevel == 100) {
                // Trigger 100% full charge popup once per cycle
                if (!mHasShown100PercentPopup) {
                    mHasShown100PercentPopup = true;
                    if (isPopupEnabled()) {
                        Log.i(TAG, "Stylus reached 100% -> Triggering full charge popup");
                        mPillController.showPopup(StylusState.FULLY_CHARGED, 100, penName);
                    }
                }
            }

            mLastBatteryLevel = displayLevel;
        }
    }

    private void hideNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }

    public static void start(Context context) {
        if (PenChargingManager.isAvailable()) {
            Intent intent = new Intent(context, PenChargingService.class);
            context.startService(intent);
            Log.i(TAG, "Starting PenChargingService");
        } else {
            Log.w(TAG, "Wireless charging hardware not available, skipping PenChargingService");
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, PenChargingService.class));
    }
}
