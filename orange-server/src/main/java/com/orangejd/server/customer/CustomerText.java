package com.orangejd.server.customer;

import java.util.regex.*;

/** Decode over-escaped paragraphs without rewriting code or quoted evidence. */
public final class CustomerText {
    private CustomerText() {}
    public static String paragraphs(String text) {
        if(text==null) return "";
        Matcher code=Pattern.compile("(?s)```.*?```|`[^`\\n]*`").matcher(text);
        StringBuilder result=new StringBuilder(); int offset=0;
        while(code.find()) {
            result.append(plain(text.substring(offset,code.start()))).append(code.group());offset=code.end();
        }
        return result.append(plain(text.substring(offset))).toString();
    }
    private static String plain(String text) { return text.replaceAll("(?:\\\\r\\\\n|\\\\n){2,}","\n\n"); }
}
