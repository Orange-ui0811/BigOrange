# LangGraph 3D 打印排障 Demo

这是阶段 5 的独立练习项目，不修改 OrangeVC，也不需要任何模型 API Key。

它使用真实的 LangGraph `StateGraph`、状态 Reducer、条件边、循环、`ToolNode` 和 `InMemorySaver`。为了让结果可复现，决策节点使用本地确定性逻辑模拟支持 Tool Calling 的 LLM。理解链路后，可以只替换 `agent_node`，接入真实 Chat Model。

## 环境

- Python 3.10+
- LangGraph 1.2.10

## Windows 运行

在本目录执行：

```powershell
uv venv .venv --python 3.13
uv pip install --python .venv\Scripts\python.exe -r requirements.txt
.venv\Scripts\python.exe agent_demo.py
.venv\Scripts\python.exe -m unittest -v
```

## 预期链路

```text
START
  → validate
  → agent：请求 lookup_fault_knowledge
  → tools：执行知识查询
  → agent：请求 recommend_parameters
  → tools：执行参数建议
  → agent：整合最终答案
  → END
```

第一次运行应看到 3 次决策节点调用和 2 个 ToolMessage。

第二次使用同一 `thread_id` 追问“把刚才的建议压缩成三条”，图会读取 `InMemorySaver` 中的历史工具结果，只调用 1 次决策节点，不重复执行工具。

## 文件

- `agent_demo.py`：完整图、工具、路由、运行入口。
- `test_agent_demo.py`：工具循环、线程记忆、非法输入和线程隔离测试。
- `requirements.txt`：可复现依赖版本。

## 边界

这个 Demo 证明已经跑通 LangGraph 的执行模型，不证明接入了真实 LLM。真实模型接入还需要：

- 安装对应 Provider 集成；
- 配置 API Key；
- 初始化支持 Tool Calling 的 Chat Model；
- `bind_tools(TOOLS)`；
- 用模型调用替换确定性 `agent_node`；
- 增加超时、重试、配额、幂等、审批和生产级持久化。
