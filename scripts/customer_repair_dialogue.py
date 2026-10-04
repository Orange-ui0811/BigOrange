"""Synthetic follow-up smoke on the repaired customer; no real profile/history."""
import json
from pathlib import Path
from customer_local_smoke import authorized_token
from run_analysis_customer_b_compare import verify_profile_empty
from run_customer_cases import Api
from customer_semantic_continuation_live import turn

OUT=Path(__file__).resolve().parents[1]/'docs/customer/repair-v410-live/dialogue.json'

def main():
    if OUT.exists():raise SystemExit('Existing dialogue result is not replayed')
    verify_profile_empty();api=Api('http://127.0.0.1:18080',authorized_token(1))
    session=api.request('POST','/sessions',{'title':'客服修复连续对话合成验收'})['id']
    result={'sessionId':session,'turns':[]}
    try:
        for message in ['你好','请先记下，不用推荐：语言Java，主题RAG和知识库，目标求职，周期半年内。','把完成周期改成两个月内，其他条件保留，先不推荐。','当前有什么条件？']:
            verify_profile_empty();row=turn(api,session,message);result['turns'].append(row)
            print(json.dumps({'status':row['status'],'intent':row['result'].get('intent'),'days':row['state'].get('maxDurationDays'),'dedupe':row['dedupePassed']},ensure_ascii=False),flush=True)
        rows=result['turns']
        result['passed']=all(r['status']=='SUCCEEDED' and r['dedupePassed'] and r['continuationPassed'] for r in rows) and rows[0]['result']['intent']=='CHAT' and rows[1]['state'].get('maxDurationDays')==180 and rows[2]['state'].get('maxDurationDays')==60 and rows[3]['state'].get('maxDurationDays')==60 and rows[3]['state'].get('language')=='Java' and not rows[3]['state'].get('pendingConditions')
    finally:
        api.request('DELETE',f'/sessions/{session}');result['sessionSoftDeleted']=True
        OUT.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    if not result.get('passed'):raise SystemExit('Dialogue assertions failed')

if __name__=='__main__':main()
