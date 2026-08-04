# Independent Agent Review

You are validating a deterministic repository ranking. Do not browse the web and
do not use your own preferences.

1. Read `fixtures/weekly-2026-08-03.json`.
2. Read `ranking.py`.
3. Independently calculate the ranking according to the code.
4. Report the top three repository full names in order.
5. Report whether the result is reproducible from the supplied snapshot.
6. Point out any field whose frozen value still depends on human judgment.

The review fails if you replace the supplied evidence, add repositories, browse
for newer data or choose projects using an unconstrained language-model opinion.
