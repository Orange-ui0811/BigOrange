先给你一个总定义：

> LangGraph 是一个用于构建长运行、有状态 Agent 和工作流的低层编排框架。它把一次 Agent 执行显式建模为“共享状态 + 节点 + 边”：节点读取状态并返回局部更新，边决定下一步执行哪个节点，循环让模型能够在“决策—工具—观察—再决策”之间反复运行，直到满足终止条件。

本阶段不是只看一个官方示例，而是已经独立创建并实际跑通一个 3D 打印排障 Demo：

```text
用户问题
  ↓
validate：输入校验
  ↓
agent：决定查询故障知识
  ↓
tools：执行 lookup_fault_knowledge
  ↓
agent：读取工具结果，决定查询保守参数
  ↓
tools：执行 recommend_parameters
  ↓
agent：读取两次工具结果，生成最终答案
  ↓
END
```

实际验证结果：

```text
第一轮：
3 次 agent 决策
2 次工具执行
最终状态 completed

第二轮，同一个 thread_id：
1 次 agent 决策
0 次新增工具执行
直接利用 Checkpointer 中的历史工具结果回答追问

单元测试：
4 项全部通过
```

完整 Demo 位于：

```text
D:\Orangejd\learning_materials\langgraph_demo
```

核心文件：

```text
agent_demo.py       完整 StateGraph、节点、工具、条件路由和运行入口
test_agent_demo.py  工具循环、线程记忆、输入校验、线程隔离测试
requirements.txt    固定 LangGraph 1.2.10
README.md           独立运行说明
VERIFICATION.md     本次真实运行验证记录
```

最重要的边界：

> Demo 中 LangGraph 的图执行、状态更新、条件边、ToolNode、循环、流式事件和 InMemorySaver 都是真实的；为了不依赖 API Key，`agent_node` 使用确定性本地逻辑模拟支持 Tool Calling 的 LLM。因此可以说“独立跑通了 LangGraph Agent Demo并理解执行链路”，但不能说“该 Demo 已接入真实大模型”。

与 OrangeVC 的边界也必须明确：

> OrangeVC 当前是 Java/Spring 工程，使用 `AgentAction`、`AiToolRouter` 等自研结构治理 AI 工具执行，并没有集成 LangGraph。LangGraph Demo 是为了满足“了解至少一种 AI Agent 开发框架并跑通 Demo”的独立学习实践，不能包装成 OrangeVC 已采用 LangGraph。

---

# 一、为什么需要 LangGraph

## 1. 普通函数调用有什么不足

一个简单 LLM 应用可以写成：

```python
answer = model.invoke(prompt)
```

如果任务只是一次生成，这已经足够。但 Agent 往往需要：

- 多步决策；
- 调用一个或多个工具；
- 根据工具结果改变下一步；
- 失败后重试或走降级路径；
- 暂停等待人工审批；
- 保存中间状态；
- 恢复未完成任务；
- 把执行过程流式展示给用户；
- 对不同节点分别测试和观测。

如果全部用嵌套 `if/while/try` 手写，代码很快会变成：

```text
控制流、业务状态、模型调用、工具副作用、重试和持久化混在一起
```

LangGraph 的价值不是“帮模型变聪明”，而是把复杂执行过程变成可描述、可检查、可恢复的图。

## 2. Workflow 与 Agent 的区别

官方文档区分：

```text
Workflow：路径主要由代码预先确定
Agent：过程和工具使用更多由模型动态决定
```

例如固定流程：

```text
读取简历 → 抽取技能 → 岗位评分 → 生成报告
```

更像 Workflow。

而下面的过程更像 Agent：

```text
模型判断缺什么信息
→ 自主选择检索工具
→ 观察结果
→ 决定继续检索、换工具还是回答
```

LangGraph 两者都能表达。图中可以同时存在：

- 代码固定的校验和安全节点；
- 模型动态决定的工具循环；
- 人工审批节点；
- 确定性的持久化与输出校验。

## 3. 为什么低层框架反而适合工程治理

LangGraph 不强迫所有 Agent 使用同一种预制结构。你可以显式决定：

- State 包含什么；
- 哪些节点允许副作用；
- 哪些边是固定的；
- 哪些边由状态决定；
- 循环何时终止；
- 哪一步需要 Checkpoint；
- 哪一步必须人工批准；
- 哪些信息可以进入模型上下文。

这种显式控制适合需要审计、恢复和复杂业务边界的 Agent。

---

# 二、LangGraph 的三个核心概念

官方 Graph API 把核心概念概括为：

```text
State
Nodes
Edges
```

## 1. State：当前世界快照

State 是所有节点共享的数据结构。它表示执行到某一时刻，系统知道什么、完成了什么、下一步可能依赖什么。

本 Demo 的 State：

```python
class AgentState(TypedDict, total=False):
    messages: Annotated[list[BaseMessage], add_messages]
    model_calls: int
    status: str
    error: str | None
    events: Annotated[list[str], operator.add]
```

各字段职责：

```text
messages
  保存 HumanMessage、AIMessage、ToolMessage
  是 Agent 观察历史和工具结果的主要上下文

model_calls
  记录本轮决策节点调用次数
  用于验证和步数保护

status
  new / running / awaiting_tool / completed / rejected / stopped
  给条件路由提供明确依据

error
  输入校验或业务错误

events
  保存验证、工具请求、最终回答等审计事件
```

State 不是数据库实体，也不应该无选择地塞入所有业务对象。应只保留后续节点、恢复和审计真正需要的数据。

## 2. Node：读取状态并返回更新

节点通常是 Python 函数：

```python
def node(state: AgentState) -> dict[str, object]:
    return {"status": "completed"}
```

节点不需要返回完整 State，只返回它修改的字段。LangGraph 再根据每个字段的 Reducer 合并更新。

本 Demo 有三个节点：

```text
validate
  确定性校验输入

agent
  模拟 LLM 决策、产生 Tool Call 或最终回答

tools
  ToolNode 根据 AIMessage 中的 tool_calls 执行注册工具
```

## 3. Edge：决定下一步

边负责控制流。

固定边：

```python
builder.add_edge(START, "validate")
builder.add_edge("tools", "agent")
```

含义：

```text
图一定先校验
工具执行完一定把观察结果交回 agent
```

条件边：

```python
builder.add_conditional_edges(
    "agent",
    route_after_agent,
    {"tools": "tools", END: END},
)
```

含义：

```text
agent 产生 tool_calls → 去 tools
agent 没有 tool_calls   → END
```

## 4. START 与 END

`START` 和 `END` 是虚拟节点：

