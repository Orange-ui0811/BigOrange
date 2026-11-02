"""Boundary tests for the R5 scorer; expected outcomes are independent of provider predictions."""
from copy import deepcopy
import unittest
from score_readme_quality_v2 import evaluate, expected_pointers, scopes_correct


def fixture():
    blocks = []
    specs = [('b', 'Project feature.\n', 'B'), ('dep', 'Requires 4 GB RAM.\n', 'B'),
             ('nav', '[Docs](#docs) [API](https://example.test/api)\n', 'N'),
             ('uncertain', 'Truncated uncertain claim...\n', 'R'), ('course', 'Course examination policy.\n', 'A')]
    cases, assessments = [], []
    for i, (bid, raw, category) in enumerate(specs):
        block = {'blockId': bid, 'text': raw, 'kind': 'PROSE', 'sha256': 'block-' + bid,
                 'startUtf16': i * 100, 'endUtf16': i * 100 + len(raw), 'startLine': i + 1, 'endLine': i + 1,
                 'sectionPath': [], 'headingIds': []}
        blocks.append(block)
        decision = 'REVIEW' if category == 'R' else 'KEEP'
        app = 'IN_SCOPE' if category == 'B' else 'OUT_OF_SCOPE' if category == 'A' else 'UNKNOWN'
        disposition = {'B': 'KNOWLEDGE_CANDIDATE', 'N': 'POINTER_METADATA_ONLY', 'R': 'REVIEW_QUEUE', 'A': 'ARCHIVE_ONLY'}[category]
        expected = {'decision': decision, 'applicability': app, 'disposition': disposition, 'requiredScopes': ['CORE'] if category == 'B' else [],
                    'critical': bid == 'dep', 'requiredBlockIds': ['dep'] if bid == 'b' else [], 'category': category,
                    'recoveryEligible': category == 'B', 'knownUsefulButBlocked': False, 'rationale': 'fixture'}
        cases.append({'caseId': bid, 'documentId': '1', 'sourceRef': 'repo/project', 'role': 'MODEL_TARGET', 'block': block, 'expected': expected})
        assessments.append({'blockId': bid, 'proposedDecision': decision, 'effectiveDecision': decision, 'kind': 'USAGE',
                            'applicability': app, 'scopes': expected['requiredScopes'], 'disposition': disposition,
                            'evidenceBlockIds': [bid], 'dependencyBlockIds': expected['requiredBlockIds'], 'reason': 'fixture'})
    source = {'source': {'documentId': '1', 'versionId': 'v1', 'sourceRef': 'repo/project', 'text': ''.join(b['text'] for b in blocks)},
              'sha256': 'source-hash', 'blocks': blocks}
    identity = {'documentId': '1', 'versionId': 'v1', 'sourceRef': 'repo/project', 'sourceSha256': 'source-hash'}
    body = dict(identity, packetId='body', primaryBlockIds=['b'], contextBlockIds=['dep'], disposition='KNOWLEDGE_CANDIDATE')
    navigation = dict(identity, packetId='navigation', primaryBlockIds=['nav'], contextBlockIds=[], disposition='POINTER_METADATA_ONLY')
    versions = {'parser': 'P', 'rulePolicy': 'RULE', 'selectionPolicy': 'MODEL', 'prompt': 'PROMPT', 'schema': 'SCHEMA', 'projectionPolicy': 'PROJECTION'}
    projection = {'policyVersion': 'PROJECTION', 'versions': {k: v for k, v in versions.items() if k != 'projectionPolicy'},
                  'assessments': assessments, 'knowledgeCandidates': [{'packet': body, 'subjectScopes': ['CORE'], 'evidence': assessments[:2]}],
                  'pointerCandidates': [{'packet': navigation, 'subjectScopes': [], 'evidence': [assessments[2]]}],
                  'pointers': [{'label': label, 'href': href, 'evidenceBlockId': 'nav'} for label, href in expected_pointers(blocks[2]['text'])]}
    result = {'preview': dict(identity, blocks=blocks, knowledgeCandidates=[body], pointerCandidates=[navigation]),
              'recommendation': projection, 'selections': [{'blockId': a['blockId'], 'decision': a['proposedDecision']} for a in assessments]}
    data = {'cases': cases, 'versions': versions, 'gates': {'bodyPrecision': .95, 'inScopeDecisionAgreement': .85, 'usableBodyRecovery': .85}}
    return data, [source], {'1': deepcopy(result)}


