"""Wait for local backend, verify provider identity, then run authenticated STATE smoke (no LLM)."""
import json, subprocess, sys, time, urllib.request
from customer_local_smoke import configured_environment, ROOT

def main():
    deadline=time.monotonic()+600
    while True:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health',timeout=2) as response:
                if json.load(response).get('status')=='UP':break
        except Exception:pass
        if time.monotonic()>deadline:raise RuntimeError('Backend did not become healthy; inspect startup logs')
        time.sleep(3)
    env=configured_environment()
    assert env.get('RAG_B_EMBEDDING_PROVIDER')=='OLLAMA'
    assert env.get('AI_EMBEDDING_MODEL')=='bge-small-zh-v1.5'
    path='docs/customer/langgraph4j/local-m3-20261008-http-smoke.json'
    subprocess.run([sys.executable,str(ROOT/'scripts/customer_graph_http_smoke.py'),'--output',path],check=True)
    report={'httpSmoke':path,'provider':env['RAG_B_EMBEDDING_PROVIDER'],'aModel':env['AI_EMBEDDING_MODEL'],
            'customerEngine':env.get('CUSTOMER_EXECUTION_ENGINE'),'realChatModelCalls':0}
    (ROOT/'docs/rag-b/local-m3-20261008/http-verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report),flush=True)

if __name__=='__main__':main()