- `START` 指定入口；
- `END` 表示本次图执行终止。

它们不是普通业务函数，不会自己生成结果。

## 5. compile 做了什么

```python
graph = builder.compile(checkpointer=checkpointer)
```

在 `compile` 前，你是在描述图；`compile` 后得到可执行对象，可以：

- `invoke`；
- `stream`；
- `get_state`；
- 使用 Checkpointer 保存线程状态；
- 配合 interrupt 暂停和恢复。

可以把它理解为：

```text
StateGraph Builder = 图定义
Compiled Graph     = 图运行时
```

---

# 三、Reducer：为什么节点返回局部更新仍能组成完整状态

## 1. 默认覆盖

没有声明 Reducer 的字段默认用新值覆盖旧值。

例如：

```text
旧 status = awaiting_tool
节点返回 status = completed
新 status = completed
```

本 Demo 中 `model_calls`、`status`、`error` 都采用覆盖语义。

## 2. operator.add

```python
events: Annotated[list[str], operator.add]
```

表示新列表追加到旧列表：

```text
["validation:accepted"]
+ ["agent:request_knowledge"]
= ["validation:accepted", "agent:request_knowledge"]
```

如果忘记 Reducer，后一个节点返回的事件会覆盖前面的事件。

## 3. add_messages

```python
messages: Annotated[list[BaseMessage], add_messages]
```

`add_messages` 专门处理消息列表。它不只是简单 `operator.add`：

- 能追加新消息；
- 能根据消息 ID 更新已有消息；
- 能把兼容的消息字典反序列化成消息对象；
- 适合对话状态。

本 Demo 每个节点只返回新增消息：

```python
return {"messages": [AIMessage(...)]}
```

Reducer 会把它合并进历史，不需要节点手工复制整个消息列表。

## 4. 并行节点为什么更需要 Reducer

当多个节点在同一 Superstep 更新同一个 State Key 时，LangGraph 必须知道怎样合并。如果没有合适 Reducer，可能发生并发更新冲突。

因此设计 State 时不能只考虑类型，还要考虑：

```text
这个字段是覆盖、累加、去重、取最大值，还是自定义合并？
```

---

# 四、消息协议：HumanMessage、AIMessage 与 ToolMessage

一次工具调用不是把字符串“请调用工具”发给框架，而是一套结构化消息协议。

## 1. HumanMessage

代表用户输入：

```python
HumanMessage(
    content="PLA 打印件四角翘起，应该先检查什么，并给出保守参数建议。"
)
```

## 2. AIMessage

模型可以输出最终文字，也可以请求工具：

```python
AIMessage(
    content="",
    tool_calls=[
        {
            "name": "lookup_fault_knowledge",
            "args": {"symptom": user_text},
            "id": "knowledge-1",
            "type": "tool_call",
        }
    ],
)
```

关键字段：

```text
name：工具名
args：结构化参数
id：本次工具调用唯一标识
type：工具调用类型
```

## 3. ToolMessage

ToolNode 执行工具后返回：

```text
ToolMessage
  name = lookup_fault_knowledge
  tool_call_id = knowledge-1
  content = 工具结果
```

`tool_call_id` 必须和发起请求的 ID 对应，模型才能知道这个结果属于哪一次调用。若一次 AIMessage 同时请求多个工具，ID 更加重要。

## 4. 一个完整消息序列

第一轮结束时，State 中的核心消息顺序是：

```text
1. HumanMessage：用户描述翘曲
2. AIMessage：请求 lookup_fault_knowledge
3. ToolMessage：返回故障知识
4. AIMessage：请求 recommend_parameters
5. ToolMessage：返回保守参数
6. AIMessage：最终整合回答
```

Agent 的“观察”就是 ToolMessage。模型再次执行时读取历史消息，才形成：

```text
Thought/Decision → Action → Observation → Decision
```

工程上不必保存或暴露模型私有思维过程；需要保存的是可审计的决策结果、工具调用参数、工具结果和最终状态。

---

# 五、工具是怎样注册和执行的

## 1. @tool 的作用

```python
@tool
def lookup_fault_knowledge(symptom: str) -> str:
    """Look up diagnostic knowledge for a 3D-printing symptom."""
```

装饰器会把普通函数包装成工具，并从以下内容形成工具 Schema：

- 函数名；
- 参数名；
- 类型标注；
- docstring；
- 返回行为。

真实 LLM 通过工具名、说明和参数 Schema 判断何时调用、怎样构造参数。

工具说明太模糊，会造成：

- 选错工具；
- 参数理解错误；
- 多个工具职责重叠；
- 不必要调用。

## 2. 本 Demo 的两个工具

### lookup_fault_knowledge

输入：

```text
symptom: 用户故障描述
```

输出：

```json
{
  "fault": "warping",
  "checks": ["...", "...", "..."],
  "risk": "..."
}
```

### recommend_parameters

输入：

```text
material
severity
```

输出保守的温度、首层速度、风扇和单变量调整原则。

## 3. ToolNode

```python
ToolNode(TOOLS, handle_tool_errors=True)
```

ToolNode 负责：

```text
读取最后一条 AIMessage.tool_calls
→ 按 name 找到注册工具
→ 用 args 调用工具
→ 把结果包装为 ToolMessage
→ 返回 messages 状态更新
```

所以 `tools` 节点不需要手写分发循环。

## 4. ToolNode 不替你解决哪些问题

ToolNode 只是执行适配层，不自动提供完整业务治理：

- 用户是否有权限；
- 工具是否允许当前租户使用；
- 是否扣费；
- 是否幂等；
- 能否重试；
- 超时时间；
- 并发限制；
- 高风险操作是否审批；
- 审计日志；
- 输出脱敏；
- 事务与补偿。

这些正是阶段 2 `AgentAction` 与 `AiToolRouter` 重点解决的问题。

## 5. 工具返回为什么使用结构化 JSON

结构化返回比自然语言更适合后续节点：

- 字段含义稳定；
- 可以验证类型；
- 便于提取和组合；
- 便于测试；
- 错误状态可以显式表达。

但 JSON 字符串仍然需要解析和校验。生产系统可以进一步使用 Pydantic 模型或明确的工具返回 Schema。

---

# 六、条件路由如何形成 Agent 循环

## 1. 校验后的路由

```python
def route_after_validation(state):
    return "agent" if state.get("status") == "running" else END
```

这保证空输入在调用模型和工具之前就终止：

```text
非法输入
→ validate 设置 rejected
→ 条件边直接 END
```

确定性业务规则应尽可能放在模型之外，因为它们：

- 更稳定；
- 更容易测试；
- 成本更低；
- 不会被 Prompt 波动改变。

