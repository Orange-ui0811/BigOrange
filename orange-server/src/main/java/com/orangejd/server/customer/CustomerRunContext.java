package com.orangejd.server.customer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request-local resources; never serialize this object into graph state. */
public record CustomerRunContext(String runId, Instant deadline, List<Map<String,Object>> usage) {}
