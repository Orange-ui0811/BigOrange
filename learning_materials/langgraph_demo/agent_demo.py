from __future__ import annotations

import json
import operator
from typing import Annotated, Literal, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    ToolMessage,
)
from langchain_core.tools import tool
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph, add_messages
from langgraph.prebuilt import ToolNode


class AgentState(TypedDict, total=False):
    """The shared state passed between all graph nodes."""

    messages: Annotated[list[BaseMessage], add_messages]
    model_calls: int
    status: str
    error: str | None
    events: Annotated[list[str], operator.add]


@tool
def lookup_fault_knowledge(symptom: str) -> str:
    """Look up diagnostic knowledge for a 3D-printing symptom."""

    normalized = symptom.lower()
    if "翘" in symptom or "warping" in normalized:
        result = {
            "fault": "warping",
            "checks": [
                "确认热床表面清洁且首层能均匀贴合",
                "检查热床温度是否适合当前材料",
                "检查首层速度、风扇和环境冷风",
            ],
            "risk": "先排查附着与环境，再逐项调整参数，避免一次改变多个变量",
        }
    elif "拉丝" in symptom or "stringing" in normalized:
        result = {
            "fault": "stringing",
            "checks": ["检查耗材是否受潮", "检查回抽距离与速度", "检查喷嘴温度"],
            "risk": "每次只调整一个参数并保留对照样件",
        }
    else:
        result = {
            "fault": "unknown",
            "checks": ["记录材料、温度、速度和故障照片", "先执行基础机械检查"],
            "risk": "证据不足时不直接给出激进参数",
        }
    return json.dumps(result, ensure_ascii=False)


@tool
def recommend_parameters(material: str, severity: str) -> str:
    """Return conservative parameter suggestions for a material and severity."""

    material_name = material.strip().upper()
    severity_name = severity.strip().lower()
    if material_name != "PLA":
        return json.dumps(
            {
                "status": "needs_manual",
                "reason": "演示工具只维护 PLA 的保守参数，其他材料需要查阅材料规格",
            },
            ensure_ascii=False,
        )

    bed_temperature = "55–60°C" if severity_name != "high" else "60°C 左右并先确认材料规格"
    result = {
        "material": "PLA",
        "severity": severity_name,
        "bed_temperature": bed_temperature,
        "first_layer_speed": "15–25 mm/s",
        "fan": "首层关闭或低速，随后逐步恢复",
        "change_policy": "一次只调整一个变量并重新打印小型测试件",
    }
    return json.dumps(result, ensure_ascii=False)


TOOLS = [lookup_fault_knowledge, recommend_parameters]


def _text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    return json.dumps(content, ensure_ascii=False)


def _last_human_index(messages: list[BaseMessage]) -> int:
    for index in range(len(messages) - 1, -1, -1):
        if isinstance(messages[index], HumanMessage):
            return index
    return -1


def _tool_payloads(messages: list[BaseMessage]) -> dict[str, dict[str, object]]:
    payloads: dict[str, dict[str, object]] = {}
    for message in messages:
        if not isinstance(message, ToolMessage):
            continue
        try:
            parsed = json.loads(_text(message))
        except json.JSONDecodeError:
            parsed = {"raw": _text(message)}
        payloads[message.name or message.tool_call_id] = parsed
    return payloads


def validate_input(state: AgentState) -> dict[str, object]:
    """A deterministic guard before any model or tool work."""

    messages = state.get("messages", [])
    human_messages = [message for message in messages if isinstance(message, HumanMessage)]
    if not human_messages or not _text(human_messages[-1]).strip():
        return {
            "status": "rejected",
            "error": "用户问题不能为空",
            "events": ["validation:rejected"],
        }
    if len(_text(human_messages[-1])) > 500:
        return {
            "status": "rejected",
            "error": "演示输入不能超过 500 个字符",
            "events": ["validation:rejected"],
        }
    return {
        "status": "running",
        "error": None,
        "events": ["validation:accepted"],
    }


def route_after_validation(state: AgentState) -> Literal["agent", "__end__"]:
    return "agent" if state.get("status") == "running" else END


