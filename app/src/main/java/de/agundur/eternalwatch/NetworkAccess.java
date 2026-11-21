// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.content.*;
import android.net.*;
import android.os.*;
import de.agundur.eternalwatch.core.NetworkPolicy;
import de.agundur.eternalwatch.core.NetworkPolicy.Status;

/** All feed entry points share this gate; a manual refresh cannot bypass it. */
final class NetworkAccess {
    record Access(Network network,Status status) { boolean allowed() { return status==Status.ALLOWED; } }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Access previous;
    private static boolean visible;
    // Keep the preference listener alive: SharedPreferences holds a weak reference.
    private static SharedPreferences.OnSharedPreferenceChangeListener preferences;

    static Access current(Context c) {
        ConnectivityManager manager=c.getSystemService(ConnectivityManager.class);
        Network network=manager.getActiveNetwork();
        NetworkCapabilities caps=manager.getNetworkCapabilities(network);
        boolean metered=caps==null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        // Also conserve data if Android has exempted this app from Data Saver.
        boolean saver=manager.getRestrictBackgroundStatus()!=ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
        Status status=NetworkPolicy.decide(caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),metered,
            FeedRepository.prefs(c).getBoolean("allow_metered",false),saver);
        return new Access(network,status);
    }
    static boolean permits(Context c,Network network) {
        Access access=current(c);
        return access.allowed() && access.network().equals(network);
    }
    static int reason(Status status) {
        return switch(status) {
            case ALLOWED -> 0;
            case OFFLINE -> R.string.paused_offline;
            case METERED -> R.string.paused_metered;
            case DATA_SAVER -> R.string.paused_data_saver;
        };
    }
    static void initialize(Context c) {
        previous=current(c);
        c.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { changed(c); }
            @Override public void onLost(Network n) { changed(c); }
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities caps) { changed(c); }
            @Override public void onBlockedStatusChanged(Network n,boolean blocked) { changed(c); }
        });
        IntentFilter filter=new IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
        BroadcastReceiver receiver=new BroadcastReceiver() {
            @Override public void onReceive(Context context,Intent intent) { changed(c); }
        };
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(receiver,filter);
        preferences=(p,key)->{ if("allow_metered".equals(key))changed(c); };
        FeedRepository.prefs(c).registerOnSharedPreferenceChangeListener(preferences);
    }
    static void setVisible(Context c,boolean value) { visible=value;if(value)changed(c); }
    static boolean interested(Context c) { return visible || WatchService.monitoring(c); }
    private static void changed(Context c) {
        MAIN.post(()->{
            Access access=current(c);
            if(access.equals(previous))return;
            previous=access;
            WatchWidget.updateAll(c);
            WatchService.refreshNotification(c);
            // Do not introduce continuous fetching for widget-only use.
            if(access.allowed() && interested(c))FeedRepository.reconnect(c);
        });
    }
}
