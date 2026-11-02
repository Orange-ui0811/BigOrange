package com.orangejd.server.customer;

import org.bsc.langgraph4j.state.AgentState;
import java.util.Map;

/** Diagnostic state only: full request payloads live in the request context/payload store. */
public final class CustomerGraphState extends AgentState {
    public CustomerGraphState(Map<String,Object> data) {super(data);}
    public String next() {return this.<String>value("next").orElse("END");}
}
