from __future__ import annotations

import unittest

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage

from agent_demo import build_graph


class LangGraphAgentDemoTest(unittest.TestCase):
    def initial_state(self, text: str) -> dict[str, object]:
        return {
            "messages": [HumanMessage(content=text)],
            "model_calls": 0,
            "status": "new",
            "error": None,
            "events": [],
        }

    def test_tool_loop_reaches_final_answer(self) -> None:
        graph = build_graph()
        config = {"configurable": {"thread_id": "test-tool-loop"}}

        result = graph.invoke(
            self.initial_state("PLA 打印件四角翘起，请诊断并给出参数。"),
            config,
        )

        self.assertEqual("completed", result["status"])
        self.assertEqual(3, result["model_calls"])
        self.assertEqual(
            2,
            sum(isinstance(message, ToolMessage) for message in result["messages"]),
        )
        self.assertIsInstance(result["messages"][-1], AIMessage)
        self.assertIn("warping", result["messages"][-1].content)
        self.assertIn("55–60°C", result["messages"][-1].content)

    def test_same_thread_uses_checkpointed_tool_results(self) -> None:
        graph = build_graph()
        config = {"configurable": {"thread_id": "test-memory"}}
        first = graph.invoke(
            self.initial_state("PLA 打印件四角翘起，请诊断并给出参数。"),
            config,
        )
        first_tool_count = sum(
            isinstance(message, ToolMessage) for message in first["messages"]
        )

        second = graph.invoke(
            self.initial_state("把刚才的建议压缩成三条。"),
            config,
        )
        second_tool_count = sum(
            isinstance(message, ToolMessage) for message in second["messages"]
        )

        self.assertEqual(first_tool_count, second_tool_count)
        self.assertEqual(1, second["model_calls"])
        self.assertIn("压缩为三点", second["messages"][-1].content)

    def test_empty_input_is_rejected_before_agent(self) -> None:
        graph = build_graph()
        config = {"configurable": {"thread_id": "test-validation"}}

        result = graph.invoke(self.initial_state("   "), config)

        self.assertEqual("rejected", result["status"])
        self.assertEqual(0, result["model_calls"])
        self.assertEqual("用户问题不能为空", result["error"])
        self.assertEqual(
            0,
            sum(isinstance(message, ToolMessage) for message in result["messages"]),
        )

    def test_different_threads_are_isolated(self) -> None:
        graph = build_graph()
        first_config = {"configurable": {"thread_id": "isolated-a"}}
        second_config = {"configurable": {"thread_id": "isolated-b"}}

        graph.invoke(self.initial_state("PLA 四角翘起，请诊断。"), first_config)
        second = graph.invoke(self.initial_state("   "), second_config)

        self.assertEqual("rejected", second["status"])
        self.assertEqual(
            0,
            sum(isinstance(message, ToolMessage) for message in second["messages"]),
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
