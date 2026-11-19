// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GameRulesTest {
    final long now=1793967918841L;
    Map<String,Object> payload() {
        Map<String,Object> p=new HashMap<>();
        p.put("GameDay","6");p.put("GameMonth","5");p.put("GameYear","89");p.put("GameHour","3");p.put("GameMinute","51");p.put("GameSecond","10");
        p.put("GameDayName","Normal Day");p.put("GameDayDescription","Just an ordinary day");p.put("Gametimestamp",Long.toString(now-10000));p.put("Timestamp",Long.toString(now));
        p.put("GIWS_Monsters","0");p.put("GIWS_Text","An old warning");p.put("GIWS_date_diff",Long.toString(now-36000000));return p;
    }
    GameSnapshot sample() { return GameSnapshot.parse(payload()); }
    AlarmEngine.State baseline(GameSnapshot s) { return AlarmEngine.transition(s,AlarmEngine.State.empty(),now,Set.of(s.name),true,true).state(); }
    @Test public void parsesStringNumbersAndEnvelope() {
        assertEquals(89,sample().year);assertEquals(13870,GameSnapshot.parse(Map.of("eldata",payload())).seconds);
        assertEquals("03:51:19",sample().clock(now,true));
    }
    @Test public void gameMinuteTakes61RealSeconds() {
        Map<String,Object> p=payload();p.put("GameHour",0);p.put("GameMinute",0);p.put("GameSecond",0);p.put("Gametimestamp",now);
        GameSnapshot s=GameSnapshot.parse(p);
        assertEquals("00:00:59",s.clock(now+60000,true));assertEquals("00:01:00",s.clock(now+61000,true));assertEquals("00:02:00",s.clock(now+122000,true));
    }
    @Test public void staleAndFutureSamplesNeverDisplayLiveTime() {
        assertFalse(sample().fresh(now+180001));assertFalse(sample().fresh(now-70000));assertEquals("--:--",sample().clock(now+200000,false));
        Map<String,Object> p=payload();p.put("Gametimestamp",now-999999);assertFalse(GameSnapshot.parse(p).fresh(now));
    }
    @Test public void oldDayIsNotCarriedAcrossRollover() {
        Map<String,Object> p=payload();p.put("GameHour",5);p.put("GameMinute",59);p.put("GameSecond",59);p.put("Gametimestamp",now-2000);
        assertEquals("--:--",GameSnapshot.parse(p).clock(now,true));
    }
    @Test public void invalidSamplesRejected() {
        for(Object[] row:new Object[][]{{"GameDay",0},{"Timestamp",null},{"GameMinute","12x"},{"GameHour",6},{"GIWS_Monsters",-1},{"GameDayName",""},{"GameYear",true}}) {
            Map<String,Object> p=payload();p.put((String)row[0],row[1]);assertThrows(IllegalArgumentException.class,()->GameSnapshot.parse(p));
        }
    }
    @Test public void genericSpecialDayHeadingResolvesToActualCatalogName() {
        Map<String,Object> p=payload();p.put("GameDayName","Today is a special day:");p.put("GameDayDescription","Day of stronger animals and monsters");
        GameSnapshot s=GameSnapshot.parse(p);assertEquals("Day of Stronger Animals and Monsters",s.name);
        assertTrue(AlarmEngine.transition(s,baseline(sample()),now,Set.of(s.name),true,false).special());
    }
    @Test public void startupAlwaysSilent() {
        Map<String,Object> p=payload();p.put("GameDayName","Day of Sun Tzu");p.put("GIWS_Text","New warning");p.put("GIWS_date_diff",now);
        AlarmEngine.Result r=AlarmEngine.transition(GameSnapshot.parse(p),AlarmEngine.State.empty(),now,Set.of("Day of Sun Tzu"),true,true);
        assertFalse(r.special());assertFalse(r.invasion());assertFalse(r.sound());assertTrue(r.state().initialized());
    }
    @Test public void onlySelectedDaysNotifyCaseInsensitively() {
        Map<String,Object> p=payload();p.put("GameDayName","Reconstruction day");GameSnapshot s=GameSnapshot.parse(p);
        assertTrue(AlarmEngine.transition(s,baseline(sample()),now,Set.of("Reconstruction Day"),true,false).special());
        assertFalse(AlarmEngine.transition(s,baseline(sample()),now,Set.of("Day of Sun Tzu"),true,false).special());
        assertFalse(AlarmEngine.transition(s,baseline(sample()),now,Set.of(),true,false).special());
    }
    @Test public void sameDayDuplicateSilentButNextDateCanNotify() {
        Map<String,Object> p=payload();p.put("GameDayName","Day of Sun Tzu");GameSnapshot s=GameSnapshot.parse(p);
        assertFalse(AlarmEngine.transition(s,baseline(s),now,Set.of(s.name),true,false).special());p.put("GameDay",7);
        assertTrue(AlarmEngine.transition(GameSnapshot.parse(p),baseline(s),now,Set.of(s.name),true,false).special());
    }
    @Test public void ordinaryDayNeverRaisesSpecialAlert() {
        Map<String,Object> p=payload();p.put("GameDayName","Day of Sun Tzu");
        assertFalse(AlarmEngine.transition(sample(),baseline(GameSnapshot.parse(p)),now,Set.of("Normal Day"),true,false).special());
    }
    @Test public void oldAndFutureInvasionsSuppressed() {
        assertFalse(sample().recentInvasion(now));Map<String,Object> p=payload();p.put("GIWS_date_diff",now+120000);assertFalse(GameSnapshot.parse(p).recentInvasion(now));
    }
    @Test public void freshInvasionWarnsOnceAndHonorsToggles() {
        Map<String,Object> p=payload();p.put("GIWS_Text","Invasion in 10 minutes");p.put("GIWS_date_diff",now);GameSnapshot s=GameSnapshot.parse(p);
        AlarmEngine.Result r=AlarmEngine.transition(s,baseline(sample()),now,Set.of(),true,true);
        assertTrue(r.invasion());assertTrue(r.sound());assertFalse(AlarmEngine.transition(s,r.state(),now,Set.of(),true,true).invasion());
        assertFalse(AlarmEngine.transition(s,baseline(sample()),now,Set.of(),false,true).invasion());assertFalse(AlarmEngine.transition(s,baseline(sample()),now,Set.of(),true,false).sound());
    }
    @Test public void staleDataDoesNotConsumeAlarmState() {
        AlarmEngine.State old=baseline(sample());Map<String,Object> p=payload();p.put("GameDayName","Day of Sun Tzu");
        AlarmEngine.Result r=AlarmEngine.transition(GameSnapshot.parse(p),old,now+999999,Set.of("Day of Sun Tzu"),true,true);
        assertSame(old,r.state());assertFalse(r.special());assertFalse(r.invasion());
    }
    @Test public void delayedResponsesNeverReplaceNewerSample() {
        GameSnapshot old=sample();Map<String,Object> p=payload();p.put("Timestamp",now-1000);assertSame(old,GameSnapshot.newer(old,GameSnapshot.parse(p)));
        p.put("Timestamp",now+1000);GameSnapshot next=GameSnapshot.parse(p);assertSame(next,GameSnapshot.newer(old,next));
    }
}
