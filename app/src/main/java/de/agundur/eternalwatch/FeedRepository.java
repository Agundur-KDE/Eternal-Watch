// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.os.Handler;
import android.os.Looper;
import de.agundur.eternalwatch.core.*;

public final class FeedRepository {
    public static final String ENDPOINT="https://eternal-lands.agundur.de/_resources/eternal-watch-feed.php";
    private static Context application;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final RequestCoordinator REQUESTS=new RequestCoordinator(8000,android.os.SystemClock::elapsedRealtime,
        command->MAIN.post(command),new RequestCoordinator.Observer() {
            @Override public void accepted(RequestCoordinator.Attempt attempt) { lastAttempt=attempt.startedMillis(); }
            @Override public void started(RequestCoordinator.Attempt attempt) {
                prefs(application).edit().putString("error","loading").apply();
            }
            @Override public void finished(RequestCoordinator.Attempt attempt,RequestCoordinator.Result result) {
                if(result==RequestCoordinator.Result.OK && attempt.value() instanceof Prepared prepared) {
                    try {
                        GameSnapshot next=prepared.snapshot();long now=System.currentTimeMillis();
                        GameSnapshot old=snapshot(application);
                        if(GameSnapshot.newer(old,next,now)==next) {
                            cached=next;loaded=true;
                            prefs(application).edit().putString("sample",prepared.raw()).apply();
                            evaluate(application,next);
                            if(!next.fresh(now))result=RequestCoordinator.Result.STALE;
                        } else result=RequestCoordinator.Result.STALE;
                    } catch(IllegalArgumentException clockChanged) { result=RequestCoordinator.Result.INVALID; }
                }
                if(REQUESTS.latest(attempt)) {
                    String error=switch(result) {
                        case OK, CANCELLED -> "";
                        case STALE -> "stale";
                        case INVALID -> "invalid";
                        case NETWORK, TIMEOUT -> "network";
                    };
                    prefs(application).edit().putString("error",error).apply();
                }
                WatchWidget.updateAll(application);
            }
        });
    private static volatile GameSnapshot cached;
    private static volatile boolean loaded;
    private static volatile long lastAttempt;
    private static AlarmEngine.State baseline=AlarmEngine.State.empty();
    private record Prepared(GameSnapshot snapshot,String raw) { }
    private FeedRepository() { }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("watch",Context.MODE_PRIVATE); }
    public static int pollSeconds(Context c) { return Math.max(15,Math.min(300,prefs(c).getInt("poll_seconds",30))); }
    static GameSnapshot parseFeed(String raw,long now) throws JSONException {
        FeedInput.validate(raw);
        JSONObject json=new JSONObject(raw);
        if(json.has("eldata")) json=json.getJSONObject("eldata");
        Map<String,Object> values=new HashMap<>();
        String[] fields={"GameDay","GameMonth","GameYear","GameHour","GameMinute","GameSecond",
            "GameDayName","GameDayDescription","GameDayRolePlay","Gametimestamp","Timestamp",
            "GIWS_Monsters","GIWS_Text","GIWS_date_diff"};
        for(String key:fields) {
            Object v=json.opt(key);
            if(v==JSONObject.NULL) v=null;
            if(v!=null && !(v instanceof String) && !(v instanceof Number))
                throw new IllegalArgumentException("Expected scalar feed field");
            values.put(key,v);
        }
        return GameSnapshot.parse(values).validateForStorage(now);
    }
    public static GameSnapshot snapshot(Context c) {
        if (!loaded) synchronized(FeedRepository.class) {
            if (!loaded) {
                try { String raw=prefs(c).getString("sample","");if(!raw.isEmpty()) cached=parseFeed(raw,System.currentTimeMillis()); }
                catch (JSONException | IllegalArgumentException ignored) {
                    cached=null;
                    prefs(c).edit().remove("sample").putString("error","invalid").apply();
                }
                loaded=true;
            }
        }
        return cached;
    }
    public static synchronized void resetAlarms() { baseline=AlarmEngine.State.empty(); }
    public static void request(Context context,boolean force) { request(context,force,()->{}); }
    public static void request(Context context,boolean force,Runnable complete) {
        Context c=context.getApplicationContext();application=c;
        if(!NetworkAccess.current(c).allowed()) { WatchWidget.updateAll(c);complete.run();return; }
        long now=android.os.SystemClock.elapsedRealtime();
        if(!force && lastAttempt>0 && !REQUESTS.busy() && now-lastAttempt<pollSeconds(c)*1000L) { complete.run();return; }
        REQUESTS.submit(attempt->fetch(c,attempt),complete,force?()->request(c,true):null);
    }
    static void reconnect(Context c) {
        if(!NetworkAccess.interested(c))return;
        application=c.getApplicationContext();
        // Re-evaluate interest and network policy when an overlapping request ends.
        REQUESTS.submit(attempt->fetch(c,attempt),()->{},()->{
            if(NetworkAccess.interested(c) && NetworkAccess.current(c).allowed())request(c,true);
        });
    }
    private static RequestCoordinator.Result fetch(Context c,RequestCoordinator.Attempt attempt) throws IOException {
        HttpURLConnection connection=null;
        try {
            attempt.check();
            NetworkAccess.Access access=NetworkAccess.current(c);
            if(!access.allowed())return RequestCoordinator.Result.CANCELLED;
            connection=(HttpURLConnection)access.network().openConnection(new URL(ENDPOINT));
            HttpURLConnection opened=connection;attempt.cleanup(opened::disconnect);
            connection.setConnectTimeout(attempt.remainingMillis(4000));
            connection.setReadTimeout(attempt.remainingMillis(4000));
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("Cache-Control","no-cache");
            connection.setInstanceFollowRedirects(false);
            attempt.check();
            if(!NetworkAccess.permits(c,access.network()))return RequestCoordinator.Result.CANCELLED;
            if(connection.getResponseCode()!=200)throw new IOException("HTTP response");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()) {
                byte[] buffer=new byte[4096];int count;
                while(true) {
                    if(!NetworkAccess.permits(c,access.network()))return RequestCoordinator.Result.CANCELLED;
                    connection.setReadTimeout(attempt.remainingMillis(4000));
                    count=input.read(buffer);if(count==-1)break;
                    if(bytes.size()+count>FeedInput.MAX_BYTES)throw new IOException("Response too large");
                    bytes.write(buffer,0,count);
                }
            }
            attempt.check();
            if(!NetworkAccess.permits(c,access.network()))return RequestCoordinator.Result.CANCELLED;
            String raw=bytes.toString(StandardCharsets.UTF_8.name());
            long receivedAt=System.currentTimeMillis();
            GameSnapshot next=parseFeed(raw,receivedAt);
            return attempt.offer(new Prepared(next,raw))?RequestCoordinator.Result.OK:RequestCoordinator.Result.CANCELLED;
        } catch(JSONException | IllegalArgumentException e) { return RequestCoordinator.Result.INVALID; }
    }
    private static synchronized void evaluate(Context c,GameSnapshot next) {
        SharedPreferences p=prefs(c);
        if(!p.getBoolean("monitor_enabled",false) || !WatchNotifications.monitorPermitted(c)) { baseline=AlarmEngine.State.empty();return; }
        AlarmEngine.Result result=AlarmEngine.transition(next,baseline,System.currentTimeMillis(),p.getStringSet("selected_days",Collections.emptySet()),p.getBoolean("invasions",true),p.getBoolean("sound",false));
        baseline=result.state();
        if(result.special()) WatchNotifications.special(c,next.name);
        if(result.invasion()) WatchNotifications.invasion(c,next.invasionText,result.sound());
    }
}
