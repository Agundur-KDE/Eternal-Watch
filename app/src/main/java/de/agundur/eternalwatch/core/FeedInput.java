// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;
import java.nio.charset.StandardCharsets;

/** Iterative strict JSON validation before the platform's recursive parser runs. */
public final class FeedInput {
    public static final int MAX_BYTES=262144;
    public static final int MAX_DEPTH=8;
    private static final int OBJECT_FIRST=0,OBJECT_KEY=1,OBJECT_COLON=2,OBJECT_VALUE=3,
        OBJECT_NEXT=4,ARRAY_FIRST=5,ARRAY_VALUE=6,ARRAY_NEXT=7;
    private FeedInput() { }
    public static void validate(String raw) {
        if(raw==null || raw.length()>MAX_BYTES || raw.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Response too large or missing");
        int i=whitespace(raw,0);
        if(i==raw.length() || raw.charAt(i)!='{') throw invalid();
        // Validate complete grammar, not just balanced brackets: JSONTokener also
        // accepts unquoted literals with quotes inside them. Those must never be
        // mistaken for strings that could hide nesting from this depth limit.
        int[] stack=new int[MAX_DEPTH];int depth=1;
        stack[0]=OBJECT_FIRST;i++;
        while(depth>0) {
            i=whitespace(raw,i);
            if(i==raw.length()) throw invalid();
            char ch=raw.charAt(i);
            int state=stack[depth-1];
            if(state==OBJECT_FIRST || state==OBJECT_KEY) {
                if(state==OBJECT_FIRST && ch=='}') { depth--;i++;continue; }
                if(ch!='"') throw invalid();
                i=stringEnd(raw,i);stack[depth-1]=OBJECT_COLON;
                continue;
            }
            if(state==OBJECT_COLON) {
                if(ch!=':') throw invalid();
                stack[depth-1]=OBJECT_VALUE;i++;continue;
            }
            if(state==OBJECT_NEXT || state==ARRAY_NEXT) {
                if(ch==(state==OBJECT_NEXT?'}':']')) { depth--;i++;continue; }
                if(ch!=',') throw invalid();
                stack[depth-1]=state==OBJECT_NEXT?OBJECT_KEY:ARRAY_VALUE;i++;continue;
            }
            if(state==ARRAY_FIRST && ch==']') { depth--;i++;continue; }
            stack[depth-1]=state==OBJECT_VALUE?OBJECT_NEXT:ARRAY_NEXT;
            if(ch=='{' || ch=='[') {
                if(depth==MAX_DEPTH) throw new IllegalArgumentException("JSON nesting limit");
                stack[depth++]=ch=='{'?OBJECT_FIRST:ARRAY_FIRST;i++;
            } else if(ch=='"') i=stringEnd(raw,i);
            else if(ch=='t' && raw.startsWith("true",i)) i+=4;
            else if(ch=='f' && raw.startsWith("false",i)) i+=5;
            else if(ch=='n' && raw.startsWith("null",i)) i+=4;
            else i=numberEnd(raw,i);
        }
        if(whitespace(raw,i)!=raw.length()) throw invalid();
    }
    private static int whitespace(String raw,int i) {
        while(i<raw.length() && " \t\r\n".indexOf(raw.charAt(i))>=0) i++;
        return i;
    }
    private static int stringEnd(String raw,int i) {
        i++;
        while(i<raw.length()) {
            char ch=raw.charAt(i++);
            if(ch=='"') return i;
            if(ch<0x20) throw invalid();
            if(ch=='\\') {
                if(i==raw.length()) throw invalid();
                char escape=raw.charAt(i++);
                if(escape=='u') {
                    if(raw.length()-i<4) throw invalid();
                    for(int end=i+4;i<end;i++)
                        if("0123456789abcdefABCDEF".indexOf(raw.charAt(i))<0) throw invalid();
                } else if("\"\\/bfnrt".indexOf(escape)<0) throw invalid();
            }
        }
        throw invalid();
    }
    private static int numberEnd(String raw,int i) {
        if(raw.charAt(i)=='-') i++;
        if(i==raw.length()) throw invalid();
        if(raw.charAt(i)=='0') i++;
        else {
            if(raw.charAt(i)<'1' || raw.charAt(i)>'9') throw invalid();
            do { i++; } while(i<raw.length() && digit(raw.charAt(i)));
        }
        if(i<raw.length() && raw.charAt(i)=='.') {
            i++;
            if(i==raw.length() || !digit(raw.charAt(i))) throw invalid();
            do { i++; } while(i<raw.length() && digit(raw.charAt(i)));
        }
        if(i<raw.length() && (raw.charAt(i)=='e' || raw.charAt(i)=='E')) {
            i++;
            if(i<raw.length() && (raw.charAt(i)=='+' || raw.charAt(i)=='-')) i++;
            if(i==raw.length() || !digit(raw.charAt(i))) throw invalid();
            do { i++; } while(i<raw.length() && digit(raw.charAt(i)));
        }
        return i;
    }
    private static boolean digit(char ch) { return ch>='0' && ch<='9'; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid JSON input"); }
}
