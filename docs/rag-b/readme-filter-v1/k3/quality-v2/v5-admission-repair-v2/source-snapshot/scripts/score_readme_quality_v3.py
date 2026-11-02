"""V2 admission scoring with a narrowly scoped, source-only navigation parser correction.

The sealed V2 scorer and historical results stay unchanged. Body labels, denominators,
scope checks and quality thresholds are inherited without adjustment.
"""
from copy import deepcopy
import re
from score_readme_quality_v2 import evaluate as evaluate_v2, expected_pointers as pointers_v2, metric

POLICY = 'README_QUALITY_V3_NO_IMAGE_NAVIGATION'


def expected_pointers(raw):
    # Linked images are decorations, not text navigation. Preserve literal text URLs/labels.
    raw = re.sub(r'(?is)<a\b[^>]*>.*?</a\s*>',
                 lambda m: '' if re.search(r'(?is)<(?:img|picture|svg)\b', m[0]) else m[0], raw)
    raw = re.sub(r'!\[[^\]\r\n]*\]\([^\r\n]*?\)', '', raw)
    return pointers_v2(raw)


def evaluate(data, source_rows, results):
    report = deepcopy(evaluate_v2(data, source_rows, results))
    report['legacyNavigationComplete'] = deepcopy(report['metrics']['navigationComplete'])
    blocks = {b['blockId']: b for row in source_rows for b in row['blocks']}
    for row in report['cases']:
        if row['category'] != 'N':
            continue
        expected = expected_pointers(blocks[row['blockId']]['text'])
        row['navigationExpected'] = expected
        row['navigationComplete'] = bool(expected and set(expected) <= {tuple(p) for p in row['navigationActual']}
            and row['assessment'] and row['assessment']['disposition'] == 'POINTER_METADATA_ONLY' and not row['inBody'])
    nav = [r for r in report['cases'] if r['category'] == 'N']
    report['metrics']['navigationComplete'] = metric(sum(r['navigationComplete'] for r in nav), len(nav))
    report['metrics']['criticalCorrectlyUsable'] = sum(r['usableBody'] or r['navigationComplete'] for r in report['cases'] if r['critical'])
    # Independently revalidate all emitted navigation, including unlabelled blocks.
    for result in results.values():
        for pointer in result['recommendation']['pointers']:
            bid = pointer['evidenceBlockId']
            if bid in blocks and (pointer['label'], pointer['href']) not in expected_pointers(blocks[bid]['text']):
                report['violations']['source'].append({'blockId': bid, 'reason': 'decorative_or_unverified_navigation'})
    report['metrics']['wrongSourceCitation'] = len(report['violations']['source'])
    if report['metrics']['wrongSourceCitation']:
        report['metrics']['qualityGatePassed'] = False
    report['scorerPolicy'] = POLICY
    report['scoringCorrection'] = 'Exclude Markdown images and image-only HTML anchors from expected text navigation; other admission metrics unchanged.'
    return report