## 2. Agent 后的路由

```python
def route_after_agent(state):
    last_message = state["messages"][-1]
    if (
        state["status"] == "awaiting_tool"
        and isinstance(last_message, AIMessage)
        and last_message.tool_calls
    ):
        return "tools"
    return END
```

这段逻辑必须同时检查：

- 当前状态允许调用工具；
- 最后一条确实是 AIMessage；
- 确实存在 tool_calls。

不能只凭自然语言里出现“工具”两个字路由。

## 3. 回边

```python
builder.add_edge("tools", "agent")
```

它使工具结果重新进入决策节点。

完整循环：

```text
agent
  ├─ 有 tool_calls → tools → agent
  └─ 无 tool_calls → END
```

## 4. 为什么不能漏掉终止条件

如果模型持续请求工具，图可能无限循环。生产中至少需要：

- 最大决策步数；
- LangGraph `recursion_limit`；
- 总执行时限；
- Token/费用预算；
- 重复 Tool Call 检测；
- 明确的完成条件；
- 人工升级或失败终止。

本 Demo 在 `agent_node` 中设置最多 4 次决策，超过后返回安全终止消息。框架级还可以：

```python
graph.invoke(input_state, {"recursion_limit": 10})
```

并捕获 `GraphRecursionError`。

## 5. 不要混用冲突的路由

官方文档提醒：同一个节点应选择一种清晰路由方式。若同时从同一节点添加普通出边和动态条件路由，两条路径可能都执行，使行为难以推理。

本 Demo：

- `validate` 只通过条件边离开；
- `agent` 只通过条件边离开；
- `tools` 只通过固定边回到 `agent`。

---

# 七、第一轮请求的完整执行过程

用户输入：

```text
PLA 打印件四角翘起，应该先检查什么，并给出保守参数建议。
```

## 第 0 步：创建初始 State

```python
{
    "messages": [HumanMessage(...)],
    "model_calls": 0,
    "status": "new",
    "error": None,
    "events": [],
}
```

并提供线程配置：

```python
config = {
    "configurable": {
        "thread_id": "printer-demo-001"
    }
}
```

## 第 1 步：START → validate

`validate_input` 检查：

- 是否存在 HumanMessage；
- 内容是否为空；
- 是否超过 Demo 的 500 字限制。

通过后返回：

```python
{
    "status": "running",
    "error": None,
    "events": ["validation:accepted"],
}
```

注意这里只返回局部更新。

## 第 2 步：validate → agent

`route_after_validation` 看到 `status=running`，返回 `agent`。

## 第 3 步：第一次 agent 决策

当前轮还没有 ToolMessage，所以 agent 产生：

```text
tool = lookup_fault_knowledge
args = 原始用户问题
status = awaiting_tool
model_calls = 1
```

State 中追加一条带 `tool_calls` 的 AIMessage。

## 第 4 步：agent → tools

`route_after_agent` 发现 Tool Call，路由到 ToolNode。

## 第 5 步：执行知识工具

ToolNode 调用：

```python
lookup_fault_knowledge(
    symptom="PLA 打印件四角翘起..."
)
```

工具把“翘起”匹配为 `warping`，返回检查项和风险原则。ToolNode 将结果包装为 ToolMessage。

## 第 6 步：tools → agent

这是固定回边。agent 现在能读取第一条 ToolMessage。

## 第 7 步：第二次 agent 决策

当前轮已有 1 条工具结果，仍缺参数建议，所以发起：

```text
tool = recommend_parameters
args = {material: PLA, severity: medium}
status = awaiting_tool
model_calls = 2
```

## 第 8 步：再次进入 tools

ToolNode 返回：

```text
热床：55–60°C
首层速度：15–25 mm/s
风扇：首层关闭或低速，随后逐步恢复
原则：一次只调整一个变量
```

## 第 9 步：第三次 agent 决策

当前轮已有 2 条 ToolMessage，信息齐全。agent 不再产生 tool_calls，而是生成最终 AIMessage：

```text
诊断：warping
先检查：热床清洁、首层贴合、温度、速度、风扇和环境冷风
保守参数：热床 55–60°C，首层 15–25 mm/s
执行原则：一次只改一个变量并重新打印测试件
```

同时：

```text
status = completed
model_calls = 3
```

## 第 10 步：agent → END

最后一条 AIMessage 没有 Tool Call，条件边返回 END。第一轮结束。

## 第 11 步：Checkpoint

因为图使用 `InMemorySaver` 编译，并且调用时提供 `thread_id`，图执行状态以 Checkpoint 形式保存在内存中。

可以使用：

```python
snapshot = graph.get_state(config)
```

读取当前线程的最新 StateSnapshot。

---

# 八、第二轮追问为什么没有重复调用工具

第二轮仍使用：

```text
thread_id = printer-demo-001
```

新输入：

```text
把刚才的建议压缩成三条。
```

## 1. 同一个 thread_id 的意义

Checkpointer 会用 `thread_id` 定位该线程之前的图状态。新的 HumanMessage 通过 `add_messages` 追加到已有历史。

此时 agent 能看到：

- 第一轮用户问题；
- 两次工具调用；
- 两条工具结果；
- 第一轮最终答案；
- 第二轮追问。

## 2. 决策过程

确定性 agent 发现：

```text
新问题含“刚才/压缩”
并且历史中已经存在 ToolMessage
```

所以直接基于已有观察生成三点摘要，不再调用工具。

实际结果：

```text
第二轮 model_calls = 1
新增 ToolMessage = 0
```

## 3. 这证明了什么

证明：

- Checkpointer 保存了线程 State；
- 相同 thread_id 能继续上下文；
- 消息 Reducer 正确追加新消息；
- Agent 可以重用历史工具结果；
- 不同线程可以隔离。

不证明：

- 状态跨进程重启仍存在；
- 这是生产级数据库持久化；
- 模型拥有无限长期记忆。

`InMemorySaver` 只存在当前进程内，进程结束后数据丢失。

---

# 九、Checkpointer、Thread 与 Store

## 1. Checkpointer

Checkpointer 在图执行步骤之间保存 State 快照，使系统能够：

- 多轮对话；
- 暂停和恢复；
- 故障恢复；
- 查看状态历史；
- Time Travel；
- Human-in-the-loop。

## 2. Thread

Thread 是一系列相关运行的状态范围。调用带 Checkpointer 的图时，需要：

```python
{"configurable": {"thread_id": "..."}}
```

可以把 `thread_id` 理解为 Checkpoint 的线程级主键或游标，但不要把它等同于一次 HTTP request ID：

