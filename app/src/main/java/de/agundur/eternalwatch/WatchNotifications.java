// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;

final class WatchNotifications {
    static boolean permitted(Context c) {
        return (Build.VERSION.SDK_INT<33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    static void channels(Context c) {
        NotificationManager m=c.getSystemService(NotificationManager.class);
        m.createNotificationChannel(new NotificationChannel("monitor",c.getString(R.string.channel_monitor),NotificationManager.IMPORTANCE_LOW));
        m.createNotificationChannel(new NotificationChannel("days",c.getString(R.string.channel_days),NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel silent=new NotificationChannel("invasion_quiet",c.getString(R.string.channel_invasion),NotificationManager.IMPORTANCE_DEFAULT);
        silent.setSound(null,null);silent.enableVibration(false);m.createNotificationChannel(silent);
        m.createNotificationChannel(new NotificationChannel("invasion_sound",c.getString(R.string.channel_invasion_sound),NotificationManager.IMPORTANCE_DEFAULT));
    }
    static PendingIntent open(Context c) { return PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); }
    static Notification ongoing(Context c) {
        channels(c);
        PendingIntent stop=PendingIntent.getService(c,1,new Intent(c,WatchService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        int paused=NetworkAccess.reason(NetworkAccess.current(c).status());
        return new Notification.Builder(c,"monitor").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(paused==0?R.string.monitor_active:R.string.monitor_paused))
            .setContentText(paused==0?c.getString(R.string.monitor_status,FeedRepository.pollSeconds(c)):c.getString(paused))
            .setContentIntent(open(c)).setOngoing(true).addAction(new Notification.Action.Builder(null,c.getString(R.string.stop),stop).build()).build();
    }
    static void special(Context c,String name) { send(c,"days",101,c.getString(R.string.special_notification),name); }
    static void invasion(Context c,String report,boolean sound) { send(c,sound?"invasion_sound":"invasion_quiet",102,c.getString(R.string.invasion_notification),report); }
    private static void send(Context c,String channel,int id,String title,String body) {
        if(!permitted(c)) return;
        channels(c);
        Notification notification=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(open(c)).setAutoCancel(true).build();
        c.getSystemService(NotificationManager.class).notify(id,notification);
    }
}
