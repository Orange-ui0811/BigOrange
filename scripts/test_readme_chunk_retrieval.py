"""Check interval-union evidence scoring independently of retrieval ranks or particular model outputs."""
import unittest
from readme_chunk_retrieval import covered


class EvidenceCoverageTest(unittest.TestCase):
    def setUp(self):
        self.gold = {'blockId': 'source-a:block-1', 'documentId': '1', 'startUtf16': 10, 'endUtf16': 30}

    def span(self, start, end, block='source-a:block-1', document='1'):
        return {'blockId': block, 'documentId': document, 'startUtf16': start, 'endUtf16': end}

    def test_adjacent_children_jointly_cover_complete_evidence(self):
        self.assertTrue(covered(self.gold, [self.span(20, 30), self.span(10, 20)]))

    def test_one_missing_character_is_not_complete_evidence(self):
        self.assertFalse(covered(self.gold, [self.span(10, 19), self.span(20, 30)]))

    def test_overlap_and_duplicates_do_not_inflate_or_break_coverage(self):
        self.assertTrue(covered(self.gold, [self.span(10, 24), self.span(20, 30), self.span(10, 24)]))

    def test_same_offsets_in_another_source_do_not_count(self):
        self.assertFalse(covered(self.gold, [self.span(10, 30, block='source-b:block-1')]))
        self.assertFalse(covered(self.gold, [self.span(10, 30, document='2')]))

    def test_partial_sentence_and_missing_evidence_are_not_complete(self):
        self.assertFalse(covered(self.gold, [self.span(10, 28)]))
        self.assertFalse(covered(self.gold, []))


if __name__ == '__main__':
    unittest.main()
