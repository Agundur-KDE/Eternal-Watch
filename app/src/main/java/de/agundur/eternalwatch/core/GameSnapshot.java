// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import java.util.Map;
import java.util.Locale;
public final class GameSnapshot {
    public final int day,month,year,seconds,monsters;
    public final long clockStamp,stamp,invasionStamp;
    public final String name,description,rolePlay,invasionText;
    public GameSnapshot(int day,int month,int year,int seconds,long clockStamp,long stamp,String name,String description,String rolePlay,int monsters,String invasionText,long invasionStamp) {
        this.day=day;this.month=month;this.year=year;this.seconds=seconds;this.clockStamp=clockStamp;this.stamp=stamp;this.name=name;this.description=description;this.rolePlay=rolePlay;this.monsters=monsters;this.invasionText=invasionText;this.invasionStamp=invasionStamp;
    }
    public static GameSnapshot parse(Map<String,?> payload) {
        if (payload==null) throw new IllegalArgumentException("Missing payload");
        Map<?,?> raw=payload.get("eldata") instanceof Map ? (Map<?,?>)payload.get("eldata") : payload;
        String description=text(raw,"GameDayDescription");
        String name=SpecialDays.resolve(text(raw,"GameDayName"),description);
        if (name.isEmpty()) throw new IllegalArgumentException("Missing day name");
        long clockStamp=timestamp(raw.get("Gametimestamp")),stamp=timestamp(raw.get("Timestamp"));
        if (clockStamp==0 || stamp==0) throw new IllegalArgumentException("Missing timestamp");
        return new GameSnapshot(integer(raw.get("GameDay"),1,30),integer(raw.get("GameMonth"),1,12),integer(raw.get("GameYear"),1,99999),
            integer(raw.get("GameHour"),0,5)*3600+integer(raw.get("GameMinute"),0,59)*60+integer(raw.get("GameSecond"),0,59),
            clockStamp,stamp,name,description,text(raw,"GameDayRolePlay"),integer(raw.get("GIWS_Monsters"),0,10000000),text(raw,"GIWS_Text"),timestamp(raw.get("GIWS_date_diff")));
    }
    private static String text(Map<?,?> raw,String key) { Object v=raw.get(key);return v==null?"":v.toString().trim(); }
    private static int integer(Object value,int min,int max) {
        if (value==null || value instanceof Boolean || value.toString().trim().isEmpty()) throw new IllegalArgumentException("Missing number");
        try {
            double v=Double.parseDouble(value.toString().trim());
            if (!Double.isFinite(v) || v!=Math.floor(v) || v<min || v>max) throw new IllegalArgumentException("Invalid number");
            return (int)v;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number"); }
    }
    private static long timestamp(Object value) {
        if (value==null || value instanceof Boolean) return 0;
        try { double v=Double.parseDouble(value.toString());return Double.isFinite(v) && v>0 && v<Long.MAX_VALUE?(long)v:0; }
        catch (NumberFormatException e) { return 0; }
    }
    public boolean fresh(long now) {
        return now-stamp>=-60000 && now-stamp<=180000 && now-clockStamp>=-60000 && now-clockStamp<=180000;
    }
    public int currentSeconds(long now) {
        if (!fresh(now)) return -1;
        long result=seconds+Math.max(0,(long)Math.floor((now-clockStamp)*60.0/61000));
        return result>=21600?-1:(int)result;
    }
    public String clock(long now,boolean showSeconds) {
        int value=currentSeconds(now);
        if (value<0) return "--:--";
        return String.format(Locale.ROOT,showSeconds?"%02d:%02d:%02d":"%02d:%02d",value/3600,(value/60)%60,value%60);
    }
    public String dayKey() { return year+"/"+month+"/"+day+"/"+SpecialDays.key(name); }
    public boolean recentInvasion(long now) {
        long age=now-invasionStamp;
        return fresh(now) && !invasionText.isEmpty() && invasionStamp>0 && age>=-60000 && age<=900000;
    }
    public static GameSnapshot newer(GameSnapshot old,GameSnapshot candidate) { return old!=null && candidate.stamp<old.stamp?old:candidate; }
}
