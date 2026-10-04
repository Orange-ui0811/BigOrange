"""Read model catalogue using the configured embedding credential; never print keys."""
import json
import urllib.request
import urllib.error
import sys
import time
from pathlib import Path
from datetime import datetime, timezone

def main():
    values = {}
    for line in Path('.env.local').read_text(encoding='utf-8-sig').splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            name, value = line.split('=', 1)
            values[name.strip()] = value.strip().strip('\"').strip("'")
    endpoint = values.get('AI_EMBEDDING_ENDPOINT') or 'https://ai.gitee.com/v1'
    if endpoint.rstrip('/') != 'https://ai.gitee.com/v1':
        raise SystemExit('Configured embedding endpoint is not the expected Gitee endpoint.')
    key = values.get('AI_EMBEDDING_KEY')
    if not key:
        raise SystemExit('No embedding credential configured.')
    request = urllib.request.Request(endpoint.rstrip('/') + '/models', headers={'Authorization': 'Bearer ' + key})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))
    except urllib.error.HTTPError as error:
        print(json.dumps({'status': error.code, 'error': 'Model catalogue request failed'}))
        return
    output = {'checkedAt': datetime.now(timezone.utc).isoformat(), 'credentialSource': 'AI_EMBEDDING_KEY', 'endpoint': endpoint, 'catalogue': data}
    target = Path('docs/rag-b/gitee-model-catalogue.json')
    target.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding='utf-8')
    models = data.get('data', []) if isinstance(data, dict) else data
    print('MODEL_COUNT=' + str(len(models)))
    if '--probe-rag' in sys.argv:
        probe_rag(endpoint, key, {item['id'] for item in models})
        return
    for model in models:
        print(json.dumps(model, ensure_ascii=False))

def probe_rag(endpoint, key, available):
    results = []
    groups = {
        'embedding': ['bge-small-zh-v1.5', 'bge-large-zh-v1.5', 'bge-m3', 'Qwen3-Embedding-0.6B', 'Qwen3-Embedding-4B', 'Qwen3-Embedding-8B', 'bce-embedding-base_v1'],
        'rerank': ['bge-reranker-v2-m3', 'Qwen3-Reranker-0.6B', 'Qwen3-Reranker-4B', 'Qwen3-Reranker-8B', 'bge-reranker-large', 'bce-reranker-base_v1'],
    }
    for kind, names in groups.items():
        for name in names:
            if name not in available:
                continue
            body = {'model': name, 'input': ['Java 项目支持数据库配置。']} if kind == 'embedding' else {'model': name, 'query': 'Java 数据库配置', 'documents': ['Java 项目支持数据库配置。', '今天的天气晴朗。'], 'top_n': 2}
            url = endpoint.rstrip('/') + ('/embeddings' if kind == 'embedding' else '/rerank')
            req = urllib.request.Request(url, data=json.dumps(body).encode('utf-8'), headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
            started = time.monotonic()
            item = {'kind': kind, 'model': name}
            try:
                with urllib.request.urlopen(req, timeout=30) as response:
                    parsed = json.loads(response.read().decode('utf-8'))
                    item['httpStatus'] = response.status
                    if kind == 'embedding':
                        rows = parsed.get('data', [])
                        item['dimensions'] = len(rows[0].get('embedding', [])) if rows else 0
                        item['validResponse'] = item['dimensions'] > 0
                    else:
                        rows = parsed.get('results', parsed.get('data', []))
                        item['resultCount'] = len(rows)
                        item['validResponse'] = len(rows) > 0
                        item['rankedIndices'] = [row.get('index') for row in rows]
                    item['usage'] = parsed.get('usage')
            except urllib.error.HTTPError as error:
                item['httpStatus'] = error.code
                item['validResponse'] = False
                item['errorCategory'] = 'HTTP_ERROR'
            except Exception as error:
                item['validResponse'] = False
                item['errorCategory'] = type(error).__name__
            item['durationMs'] = round((time.monotonic() - started) * 1000)
            results.append(item)
            print(json.dumps(item, ensure_ascii=False), flush=True)
    Path('docs/rag-b/gitee-rag-model-probes.json').write_text(json.dumps({'checkedAt': datetime.now(timezone.utc).isoformat(), 'credentialSource': 'AI_EMBEDDING_KEY', 'results': results, 'note': 'One synthetic request per model; connectivity only, not a quality or latency benchmark.'}, ensure_ascii=False, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()
