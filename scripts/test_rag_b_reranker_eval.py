import math
import unittest
import rag_b_reranker_eval as e

def hit(i,source,score,text='A fact',title='Tool'):
    return {'chunkId':i,'sourceType':'GITHUB','sourceRef':source,'score':score,'title':title,'content':text,'documentId':source,'sectionPath':'Features'}

class RerankerComparisonTest(unittest.TestCase):
    def test_reordering_cannot_invent_a_candidate(self):
        pool=[hit(1,'a',.9),hit(2,'b',.8)]
        rows=e.select(pool,'Find a tool',{'1':.1,'2':.9});self.assertEqual([2,1],[h['chunkId'] for h in rows]);self.assertEqual(.8,rows[0]['score'])
    def test_same_project_diversity_applies_after_model_ranking(self):
        pool=[hit(1,'a',.9),hit(2,'a',.8),hit(3,'b',.7)]
        self.assertEqual([1,3],[h['chunkId'] for h in e.select(pool,'推荐项目',k=2)])
        self.assertEqual([2,3],[h['chunkId'] for h in e.select(pool,'推荐项目',{'1':.8,'2':.9,'3':.1},k=2)])
    def test_score_reply_requires_each_original_index_exactly_once(self):
        self.assertEqual({0:.2,1:.8},e.parse_scores({'results':[{'index':1,'relevance_score':.8},{'index':0,'relevance_score':.2}]},2))
        for rows in [[{'index':0,'score':.2}], [{'index':0,'score':.2},{'index':0,'score':.9}], [{'index':0,'score':math.nan},{'index':1,'score':.2}]]:
            with self.assertRaises(AssertionError):e.parse_scores({'results':rows},2)
    def test_project_hit_does_not_imply_target_fact_hit(self):
        case={'refs':['a'],'anchors':['Durable execution']}
        self.assertEqual(1,e.rank(case,[hit(1,'a',.9)]));self.assertEqual(0,e.rank(case,[hit(1,'a',.9)],True))
    def test_wrong_source_cannot_supply_the_literal_anchor(self):
        self.assertEqual(0,e.rank({'refs':['a'],'anchors':['Durable execution']},[hit(1,'b',.9,'Durable execution')],True))
    def test_missing_commercial_price_evidence_keeps_native_gate(self):
        self.assertEqual([],e.gate('商业许可证价格是多少',[hit(1,'a',.9,'MIT license')]))
        self.assertEqual(1,len(e.gate('商业许可证价格是多少',[hit(1,'a',.9,'Commercial license price is $10')])) )
    def test_empty_pool_has_no_successful_evidence(self):
        self.assertEqual('REFUSE_NO_MATCH',e.policy([]));self.assertEqual([],e.select([],'推荐'))
    def test_outbound_document_does_not_include_gold_or_rank_scores(self):
        text=e.document(hit(1,'owner/tool',.987,'Known fact'));self.assertIn('Known fact',text);self.assertNotIn('0.987',text);self.assertNotIn('expected',text)

if __name__=='__main__':unittest.main()
