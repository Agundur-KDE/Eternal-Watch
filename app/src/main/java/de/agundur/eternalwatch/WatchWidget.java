// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;
import de.agundur.eternalwatch.core.GameSnapshot;

public final class WatchWidget extends AppWidgetProvider {
    private static final String REFRESH="de.agundur.eternalwatch.REFRESH_WIDGET";
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        if(i!=null && (REFRESH.equals(i.getAction()) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(i.getAction()))) {
            PendingResult result=goAsync();FeedRepository.request(c,true,result::finish);
        }
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids) { updateAll(c); }
    static void updateAll(Context c) {
        AppWidgetManager manager=c.getSystemService(AppWidgetManager.class);
        int[] ids=manager.getAppWidgetIds(new ComponentName(c,WatchWidget.class));
        if(ids.length==0) return;
        manager.updateAppWidget(ids,views(c));
    }
    static RemoteViews views(Context c) {
        GameSnapshot data=FeedRepository.snapshot(c);
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.watch_widget);
        String clock="--:--",day=c.getString(R.string.waiting),stamp="";
        if(data!=null) {
            // This is explicitly a timestamped sample, never a pretend ticking EL clock.
            clock=data.clock(data.clockStamp,false);day=data.name;
            stamp=c.getString(R.string.widget_sample,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,c.getResources().getConfiguration().getLocales().get(0)).format(new Date(data.clockStamp)));
        }
        views.setTextViewText(R.id.widget_time,clock);views.setTextViewText(R.id.widget_day,day);views.setTextViewText(R.id.widget_stamp,stamp);
        int paused=NetworkAccess.reason(NetworkAccess.current(c).status());
        views.setTextViewText(R.id.widget_status,paused==0?"":c.getString(paused));
        views.setViewVisibility(R.id.widget_status,paused==0?android.view.View.GONE:android.view.View.VISIBLE);
        views.setOnClickPendingIntent(R.id.widget_root,WatchNotifications.open(c));
        views.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(c,2,new Intent(c,WatchWidget.class).setAction(REFRESH),PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
}
