// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.app.*;
import android.content.Context;

/** Checks Android's actual channel importance without altering the user's monitor channel. */
final class MonitorChannelChecks {
    static void run(Context c) {
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        String prefix="ew_debug_monitor_check";
        String blocked=prefix+"_blocked",silent=prefix+"_silent";
        try {
            NotificationChannel none=new NotificationChannel(blocked,c.getString(R.string.channel_monitor),NotificationManager.IMPORTANCE_NONE);
            manager.createNotificationChannel(none);
            NotificationChannel low=new NotificationChannel(silent,c.getString(R.string.channel_monitor),NotificationManager.IMPORTANCE_LOW);
            low.setSound(null,null);manager.createNotificationChannel(low);
            if(WatchNotifications.channelPermitted(manager,manager.getNotificationChannel(blocked)))throw new AssertionError("Blocked channel must prevent monitoring");
            if(!WatchNotifications.channelPermitted(manager,manager.getNotificationChannel(silent)))throw new AssertionError("Silent LOW channel must allow monitoring");
            if(WatchNotifications.channelPermitted(manager,null))throw new AssertionError("Missing channel must prevent monitoring");
        } finally {
            manager.deleteNotificationChannel(blocked);manager.deleteNotificationChannel(silent);
        }
    }
}
