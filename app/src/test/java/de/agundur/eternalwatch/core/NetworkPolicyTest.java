// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.agundur.eternalwatch.core.NetworkPolicy.Status.*;

public class NetworkPolicyTest {
    @Test public void defaultsToBlockingMeteredConnections() {
        assertEquals(METERED,NetworkPolicy.decide(true,true,true,false,false));
    }
    @Test public void explicitChoiceAllowsMeteredConnections() {
        assertEquals(ALLOWED,NetworkPolicy.decide(true,true,true,true,false));
    }
    @Test public void saverOverridesMeteredOptIn() {
        assertEquals(DATA_SAVER,NetworkPolicy.decide(true,true,true,true,true));
        assertEquals(DATA_SAVER,NetworkPolicy.decide(true,true,true,false,true));
    }
    @Test public void unmeteredWorksWithEitherChoiceAndSaver() {
        for(boolean choice:new boolean[]{true,false})for(boolean saver:new boolean[]{true,false})
            assertEquals(ALLOWED,NetworkPolicy.decide(true,true,false,choice,saver));
    }
    @Test public void offlineAndCaptivePortalNeverAllowFetching() {
        for(boolean choice:new boolean[]{true,false}) {
            assertEquals(OFFLINE,NetworkPolicy.decide(false,false,true,choice,false));
            assertEquals(OFFLINE,NetworkPolicy.decide(true,false,false,choice,false));
            assertEquals(OFFLINE,NetworkPolicy.decide(false,true,false,choice,false));
        }
    }
}