```text
request_id：通常标识一次请求，用于幂等和追踪
thread_id：通常标识一个可持续多轮的执行线程
```

两者可能有关联，但职责不同。

## 3. InMemorySaver

适合：

- Demo；
- 单元测试；
- 本地开发。

不适合：

- 跨进程恢复；
- 多实例共享；
- 长期可靠保存；
- 生产故障恢复。

## 4. 生产 Checkpointer

官方生态提供 SQLite、PostgreSQL 等持久化实现，也有其他数据库集成。选择需要考虑：

- 事务与一致性；
- 并发；
- Checkpoint 规模；
- 清理策略；
- 加密；
- 多租户隔离；
- 延迟；
- 灾备。

## 5. Store

Checkpointer 保存单个 Thread 的图状态；Store 保存跨 Thread 的应用级长期信息。

```text
Checkpointer：这次对话进行到哪里、有哪些消息
Store：这个用户跨会话的长期偏好、事实或记忆
```

不要把所有长期知识都无限追加到 `messages`，否则 Checkpoint 和上下文会持续膨胀。

## 6. 与 RAG 向量库的区别

```text
Checkpointer
  保存图执行状态

Store
  保存跨线程记忆或应用数据

Vector Store
  根据语义相似度检索外部知识
```

三者可以同时使用，但不是同一个概念。

---

# 十、Streaming：怎样观察图正在做什么

本 Demo 使用：

```python
graph.stream(
    input_state,
    config,
    stream_mode="updates",
    version="v2",
)
```

## 1. updates

只返回每个节点产生的 State 增量。

实际输出：

```text
node=validate status=running
node=agent status=awaiting_tool
node=tools messages=['ToolMessage']
node=agent status=awaiting_tool
node=tools messages=['ToolMessage']
node=agent status=completed
```

它特别适合：

- UI 展示当前步骤；
- 调试哪个节点改变了状态；
- 避免每次传输整个 State。

## 2. values

每一步返回完整 State 快照，适合观察状态整体如何演变，但消息很多时数据量更大。

## 3. messages

用于流式接收模型消息或 Token，适合聊天 UI。

## 4. custom

节点可以发送自定义进度事件，例如：

```text
知识检索 30%
参数分析 60%
结果校验 90%
```

## 5. checkpoints、tasks、debug

可用于更深入的持久化和执行调试。生产环境不应把完整敏感 State 无条件暴露给前端或日志。

## 6. version="v2"

当前官方 Streaming API 的 v2 格式用统一事件结构表达类型、命名空间和数据。本 Demo 已在 LangGraph 1.2.10 上真实运行该格式。

---

# 十一、怎样替换为真实 LLM

当前 `agent_node` 是确定性模型替身。真实版本保留图结构，只替换决策函数。

概念代码：

```python
from langchain.chat_models import init_chat_model
from langchain_core.messages import SystemMessage

model = init_chat_model(
    "provider:model-name",
    temperature=0,
)
model_with_tools = model.bind_tools(TOOLS)


def real_agent_node(state: AgentState) -> dict[str, object]:
    response = model_with_tools.invoke(
        [
            SystemMessage(
                content=(
                    "你是3D打印排障助手。先使用工具获取证据，"
                    "证据不足时不要编造；高风险参数必须请求人工确认。"
                )
            )
        ]
        + state["messages"]
    )
    return {
        "messages": [response],
        "model_calls": state.get("model_calls", 0) + 1,
    }
```

然后将：

```python
builder.add_node("agent", agent_node)
```

替换为：

```python
builder.add_node("agent", real_agent_node)
```

## 1. bind_tools 做了什么

它把工具 Schema 提供给支持 Tool Calling 的模型，使模型能够返回结构化 `tool_calls`。

它不会自动执行工具。执行仍由 ToolNode 或你的治理层完成。

## 2. 为什么真实 LLM 不应直接拥有所有工具

工具越多：

- Schema Token 越多；
- 选择混淆越大；
- 攻击面越大；
- 权限边界越复杂。

应根据用户权限、当前阶段和任务范围动态提供最小工具集合。

## 3. 接入真实模型还要补什么

- API Key 安全管理；
- 超时；
- Provider 错误分类；
- 有限重试与退避；
- Token 和费用预算；
- 模型配置版本；
- Prompt 版本；
- Tool Call 参数校验；
- 内容安全；
- 可观测性；
- 结构化输出校验；
- 降级策略。

所以“把本地决策器换成 API 调用”只是模型接入，不等于生产化完成。

---

# 十二、Human-in-the-loop 与 interrupt

当前 Demo 的工具只返回建议，没有执行真实设备操作，因此没有加入人工审批。但 3D 打印自动调参场景中，如果工具会真正修改设备参数，适合在执行前暂停。

概念流程：

```text
Agent 提出参数修改
→ review 节点调用 interrupt
→ 图保存 Checkpoint 并暂停
→ 人工查看修改内容
→ approve / reject / edit
→ Command(resume=...) 恢复
```

概念代码：

```python
from langgraph.types import Command, interrupt


def human_review(state: AgentState):
    decision = interrupt(
        {
            "question": "是否批准修改打印参数？",
            "proposed_action": state["proposed_action"],
        }
    )
    return {"approval": decision}


graph.invoke(
    Command(resume={"approved": True}),
    config,
)
```

关键规则：

- 必须配置 Checkpointer；
- 使用同一个 thread_id 恢复；
- resume 值会成为 `interrupt()` 的返回值；
- 节点恢复时会从节点开头重新执行；
- interrupt 前的副作用必须幂等；
- 不应随意把 interrupt 包进普通 try/except；
- 暂停载荷应是可序列化数据。

真正执行设备动作时，审批记录、执行幂等键和设备回执还要落到业务治理层。

---

# 十三、失败、重试与副作用治理

## 1. 输入失败

本 Demo 在模型前校验空输入和长度，失败直接 END。

原则：能用确定性代码判断的，不交给模型猜。

## 2. Tool 参数失败

真实 LLM 可能生成：

- 缺字段；
- 类型错误；
- 越界值；
- 未注册工具名；
- 注入式参数。

需要 Schema 校验、白名单和业务验证。

## 3. Tool 执行失败

`ToolNode(..., handle_tool_errors=True)` 可以把部分工具异常转换成 ToolMessage，使模型看到失败并决定下一步。

但不是所有失败都应该交给模型：

- 权限拒绝不应自动绕过；
- 参数违法应直接阻止；
- 余额不足不应无限重试；
- 设备危险状态应立即停止；
- Provider 429/5xx 可以按策略有限重试。

