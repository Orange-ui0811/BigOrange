"""Fresh synthetic sessions only; reuse user-authorized provider and evidence access."""
import argparse
import json
import time
import uuid
from pathlib import Path
from customer_local_smoke import authorized_token
from run_customer_cases import Api, TERMINAL
from run_analysis_customer_b_compare import verify_profile_empty

ROOT = Path(__file__).resolve().parents[1] / 'docs/customer/repair-v412-live'
CASES = {
    'ai_application': '请直接推荐项目。方向是AI应用开发；目标是求职；我有Vue前端和Linux部署经验；语言不限；完成周期半年内。可以先展示真实的待核实探索项目，说明功能、推荐理由和学习路径。',
    'rag': '请直接推荐项目。方向是RAG；目标是求职；我有Vue前端和Linux部署经验；语言不限；完成周期半年内。可以先展示真实的待核实探索项目，说明功能、推荐理由和学习路径。',
    'no_match': '请推荐项目，主题是量子纠错芯片流片，只推荐Python项目，周期不限。允许资料待核实的探索项目；如果没有，请建议如何修改条件，不要自动修改。',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--phase', choices=['before', 'after'], required=True)
    args = parser.parse_args()
    verify_profile_empty()
    ROOT.mkdir(parents=True, exist_ok=True)
    api = Api('http://127.0.0.1:18080', authorized_token(1))
    for key, message in CASES.items():
        if args.phase == 'before' and key == 'no_match':
            continue
        target = ROOT / f'{args.phase}-{key}.json'
        if target.exists():
            print(f'{target.name}: existing result retained', flush=True)
            continue
        verify_profile_empty()
        session = api.request('POST', '/sessions', {'title': f'合成语义对照 {args.phase}-{key}'})
        run = None
        started = time.monotonic()
        try:
            run = api.request('POST', f"/sessions/{session['id']}/runs", {'requestId': str(uuid.uuid4()), 'message': message})
            limit = time.monotonic() + 100
            while run['status'] not in TERMINAL and time.monotonic() < limit:
                time.sleep(1)
                run = api.request('GET', '/runs/' + run['id'])
            if run['status'] not in TERMINAL:
                run = api.request('POST', '/runs/' + run['id'] + '/cancel', {})
            result = json.loads(run.get('result_json') or '{}')
            metrics = json.loads(run.get('metrics_json') or '{}')
            row = dict(phase=args.phase, case=key, message=message, runId=run['id'], status=run['status'],
                       durationMs=round((time.monotonic()-started)*1000), result=result, metrics=metrics)
            target.write_text(json.dumps(row, ensure_ascii=False, indent=2), encoding='utf-8')
            cards = result.get('projects', [])
            print(json.dumps(dict(case=key, phase=args.phase, status=run['status'], resultStatus=result.get('status'),
                prompt=metrics.get('promptVersion'), durationMs=row['durationMs'], projects=len(cards),
                personalized=sum(bool(c.get('practiceAdvice')) for c in cards), references=len(result.get('references', [])),
                modelCalls=len(metrics.get('modelCalls', []))), ensure_ascii=False), flush=True)
        finally:
            if run and run['status'] not in TERMINAL:
                api.request('POST', '/runs/' + run['id'] + '/cancel', {})
            api.request('DELETE', f"/sessions/{session['id']}")


if __name__ == '__main__':
    main()
