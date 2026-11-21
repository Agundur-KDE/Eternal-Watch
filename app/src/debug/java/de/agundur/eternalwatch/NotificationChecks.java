// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

/** Explicit, debug-only posting check. Does not inject or persist feed events. */
public final class NotificationChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        Context c=getTargetContext();
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        boolean dayOwned=false,invasionOwned=false;
        int code=Activity.RESULT_CANCELED;
        try {
            require(WatchNotifications.permitted(c),"Notification permission is required");
            for(StatusBarNotification n:manager.getActiveNotifications())
                require(n.getId()!=101 && n.getId()!=102,"Existing event notifications must be preserved; check skipped");
            // Do not race the live monitor for its notification IDs.
            require(!FeedRepository.prefs(c).getBoolean("monitor_enabled",false),"Stop monitoring before this explicit test");
            String body=c.getString(R.string.notification_test_body);
            dayOwned=true;
            WatchNotifications.special(c,body);
            verify(manager,101,"days",c.getString(R.string.special_notification),body);
            invasionOwned=true;
            WatchNotifications.invasion(c,body,false);
            verify(manager,102,"invasion_quiet",c.getString(R.string.invasion_notification),body);
            require(manager.getNotificationChannel("invasion_quiet").getSound()==null,"Quiet invasion channel has no sound");
            result.putString("stream","Production Special Day and quiet invasion notifications posted and found in Android active notifications; titles, bodies, channels and content intents passed. Synthetic test messages only; no real event, audible delivery or heads-up assertion.\n");
            code=Activity.RESULT_OK;
        } catch(Throwable error) {
            result.putString("stream","Notification checks failed: "+error.getClass().getSimpleName()+" "+error.getMessage()+"\n");
        } finally {
            if(dayOwned)manager.cancel(101);
            if(invasionOwned)manager.cancel(102);
        }
        finish(code,result);
    }
    private static void verify(NotificationManager manager,int id,String channel,String title,String body) {
        for(int attempt=0;attempt<30;attempt++) {
            for(StatusBarNotification entry:manager.getActiveNotifications()) {
                if(entry.getId()!=id)continue;
                Notification n=entry.getNotification();
                require(channel.equals(n.getChannelId()),"Expected channel");
                require(title.contentEquals(n.extras.getCharSequence(Notification.EXTRA_TITLE,"")),"Expected title");
                require(body.contentEquals(n.extras.getCharSequence(Notification.EXTRA_TEXT,"")),"Expected test body");
                require(n.contentIntent!=null,"Notification opens the app");
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Notification was not posted: "+id);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
