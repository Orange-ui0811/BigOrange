#!/usr/bin/env python3
"""Deterministic beginner-friendly GitHub ranking demo."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any


ALLOWED_LANGUAGES = {"TypeScript", "JavaScript", "Python"}


def hard_filter(project: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    if project.get("archived"):
        reasons.append("repository is archived")
    if not project.get("license"):
        reasons.append("missing open-source license")
    if project.get("language") not in ALLOWED_LANGUAGES:
        reasons.append("primary language is outside the beginner track")
    if project.get("requiresGpu"):
        reasons.append("GPU is required")
    if not project.get("hasQuickStart"):
        reasons.append("no verifiable quick start")
    return reasons


def activity_score(weekly_stars: int) -> int:
    if weekly_stars >= 1_000:
        return 10
    if weekly_stars >= 300:
        return 8
    if weekly_stars >= 100:
        return 6
    if weekly_stars >= 30:
        return 4
    return 2


def environment_score(project: dict[str, Any]) -> tuple[int, list[str]]:
    score = 25
    deductions: list[str] = []
    checks = (
        ("requiresDocker", 8, "requires Docker"),
        ("requiresDatabase", 8, "requires a database"),
        ("requiresCloudAccount", 5, "requires a cloud account"),
        ("requiresModelAccess", 5, "requires an existing model/agent account"),
        ("requiresNativeBuild", 6, "requires native build tooling"),
    )
    for field, points, reason in checks:
        if project.get(field):
            score -= points
            deductions.append(f"-{points}: {reason}")
    if int(project.get("setupSteps", 0)) > 4:
        score -= 3
        deductions.append("-3: more than four setup steps")
    if project.get("codebaseSize") == "large":
        score -= 8
        deductions.append("-8: large codebase")
    return max(score, 0), deductions


def score_project(project: dict[str, Any]) -> dict[str, Any]:
    environment, deductions = environment_score(project)
    dimensions = {
        "environment": environment,
        "vibeCodingFit": int(project["taskDecomposability"]) * 4,
        "documentation": int(project["docsQuality"]) * 3,
        "showcaseValue": int(project["demoValue"]) * 3,
        "extensionPotential": int(project["extensionPotential"]) * 2,
        "weeklyActivity": activity_score(int(project["weeklyStars"])),
        "portfolioValue": int(project["portfolioValue"]),
    }
    return {
        **project,
        "dimensions": dimensions,
        "deductions": deductions,
        "score": sum(dimensions.values()),
    }


def rank(snapshot: dict[str, Any]) -> dict[str, Any]:
    eligible: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    for project in snapshot["projects"]:
        reasons = hard_filter(project)
        if reasons:
            rejected.append({"fullName": project["fullName"], "reasons": reasons})
        else:
            eligible.append(score_project(project))

    eligible.sort(
        key=lambda item: (
            -item["score"],
            -item["dimensions"]["environment"],
            -int(item["weeklyStars"]),
            item["fullName"].lower(),
        )
    )
    return {
        "algorithmVersion": snapshot["algorithmVersion"],
        "snapshotAt": snapshot["snapshotAt"],
        "candidateCount": len(snapshot["projects"]),
        "eligibleCount": len(eligible),
        "rejected": rejected,
        "ranking": eligible,
        "topThree": [item["fullName"] for item in eligible[:3]],
    }


def to_markdown(result: dict[str, Any]) -> str:
    lines = [
        "# Beginner Ranking Demo Result",
        "",
        f"- Algorithm: `{result['algorithmVersion']}`",
        f"- Snapshot: `{result['snapshotAt']}`",
        f"- Candidates: {result['candidateCount']}",
        f"- Eligible: {result['eligibleCount']}",
        "",
        "| Rank | Repository | Score | Weekly stars | Difficulty |",
        "|---:|---|---:|---:|---|",
    ]
    for index, project in enumerate(result["ranking"], 1):
        lines.append(
            f"| {index} | [{project['fullName']}]({project['htmlUrl']}) "
            f"| {project['score']} | {project['weeklyStars']} | {project['difficulty']} |"
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "snapshot",
        nargs="?",
        type=Path,
        default=Path(__file__).parent / "fixtures" / "weekly-2026-08-03.json",
    )
    parser.add_argument("--json", action="store_true", help="Print JSON instead of Markdown")
    args = parser.parse_args()
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8"))
    result = rank(snapshot)
    print(json.dumps(result, ensure_ascii=False, indent=2) if args.json else to_markdown(result))


if __name__ == "__main__":
    main()
