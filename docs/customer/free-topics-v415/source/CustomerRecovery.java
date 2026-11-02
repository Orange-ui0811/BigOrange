package com.orangejd.server.customer;

import java.util.*;

/** User-facing continuation; the real error and terminal status remain auditable. */
public final class CustomerRecovery {
    private CustomerRecovery() {}
    public static String message(String status,String error) {
        if("CANCELLED".equals(status)) return "已停止这次回答，之前的消息和已确认条件都保留着。你可以继续聊，或换一个问题。";
        if("TIMED_OUT".equals(status)) return "这次响应超过了等待时间，我还没有拿到完整结果。会话和已确认条件已保留，你可以继续补充要求或稍后让我接着推荐。";
        if("INTERRUPTED".equals(status)) return "服务重启打断了这次回答，历史和已确认条件已保留。你可以从这里继续，我不会自动重复执行刚才的请求。";
        if(error!=null && (error.contains("CONDITION") || error.contains("DURATION") || error.contains("MODEL_OUTPUT") || error.contains("MODEL_JSON") || error.contains("INTENT")))
            return "我还没能可靠地整理这句话中的要求，因此没有擅自更改条件。你可以继续解释最重要的一项，我会接着帮你；已有会话仍然保留。";
        if(error!=null && (error.contains("EVIDENCE") || error.contains("INDEX") || error.contains("SEARCH")))
            return "资料核对暂时没有完成，不能据此确认项目细节。我们可以继续讨论需求；恢复后我会根据已确认条件重新查找。";
        return "这次服务暂时没有返回完整回复，已确认条件和聊天记录仍然保留。你可以继续发消息；我不会把未完成的内容当作已核实的答案。";
    }
    public static String code(RuntimeException ex) {
        String code=ex.getMessage();
        return code!=null && code.matches("[A-Z][A-Z0-9_]{2,70}")?code:"DEPENDENCY_FAILED";
    }
    public static void requireRecoverable(RuntimeException ex) {
        if(Thread.currentThread().isInterrupted() || Set.of("RUN_STOPPED","CUSTOMER_DISABLED","DEADLINE_EXCEEDED").contains(code(ex))) throw ex;
        if(ex instanceof org.springframework.web.server.ResponseStatusException) throw ex;
    }
}
