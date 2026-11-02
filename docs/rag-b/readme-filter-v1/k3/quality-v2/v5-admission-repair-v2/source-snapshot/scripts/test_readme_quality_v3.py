"""Independent regressions for the image/navigation scoring defect; no provider access."""
import json
from pathlib import Path
import unittest
from score_readme_quality_v2 import evaluate as legacy_evaluate
from score_readme_quality_v3 import evaluate, expected_pointers

ROOT = Path(__file__).resolve().parents[1]
Q = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


class NavigationScoringTest(unittest.TestCase):
    def test_linked_badge_does_not_add_an_image_url_or_empty_wrapper(self):
        raw = '[![Docker](https://img.test/badge.svg)](https://docs.test/docker) [Docker](https://docs.test/docker) **Recommended**'
        self.assertEqual([('Docker', 'https://docs.test/docker')], expected_pointers(raw))

    def test_html_image_anchor_is_not_text_navigation(self):
        raw = '<a href="/badge"><img alt="License" src="badge.svg"></a><a href="/guide">Guide</a>'
        self.assertEqual([('Guide', '/guide')], expected_pointers(raw))

    def test_negative_facts_around_links_are_not_rewritten(self):
        raw = '| Authentication | No | [Details](https://docs.test/auth) |'
        self.assertEqual([('Details', 'https://docs.test/auth')], expected_pointers(raw))
        self.assertIn('| No |', raw)

    def test_frozen_appsmith_table_contains_three_text_destinations(self):
        source = next(s for s in read(Q/'r4/sources.json')['documents'] if s['source']['documentId'] == '522')
        table = next(b for b in source['blocks'] if b['startLine'] == 39)
        pointers = expected_pointers(table['text'])
        self.assertEqual(['Docker', 'Kubernetes', 'AWS AMI'], [p[0] for p in pointers])
        self.assertTrue(all(p[1].startswith('https://docs.appsmith.com/') for p in pointers))

    def test_frozen_replay_correction_does_not_change_body_scores_or_gates(self):
        stage = ROOT/'_experiment_staging/readme-v5-deterministic-repair-v1'
        replay = read(stage/'replay-results.json')
        data = read(Q/'v5-audit-v1/reviewed-labels-v3.json')
        data['versions'] = replay['versions']
        sources = read(Q/'r4/sources.json')['documents']
        results = {d['documentId']: d['result'] for d in replay['documents']}
        old, new = legacy_evaluate(data, sources, results), evaluate(data, sources, results)
        self.assertEqual(26, old['metrics']['navigationComplete']['numerator'])
        self.assertEqual({'numerator': 27, 'denominator': 27, 'rate': 1.0}, new['metrics']['navigationComplete'])
        for key, value in old['metrics'].items():
            if key not in {'navigationComplete', 'criticalCorrectlyUsable'}:
                self.assertEqual(value, new['metrics'][key], key)
        self.assertEqual(old['violations'], new['violations'])


if __name__ == '__main__':
    unittest.main()
