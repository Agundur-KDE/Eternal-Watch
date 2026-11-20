// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import de.agundur.eternalwatch.core.*;
import org.json.*;

/** Actual platform parser checks, entirely in memory: no preferences or network mutations. */
final class FeedSecurityChecks {
    private FeedSecurityChecks() { }
    static void run() throws Exception {
        long now=System.currentTimeMillis();
        JSONObject feed=new JSONObject();
        feed.put("GameDay",6).put("GameMonth",5).put("GameYear",89);
        feed.put("GameHour",3).put("GameMinute",51).put("GameSecond",10);
        feed.put("GameDayName","Normal Day").put("GameDayDescription","[{}] \"text\" \\ path / apostrophe '");
        feed.put("Gametimestamp",now).put("Timestamp",now).put("GIWS_Monsters",0);
        String healthy=feed.toString();
        require(FeedRepository.parseFeed(healthy,now).fresh(now),"Healthy feed");
        require(FeedRepository.parseFeed(new JSONObject().put("eldata",feed).toString(),now).fresh(now),"Wrapped feed");
        feed.put("ignored",new JSONObject().put("nested",new JSONArray().put(true)));
        require(FeedRepository.parseFeed(feed.toString(),now).fresh(now),"Unknown bounded fields");
        feed.remove("ignored");
        for(String field:new String[]{"Timestamp","Gametimestamp"}) {
            Object original=feed.get(field);
            feed.put(field,now+60001);reject(feed.toString(),now);
            feed.put(field,original);
        }
        for(Object bad:new Object[]{new JSONObject(),new JSONArray(),true}) {
            feed.put("GameDayDescription",bad);reject(feed.toString(),now);
        }
        feed.put("GameDayDescription",JSONObject.NULL);
        require(FeedRepository.parseFeed(feed.toString(),now).description.isEmpty(),"Null optional text");
        String deep="{\"ignored\":".repeat(5000)+"0"+"}".repeat(5000);
        reject(deep,now);
        reject("{\"ignored\":"+"[".repeat(5000)+"0"+"]".repeat(5000)+"}",now);
        reject("{\"ignored\":\""+"я".repeat(140000)+"\"}",now);
        reject("{/*comment*/\"x\":1}",now);
        // Android's lenient parser treats quotes inside unquoted literals as
        // literal characters. Reject these before its grammar can diverge.
        reject("{true\"key\":0}",now);
        reject("{\"a\":true\"text\"}",now);
        reject("{\"a\":01}",now);
        reject("{\"a\":[0,]}",now);
        // Both the network and cache loader use this parser. Historical values
        // remain available, and rejecting a bad input does not poison the next one.
        require(!FeedRepository.parseFeed(healthy,now+86400000).fresh(now+86400000),"Historical cache");
        require(FeedRepository.parseFeed(healthy,now).fresh(now),"Recovery after invalid inputs");
    }
    private static void reject(String raw,long now) throws Exception {
        try { FeedRepository.parseFeed(raw,now); }
        catch(IllegalArgumentException | JSONException expected) { return; }
        throw new AssertionError("Unsafe feed accepted");
    }
    private static void require(boolean condition,String label) {
        if(!condition) throw new AssertionError(label);
    }
}
