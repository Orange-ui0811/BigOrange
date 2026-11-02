package com.orangejd.server.customer;

/** Orchestration only. The runner owns the final transactional commit. */
public interface CustomerExecutionEngine {
    enum Kind { LEGACY, LANGGRAPH4J }
    Kind kind();
    CustomerExecutionResult execute(CustomerRunContext context);
}
