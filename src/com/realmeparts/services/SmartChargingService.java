/*
 * Copyright (C) 2020 The LineageOS Project
 * Copyright (C) 2024 Hyperteam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.realmeparts;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class SmartChargingService extends Service {

    private static final int Charging_Notification_Channel_ID = 0x110110;
    private static final boolean Debug = false;
    private static final boolean resetBatteryStats = false;
    public static String cool_down = "/sys/class/power_supply/battery/cool_down";
    public static String current = "/sys/class/power_supply/battery/current_now";
    public static String mmi_charging_enable = "/sys/class/power_supply/battery/mmi_charging_enable";
    public static String battery_capacity = "/sys/class/power_supply/battery/capacity";
    public static String battery_temperature = "/sys/class/power_supply/battery/temp";
    private boolean mconnectionInfoReceiver;
    private SharedPreferences sharedPreferences;
    private PowerManager.WakeLock wakeLock;
    private Handler handler = new Handler();

    private BroadcastReceiver mBatteryInfo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateChargingStatus(context);
        }
    };

    private BroadcastReceiver mconnectionInfo = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int battCap = Integer.parseInt(Utils.readLine(battery_capacity));
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                if (!mconnectionInfoReceiver) {
                    IntentFilter batteryInfo = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    context.getApplicationContext().registerReceiver(mBatteryInfo, batteryInfo);
                    mconnectionInfoReceiver = true;
                }
                Log.d("DeviceSettings", "Charger/USB Connected");
                if (Utils.isPowerConnected(context)) {
                    AppNotification.Send(context, Charging_Notification_Channel_ID, context.getString(R.string.smart_charging_status_notif), "");
                }
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                if (sharedPreferences.getBoolean("reset_stats", false) && sharedPreferences.getInt("seek_bar", 95) == battCap) {
                    resetStats();
                }
                if (mconnectionInfoReceiver) {
                    context.getApplicationContext().unregisterReceiver(mBatteryInfo);
                    mconnectionInfoReceiver = false;
                }
                Log.d("DeviceSettings", "Charger/USB Disconnected");
                AppNotification.Cancel(context, Charging_Notification_Channel_ID);
            }
        }
    };

    private void updateChargingStatus(final Context context) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Read current values immediately
                    float battTemp = ((float) Integer.parseInt(Utils.readLine(battery_temperature))) / 10;
                    int battCap = Integer.parseInt(Utils.readLine(battery_capacity));
                    int coolDown = Integer.parseInt(Utils.readLine(cool_down));
                    int currentmA = -(Integer.parseInt(Utils.readLine(current)));
                    int chargingLimit = Integer.parseInt(Utils.readLine(mmi_charging_enable));
                    int userSelectedChargingLimit = sharedPreferences.getInt("seek_bar", 95);
                    int chargingSpeed = Settings.Secure.getInt(context.getContentResolver(), "charging_speed", 0);

                    Log.d("DeviceSettings", "Battery Temperature: " + battTemp + ", Battery Capacity: " + battCap + "%, Charging Speed: " + currentmA + " mA, Cool Down: " + coolDown);

                    if (isCoolDownAvailable() && chargingLimit == 1) {
                        if (chargingSpeed != 0 && coolDown != chargingSpeed) {
                            Utils.writeValue(cool_down, String.valueOf(chargingSpeed));
                            Log.d("DeviceSettings", "Updated cool down to " + chargingSpeed);
                        } else if (chargingSpeed == 0) {
                            if (battTemp >= 39.5 && coolDown != 2 && coolDown == 0) {
                                Utils.writeValue(cool_down, "2");
                                Log.d("DeviceSettings", "Applied cool down due to high temperature");
                            } else if (battTemp <= 38.5 && coolDown != 0 && coolDown == 2) {
                                Utils.writeValue(cool_down, "0");
                                Log.d("DeviceSettings", "Removed cool down due to low temperature");
                            }
                        }
                    }

                    // Check and enforce charging limit
                    if (((userSelectedChargingLimit == battCap) || (userSelectedChargingLimit < battCap)) && chargingLimit != 0) {
                        if (isCoolDownAvailable()) Utils.writeValue(cool_down, "0");
                        Utils.writeValue(mmi_charging_enable, "0");
                        Log.d("DeviceSettings", "Stopped charging as battery reached user selected limit");
                        if (Utils.isPowerConnected(context)) {
                            AppNotification.Send(context, Charging_Notification_Channel_ID, context.getString(R.string.smart_charging_title), context.getString(R.string.smart_charging_stoppped_notif));
                        }
                    } else if (userSelectedChargingLimit > battCap && chargingLimit != 1) {
                        Utils.writeValue(mmi_charging_enable, "1");
                        if (Utils.isPowerConnected(context)) {
                            AppNotification.Send(context, Charging_Notification_Channel_ID, context.getString(R.string.smart_charging_status_notif), "");
                        }
                        Log.d("DeviceSettings", "Charging continues");
                    }
                } catch (NumberFormatException e) {
                    Log.e("DeviceSettings", "Error reading or parsing file values", e);
                }
            }
        });
    }

    public static boolean isCoolDownAvailable() {
        return Utils.fileWritable(cool_down);
    }

    public static void resetStats() {
        try {
            Runtime.getRuntime().exec("dumpsys batterystats --reset");
            Thread.sleep(1000);
        } catch (Exception e) {
            Log.e("DeviceSettings", "SmartChargingService: " + e.toString());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCharging::WakeLock");
        wakeLock.acquire();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener);

        IntentFilter connectionInfo = new IntentFilter();
        connectionInfo.addAction(Intent.ACTION_POWER_CONNECTED);
        connectionInfo.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mconnectionInfo, connectionInfo);

        if (Utils.isPowerConnected(this)) {
            AppNotification.Send(
                this,
                Charging_Notification_Channel_ID,
                getString(R.string.smart_charging_title),
                getString(R.string.smart_charging_status_notif)
            );
            startForeground(Charging_Notification_Channel_ID, AppNotification.getNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        unregisterReceiver(mconnectionInfo);
        if (mconnectionInfoReceiver) {
            getApplicationContext().unregisterReceiver(mBatteryInfo);
        }
        if (AppNotification.NotificationSent) {
            AppNotification.Cancel(getApplicationContext(), Charging_Notification_Channel_ID);
        }
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private OnSharedPreferenceChangeListener prefChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("seek_bar")) {
                updateChargingStatus(getApplicationContext());
            }
        }
    };
}
