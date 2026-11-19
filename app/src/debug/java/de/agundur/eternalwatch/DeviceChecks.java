// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import de.agundur.eternalwatch.core.*;

/** Debug-only checks against production Android rendering; no external test framework. */
public final class DeviceChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context c=getTargetContext();
            GameSnapshot snapshot=FeedRepository.snapshot(c);
            require(snapshot!=null,"Live sample is cached");
            require(!snapshot.name.isEmpty(),"Resolved day name is present");
            Configuration de=new Configuration(c.getResources().getConfiguration());de.setLocale(Locale.GERMAN);
            Context german=c.createConfigurationContext(de);
            require(german.getResources().getQuantityString(R.plurals.selection_count,1,1).equals("1 Tag ausgewählt"),"German singular resource");
            require(german.getResources().getQuantityString(R.plurals.selection_count,2,2).equals("2 Tage ausgewählt"),"German plural resource");
            AtomicReference<Throwable> failure=new AtomicReference<>();
            runOnMainSync(()->{
                try {
                    FrameLayout parent=new FrameLayout(c);
                    View widget=WatchWidget.views(c).apply(c,parent);
                    widget.measure(View.MeasureSpec.makeMeasureSpec(660,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));
                    widget.layout(0,0,660,500);
                    require(((TextView)widget.findViewById(R.id.widget_day)).getText().toString().equals(snapshot.name),"Widget contains current day");
                    require(!((TextView)widget.findViewById(R.id.widget_stamp)).getText().toString().isEmpty(),"Widget labels sample timestamp");
                    require(widget.findViewById(R.id.widget_refresh).hasOnClickListeners(),"Widget refresh action is bound");
                } catch(Throwable error) { failure.set(error); }
            });
            if(failure.get()!=null) throw new AssertionError("Widget rendering failed",failure.get());
            result.putString("stream","Live sample, German quantity resources and production RemoteViews rendering/actions passed. No launcher layout modified.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","Device checks failed: "+error.getClass().getSimpleName()+" "+error.getMessage()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
