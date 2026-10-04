import json,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_ragas_m3_full_ab import AuditedLLM,BudgetStopped

class BudgetTests(unittest.TestCase):
    def adapter(self,root,events):
        llm=AuditedLLM.__new__(AuditedLLM)
        llm.model='test';llm.key='not-a-real-key';llm.endpoint='https://api.deepseek.com'
        llm.ledger=Path(root)/'calls.jsonl';llm.cache=Path(root)/'cache';llm.cache.mkdir()
        llm.ledger.write_text('\n'.join(json.dumps(e) for e in events),encoding='utf-8')
        return llm
    def test_request_limit_stops_before_network(self):
        with tempfile.TemporaryDirectory() as root:
            llm=self.adapter(root,[{'kind':'START','attempt':i,'purpose':'judge','reservedPeakUsd':0} for i in range(600)])
            with self.assertRaises(BudgetStopped):llm.complete([{'role':'user','content':'x'}],'judge')
    def test_generation_limit_stops_before_network(self):
        with tempfile.TemporaryDirectory() as root:
            llm=self.adapter(root,[{'kind':'START','attempt':i,'purpose':'answer','reservedPeakUsd':0} for i in range(36)])
            with self.assertRaises(BudgetStopped):llm.complete([{'role':'user','content':'x'}],'answer')
    def test_conservative_cost_reserve_stops_before_network(self):
        with tempfile.TemporaryDirectory() as root:
            llm=self.adapter(root,[{'kind':'START','attempt':1,'purpose':'judge','reservedPeakUsd':4.999}])
            with self.assertRaises(BudgetStopped):llm.complete([{'role':'user','content':'x'}],'judge')

if __name__=='__main__':unittest.main()
