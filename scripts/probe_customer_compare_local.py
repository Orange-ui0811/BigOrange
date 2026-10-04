"""Aggregate-only preflight; never reads profile contents or prints credentials."""
import json
from customer_local_smoke import sql,configured_environment,authorized_token
from run_customer_cases import Api
from run_rag_b_first_round import call
def main():
    counts={}
    for owner in [1,5]:
        counts[str(owner)]={'activeProfileEntries':int(sql(f"SELECT COUNT(*) FROM persona_profile_entry WHERE account_id={owner} AND status='ACTIVE'")),
            'activeRuns':int(sql(f"SELECT COUNT(*) FROM customer_run WHERE account_id={owner} AND status IN ('QUEUED','RUNNING')"))}
    print(json.dumps({'accounts':counts,'capabilities':Api('http://127.0.0.1:18080',authorized_token(1)).request('GET','/capabilities')},ensure_ascii=False))
    configs=call('GET','/admin/ai-config')
    print(json.dumps({k:{field:v.get(field) for field in ['endpoint','model','version','source']} for k,v in configs.items() if isinstance(v,dict)},ensure_ascii=False))
if __name__=='__main__':main()
