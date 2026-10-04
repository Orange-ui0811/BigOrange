"""Verify corpus coverage, structural boundaries and evaluation metric correctness."""
import sys
import unittest
from pathlib import Path
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_rag_model_chunk_experiment import sections, split_document, evaluate, tokenizers


class ChunkExperimentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ts = tokenizers()

    def test_fenced_heading_is_not_section(self):
        text = '# Real\n\n```python\n# not a heading\nprint(1)\n```\n\n## Next\ntext'
        spans = sections(text)
        self.assertEqual([s[2] for s in spans], ['Real','Real > Next'])
        self.assertEqual(''.join(text[a:b] for a,b,_ in spans),text)

    def test_long_unbroken_text_has_no_gaps_and_progresses(self):
        text = '# Heading\n' + ('数据abc😀'*1000)
        doc={'id':1,'sourceRef':'sample','sourceType':'MANUAL','title':'Sample','content':text}
        chunks=split_document(doc,self.ts,256,384,32)
        covered=0
        for c in chunks:
            self.assertLessEqual(c['start'],covered)
            self.assertEqual(c['content'],text[c['start']:c['end']])
            self.assertGreater(c['end'],c['start'])
            covered=max(covered,c['end'])
            self.assertLessEqual(max(len(t.encode(c['content'],add_special_tokens=False).ids) for t in self.ts),384)
        self.assertEqual(covered,len(text))

    def test_sections_do_not_overlap(self):
        text='# One\n'+('这是第一部分。\n'*80)+'# Two\n'+('这是第二部分。\n'*80)
        doc={'id':1,'sourceRef':'sample','sourceType':'MANUAL','title':'Sample','content':text}
        chunks=split_document(doc,self.ts,256,384,32)
        boundary=text.index('# Two')
        self.assertTrue(all(c['end']<=boundary or c['start']>=boundary for c in chunks))

    def test_source_rank_does_not_count_as_evidence_rank(self):
        chunks=[{'sourceRef':'right','sourceType':'GITHUB','content':'irrelevant','documentId':1,
                 'start':0,'end':10},
                {'sourceRef':'right','sourceType':'GITHUB','content':'actual proof','documentId':1,
                 'start':10,'end':22}]
        cases=[{'key':'q','group':'g','category':'semantic','types':['GITHUB'],
                'refs':['right'],'anchors':['actual proof']}]
        r=evaluate(chunks,np.array([[1.,0.],[.8,.2]]),cases,np.array([[1.,0.]]))
        self.assertEqual(r['rows'][0]['sourceRank'],1)
        self.assertEqual(r['rows'][0]['evidenceRank'],2)
        self.assertEqual(r['semantic']['evidenceMRR8'],.5)


if __name__=='__main__':unittest.main()
