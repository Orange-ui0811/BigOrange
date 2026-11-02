import unittest
from readme_three_arm import covered,selection

class EvidenceScoringTest(unittest.TestCase):
    def setUp(self):self.gold={'documentId':'d','startUtf16':10,'endUtf16':20}
    def span(self,a,b,doc='d'):return {'documentId':doc,'startUtf16':a,'endUtf16':b}
    def test_merged_spans_cover_without_counting_unrelated_tail(self):
        self.assertTrue(covered(self.gold,[self.span(0,13),self.span(13,20),self.span(25,40)]))
    def test_hole_wrong_source_and_missing_character_fail(self):
        for spans in [[self.span(10,14),self.span(15,20)],[self.span(10,20,'other')],[self.span(10,19)],[]]:
            self.assertFalse(covered(self.gold,spans))
    def test_parent_budget_is_shared_deduplicated_and_never_truncates(self):
        def chunk(name,unit,tokens):return {'id':name,'variant':'C','unitId':unit,'tokens':1,'expandedTokens':tokens,'sourceSpans':[], 'expandedSpans':[]}
        corpus={c['id']:c for c in [chunk('a','u',1800),chunk('b','u',1800),chunk('c','v',500),chunk('d','w',100)]}
        selected,_,tokens,deferred=selection([{'id':x} for x in ['a','b','c','d']],corpus,4,True,2048)
        self.assertEqual([c['id'] for c in selected],['a','d']);self.assertEqual(tokens,1900);self.assertEqual(deferred,['v'])
    def test_no_backfill_from_outside_top_k(self):
        c={'id':'a','variant':'A','unitId':'','tokens':2500,'sourceSpans':[]}
        self.assertEqual(selection([{'id':'a'},{'id':'missing'}],{'a':c},1,False,2048)[0],[])

if __name__=='__main__':unittest.main()
