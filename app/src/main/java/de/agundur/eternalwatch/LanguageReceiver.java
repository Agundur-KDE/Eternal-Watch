// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.content.*;
import android.os.UserManager;

public final class LanguageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(i==null || (!Intent.ACTION_LOCALE_CHANGED.equals(i.getAction())
                && !"android.intent.action.APPLICATION_LOCALE_CHANGED".equals(i.getAction()))) return;
        if(!c.getSystemService(UserManager.class).isUserUnlocked()) return;
        WatchNotifications.channels(c);WatchWidget.updateAll(c);
    }
}
