// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import java.util.Set;
public final class AlarmEngine {
    public record State(boolean initialized,String day,String invasion) {
        public static State empty() { return new State(false,"",""); }
    }
    public record Result(State state,boolean special,boolean invasion,boolean sound) { }
    public static Result transition(GameSnapshot data,State state,long now,Set<String> days,boolean invasion,boolean sound) {
        if (data==null || !data.fresh(now)) return new Result(state,false,false,false);
        String dayKey=data.dayKey(),invasionKey=data.invasionText.isEmpty()?"":data.invasionStamp+"/"+data.invasionText;
        boolean special=state.initialized() && !dayKey.equals(state.day()) && !SpecialDays.normal(data.name) && SpecialDays.selected(data.name,days);
        boolean warning=state.initialized() && invasion && !invasionKey.equals(state.invasion()) && data.recentInvasion(now);
        return new Result(new State(true,dayKey,invasionKey),special,warning,warning && sound);
    }
}
