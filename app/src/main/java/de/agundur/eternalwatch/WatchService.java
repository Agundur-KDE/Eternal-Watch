// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.IBinder;
import java.util.concurrent.*;

public final class WatchService extends Service {
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
    private boolean scheduled;
    @Override public void onCreate() { super.onCreate();startForeground(1,WatchNotifications.ongoing(this)); }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "STOP".equals(intent.getAction())) FeedRepository.prefs(this).edit().putBoolean("monitor_enabled",false).commit();
        if(!FeedRepository.prefs(this).getBoolean("monitor_enabled",false) || !WatchNotifications.permitted(this)) {
            stopSelf();return START_NOT_STICKY;
        }
        if(!scheduled) {
            scheduled=true;FeedRepository.resetAlarms();
            scheduler.scheduleWithFixedDelay(()->FeedRepository.request(this,false),0,5,TimeUnit.SECONDS);
        }
        return START_STICKY;
    }
    @Override public void onDestroy() { scheduler.shutdownNow();super.onDestroy(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if(FeedRepository.prefs(this).getBoolean("monitor_enabled",false) && WatchNotifications.permitted(this))
            getSystemService(NotificationManager.class).notify(1,WatchNotifications.ongoing(this));
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
