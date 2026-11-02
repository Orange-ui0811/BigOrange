"""Build/verify K1 audit artifacts from a frozen local snapshot and explicit annotations.

No database, HTTP, model, embedding, or vector-index access. This is an audit
fixture builder, not an implemented production knowledge filter.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/rag-b/readme-filter-v1"
DEFAULT_SNAPSHOT = ROOT / "_experiment_staging/readme-filter-v1-20261029/snapshot.json"
EXPECTED_INITIAL = {46, 50, 228, 268, 285, 299, 318, 329, 335, 354, 357, 408,
                    435, 517, 518, 519, 523, 543}
EXPECTED_ADDITIONAL = {47, 54, 55, 57, 76, 164, 231, 247, 254, 259, 265, 278,
                       279, 284, 294, 306, 344, 378, 421, 561}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def digest_file(path: Path) -> str:
    return sha(path.read_bytes())


def source_span(text: str, first: int, last: int) -> dict:
    lines = text.splitlines(keepends=True)
    require(1 <= first <= last <= len(lines), f"Invalid span {first}-{last} / {len(lines)} lines")
    start = sum(map(len, lines[:first - 1]))
    end = sum(map(len, lines[:last]))
    quote = text[start:end]
    raw16 = text.encode("utf-16-le")
    start16 = len(text[:start].encode("utf-16-le")) // 2
    end16 = len(text[:end].encode("utf-16-le")) // 2
    require(raw16[start16 * 2:end16 * 2].decode("utf-16-le") == quote, "UTF-16 roundtrip mismatch")
    require(quote == "".join(lines[first - 1:last]), "Line roundtrip mismatch")
    return {"startLine": first, "endLine": last, "startCodePoint": start,
            "endCodePointExclusive": end, "startUtf16": start16,
            "endUtf16Exclusive": end16, "text": quote,
            "textSha256": sha(quote.encode("utf-8"))}


def disposition(case):
    if case["decision"] == "REVIEW":
        return "REVIEW_QUEUE_NOT_INDEXED"
    if case["decision"] == "DROP":
        return "ARCHIVE_ONLY_NOT_INDEXED"
    if case["scope"] == "MIXED":
        return "PENDING_SAFE_SPLIT_NOT_DIRECTLY_INDEXED"
    if case["kind"] == "SOURCE_POINTER" or case["scope"] == "SOURCE_POINTER":
        return "POINTER_METADATA_CANDIDATE"
    return "KNOWLEDGE_CANDIDATE_NOT_YET_INDEXED"


def dump(value):
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def build(snapshot_path: Path):
    snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
    spec_path = OUT / "audit-spec.json"
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    documents = {int(d["documentId"]): d for d in snapshot["documents"]}
    require(len(documents) == len(snapshot["documents"]), "Duplicate source document IDs")
    samples = spec["samples"]
    ids = [s["id"] for s in samples]
    require(len(ids) == len(set(ids)) == 38, "Expected 38 unique document samples")
    for group, expected in [("initial18", EXPECTED_INITIAL), ("additional20", EXPECTED_ADDITIONAL)]:
        require({s["id"] for s in samples if s["round"] == group} == expected, f"Unexpected {group}")
    require(Counter(s["split"] for s in samples) == {"development": 24, "validation": 14},
            "Expected development 24 / validation 14")
    ledger_results = []
    for relative, baseline in snapshot["ledgersBefore"].items():
        path = ROOT / relative.replace("\\", "/")
        current = {"sha256": digest_file(path),
                   "entries": sum(bool(x.strip()) for x in path.read_text(encoding="utf-8").splitlines())}
        require(current == baseline, f"Provider ledger changed: {relative}; investigate, do not rewrite baseline")
        ledger_results.append({"path": path.relative_to(ROOT).as_posix(), "before": baseline,
                               "after": current, "unchanged": True})
    for doc in documents.values():
        require(doc["sha256"] == sha(doc["content"].encode("utf-8")),
                f"Frozen source hash mismatch: {doc['documentId']}")

    manifest = []
    cases = []
    seen_repos = set()
    for sample in samples:
        doc = documents[sample["id"]]
        repo = doc["sourceRef"]
        require(repo.casefold() not in seen_repos, f"Repository overlap: {repo}")
        seen_repos.add(repo.casefold())
        entry = {k: v for k, v in sample.items() if k not in ("cases", "id")}
        entry.update({"documentId": sample["id"], "versionId": doc["versionId"],
                      "sourceRef": repo, "sourceType": doc["sourceType"], "title": doc["title"],
                      "contentSha256": doc["sha256"], "storedHash": doc["storedHash"],
                      "characters": len(doc["content"]), "lines": len(doc["content"].splitlines()),
                      "versionCreatedAt": doc["versionCreatedAt"],
                      "provenance": "STORED_GITHUB_SOURCE_NOT_FRESH_REMOTE_VERIFIED",
                      "auditScope": "TARGETED_SPANS_NOT_FULL_DOCUMENT",
                      "caseCount": len(sample["cases"])})
        manifest.append(entry)
        for number, row in enumerate(sample["cases"], 1):
            require(len(row) == len(spec["caseColumns"]), f"Malformed annotation {repo}/{number}")
            case = dict(zip(spec["caseColumns"], row))
            require(case["decision"] in ("KEEP", "DROP", "REVIEW"), "Unknown decision")
            require(case["rule"] in {f"R{x:02}" for x in range(1, 13)}, "Unknown rule")
            span = source_span(doc["content"], row[0], row[1])
            case.update({"caseId": f"K1-{sample['id']:03}-{number:02}",
                         "documentId": sample["id"], "versionId": doc["versionId"],
                         "sourceRef": repo, "sourceSha256": doc["sha256"],
                         "round": sample["round"], "split": sample["split"], "repoRole": sample["role"],
                         "annotationAuthority": spec["annotationAuthority"], "sourceSpan": span})
            case["plannedDisposition"] = disposition(case)
            # Explicit source dependencies; they are not concatenated into a false continuous quote.
            context_ranges = []
            if sample["id"] == 284 and row[0] != 1:
                context_ranges.append((1, 3))
            if sample["id"] == 435 and row[0] == 1:
                context_ranges.append((45, 45))
            case["additionalContextSpans"] = [source_span(doc["content"], a, b) for a, b in context_ranges]
            cases.append(case)

    common = {"schemaVersion": 1, "sourceSnapshot": snapshot_path.relative_to(ROOT).as_posix(),
              "snapshotSha256": digest_file(snapshot_path), "frozenAt": snapshot["frozenAt"],
              "specSha256": digest_file(spec_path), "frozenDocuments": len(documents),
              "annotationAuthority": spec["annotationAuthority"]}
    report = ["# K1：38 份 README 代表性样本原文审计", "",
              "这是当前数据库冻结正文的定向片段审计，不是 38 份 README 全文逐行标注，也不是自动过滤器的评测结果。",
              "新增 20 份与首批 18 份不重复；所有标签均为助手审阅，尚未经用户人工确认。",
              "开发集 24 份，预留验证集 14 份；验证片段已为标注而阅读，不能称为盲测集。",
              "来源可能已经被旧流程翻译或截断；未访问 GitHub 重新核实。KEEP 不是事实已经得到运行验证。", "",
              "原文行号从 1 开始；机器文件还记录 Python code point 与 Java UTF-16 半开区间。",
              "规则见 [filter-policy.md](filter-policy.md)，结构化记录见 [audit-cases.json](audit-cases.json)。", ""]
    for sample in manifest:
        report += [f"## {sample['documentId']} · {sample['sourceRef']}", "",
                   f"批次：{sample['round']}；用途：{sample['role']}；集合：{sample['split']}。",
                   f"选择原因：{sample['selection']}。",
                   f"文档版本：{sample['versionId']}；正文 SHA-256：`{sample['contentSha256']}`。",
                   "正文风险标记：" + ("、".join(sample["flags"]) or "未在已审计片段标记文档级风险") + "。", ""]
        for case in [c for c in cases if c["documentId"] == sample["documentId"]]:
            report += [f"### {case['caseId']} · {case['decision']} · L{case['startLine']}–L{case['endLine']}", "",
                       f"类型：{case['kind']}；范围：{case['scope']}；规则：{case['rule']}。",
                       case["reason"], "",
                       f"后续去向：`{case['plannedDisposition']}`。本轮未写入任何索引。", ""]
            # A long tilde fence preserves nested backticks and all original markup as source data.
            quote = case["sourceSpan"]["text"]
            fence = "~" * max(8, max((len(t) for t in quote.split() if set(t) == {"~"}), default=0) + 1)
            report += [fence + "text", quote.rstrip("\r\n"), fence, ""]
            for context in case["additionalContextSpans"]:
                report += [f"附带限定原文 L{context['startLine']}–L{context['endLine']}：", "",
                           "~~~~~~~~text", context["text"].rstrip("\r\n"), "~~~~~~~~", ""]

    counts = dict(Counter(c["decision"] for c in cases))
    verification = {**common, "status": "K1_FIXTURE_INTEGRITY_PASS_NOT_FILTER_QUALITY_PASS",
                    "sampleCount": len(samples), "initialCount": 18, "additionalCount": 20,
                    "uniqueRepositories": len(seen_repos), "developmentCount": 24, "validationCount": 14,
                    "caseCount": len(cases), "decisionCounts": counts,
                    "sampleLabelCounts": {label: sum(any(c["decision"] == label for c in cases if c["documentId"] == s["id"]) for s in samples) for label in ("KEEP", "DROP", "REVIEW")},
                    "sourceHashesVerified": len(documents), "allSpansRoundtrip": True,
                    "allUtf16SpansRoundtrip": True, "repositorySplitDisjoint": True,
                    "annotationCoverage": "TARGETED_SPANS", "userConfirmedLabels": False,
                    "classifierImplemented": False, "retrievalEvaluationRun": False,
                    "additionalExternalInferenceCalls": 0, "productionIndexWrites": 0,
                    "ledgerChecks": ledger_results,
                    "limitations": ["Labels are assistant judgments, not independent human gold labels",
                                    "No semantic accuracy, retrieval benefit, or latency result is claimed",
                                    "Validation documents were read for annotation; not blind evaluation",
                                    "Zero-call claim applies to this offline script and inspected unchanged ledgers"]}
    return {"sample-manifest.json": dump({**common, "samples": manifest}),
            "audit-cases.json": dump({**common, "cases": cases}),
            "audit-examples.md": "\n".join(report) + "\n",
            "verification.json": dump(verification)}, verification


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, default=DEFAULT_SNAPSHOT)
    parser.add_argument("--verify-only", action="store_true", help="Check existing artifacts without rewriting them")
    args = parser.parse_args()
    artifacts, result = build(args.snapshot.resolve())
    for name, contents in artifacts.items():
        path = OUT / name
        if args.verify_only:
            require(path.exists(), f"Missing artifact: {path}")
            require(path.read_text(encoding="utf-8") == contents, f"Artifact differs: {path}; inspect before rebuild")
        else:
            path.write_text(contents, encoding="utf-8", newline="\n")
    print(json.dumps({k: result[k] for k in ("status", "frozenDocuments", "sampleCount", "additionalCount",
                                            "caseCount", "decisionCounts", "sourceHashesVerified")}, ensure_ascii=False))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
