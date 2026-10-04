"""Export exact prompt evidence for offline RAGAS; makes no model calls and invents no references."""
import argparse
import json
import uuid
from pathlib import Path
from customer_local_smoke import sql

def export(rows):
    samples=[]
    for row in rows:
        for turn in row.get('turns', []):
            run=turn.get('runId', '')
            if str(uuid.UUID(run)) != run:
                raise ValueError('Invalid run identifier')
            trace=sql("SELECT s.detail_json FROM customer_step s JOIN customer_run r ON r.id=s.run_id "
                "WHERE r.account_id IN(1,5) AND s.run_id='"+run+"' AND s.tool_name='buildPrompt' ORDER BY s.id DESC LIMIT 1")
            result=turn.get('result', {})
            sample={'caseId':row['caseId'], 'runId':run, 'user_input':turn['message'],
                'response':'\n'.join([result.get('answer','')] + [ref.get('text','') for ref in result.get('references', [])]),
                'reference':row.get('humanReference'), 'traceStatus':'EXACT_PROMPT_TRACE' if trace else 'NO_PROMPT_TRACE',
                'retrieved_contexts':[]}
            if trace:
                prompt=json.loads(trace)
                sample['promptVersion']=prompt['promptVersion']
                sample['retrieved_contexts']=['\n'.join(span['text'] for span in evidence['spans']) for evidence in prompt['evidence']]
            # Absence of a human reference leaves reference-based metrics undefined, never guessed.
            sample['referenceMetricsAvailable']=isinstance(sample['reference'],str) and bool(sample['reference'])
            samples.append(sample)
    return samples

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('results',type=Path); parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    rows=json.loads(args.results.read_text(encoding='utf-8-sig'))
    samples=export(rows)
    args.output.write_text(json.dumps({'samples':samples,'notes':'Local export only. RAGAS calls require explicit outbound authorization; missing prompt traces and human references must not be silently reconstructed.'},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'samples':len(samples),'withExactPromptTrace':sum(s['traceStatus']=='EXACT_PROMPT_TRACE' for s in samples)}))

if __name__ == '__main__':
    main()
