"""Run the reproducible first-round RAG B experiment against local OrangeJD."""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


BASE_URL = os.getenv("RAG_B_BASE_URL", "http://127.0.0.1:18080")
if BASE_URL not in {"http://127.0.0.1:18080", "http://localhost:18080"}:
    raise SystemExit("This runner only permits the local OrangeJD backend.")

SAMPLE_IDS = {
    "BEGINNER_PROJECT": list(range(843, 858)),
    "CAREER_PROJECT": list(range(905, 920)),
    "JOB_POSTING": [506, 505, 504, 503, 502, 501, 500, 499, 498, 495, 494, 493, 492, 491, 490],
    "GITHUB": [802, 794, 774, 772, 767, 765, 759, 758, 752, 744, 736, 704, 703, 702, 684],
}


def b64(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def local_admin_token() -> str:
    now = int(time.time())
    header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = b64(json.dumps({
        "id": 1,
        "username": "admin",
        "role": "ADMIN",
        "tokenType": "ADMIN",
        "iss": "orangevc",
        "aud": "ADMIN",
        "iat": now,
        "exp": now + 7200,
    }, separators=(",", ":")).encode())
    secret = os.getenv("JWT_ADMIN_SECRET", "change-me-admin").encode().ljust(32, b"\0")
    signature = b64(hmac.new(secret, f"{header}.{payload}".encode(), hashlib.sha256).digest())
    return f"sk-{header}.{payload}.{signature}"


TOKEN = local_admin_token()


def call(method: str, path: str, body=None, timeout=300):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + path,
        data=data,
        method=method,
        headers={"Admin-Token": TOKEN, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            parsed = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"HTTP {error.code} {path}: {error.read().decode('utf-8', 'replace')}") from error
    if parsed.get("code") != 1:
        raise RuntimeError(f"API rejected {path}: {parsed.get('msg') or parsed}")
    return parsed.get("data")


def wait_for_documents(expected: int, timeout_seconds=1800):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        documents = call("GET", "/admin/rag-lab/documents?limit=200") or []
        states = {}
        for document in documents:
            states[document.get("status", "UNKNOWN")] = states.get(document.get("status", "UNKNOWN"), 0) + 1
        print(f"DOCUMENT_PROGRESS total={len(documents)}/{expected} states={states}", flush=True)
        if len(documents) >= expected and not any(key in states for key in ("PENDING", "PROCESSING")):
            return documents
        time.sleep(10)
    raise TimeoutError("Timed out waiting for document indexing")


def wait_for_generation(timeout_seconds=1800):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        generations = call("GET", "/admin/rag-lab/generations?limit=20") or []
        summary = [(item.get("generationKey"), item.get("adapterType"), item.get("status"), item.get("active"))
                   for item in generations]
        print(f"GENERATION_PROGRESS {summary}", flush=True)
        active = next((item for item in generations
                       if item.get("active") and item.get("adapterType") == "HNSW_HYBRID"
                       and item.get("status") in {"READY", "ACTIVE"}), None)
        if active:
            return active, generations
        failed = next((item for item in generations if item.get("status") == "FAILED"), None)
        if failed:
            raise RuntimeError(f"Generation failed: {failed.get('lastError')}")
        time.sleep(10)
    raise TimeoutError("Timed out waiting for HNSW generation")


def main():
    started = time.time()
    status = call("GET", "/admin/rag-lab/status")
    if not status.get("enabled"):
        raise RuntimeError("RAG B is disabled")
    imports = []
    existing = call("GET", "/admin/rag-lab/documents?limit=200") or []
    expected = sum(len(value) for value in SAMPLE_IDS.values())
    if len(existing) >= expected:
        print(f"REUSING_EXISTING_SAMPLE total={len(existing)}", flush=True)
        for document in existing:
            if document.get("status") == "FAILED":
                result = call("POST", f"/admin/rag-lab/documents/{document['id']}/retry")
                imports.append({"documentId": document["id"], "source": document.get("sourceType"),
                                "result": result, "operation": "RETRY"})
                print(f"RETRY document={document['id']} source={document.get('sourceType')}", flush=True)
    else:
        for source, identifiers in SAMPLE_IDS.items():
            for legacy_id in identifiers:
                try:
                    result = call("POST", f"/admin/rag-lab/imports/legacy/{legacy_id}")
                    imports.append({"legacyDocumentId": legacy_id, "source": source, "result": result,
                                    "operation": "IMPORT"})
                    print(f"IMPORT source={source} id={legacy_id} status={result.get('status')}", flush=True)
                except Exception as error:  # Continue so one stale legacy row cannot invalidate the full sample.
                    imports.append({"legacyDocumentId": legacy_id, "source": source, "error": str(error),
                                    "operation": "IMPORT"})
                    print(f"IMPORT_FAILED source={source} id={legacy_id} error={error}", flush=True)

    accepted = [item for item in imports if "error" not in item]
    documents = wait_for_documents(expected)
    ready = [item for item in documents if item.get("status") == "READY"]
    failed = [item for item in documents if item.get("status") == "FAILED"]
    if not ready:
        raise RuntimeError("No imported documents became READY")

    generations = call("GET", "/admin/rag-lab/generations?limit=20") or []
    active_generation = next((item for item in generations
                              if item.get("active") and item.get("adapterType") == "HNSW_HYBRID"
                              and item.get("status") in {"READY", "ACTIVE"}), None)
    if active_generation is None:
        rebuild = call("POST", "/admin/rag-lab/generations/rebuild?adapter=HNSW_HYBRID")
        print(f"REBUILD_QUEUED {rebuild}", flush=True)
        active_generation, generations = wait_for_generation()
    else:
        print(f"REUSING_ACTIVE_GENERATION {active_generation.get('generationKey')}", flush=True)
    evaluation = call("POST", "/admin/rag-lab/evaluation/run?topK=8&threshold=0.55", timeout=900)

    answer_cases = [
        {"query": "推荐一个适合新手、两周内能完成的 Java 项目", "topK": 8, "threshold": 0.55,
         "sourceTypes": ["BEGINNER_PROJECT"], "requiredLanguages": ["Java"], "maxDurationDays": 14,
         "beginnerPreferred": True},
        {"query": "当前有效 Java 岗位要求哪些技能？", "topK": 8, "threshold": 0.55,
         "sourceTypes": ["JOB_POSTING"], "requiredLanguages": ["Java"], "careerPreferred": True},
        {"query": "项目运行需要哪些依赖、数据库或密钥？", "topK": 8, "threshold": 0.55,
         "sourceTypes": ["GITHUB"]},
    ]
    output = Path("docs/rag-b/first-round-result.json")
    previous = json.loads(output.read_text(encoding="utf-8")) if output.exists() else {}
    answers = previous.get("answerPreviews", []) if os.getenv("RAG_B_SKIP_ANSWERS") == "true" else []
    if os.getenv("RAG_B_SKIP_ANSWERS") != "true":
        for value in answer_cases:
            case_started = time.time()
            try:
                result = call("POST", "/admin/rag-lab/answer-preview", value, timeout=300)
                answers.append({"query": value["query"], "durationMs": round((time.time() - case_started) * 1000),
                                "result": result})
            except Exception as error:
                answers.append({"query": value["query"], "durationMs": round((time.time() - case_started) * 1000),
                                "error": str(error)})

    report = {
        "runAt": datetime.now(timezone.utc).isoformat(),
        "samplePlan": {key: len(value) for key, value in SAMPLE_IDS.items()},
        "requestedDocuments": sum(len(value) for value in SAMPLE_IDS.values()),
        "acceptedOperations": len(accepted),
        "readyDocuments": len(ready),
        "failedDocuments": len(failed),
        "failedDocumentDetails": failed,
        "importErrors": [item for item in imports if "error" in item],
        "activeGeneration": active_generation,
        "generations": generations,
        "evaluation": evaluation,
        "answerPreviews": answers,
        "wallClockSeconds": round(time.time() - started, 3),
        "costBoundary": "Provider billing/usage is not returned by the current embedding and text client; enrich with chunk and request counts after the run.",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"REPORT={output.resolve()}", flush=True)
    print(json.dumps({
        "readyDocuments": len(ready),
        "failedDocuments": len(failed),
        "evaluationSummary": evaluation.get("summary"),
        "wallClockSeconds": report["wallClockSeconds"],
    }, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"EXPERIMENT_FAILED={error}", file=sys.stderr, flush=True)
        raise
