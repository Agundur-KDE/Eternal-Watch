// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedInputTest {
    @Test public void rejectsDeepObjectsIncludingUnknownFields() {
        String deep="{\"ignored\":".repeat(5000)+"0"+"}".repeat(5000);
        assertTrue(deep.length()<FeedInput.MAX_BYTES);
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate(deep));
    }
    @Test public void rejectsDeepArraysAndMixedContainers() {
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate("{\"ignored\":"+"[".repeat(5000)+"0"+"]".repeat(5000)+"}"));
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate("{\"ignored\":"+"[{\"a\":".repeat(100)+"0"+"}]".repeat(100)+"}"));
    }
    @Test public void allowsNormalEnvelopeAndStructuralText() {
        FeedInput.validate(" {\"eldata\":{\"GameDayName\":\"Day of Sun Tzu\",\"GameDay\":6,\"GIWS_Text\":\"[{}] \\\"quote\\\" \\\\ slash / apostrophe '\"}}\n");
        FeedInput.validate("{\"unicode\":\"Привет \\u0022\",\"ignored\":[true,false,null,-1.2e+3]}");
    }
    @Test public void enforcesExactDepthBoundary() {
        FeedInput.validate("{\"a\":".repeat(FeedInput.MAX_DEPTH)+"0"+"}".repeat(FeedInput.MAX_DEPTH));
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate("{\"a\":".repeat(FeedInput.MAX_DEPTH+1)+"0"+"}".repeat(FeedInput.MAX_DEPTH+1)));
    }
    @Test public void rejectsOversizedUtf8InputIncludingCachedStrings() {
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate("{\"x\":\""+"a".repeat(FeedInput.MAX_BYTES)+"\"}"));
        String unicode="{\"x\":\""+"я".repeat(140000)+"\"}";
        assertTrue(unicode.length()<FeedInput.MAX_BYTES);
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate(unicode));
    }
    @Test public void refusesLenientParserSyntaxThatCouldHideNesting() {
        for(String raw:new String[]{"{/* comment */\"a\":1}","{\"a\":'text'}","{\"a\":1;// comment\n}","{\"a\":1#comment\n}","{\"a\"=1}"})
            assertThrows(raw,IllegalArgumentException.class,()->FeedInput.validate(raw));
    }
    @Test public void rejectsBrokenOrMultipleRoots() {
        for(String raw:new String[]{"", "[]", "{}{}", "{]", "{\"a\":\"unterminated}", "{\"a\":\"line\nbreak\"}", "{\"a\":[1}"})
            assertThrows(raw,IllegalArgumentException.class,()->FeedInput.validate(raw));
        assertThrows(IllegalArgumentException.class,()->FeedInput.validate(null));
    }
    @Test public void rejectsUnquotedTokensAndLenientGrammarBeforePlatformParser() {
        for(String raw:new String[]{"{true\"key\":0}","{true:0}","{\"a\":true\"text\"}",
                "{\"a\":01}","{\"a\":+1}","{\"a\":1.}","{\"a\":1e}","{\"a\":truefalse}",
                "{\"a\":1,}","{\"a\":[,]}","{\"a\":[0,]}","{\"a\":\"\\x41\"}","{\"a\":\"\\u12xz\"}"})
            assertThrows(raw,IllegalArgumentException.class,()->FeedInput.validate(raw));
    }
}