## 4. 重试为什么要分类

```text
可重试：超时、临时网络错误、限流、部分 5xx
通常不可重试：鉴权失败、参数错误、权限拒绝、余额不足
```

每次重试都可能重复产生副作用，所以写操作必须具备幂等语义。

## 5. Checkpoint 不等于业务事务

Checkpoint 保存图状态，不会自动回滚外部世界。

例如：

```text
工具已把热床温度改为 60°C
→ 随后写 Checkpoint 失败
```

重新执行可能再次修改设备。需要：

- action_id / idempotency_key；
- 执行前后状态；
- 外部系统回执；
- 幂等检查；
- 补偿动作；
- 人工审批。

这与 OrangeVC `AgentAction` 的动作身份和 `AiToolRouter` 的治理思路可以相互映射。

## 6. Step Limit 与 Recursion Limit

```text
业务 Step Limit
  根据动作次数、费用、任务语义限制

LangGraph recursion_limit
  防止图无限 Superstep 循环
```

生产系统通常两者都要有。

---

# 十四、四组单元测试分别证明什么

## 1. test_tool_loop_reaches_final_answer

验证：

- 最终状态为 completed；
- agent 恰好执行 3 次；
- 产生 2 个 ToolMessage；
- 最终回答包含 warping 和 55–60°C。

它证明工具循环真正发生，而不是程序直接硬编码打印最终文字。

## 2. test_same_thread_uses_checkpointed_tool_results

先执行第一轮，再用相同 thread_id 追问。

验证：

- 第二轮 ToolMessage 总数不增加；
- 第二轮只决策 1 次；
- 回答使用“压缩为三点”。

它证明短期线程状态可复用。

## 3. test_empty_input_is_rejected_before_agent

验证：

- status 为 rejected；
- model_calls 仍为 0；
- 没有 ToolMessage；
- error 明确。

它证明校验节点能在昂贵或有副作用的步骤前阻断非法输入。

## 4. test_different_threads_are_isolated

线程 A 先完成工具调用，再在线程 B 提交空输入。

验证线程 B：

- 仍被拒绝；
- 没有继承线程 A 的 ToolMessage。

它证明不同 thread_id 不会共享短期消息状态。

## 5. 测试没有证明什么

- 没有评估真实 LLM 的 Tool Selection 正确率；
- 没有网络与 Provider 故障测试；
- 没有生产数据库恢复；
- 没有高并发测试；
- 没有设备真实写操作；
- 没有 Prompt Injection 红队测试。

主动说明测试边界，比笼统说“功能都没问题”更专业。

---

# 十五、LangGraph 与 OrangeVC 执行治理如何对应

| LangGraph 概念 | OrangeVC 对应思路 | 相似点 | 关键差异 |
|---|---|---|---|
| State | 请求上下文、动作状态、业务结果 | 都保存执行所需信息 | OrangeVC 状态分散在 Java 对象和持久化结构中 |
| AIMessage.tool_calls | `AgentAction` | 都把意图变成结构化动作 | 字段、生命周期和治理规则不同 |
| ToolNode | `AiToolRouter` 的分发部分 | 按工具名路由执行 | AiToolRouter 还承担更多权限、策略、重试等治理 |
| Conditional Edge | Router 决策与状态分支 | 根据当前结果选择下一步 | LangGraph 将控制流显式画成图 |
| Checkpointer | 业务状态持久化的一部分 | 都支持恢复和审计思路 | Checkpoint 不等于业务事务或幂等记录 |
| thread_id | 会话/任务身份 | 都关联多步过程 | 不应直接等同 requestId/idempotency key |
| interrupt | 人工审批/挂起 | 都能暂停高风险动作 | OrangeVC 当前是否实现要按真实代码说明 |
| recursion_limit | 最大循环/重试限制 | 防止无限执行 | 一个是图运行保护，一个可能是业务政策 |

## 1. AgentAction 与 Tool Call

都包含：

```text
调用什么
传什么参数
如何标识本次动作
```

但 `AgentAction` 可以承载更多业务治理字段，例如风险、来源、审批、幂等和执行状态；LangGraph 的基础 Tool Call 更接近模型协议。

## 2. AiToolRouter 与 ToolNode

ToolNode 的核心是“执行工具并返回 ToolMessage”。AiToolRouter 更像企业应用中的治理路由层：

- 注册与发现；
- 权限；
- 参数和业务策略；
- Provider 异常分类；
- 重试；
- 配额；
- 审计；
- 统一结果。

一个合理组合可以是：

```text
LangGraph 负责多步状态与控制流
→ LangGraph 的工具节点调用 AiToolRouter 风格的治理服务
→ 治理服务再执行真实业务工具
```

框架编排与业务治理不是互相替代关系。

## 3. 为什么不直接把 OrangeVC 重写成 LangGraph

用户当前目标是吃透项目和独立学习框架，不是改造系统。是否集成要评估：

- Java 与 Python 跨语言边界；
- 现有治理能力是否重复；
- 状态一致性；
- 部署与运维复杂度；
- 延迟；
- 团队技术栈；
- 实际业务是否需要长运行图。

为了简历关键词而引入框架不是充分理由。

---

# 十六、Graph API 与 Functional API

## 1. Graph API

显式定义 State、Node 和 Edge。

适合：

- 需要可视化拓扑；
- 复杂分支和循环；
- 希望逐节点测试；
- 需要明确状态 Reducer；
- 强调可解释控制流。

本 Demo 使用 Graph API。

## 2. Functional API

使用 `@entrypoint`、`@task` 和普通 Python 控制流表达流程。

适合：

- 已有函数代码较多；
- 希望低改造接入持久化、Streaming、HITL；
- 流程用标准 `if/while` 更自然。

## 3. 如何选择

没有绝对优劣：

```text
图拓扑和共享状态是主要认知模型 → Graph API
已有函数控制流是主要认知模型   → Functional API
```

面试时不要说 Functional API 就“不是图”。它仍由 LangGraph 运行时提供持久化和执行能力，只是开发接口不同。

---

# 十七、常见 Agent 工作流模式

## 1. Prompt Chaining

前一步输出作为后一步输入：

```text
提取事实 → 检查事实 → 生成报告
```

## 2. Routing

根据输入类型选择专门分支：

```text
喷嘴问题 → nozzle_agent
翘曲问题 → warping_agent
电气问题 → human_escalation
```

## 3. Parallelization

多个独立节点并行，例如同时：

- 检索故障知识；
- 读取传感器；
- 检查历史维修记录。

并行能降低总延迟，但要设计共享 State 的 Reducer、失败策略和结果汇总。

