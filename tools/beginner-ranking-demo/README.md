# OrangeVC Beginner Ranking Demo

This demo proves that a frozen weekly candidate snapshot and a versioned scoring
algorithm produce the same beginner-friendly ranking without asking an LLM to
choose repositories by intuition.

## Run

```powershell
cd D:\Orangejd\tools\beginner-ranking-demo
python ranking.py
python -m unittest -v test_ranking.py
```

Expected top three for the `2026-08-03` snapshot:

1. `BuilderIO/agent-native`
2. `virgiliojr94/book-to-skill`
3. `pbakaus/impeccable`

## Boundaries

- The fixture is a frozen research snapshot, not a live crawler.
- Boolean environment facts should eventually be extracted from repository files
  and stored with evidence paths.
- Subjective 1-5 evidence fields must use a published rubric before production.
- AI may extract evidence and write Chinese summaries. It must not directly set
  the final score or reorder the result.
- A production run should retain every candidate, rejection reason, score,
  algorithm version and source timestamp.

## Product model

The production feature should expose two layers:

1. **Weekly picks**: three editorially reviewed projects shown prominently.
2. **Beginner project library**: every eligible historical project, searchable by
   stack, direction, difficulty, setup burden, estimated time and project type.

Weekly picks are snapshots. The library accumulates projects over time and
re-verifies stale records before displaying them as currently recommended.
