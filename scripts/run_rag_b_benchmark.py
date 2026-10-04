"""Run the 60-case document-grounded RAG A/B benchmark against local OrangeJD."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from run_rag_b_first_round import call


def main() -> None:
    status = call("GET", "/admin/rag-lab/status")
    if not status.get("enabled"):
        raise RuntimeError("RAG B is disabled")
    evaluation = call("POST", "/admin/rag-lab/evaluation/benchmark/run?topK=8&threshold=0.55", timeout=1800)
    result = {
        "runAt": datetime.now(timezone.utc).isoformat(),
        "purpose": "60-case document-grounded blind benchmark; no text-generation calls.",
        "evaluation": evaluation,
        "externalCalls": "Each case performs one A and one B query embedding; no document re-embedding or answer generation.",
    }
    output = Path("docs/rag-b/benchmark-v1-result.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evaluation.get("summary", {}), ensure_ascii=False))
    print(f"REPORT={output.resolve()}")


if __name__ == "__main__":
    main()