## 4. Orchestrator-Worker

Orchestrator 动态拆分任务并分发给 Worker，最后汇总。适合子任务数量不能预先固定的复杂任务。

## 5. Evaluator-Optimizer

生成器产出结果，评估器检查，不合格则带反馈回到生成器。

必须有最大迭代次数，否则可能无限循环和失控消费。

## 6. Agent Tool Loop

本 Demo 属于：

```text
模型决策 → 工具执行 → 模型观察 → 再决策
```

真实 3D 打印排障还可以加入：

```text
感知 → 诊断 → 决策 → 人工审批 → 执行 → 验证
```

这正好对应岗位描述中的闭环系统。

---

# 十八、如果扩展成 3D 打印闭环 Agent

这只是学习设计，不代表 OrangeVC 已实现。

## 1. 建议 State

```text
job_id
printer_id
messages
sensor_snapshot
image_observations
suspected_faults
retrieved_evidence
proposed_actions
approval
execution_receipts
verification_result
step_count
budget
status
errors
```

## 2. 建议节点

```text
collect_sensor
inspect_image
retrieve_manual
diagnose
plan_adjustment
risk_gate
human_review
execute_parameter_change
verify_print
final_report
```

## 3. 建议路由

```text
证据不足      → 继续感知或人工升级
低风险只读动作 → 自动执行
高风险写动作   → interrupt 等待审批
执行成功      → verify_print
验证失败且有预算 → diagnose
超过步数/预算  → END + 人工升级
```

## 4. 为什么不能让 LLM 直接改设备

LLM 输出是概率性的，且可能受到错误上下文和注入影响。真实设备写操作至少需要：

- 参数白名单；
- 安全范围；
- 设备状态前置条件；
- 动作幂等；
- 审批；
- 急停；
- 回滚/补偿；
- 结果传感器验证；
- 全链路审计。

LangGraph 能编排这些步骤，但安全规则必须由确定性业务代码实施。

---

# 十九、怎样评估一个 LangGraph Agent

## 1. 节点级指标

- 输入校验拒绝是否准确；
- 模型 Tool Selection 正确率；
- Tool 参数合法率；
- Tool 成功率；
- 节点 P50/P95/P99 延迟；
- 节点错误类型；
- 单节点成本。

## 2. 路径级指标

- 平均决策步数；
- 平均工具调用数；
- 无效或重复工具调用率；
- 循环超限率；
- 人工审批率；
- 降级率；
- 成功终止率；
- 各路径分布。

## 3. 任务级指标

- 最终任务成功率；
- 答案正确性与忠实度；
- 设备动作安全率；
- 平均完成时间；
- 单任务成本；
- 用户修正率；
- 人工接管后的结果。

## 4. Checkpoint 指标

- 保存失败率；
- 恢复成功率；
- State 大小；
- Checkpoint 增长速度；
- 过期清理；
- 序列化错误；
- 跨版本兼容性。

## 5. 测试方法

```text
纯函数节点单元测试
条件路由表驱动测试
工具适配契约测试
模型调用 Mock 测试
整图路径测试
Checkpoint 恢复测试
错误注入测试
并发与负载测试
Prompt Injection 和越权测试
真实任务离线评估
```

本 Demo 已完成的是前四类中最基础的整图、路由、记忆和隔离验证。

---

# 二十、常见失败场景与排查顺序

## 1. State 字段被覆盖

症状：消息或事件只剩最后一条。

原因：缺少 Reducer，默认覆盖。

## 2. 工具结果没有回到模型

症状：工具执行了，但 agent 一直重复同一调用。

检查：

- ToolMessage 是否写回 `messages`；
- tool_call_id 是否匹配；
- `tools → agent` 回边是否存在；
- Reducer 是否追加消息。

## 3. 图无限循环

检查：

- agent 是否存在最终无 Tool Call 分支；
- 工具错误是否导致模型重复相同请求；
- 是否有 Step Limit；
- 是否设置 recursion_limit；
- 是否检测重复动作。

## 4. 相同用户却没有记忆

检查：

- compile 是否传 checkpointer；
- 每次是否提供 thread_id；
- thread_id 是否一致；
- 是否误用新的 Graph/Checkpointer 实例；
- 使用 InMemorySaver 时进程是否重启。

## 5. 不同用户串状态

检查：

- 是否错误复用同一个 thread_id；
- thread_id 是否包含正确租户边界；
- Store namespace 是否隔离；
- 查询和管理接口是否鉴权。

## 6. interrupt 前副作用重复

原因：恢复时节点从头重跑。

处理：把副作用放到 interrupt 之后，或使用幂等动作和执行状态检查。

## 7. Checkpoint 越来越大

原因：消息、工具大结果和历史快照无限积累。

处理：

- 摘要或裁剪上下文；
- 大结果外部存储，State 保存引用；
- Retention Policy；
- 合理使用增量 Channel；
- 监控 State 大小。

## 8. 单元测试通过，真实模型失败

确定性模型不会产生：

- 错误工具名；
- 错误参数；
- 不必要循环；
- 格式波动；
- Prompt Injection 响应。

接入真实模型后必须新增模型评估集和随机性测试。

---

# 二十一、高频面试问题与回答要点

## 1. LangGraph 是什么

答：一个用于构建长运行、有状态 Agent 和 Workflow 的低层编排框架。核心是 State、Node、Edge，并提供循环、持久化、Streaming、Interrupt 等运行能力。

## 2. 为什么不用普通 while 循环

答：简单任务可以用 while。LangGraph 的价值在复杂状态、分支、Checkpoint、恢复、HITL、Streaming 和逐节点观测。是否使用取决于复杂度，不应为框架而框架。

## 3. State、Node、Edge 分别是什么

答：State 是共享快照；Node 读取 State 并返回局部更新；Edge 根据固定规则或 State 决定下一节点。

## 4. Reducer 是什么

答：定义节点返回的更新如何与旧 State 合并。没有 Reducer 默认覆盖；列表常用 add；消息常用 add_messages。

## 5. 为什么 messages 使用 add_messages

答：它适合消息状态，能追加新消息，也支持按消息 ID 更新和消息对象转换，比简单列表拼接更符合对话协议。

## 6. START 和 END 是什么

答：虚拟入口和终点，用来定义图从哪里开始以及何时完成，不是普通业务节点。

## 7. ToolNode 做什么

答：读取 AIMessage 的结构化 tool_calls，找到对应工具，以参数调用，再把结果包装成 ToolMessage 写回消息状态。

## 8. bind_tools 会自动执行工具吗

答：不会。它把工具 Schema 提供给模型，使模型能生成 Tool Call；ToolNode或业务执行层负责真正调用。

