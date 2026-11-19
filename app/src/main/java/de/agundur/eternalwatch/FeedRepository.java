// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import de.agundur.eternalwatch.core.*;

public final class FeedRepository {
    public static final String ENDPOINT="https://mercator.holy-eternalland.de/botmanagement/elinfo.json";
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile GameSnapshot cached;
    private static volatile boolean loaded;
    private static volatile long lastAttempt;
    private static AlarmEngine.State baseline=AlarmEngine.State.empty();
    private FeedRepository() { }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("watch",Context.MODE_PRIVATE); }
    public static int pollSeconds(Context c) { return Math.max(15,Math.min(300,prefs(c).getInt("poll_seconds",30))); }
    private static Map<String,Object> map(JSONObject json) throws JSONException {
        Map<String,Object> values=new HashMap<>();
        Iterator<String> keys=json.keys();
        while(keys.hasNext()) { String key=keys.next();Object v=json.get(key);values.put(key,v==JSONObject.NULL?null:v instanceof JSONObject?map((JSONObject)v):v); }
        return values;
    }
    public static GameSnapshot snapshot(Context c) {
        if (!loaded) synchronized(FeedRepository.class) {
            if (!loaded) {
                try { String raw=prefs(c).getString("sample","");if(!raw.isEmpty()) cached=GameSnapshot.parse(map(new JSONObject(raw))); }
                catch (JSONException | IllegalArgumentException ignored) { }
                loaded=true;
            }
        }
        return cached;
    }
    public static synchronized void resetAlarms() { baseline=AlarmEngine.State.empty(); }
    public static void request(Context context,boolean force) { request(context,force,()->{}); }
    public static void request(Context context,boolean force,Runnable complete) {
        Context c=context.getApplicationContext();
        long now=android.os.SystemClock.elapsedRealtime();
        if ((!force && now-lastAttempt<pollSeconds(c)*1000L) || !BUSY.compareAndSet(false,true)) { complete.run();return; }
        lastAttempt=now;
        NETWORK.execute(()->{
            HttpURLConnection connection=null;
            long deadline=android.os.SystemClock.elapsedRealtime()+8000;
            try {
                prefs(c).edit().putString("error","loading").apply();
                connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
                connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
                connection.setRequestProperty("Accept","application/json");
                connection.setRequestProperty("Cache-Control","no-cache");
                connection.setInstanceFollowRedirects(false);
                if(connection.getResponseCode()!=200) throw new IOException("HTTP response");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=connection.getInputStream()) {
                    byte[] buffer=new byte[4096];int count;
                    while(true) {
                        long remaining=deadline-android.os.SystemClock.elapsedRealtime();
                        if(remaining<=0) throw new IOException("Request deadline");
                        connection.setReadTimeout((int)Math.min(4000,remaining));
                        count=input.read(buffer);if(count==-1)break;
                        if(bytes.size()+count>262144) throw new IOException("Response too large");bytes.write(buffer,0,count);
                    }
                }
                String raw=bytes.toString(StandardCharsets.UTF_8.name());
                GameSnapshot next=GameSnapshot.parse(map(new JSONObject(raw)));
                GameSnapshot old=snapshot(c);
                if(GameSnapshot.newer(old,next)==next) {
                    cached=next;loaded=true;
                    prefs(c).edit().putString("sample",raw).putString("error",next.fresh(System.currentTimeMillis())?"":"stale").apply();
                    evaluate(c,next);
                } else prefs(c).edit().putString("error","stale").apply();
            } catch(JSONException | IllegalArgumentException e) { prefs(c).edit().putString("error","invalid").apply(); }
            catch(IOException e) { prefs(c).edit().putString("error","network").apply(); }
            finally {
                if(connection!=null) connection.disconnect();
                BUSY.set(false);
                try { WatchWidget.updateAll(c); } finally { complete.run(); }
            }
        });
    }
    private static synchronized void evaluate(Context c,GameSnapshot next) {
        SharedPreferences p=prefs(c);
        if(!p.getBoolean("monitor_enabled",false)) { baseline=AlarmEngine.State.empty();return; }
        AlarmEngine.Result result=AlarmEngine.transition(next,baseline,System.currentTimeMillis(),p.getStringSet("selected_days",Collections.emptySet()),p.getBoolean("invasions",true),p.getBoolean("sound",false));
        baseline=result.state();
        if(result.special()) WatchNotifications.special(c,next.name);
        if(result.invasion()) WatchNotifications.invasion(c,next.invasionText,result.sound());
    }
}
