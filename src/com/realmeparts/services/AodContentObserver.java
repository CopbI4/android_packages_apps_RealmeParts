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

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.realmeparts.doze.DozeUtils;

public class AodContentObserver extends ContentObserver {
    private static final String TAG = "AodContentObserver";
    private Context context;

    public AodContentObserver(Handler handler, Context context) {
        super(handler);
        this.context = context;
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        boolean isAodEnabled = DozeUtils.isAlwaysOnEnabled(context);

        if (isAodEnabled) {
            Intent serviceIntent = new Intent(context, AodLightService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
        } else {
            Intent serviceIntent = new Intent(context, AodLightService.class);
            context.stopService(serviceIntent);
        }
    }
}