## 9. Agent 循环怎么形成

答：agent 产生 Tool Call 后条件边进入 tools，tools 把 ToolMessage 写回后通过固定边回到 agent；agent 最终不再产生 Tool Call时进入 END。

## 10. 如何防止无限循环

答：业务步数、recursion_limit、超时、费用预算、重复 Tool Call 检测和明确终止条件共同限制，超限后安全失败或人工升级。

## 11. thread_id 是什么

答：Checkpointer 用来定位某个线程 Checkpoint 的标识。同一 thread_id 可以延续图状态，不同 thread_id 应隔离。

## 12. Checkpointer 与 Store 有什么区别

答：Checkpointer 保存单线程图状态和执行位置，属于短期线程记忆；Store 保存跨线程的用户事实或应用记忆。

## 13. Checkpointer 与向量库有什么区别

答：前者保存执行状态，后者按语义检索外部知识。一个 Agent 可以同时使用二者。

## 14. InMemorySaver 能用于生产吗

答：适合 Demo 和测试，进程结束即丢失。生产应使用持久化 Checkpointer，并治理并发、加密、清理和灾备。

## 15. 什么是 interrupt

答：节点调用 interrupt 后，图保存状态并暂停等待外部输入，之后用同一 thread_id 和 `Command(resume=...)` 恢复，适合人工审批。

## 16. interrupt 后从哪继续

答：恢复时节点会从开头重新执行，`resume` 值成为 interrupt 调用的返回值。所以 interrupt 前副作用必须幂等或移到之后。

## 17. Command 与条件边有什么区别

答：条件边适合只根据 State 路由；Command 可以在一个节点返回中同时更新 State 和指定 goto，也用于 resume interrupt。

## 18. updates 和 values Streaming 有什么区别

答：updates 只给每个节点的增量，values 给每一步完整 State。前者轻量且适合观察节点变化，后者适合查看完整快照。

## 19. Graph API 和 Functional API 怎么选

答：需要显式拓扑和共享状态时用 Graph API；已有普通函数控制流、希望低改造加入 LangGraph 能力时用 Functional API。

## 20. LangGraph 会自动保证工具安全和幂等吗

答：不会。它提供编排原语，权限、参数边界、幂等、配额、审批、事务、补偿和审计仍需业务层实现。

## 21. Checkpoint 能回滚外部工具副作用吗

答：不能。Checkpoint 保存图状态，不会自动撤销数据库、支付或设备动作。外部写操作要有幂等键、回执和补偿。

## 22. 你的 Demo 为什么不用真实 LLM

答：为了不依赖 API Key并让链路与测试完全可复现。我使用真实 LangGraph 图、ToolNode、消息协议、循环和 Checkpointer，只把模型决策替换成确定性本地函数；接入真实模型只需用 bind_tools 后的模型调用替换 agent_node，但还要增加模型评估和 Provider 治理。

## 23. 这个 Demo 实际跑通了什么

答：StateGraph、TypedDict State、add_messages和列表 Reducer、固定与条件边、两次 ToolNode执行、Agent—Tool 循环、v2 updates Streaming、InMemorySaver、同线程记忆、线程隔离和四组测试。

## 24. 第一次执行为什么有 3 次 agent 调用

答：第一次请求知识工具，第二次读取知识后请求参数工具，第三次读取两个工具结果后生成最终答案。

## 25. 第二轮为什么只执行 1 次 agent

答：使用相同 thread_id，Checkpointer 恢复历史 ToolMessage；新问题要求压缩刚才结果，不需要再次调用工具。

## 26. 如何测试条件路由

答：为每个状态组合构造输入，断言返回节点；整图测试再断言节点执行次数、消息类型、最终状态和副作用次数。

## 27. LangGraph 与 AiToolRouter 有什么关系

答：LangGraph 偏多步状态和控制流编排，AiToolRouter 偏企业级工具注册、权限、重试、配额和审计治理。可组合使用，不互相替代。

## 28. RAG 如何放进 LangGraph

答：把查询改写、Retriever、Reranker、上下文组装做成节点或工具；State 保存查询、候选和引用；条件边处理空召回、重试和无答案分支；最后由生成节点使用证据回答。

## 29. Multi-Agent 是否等于多个节点

答：不等于。多个确定性处理节点仍可能只是单 Workflow。Multi-Agent 通常意味着多个具有独立角色、上下文或决策能力的 Agent 协作，仍需明确通信、委派和终止协议。

## 30. 什么时候不应该使用 LangGraph

答：一次简单模型调用、没有分支循环和持久化需求时，普通函数或更高层 Agent API 更简单。引入框架的收益应大于状态、部署和调试复杂度。

---

# 二十二、面试中的完整回答模板

如果面试官问“你用过什么 Agent 框架”，可以这样回答：

> 我独立跑通过一个基于 LangGraph 1.2.10 Graph API 的 3D 打印排障 Demo。它没有修改 OrangeVC，而是作为 Python Agent 框架练习。图的 State 使用 TypedDict，messages 用 add_messages Reducer，events 用列表累加 Reducer；节点包括输入校验、Agent 决策和 ToolNode，边包括固定边和条件边。
>
> 第一轮输入 PLA 翘曲问题后，validate 先做确定性校验。Agent 第一次产生 `lookup_fault_knowledge` 的结构化 Tool Call，ToolNode 执行后返回 ToolMessage；Agent 读取观察结果，再调用 `recommend_parameters`；第二次 ToolMessage 返回后，Agent 第三次决策生成最终答案，不再包含 Tool Call，条件边进入 END。实际运行是 3 次决策、2 次工具执行，最终状态 completed。
>
> 图编译时接入 InMemorySaver，每次调用传入 thread_id。使用同一个 thread_id 追问“把刚才建议压缩成三条”时，Checkpointer 恢复了历史工具结果，第二轮只做一次决策，没有重复调用工具。我还写了四个单元测试，验证完整工具循环、同线程记忆、空输入在模型前被拒绝和不同线程隔离，全部通过。
>
> 为了不依赖 API Key并让结果可复现，我用确定性 agent_node 模拟支持 Tool Calling 的模型，所以我不会声称已经在这个 Demo 接入真实 LLM。真实接入时会用 `model.bind_tools` 后的调用替换 agent_node，保留图结构，同时新增 Provider 超时重试、Prompt与模型版本、Tool 参数校验、成本预算和模型行为评估。
>
> 我理解 LangGraph 主要负责有状态控制流，不自动替代业务治理。ToolNode 能执行工具，但权限、幂等、配额、审批和审计仍需要类似 OrangeVC AiToolRouter 的治理层。对于真实 3D 打印参数修改，我会在执行前加入 interrupt 人工审批，使用持久化 Checkpointer、动作幂等键、设备回执和执行后的传感器验证。

