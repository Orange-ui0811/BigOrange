import unittest
from readme_recommendation_eval import candidate_score

class RecommendationEvidenceTest(unittest.TestCase):
    def span(self,a,b,doc='d'):return {'documentId':doc,'startUtf16':a,'endUtf16':b}
    def candidate(self):return {'evidenceGroups':[[self.span(0,10)],[self.span(20,30)]],'limitGroups':[[self.span(40,50)]]}
    def test_requires_all_facts_not_just_matching_project(self):
        self.assertFalse(candidate_score(self.candidate(),[self.span(0,10)])['proofComplete'])
    def test_missing_limit_cannot_count_as_complete_recommendation_evidence(self):
        score=candidate_score(self.candidate(),[self.span(0,10),self.span(20,30)])
        self.assertTrue(score['proofComplete']);self.assertFalse(score['allEvidenceComplete'])
    def test_full_fact_and_limit_coverage(self):
        self.assertTrue(candidate_score(self.candidate(),[self.span(0,50)])['allEvidenceComplete'])
    def test_wrong_project_cannot_support_same_coordinates(self):
        self.assertFalse(candidate_score(self.candidate(),[self.span(0,50,'other')])['proofComplete'])
    def test_empty_gold_is_not_a_successful_observation(self):
        self.assertFalse(candidate_score({'evidenceGroups':[],'limitGroups':[]},[])['allEvidenceComplete'])
    def test_equivalent_source_locations_are_alternatives(self):
        c={'evidenceGroups':[[self.span(0,10),self.span(20,30)]],'limitGroups':[]}
        self.assertTrue(candidate_score(c,[self.span(20,30)])['allEvidenceComplete'])

if __name__=='__main__':unittest.main()
