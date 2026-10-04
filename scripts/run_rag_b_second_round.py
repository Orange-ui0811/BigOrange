"""Run the stricter post-fix A/B retrieval evaluation against local OrangeJD."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from run_rag_b_first_round import call


def main() -> None:
    status = call("GET", "/admin/rag-lab/status")
    if not status.get("enabled"):
        raise RuntimeError("RAG B is disabled")
    refresh = call("POST", "/admin/rag-lab/documents/refresh-metadata")
    evaluation = call("POST", "/admin/rag-lab/evaluation/run?topK=8&threshold=0.55", timeout=900)
    result = {
        "runAt": datetime.now(timezone.utc).isoformat(),
        "purpose": "Post-fix retrieval comparison; no text-generation calls.",
        "metadataRefresh": refresh,
        "evaluation": evaluation,
        "externalCalls": "Evaluation performs query embeddings only; no document re-embedding or answer generation.",
    }
    output = Path("docs/rag-b/second-round-result.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evaluation.get("summary", {}), ensure_ascii=False))
    print(f"REPORT={output.resolve()}")


if __name__ == "__main__":
    main()