如果追问“它与 OrangeVC 有什么关系”，回答：

> 两者是互补学习。OrangeVC 让我理解 Java 工程中 `AgentAction` 和 `AiToolRouter` 怎样治理动作，LangGraph Demo 让我实际掌握 Python Agent 框架如何用 State、Node、Edge、ToolMessage、循环和 Checkpoint 编排多步过程。OrangeVC 没有集成 LangGraph，我会明确区分项目经验和独立 Demo。

---

# 二十三、简历上可以怎样准确描述

只有在你自己重新运行并能回答本章问题后，才建议写：

```text
LangGraph 3D 打印排障 Agent Demo（独立实践）

基于 LangGraph Graph API 构建有状态工具调用工作流，使用 TypedDict State、
add_messages Reducer、条件边与 ToolNode 实现“决策—工具—观察—再决策”循环；
接入 InMemorySaver 验证同线程短期记忆与线程隔离，并编写 4 组单元测试覆盖
工具循环、非法输入阻断、记忆复用和隔离场景。
```

如果尚未接入真实 LLM，不要写：

```text
错误：基于大模型自主完成复杂 3D 打印诊断
错误：实现生产级持久化 Agent
错误：完成真实打印机自动调参
错误：OrangeVC 使用 LangGraph 重构
```

面试官真正会追问的是：

- State 字段和 Reducer；
- 为什么需要回边；
- Tool Call 与 ToolMessage 如何关联；
- 为什么 3 次 Agent 调用；
- thread_id 如何实现记忆；
- Checkpoint 与业务幂等差异；
- 真实 LLM 接入还缺什么。

本章已经为这些问题提供了可验证答案。

---

# 二十四、三天吃透这个 Demo 的学习顺序

## 第 1 天：不看代码先画图

任务：

- 画 START、validate、agent、tools、END；
- 标出固定边和条件边；
- 写出 State 五个字段；
- 解释三个 Reducer 行为；
- 复述第一轮 6 条消息。

完成标准：不看文档口述第一轮每一步 State 如何变化。

## 第 2 天：重新运行并做改动实验

依次修改：

1. 把空输入改为超长输入，观察 validate 终止；
2. 把问题改成“拉丝”，观察知识工具输出；
3. 把 material 改成 ABS，观察工具的 needs_manual；
4. 把最大步数调低，观察安全终止；
5. 换一个 thread_id，验证不继承历史；
6. 删除 `tools → agent` 回边前先预测结果，再实际验证；
7. 去掉 messages Reducer前先预测状态问题，再恢复。

不要把破坏性实验直接留在最终源码，可复制临时版本练习。

## 第 3 天：生产化推演与模拟面试

任务：

- 设计真实 LLM 替换节点；
- 设计参数修改前 interrupt；
- 区分 Checkpointer、Store、VectorStore；
- 设计 Step Limit、费用预算和幂等键；
- 回答 30 道问题；
- 用 5 分钟完整讲 Demo；
- 用 2 分钟解释与 OrangeVC 的边界。

完成标准：面试官任意指定一个节点或消息，你都能说出输入、输出、下一条边和失败处理。

---

# 二十五、真正掌握这一阶段的标准

你需要不看资料做到：

1. 一句话定义 LangGraph；
2. 区分 Workflow 与 Agent；
3. 解释 State、Node、Edge、START、END；
4. 写出 Demo 的 AgentState；
5. 解释默认覆盖、operator.add 和 add_messages；
6. 画出第一轮完整节点图；
7. 写出 AIMessage Tool Call 的 name、args、id；
8. 解释 ToolMessage 为什么必须匹配 tool_call_id；
9. 解释 ToolNode 做什么和不做什么；
10. 解释条件边如何决定 tools 或 END；
11. 解释 `tools → agent` 为什么构成观察循环；
12. 解释第一轮为什么 3 次决策、2 次工具；
13. 解释第二轮为什么没有重复工具；
14. 区分 request_id、thread_id 和 idempotency_key；
15. 区分 Checkpointer、Store、VectorStore；
16. 解释 InMemorySaver 的边界；
17. 解释 updates 和 values Streaming；
18. 说明 Step Limit 与 recursion_limit；
19. 解释 interrupt 的暂停与恢复语义；
20. 说明为什么 interrupt 前副作用必须幂等；
21. 说明 Checkpoint 为什么不能回滚外部设备；
22. 写出真实 LLM 替换节点的基本结构；
23. 说明 LangGraph 与 AiToolRouter 可以如何组合；
24. 主动承认 Demo 没有真实 LLM、生产数据库和设备控制；
25. 在本机重新执行 4 项测试并全部通过。

掌握后的完整认知应该是：

```text
LLM/本地决策器负责提出下一动作
→ LangGraph 把动作、观察和状态组织成可循环的图
→ ToolNode完成基础工具协议适配
→ 业务治理层负责权限、幂等、配额、审批和审计
→ Checkpointer保存线程图状态
→ Store保存跨线程长期记忆
→ VectorStore检索外部语义知识
→ Streaming和Tracing让执行过程可观察
→ 测试和评估证明每条关键路径符合预期
```

做到这些，你就不只是“运行过 LangGraph Quickstart”，而是能解释一个 Agent 框架为什么需要状态、循环、持久化和治理，以及这些概念如何与 OrangeVC 的工程实践互相映射。

---

# 二十六、官方资料与版本说明

本 Demo 于 2026-09-05 使用以下环境实际验证：

```text
Python 3.13.8
LangGraph 1.2.10
langchain-core 1.6.2
```

官方参考：

- LangGraph Quickstart：https://docs.langchain.com/oss/python/langgraph/quickstart
- Graph API：https://docs.langchain.com/oss/python/langgraph/graph-api
- Graph API 使用指南：https://docs.langchain.com/oss/python/langgraph/use-graph-api
- Workflows and agents：https://docs.langchain.com/oss/python/langgraph/workflows-agents
- Persistence：https://docs.langchain.com/oss/python/langgraph/persistence
- Streaming：https://docs.langchain.com/oss/python/langgraph/streaming
- Interrupts：https://docs.langchain.com/oss/python/langgraph/interrupts
- 官方源码：https://github.com/langchain-ai/langgraph

LangGraph API 会演进。以后重新学习或重装依赖时，应先核对固定版本与当前官方文档，不要把旧教程中的导入路径和接口直接复制到新版本。
