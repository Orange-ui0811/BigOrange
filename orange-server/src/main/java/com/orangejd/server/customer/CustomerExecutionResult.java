package com.orangejd.server.customer;

import java.util.Map;

public record CustomerExecutionResult(Map<String,Object> result, Map<String,Object> state) {}
