import json
import unittest
from pathlib import Path

from ranking import rank


ROOT = Path(__file__).parent


class RankingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.snapshot = json.loads(
            (ROOT / "fixtures" / "weekly-2026-08-03.json").read_text(encoding="utf-8")
        )

    def test_expected_top_three(self) -> None:
        result = rank(self.snapshot)
        self.assertEqual(
            result["topThree"],
            [
                "BuilderIO/agent-native",
                "virgiliojr94/book-to-skill",
                "pbakaus/impeccable",
            ],
        )

    def test_gpu_projects_are_rejected(self) -> None:
        result = rank(self.snapshot)
        rejected = {item["fullName"] for item in result["rejected"]}
        self.assertIn("microsoft/TRELLIS.2", rejected)
        self.assertIn("microsoft/VibeVoice", rejected)

    def test_result_is_deterministic(self) -> None:
        self.assertEqual(rank(self.snapshot), rank(self.snapshot))


if __name__ == "__main__":
    unittest.main()
