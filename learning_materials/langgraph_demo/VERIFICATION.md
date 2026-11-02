# 运行验证记录

- 验证日期：2026-09-05
- Python：3.13.8
- LangGraph：1.2.10
- langchain-core：1.6.2
- 真实模型 API：未使用

## 单元测试

```text
test_different_threads_are_isolated ... ok
test_empty_input_is_rejected_before_agent ... ok
test_same_thread_uses_checkpointed_tool_results ... ok
test_tool_loop_reaches_final_answer ... ok

Ran 4 tests in 0.159s
OK
```

## 第一轮图执行

```text
validate
→ agent:request_knowledge
→ tools:lookup_fault_knowledge
→ agent:request_parameters
→ tools:recommend_parameters
→ agent:final_answer
→ END
```

最终状态：`completed`

- 决策节点调用：3 次
- 工具结果消息：2 条
- 最终答案包含故障分类、检查步骤、保守参数和单变量调整原则

## 第二轮同线程追问

使用同一个 `thread_id=printer-demo-001` 追问“把刚才的建议压缩成三条”。

- 决策节点调用：1 次
- 新增工具调用：0 次
- 成功读取 Checkpointer 中的历史工具结果并生成压缩答案

## 结论

Demo 已实际跑通 StateGraph、状态 Reducer、条件边、Agent—Tool 循环、ToolNode、InMemorySaver、同线程短期记忆和不同线程隔离。确定性 `agent_node` 是模型替身，因此本记录不声称已经接入真实 LLM。
