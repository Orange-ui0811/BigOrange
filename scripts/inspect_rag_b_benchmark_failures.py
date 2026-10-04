"""Capture ranked sources for failed cases in the saved RAG B benchmark."""

from __future__ import annotations

import json
from pathlib import Path

from run_rag_b_first_round import call


def main() -> None:
    result_path = Path("docs/rag-b/benchmark-v1-result.json")
    saved = json.loads(result_path.read_text(encoding="utf-8"))
    failures = [case for case in saved["evaluation"]["cases"] if not case["b"]["passed"]]
    diagnostics = []
    for case in failures:
        request = {
            "query": case["question"],
            "topK": 8,
            "threshold": 0.55,
            "sourceTypes": case.get("expectedSourceTypes") or [],
        }
        response = call("POST", "/admin/rag-lab/search", request)
        diagnostics.append({
            "key": case["key"],
            "question": case["question"],
            "expectedSourceRefs": case.get("expectedSourceRefs") or [],
            "candidateCount": response.get("candidateCount"),
            "selectedCount": response.get("selectedCount"),
            "rankedHits": [
                {
                    "rank": rank,
                    "title": hit.get("title"),
                    "sourceType": hit.get("sourceType"),
                    "sourceRef": hit.get("sourceRef"),
                    "score": hit.get("score"),
                    "sectionPath": hit.get("sectionPath"),
                }
                for rank, hit in enumerate(response.get("hits") or [], start=1)
            ],
        })
        print(case["key"], [hit["sourceRef"] for hit in diagnostics[-1]["rankedHits"]], flush=True)

    output = Path("docs/rag-b/benchmark-v1-failure-diagnostics.json")
    output.write_text(json.dumps(diagnostics, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"REPORT={output.resolve()}")


if __name__ == "__main__":
    main()
