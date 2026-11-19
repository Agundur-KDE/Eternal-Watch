// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.content.*;
import android.os.UserManager;
import android.util.Log;

public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(i==null || (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))) return;
        if(!c.getSystemService(UserManager.class).isUserUnlocked() || !FeedRepository.prefs(c).getBoolean("monitor_enabled",false) || !WatchNotifications.permitted(c)) return;
        try { c.startForegroundService(new Intent(c,WatchService.class)); }
        catch(IllegalStateException|SecurityException e) { Log.w("EternalWatch","Startup blocked: "+e.getClass().getSimpleName()); }
    }
}
