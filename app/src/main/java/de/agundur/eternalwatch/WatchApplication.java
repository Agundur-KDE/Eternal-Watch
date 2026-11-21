// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.app.Application;
import android.content.res.Configuration;

public final class WatchApplication extends Application {
    @Override public void onCreate() { super.onCreate();NetworkAccess.initialize(this);refreshLanguage(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);refreshLanguage();
    }
    private void refreshLanguage() { WatchNotifications.channels(this);WatchWidget.updateAll(this); }
}
