// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import de.agundur.eternalwatch.core.NetworkPolicy.Status;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit device checks; temporarily changes only allow_metered and restores it. */
public final class NetworkChecks extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { arguments=args;super.onCreate(args);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        Context c=getTargetContext();SharedPreferences prefs=FeedRepository.prefs(c);
        boolean existed=prefs.contains("allow_metered"),original=prefs.getBoolean("allow_metered",false),changed=false;
        boolean monitorExisted=prefs.contains("monitor_enabled"),monitorChanged=false;
        Activity activity=null;
        SharedPreferences.OnSharedPreferenceChangeListener listener=null;
        try {
            require(!prefs.getBoolean("monitor_enabled",false),"Stop monitoring before network checks");
            String expectation=arguments.getString("expected");
            require(expectation!=null,"Pass -e expected ALLOWED, METERED, DATA_SAVER or OFFLINE");
            boolean allow=Boolean.parseBoolean(arguments.getString("allow_metered","false"));
            changed=true;require(prefs.edit().putBoolean("allow_metered",allow).commit(),"Save test choice");
            Status status=NetworkAccess.current(c).status();
            // Android may briefly remove validation while applying a metering override.
            for(int i=0;i<60 && status!=Status.valueOf(expectation);i++) {
                android.os.SystemClock.sleep(250);status=NetworkAccess.current(c).status();
            }
            android.net.ConnectivityManager manager=c.getSystemService(android.net.ConnectivityManager.class);
            android.net.Network network=manager.getActiveNetwork();
            android.net.NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            require(status==Status.valueOf(expectation),"Expected "+expectation+", got "+status+"; active="+(network!=null)+", capabilities="+(caps!=null)+", internet="+(caps!=null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET))+", validated="+(caps!=null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
            String sample=prefs.getString("sample",null),error=prefs.getString("error",null);
            if(status!=Status.ALLOWED) {
                for(boolean force:new boolean[]{false,true}) {
                    CountDownLatch complete=new CountDownLatch(1);
                    FeedRepository.request(c,force,complete::countDown);
                    require(complete.await(1,TimeUnit.SECONDS),"Blocked request completes without network work");
                }
                require(Objects.equals(sample,prefs.getString("sample",null)),"Blocked requests preserve cache");
                require(Objects.equals(error,prefs.getString("error",null)),"Blocked requests do not pretend to fetch");
            } else {
                CountDownLatch complete=new CountDownLatch(1);
                FeedRepository.request(c,true,complete::countDown);
                require(complete.await(12,TimeUnit.SECONDS),"Permitted request completes");
                // Read on the main thread after queued apply() changes.
                require(!"network".equals(prefs.getString("error","")) && !"invalid".equals(prefs.getString("error","")),"Permitted connection fetches a valid response");
                require(prefs.contains("sample"),"Permitted connection has a cached sample");
            }
            for(String lang:new String[]{"en","de","es","ru"}) {
                Configuration configuration=new Configuration(c.getResources().getConfiguration());
                configuration.setLocale(Locale.forLanguageTag(lang));Context localized=c.createConfigurationContext(configuration);
                int reason=NetworkAccess.reason(status);
                Notification notification=WatchNotifications.ongoing(localized);
                String title=localized.getString(reason==0?R.string.monitor_active:R.string.monitor_paused);
                require(title.contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TITLE,"")),lang+" monitor title");
                if(reason!=0)require(localized.getString(reason).contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT,"")),lang+" paused notification");
                AtomicReference<Throwable> failure=new AtomicReference<>();
                runOnMainSync(()->{
                    try {
                        View widget=WatchWidget.views(localized).apply(localized,new FrameLayout(localized));
                        AppWidgetProviderInfo info=AppWidgetManager.getInstance(localized).getInstalledProviders().stream()
                            .filter(p->p.provider.equals(new ComponentName(localized,WatchWidget.class))).findFirst().orElseThrow();
                        widget.measure(View.MeasureSpec.makeMeasureSpec(info.minWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(info.minHeight,View.MeasureSpec.EXACTLY));
                        widget.layout(0,0,info.minWidth,info.minHeight);
                        android.graphics.Rect refresh=new android.graphics.Rect();
                        widget.findViewById(R.id.widget_refresh).getDrawingRect(refresh);
                        ((android.view.ViewGroup)widget).offsetDescendantRectToMyCoords(widget.findViewById(R.id.widget_refresh),refresh);
                        require(refresh.bottom<=widget.getHeight()-widget.getPaddingBottom(),lang+" refresh fits declared widget minimum height");
                        TextView label=widget.findViewById(R.id.widget_status);
                        require(label.getVisibility()==(reason==0?View.GONE:View.VISIBLE),lang+" widget status visibility");
                        if(reason!=0)require(localized.getString(reason).contentEquals(label.getText()),lang+" widget pause reason");
                    } catch(Throwable widgetError) { failure.set(widgetError); }
                });
                if(failure.get()!=null)throw new AssertionError(lang+" widget status",failure.get());
            }
            if(Boolean.parseBoolean(arguments.getString("watch_resume","false"))) {
                require(status==Status.METERED,"Resume check starts on a blocked metered connection");
                require(WatchNotifications.permitted(c),"Resume check needs existing notification permission");
                CountDownLatch response=new CountDownLatch(1);AtomicBoolean loading=new AtomicBoolean();
                listener=(p,key)->{
                    if(!"error".equals(key))return;
                    String value=p.getString("error","");
                    if("loading".equals(value))loading.set(true);
                    else if(loading.get() && (value.isEmpty() || "stale".equals(value)))response.countDown();
                };
                prefs.registerOnSharedPreferenceChangeListener(listener);
                activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("qa_keep_awake",true));
                monitorChanged=true;require(prefs.edit().putBoolean("monitor_enabled",true).commit(),"Enable temporary monitor");
                c.startForegroundService(new Intent(c,WatchService.class));
                NotificationManager notifications=c.getSystemService(NotificationManager.class);
                require(waitForTitle(notifications,c.getString(R.string.monitor_paused)),"Real foreground notification shows paused state");
                // Leave the service running with the activity closed: reconnect must work in the background.
                Activity opened=activity;runOnMainSync(opened::finish);activity=null;
                Bundle ready=new Bundle();ready.putString("stream","NETWORK_RESUME_READY\n");sendStatus(0,ready);
                require(response.await(30,TimeUnit.SECONDS),"Allowed connection automatically resumes a successful feed request");
                require(NetworkAccess.current(c).allowed(),"Resumed connection is allowed");
                require(waitForTitle(notifications,c.getString(R.string.monitor_active)),"Real notification returns to monitoring state");
            }
            result.putString("stream","Network policy "+status+" passed: production gate, cache behavior and four-language notification/widget status.\n");code=Activity.RESULT_OK;
        } catch(Throwable error) {
            result.putString("stream","Network checks failed: "+error.getClass().getSimpleName()+" "+error.getMessage()+(error.getCause()==null?"":"; "+error.getCause())+"\n");
        } finally {
            if(listener!=null)prefs.unregisterOnSharedPreferenceChangeListener(listener);
            if(activity!=null) { Activity opened=activity;runOnMainSync(opened::finish); }
            if(monitorChanged) {
                c.stopService(new Intent(c,WatchService.class));
                SharedPreferences.Editor edit=prefs.edit();
                if(monitorExisted)edit.putBoolean("monitor_enabled",false);else edit.remove("monitor_enabled");
                if(!edit.commit()) { code=Activity.RESULT_CANCELED;result.putString("stream","Monitor restoration failed\n"); }
            }
            if(changed) {
                SharedPreferences.Editor edit=prefs.edit();
                if(existed)edit.putBoolean("allow_metered",original);else edit.remove("allow_metered");
                if(!edit.commit()) { code=Activity.RESULT_CANCELED;result.putString("stream","Network choice restoration failed\n"); }
            }
            WatchNotifications.channels(c);WatchWidget.updateAll(c);
        }
        finish(code,result);
    }
    private static boolean waitForTitle(NotificationManager manager,String title) {
        for(int i=0;i<40;i++) {
            for(android.service.notification.StatusBarNotification entry:manager.getActiveNotifications())
                if(entry.getId()==1 && title.contentEquals(entry.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE,"")))return true;
            android.os.SystemClock.sleep(100);
        }
        return false;
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
