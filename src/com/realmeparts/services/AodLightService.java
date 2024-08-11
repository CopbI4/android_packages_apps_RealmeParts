/*
 * Copyright (C) 2024 HyperTeam
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
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.realmeparts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AodLightService extends Service {
    private static final String TAG = "AodLightService";
    private static final String POWER_STATUS_PATH = "/sys/kernel/oppo_display/power_status";
    private static final String LIGHT_MODE_PATH = "/sys/kernel/oppo_display/aod_light_mode_set";
    private static final String CHANNEL_ID = "AodLightServiceChannel";

    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AOD Light Service")
                .setContentText("AOD Light Service is running")
                .setSmallIcon(R.drawable.ic_service_icon)
                .build();

        startForeground(1, notification);

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkAndSetLightMode, 0, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void checkAndSetLightMode() {
        int status = readPowerStatus();
        if (status == 3) {
            setLightMode(1);
        }
    }

    private int readPowerStatus() {
        try (BufferedReader reader = new BufferedReader(new FileReader(POWER_STATUS_PATH))) {
            String line = reader.readLine();
            return Integer.parseInt(line.trim());
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Error reading power status", e);
            return -1;
        }
    }

    private void setLightMode(int mode) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LIGHT_MODE_PATH))) {
            writer.write(Integer.toString(mode));
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error setting light mode", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "AOD Light Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