class FrozenScorerTest(unittest.TestCase):
    def score(self, mutation=None):
        d, s, r = fixture()
        if mutation:
            mutation(d, r['1'])
        return evaluate(d, s, r)

    def test_exact_admission_passes(self):
        m = self.score()['metrics']
        self.assertTrue(m['qualityGatePassed'])
        self.assertEqual({'numerator': 2, 'denominator': 2, 'rate': 1.0}, m['usableBodyRecovery'])

    def test_failure_does_not_remove_denominator(self):
        d, s, _ = fixture()
        m = evaluate(d, s, {})['metrics']
        self.assertEqual(2, m['usableBodyRecovery']['denominator'])
        self.assertEqual(2, m['inScopeDecisionAgreement']['denominator'])
        self.assertIsNone(m['bodyPrecision']['rate'])
        self.assertFalse(m['qualityGatePassed'])

    def test_model_keep_does_not_count_as_recovery(self):
        def change(d, r):
            r['preview']['knowledgeCandidates'].clear()
            r['recommendation']['knowledgeCandidates'].clear()
            for a in r['recommendation']['assessments'][:2]:
                a.update(effectiveDecision='REVIEW', disposition='REVIEW_QUEUE')
        m = self.score(change)['metrics']
        self.assertEqual(0, m['usableBodyRecovery']['numerator'])
        self.assertEqual(1.0, m['allTargetProposalDecisionAgreement']['rate'])
        self.assertFalse(m['qualityGatePassed'])

    def leak(self, d, r, index):
        a = r['recommendation']['assessments'][index]
        a.update(effectiveDecision='KEEP', disposition='KNOWLEDGE_CANDIDATE', applicability='IN_SCOPE', scopes=['CORE'])
        r['recommendation']['knowledgeCandidates'][0]['evidence'].append(a)
        r['preview']['knowledgeCandidates'][0]['contextBlockIds'].append(a['blockId'])

    def test_navigation_cannot_leak_through_context(self):
        m = self.score(lambda d, r: self.leak(d, r, 2))['metrics']
        self.assertEqual(3, m['bodyPrecision']['denominator'])
        self.assertEqual(1, m['scopeOverreach'])
        self.assertFalse(m['qualityGatePassed'])

    def test_out_of_scope_valid_fact_is_not_body(self):
        m = self.score(lambda d, r: self.leak(d, r, 4))['metrics']
        self.assertEqual(1, m['scopeOverreach'])
        self.assertFalse(m['qualityGatePassed'])

    def test_unknown_promotion_is_a_zero_tolerance_failure(self):
        m = self.score(lambda d, r: self.leak(d, r, 3))['metrics']
        self.assertEqual(1, m['uncertainPromotion'])
        self.assertFalse(m['qualityGatePassed'])

    def test_missing_dependency_invalidates_usable_feature(self):
        def change(d, r):
            r['preview']['knowledgeCandidates'][0]['contextBlockIds'].clear()
            r['recommendation']['knowledgeCandidates'][0]['evidence'].pop()
        m = self.score(change)['metrics']
        self.assertEqual(1, m['missingConditionsInUsedEvidence'])
        self.assertEqual(0, m['usableBodyRecovery']['numerator'])
        self.assertFalse(m['qualityGatePassed'])

    def test_lost_edition_scope_fails(self):
        def change(d, r):
            d['cases'][0]['expected']['requiredScopes'] = ['ENTERPRISE']
        m = self.score(change)['metrics']
        self.assertEqual(1, m['scopeOverreach'])
        self.assertFalse(m['qualityGatePassed'])

    def test_wrong_source_and_raw_rewrite_fail(self):
        def change(d, r):
            r['preview']['versionId'] = 'other'
            r['preview']['blocks'][0]['text'] = 'Invented fact'
        m = self.score(change)['metrics']
        self.assertGreaterEqual(m['wrongSourceCitation'], 2)
        self.assertFalse(m['qualityGatePassed'])

    def test_multi_link_navigation_requires_all_entries(self):
        def change(d, r):
            r['recommendation']['pointers'].pop()
        m = self.score(change)['metrics']
        self.assertEqual(0, m['navigationComplete']['numerator'])
        self.assertEqual(1, m['navigationComplete']['denominator'])

    def test_html_navigation_expected_independently(self):
        self.assertEqual([('Docs', 'https://example.test/docs')], expected_pointers('<a href="https://example.test/docs"><b>Docs</b></a>'))

    def test_unjudged_evidence_is_not_assumed_correct(self):
        def change(d, r):
            d['cases'] = [c for c in d['cases'] if c['caseId'] != 'dep']
        m = self.score(change)['metrics']
        self.assertEqual(1, m['unjudgedBodyBlocks'])
        self.assertEqual(1, m['bodyPrecision']['denominator'])

    def test_critical_wrong_drop_fails(self):
        def change(d, r):
            a = r['recommendation']['assessments'][1]
            a.update(effectiveDecision='DROP', disposition='ARCHIVE_ONLY')
        m = self.score(change)['metrics']
        self.assertEqual(1, m['criticalWrongDeletion'])
        self.assertFalse(m['qualityGatePassed'])

    def test_additional_core_cannot_replace_related_product(self):
        self.assertFalse(scopes_correct(['RELATED_PRODUCT'], ['CORE']))
        self.assertFalse(scopes_correct(['CORE'], ['CORE', 'ENTERPRISE']))
        self.assertTrue(scopes_correct(['ENTERPRISE'], ['ENTERPRISE', 'PLATFORM_SPECIFIC']))


if __name__ == '__main__':
    unittest.main(verbosity=2)
