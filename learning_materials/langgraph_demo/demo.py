"""A dependency-light LangGraph demo for learning agent execution governance.

The planner is deterministic on purpose: it lets the learner observe state,
conditional routing, human approval, checkpoint/resume, and execution without
requiring an API key. A real LLM can later replace ``diagnose`` and ``decide``.
"""

from __future__ import annotations

import json
import operator
from typing import Annotated, Literal

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt
from typing_extensions import TypedDict


class PrinterState(TypedDict, total=False):
    report: str
    signals: list[str]
    diagnosis: str
    proposed_action: str
    risk: Literal["low", "medium", "high"]
    needs_confirmation: bool
    approved: bool
    result: str
    trace: Annotated[list[str], operator.add]


def perceive(state: PrinterState) -> dict:
    """Convert the raw user report into normalized observable signals."""
    report = state["report"]
    signals: list[str] = []
    if "不出料" in report or "堵" in report:
        signals.append("no_extrusion")
    if "翘" in report:
        signals.append("warping")
    if "拉丝" in report:
        signals.append("stringing")
    if "温度正常" in report:
        signals.append("temperature_normal")
    if not signals:
        signals.append("unknown")
    return {"signals": signals, "trace": [f"perceive -> {signals}"]}


def diagnose(state: PrinterState) -> dict:
    """Stand in for an LLM/RAG diagnosis node."""
    signals = set(state["signals"])
    if "no_extrusion" in signals and "temperature_normal" in signals:
        diagnosis = "suspected_nozzle_clog"
    elif "warping" in signals:
        diagnosis = "insufficient_bed_adhesion"
    elif "stringing" in signals:
        diagnosis = "retraction_or_temperature_issue"
    else:
        diagnosis = "insufficient_evidence"
    return {"diagnosis": diagnosis, "trace": [f"diagnose -> {diagnosis}"]}


def decide(state: PrinterState) -> dict:
    """Map a diagnosis to an action and an execution risk."""
    diagnosis = state["diagnosis"]
    if diagnosis == "suspected_nozzle_clog":
        action, risk = "pause_print_and_clean_nozzle", "high"
    elif diagnosis == "insufficient_bed_adhesion":
        action, risk = "raise_bed_temperature_by_5c", "medium"
    elif diagnosis == "retraction_or_temperature_issue":
        action, risk = "run_retraction_calibration", "medium"
    else:
        action, risk = "collect_more_sensor_data", "low"
    needs_confirmation = risk == "high"
    return {
        "proposed_action": action,
        "risk": risk,
        "needs_confirmation": needs_confirmation,
        "trace": [f"decide -> {action}, risk={risk}"],
    }


def route_after_decision(state: PrinterState) -> Literal["human_review", "execute"]:
    return "human_review" if state["needs_confirmation"] else "execute"


def human_review(state: PrinterState) -> dict:
    """Pause high-risk execution and resume with a human decision."""
    approved = bool(
        interrupt(
            {
                "question": "是否批准执行高风险动作？",
                "action": state["proposed_action"],
                "risk": state["risk"],
            }
        )
    )
    return {
        "approved": approved,
        "trace": [f"human_review -> approved={approved}"],
    }


def route_after_review(state: PrinterState) -> Literal["execute", "reject"]:
    return "execute" if state.get("approved", False) else "reject"


def execute(state: PrinterState) -> dict:
    """Stand in for a real printer-control tool call."""
    action = state["proposed_action"]
    return {
        "result": f"SIMULATED_EXECUTION_OK: {action}",
        "trace": [f"execute -> {action}"],
    }


def reject(state: PrinterState) -> dict:
    return {
        "result": "CANCELLED_BY_HUMAN",
        "trace": ["reject -> no side effect executed"],
    }


def build_graph():
    builder = StateGraph(PrinterState)
    builder.add_node("perceive", perceive)
    builder.add_node("diagnose", diagnose)
    builder.add_node("decide", decide)
    builder.add_node("human_review", human_review)
    builder.add_node("execute", execute)
    builder.add_node("reject", reject)

    builder.add_edge(START, "perceive")
    builder.add_edge("perceive", "diagnose")
    builder.add_edge("diagnose", "decide")
    builder.add_conditional_edges("decide", route_after_decision)
    builder.add_conditional_edges("human_review", route_after_review)
    builder.add_edge("execute", END)
    builder.add_edge("reject", END)

    return builder.compile(checkpointer=InMemorySaver())


def main() -> None:
    graph = build_graph()

    # Case 1: a high-risk action pauses and resumes after human approval.
    config = {"configurable": {"thread_id": "printer-demo-high-risk"}}
    paused = graph.invoke(
        {
            "report": "喷嘴温度正常，但是打印中突然不出料，怀疑堵塞",
            "trace": [],
        },
        config=config,
    )
    payload = paused["__interrupt__"][0].value
    print("PAUSED_FOR_CONFIRMATION")
    print(json.dumps(payload, ensure_ascii=False, sort_keys=True))

    resumed = graph.invoke(Command(resume=True), config=config)
    print("RESUMED_RESULT")
    print(json.dumps({"result": resumed["result"], "trace": resumed["trace"]},
                     ensure_ascii=False, indent=2))

    # Case 2: a medium-risk simulated adjustment follows the direct branch.
    direct = graph.invoke(
        {"report": "首层打印时四个边角明显翘起", "trace": []},
        config={"configurable": {"thread_id": "printer-demo-medium-risk"}},
    )
    print("DIRECT_RESULT")
    print(json.dumps({"result": direct["result"], "trace": direct["trace"]},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