def agent_node(state: AgentState) -> dict[str, object]:
    """
    A deterministic stand-in for an LLM with tool calling.

    The graph and tool protocol are real. Replacing this function with a bound
    chat model turns the same graph into an LLM-driven agent.
    """

    messages = state.get("messages", [])
    calls = state.get("model_calls", 0) + 1
    human_index = _last_human_index(messages)
    current_messages = messages[human_index + 1 :]
    current_tool_messages = [
        message for message in current_messages if isinstance(message, ToolMessage)
    ]
    all_payloads = _tool_payloads(messages)
    user_text = _text(messages[human_index]) if human_index >= 0 else ""

    if calls > 4:
        return {
            "messages": [AIMessage(content="已达到演示的最大决策步数，安全终止。")],
            "model_calls": calls,
            "status": "stopped",
            "events": ["agent:step_limit"],
        }

    if ("刚才" in user_text or "压缩" in user_text) and all_payloads:
        return {
            "messages": [
                AIMessage(
                    content=(
                        "基于当前线程中已经执行过的工具结果，压缩为三点：\n"
                        "1. 先清洁热床并确认首层均匀贴合；\n"
                        "2. PLA 热床从 55–60°C、首层 15–25 mm/s 做保守测试；\n"
                        "3. 首层少吹风且一次只改一个变量，保留对照。"
                    )
                )
            ],
            "model_calls": calls,
            "status": "completed",
            "events": ["agent:memory_answer"],
        }

    if not current_tool_messages:
        return {
            "messages": [
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "lookup_fault_knowledge",
                            "args": {"symptom": user_text},
                            "id": f"knowledge-{calls}",
                            "type": "tool_call",
                        }
                    ],
                )
            ],
            "model_calls": calls,
            "status": "awaiting_tool",
            "events": ["agent:request_knowledge"],
        }

    if len(current_tool_messages) == 1:
        return {
            "messages": [
                AIMessage(
                    content="",
                    tool_calls=[
                        {
                            "name": "recommend_parameters",
                            "args": {"material": "PLA", "severity": "medium"},
                            "id": f"parameters-{calls}",
                            "type": "tool_call",
                        }
                    ],
                )
            ],
            "model_calls": calls,
            "status": "awaiting_tool",
            "events": ["agent:request_parameters"],
        }

    payloads = _tool_payloads(current_tool_messages)
    knowledge = payloads.get("lookup_fault_knowledge", {})
    parameters = payloads.get("recommend_parameters", {})
    checks = knowledge.get("checks", [])
    checks_text = "；".join(str(item) for item in checks)
    final_answer = (
        f"诊断：{knowledge.get('fault', 'unknown')}。\n"
        f"先检查：{checks_text}。\n"
        f"保守参数：热床 {parameters.get('bed_temperature', '需人工确认')}，"
        f"首层速度 {parameters.get('first_layer_speed', '需人工确认')}，"
        f"风扇策略为 {parameters.get('fan', '需人工确认')}。\n"
        f"执行原则：{parameters.get('change_policy', knowledge.get('risk', '先验证再调整'))}。"
    )
    return {
        "messages": [AIMessage(content=final_answer)],
        "model_calls": calls,
        "status": "completed",
        "events": ["agent:final_answer"],
    }


def route_after_agent(state: AgentState) -> Literal["tools", "__end__"]:
    messages = state.get("messages", [])
    if not messages:
        return END
    last_message = messages[-1]
    if (
        state.get("status") == "awaiting_tool"
        and isinstance(last_message, AIMessage)
        and bool(last_message.tool_calls)
    ):
        return "tools"
    return END


def build_graph():
    """Build a fresh graph and in-memory checkpointer."""

    builder = StateGraph(AgentState)
    builder.add_node("validate", validate_input)
    builder.add_node("agent", agent_node)
    builder.add_node("tools", ToolNode(TOOLS, handle_tool_errors=True))

    builder.add_edge(START, "validate")
    builder.add_conditional_edges(
        "validate",
        route_after_validation,
        {"agent": "agent", END: END},
    )
    builder.add_conditional_edges(
        "agent",
        route_after_agent,
        {"tools": "tools", END: END},
    )
    builder.add_edge("tools", "agent")

    checkpointer = InMemorySaver()
    return builder.compile(checkpointer=checkpointer)


def _print_update(part: dict[str, object]) -> None:
    if part.get("type") != "updates":
        return
    data = part.get("data", {})
    if not isinstance(data, dict):
        return
    for node_name, update in data.items():
        if not isinstance(update, dict):
            print(f"node={node_name} update={update}")
            continue
        messages = update.get("messages", [])
        message_types = [type(message).__name__ for message in messages]
        print(
            f"node={node_name} "
            f"status={update.get('status', '-')} "
            f"messages={message_types or '-'} "
            f"events={update.get('events', '-')}"
        )


def run_demo() -> None:
    graph = build_graph()
    config = {"configurable": {"thread_id": "printer-demo-001"}}

    print("=== FIRST TURN: tool loop ===")
    first_input: AgentState = {
        "messages": [
            HumanMessage(content="PLA 打印件四角翘起，应该先检查什么，并给出保守参数建议。")
        ],
        "model_calls": 0,
        "status": "new",
        "error": None,
        "events": [],
    }
    for part in graph.stream(
        first_input,
        config,
        stream_mode="updates",
        version="v2",
    ):
        _print_update(part)

    first_snapshot = graph.get_state(config)
    first_messages = first_snapshot.values["messages"]
    print(f"final_status={first_snapshot.values['status']}")
    print(f"model_calls={first_snapshot.values['model_calls']}")
    print(f"tool_messages={sum(isinstance(m, ToolMessage) for m in first_messages)}")
    print(f"answer={_text(first_messages[-1])}")

    print("\n=== SECOND TURN: same-thread memory ===")
    second_input: AgentState = {
        "messages": [HumanMessage(content="把刚才的建议压缩成三条。")],
        "model_calls": 0,
        "status": "new",
        "error": None,
        "events": [],
    }
    for part in graph.stream(
        second_input,
        config,
        stream_mode="updates",
        version="v2",
    ):
        _print_update(part)

    second_snapshot = graph.get_state(config)
    second_messages = second_snapshot.values["messages"]
    print(f"final_status={second_snapshot.values['status']}")
    print(f"model_calls_this_turn={second_snapshot.values['model_calls']}")
    print(f"answer={_text(second_messages[-1])}")


if __name__ == "__main__":
    run_demo()
