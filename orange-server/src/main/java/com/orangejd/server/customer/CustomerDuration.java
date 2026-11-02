package com.orangejd.server.customer;

import java.util.*;
import java.util.regex.*;

/** Model supplies temporal semantics; Java owns units, bounds and inclusive comparison. */
public final class CustomerDuration {
    private CustomerDuration() {}
    private static final Map<String,Integer> UNITS=Map.of("DAY",1,"WEEK",7,"MONTH",30,"YEAR",365);
    public static int normalize(Object value) {
        return normalize(value,"AT_MOST");
    }
    public static int normalize(Object value,String relation) {
        if(value instanceof Number n) { if(n.doubleValue()!=n.intValue()) throw new IllegalArgumentException("INVALID_DURATION"); return bounded(n.doubleValue()); } // persisted v3 actions
        if(!(value instanceof Map<?,?> temporal)) throw new IllegalArgumentException("INVALID_DURATION");
        if(!relation.equals(temporal.get("relation"))) throw new IllegalArgumentException("AMBIGUOUS_DURATION_RELATION");
        if(!(temporal.get("quantity") instanceof Number n) || !UNITS.containsKey(temporal.get("unit"))) throw new IllegalArgumentException("INVALID_DURATION");
        double days=n.doubleValue()*UNITS.get(temporal.get("unit"));
        int floor=bounded(days);
        return "AT_LEAST".equals(relation)?(int)Math.ceil(days):floor;
    }
    private static int bounded(double days) {
        if(!Double.isFinite(days) || days<1 || days>3650) throw new IllegalArgumentException("INVALID_DURATION");
        return (int)Math.floor(days);
    }
    /** Conservative parser for stored project estimates, never infers a missing estimate. */
    public static Integer estimate(String raw) {
        return estimate(raw,true);
    }
    public static Integer estimate(String raw,boolean upper) {
        if(raw==null || raw.matches("(?si).*(每天|每日|每晚|工作日|小时|per day|daily|working day|business day).*")) return null;
        String text=raw.toLowerCase(Locale.ROOT).strip().replace("半年","6个月").replace("半个月","15天").replace("半月","15天").replace("半个星期","3.5天").replace("半周","3.5天");
        Matcher chinese=Pattern.compile("[零〇一二两三四五六七八九十百]+").matcher(text);
        StringBuffer normalized=new StringBuffer();
        while(chinese.find()) chinese.appendReplacement(normalized,String.valueOf(chineseNumber(chinese.group())));
        chinese.appendTail(normalized); text=normalized.toString();
        Matcher m=Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*[-~至到—–]\\s*(\\d+(?:\\.\\d+)?))?\\s*(个月|星期|礼拜|周|天|月|年|weeks?|months?|days?|years?)").matcher(text);
        if(!m.find()) return null;
        String prefix=text.substring(0,m.start()),suffix=text.substring(m.end());
        // Mixed estimates, lower bounds and free prose stay unknown; do not pick a convenient number.
        if(!prefix.matches("(?:约|大约|预计|最多|至多|不超过|少于|小于|within|about|up to|at most|under|in|\\s)*")
            || !suffix.matches("(?:内|以内|左右|完成|结束|搞定|之内|\\s)*")) return null;
        double quantity=Double.parseDouble(!upper || m.group(2)==null?m.group(1):m.group(2));
        String unit=m.group(3);
        int multiplier=unit.matches("周|星期|礼拜|weeks?")?7:unit.matches("个月|月|months?")?30:unit.matches("年|years?")?365:1;
        double days=quantity*multiplier;
        return days>=1 && days<=3650?(upper?(int)Math.ceil(days):(int)Math.floor(days)):null;
    }
    private static int chineseNumber(String value) {
        int result=0,digit=0;
        for(char c:value.toCharArray()) {
            if(c=='十' || c=='百') { result+=(digit==0?1:digit)*(c=='十'?10:100); digit=0; }
            else digit=switch(c) { case '一'->1; case '二','两'->2; case '三'->3; case '四'->4; case '五'->5; case '六'->6; case '七'->7; case '八'->8; case '九'->9; default->0; };
        }
        return result+digit;
    }
}
