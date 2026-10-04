"""Isolated Redis Stack experiment; no platform index or model configuration changes.

Replays the B repository's RRF weights and source diversity, but does not claim
to execute the Java service's business guards, preferences or citation validator.
"""
import json
import argparse
import re
import socket
import sys
import time
from datetime import datetime, timezone

import numpy as np

from run_rag_model_chunk_experiment import (
    ROOT, STAGE, REPORT, Provider, build, dataset, digest, load_json, norm, write_json,
)

OUT = REPORT / 'hybrid-translation'
FRESH = [
    '我想在自己的电脑上写 Markdown 笔记，资料要私密，还想在里面问 AI，有现成项目吗？',
    '网页要有液体流动或玻璃碎裂的特效，但按钮仍然能操作，找哪个组件？',
    '两台设备都没联网，能不能让摄像头拍另一块屏幕来传文件？',
    'AI 写的产品介绍太生硬了，有没有能改得像真人文案的工具？',
    '我做好了设计作品，想挑比赛并写报名材料，哪种智能体技能能帮忙？',
    '我想不部署服务器就剪视频，加字幕，还能用聊天来编辑，有什么项目？',
    '拿一张图就能在网页里转着看三维效果，不想下载模型文件，用什么？',
    '有没有一个 HTML 文件就能编辑、存储并放映整套幻灯片的项目？',
    '做浏览器里的射击游戏，不准备贴图、音效或模型素材，哪个项目可以参考？',
    '我想边手写边画图和列公式给 AI 看，有没有这样的白板？',
    '从衣服照片里抠出服装，再试穿到虚拟人物身上，找什么工具？',
    '个人主页上想放一张访客可以撕开的贴纸，哪个库能做？',
    '我想做地球仪和地图的三维展示，有哪些 JS 项目值得研究？',
    'Node 后端要处理 HTML 和 XML，希望像 jQuery 一样操作，哪个库合适？',
    '几个媒体服务器要放到一起监控，还要看出有人共享账号，用什么项目？',
    'Windows 的系统盘满了，我想找带操作界面的清理程序参考，选哪个？',
    '想找既能在终端也能在聊天软件里运行、支持换后端并持续执行后台任务的代理框架。',
    '用 Rust 开发智能合约，从编译一直到测试和部署，有什么完整工具链？',
    '有没有工具能检查仓库质量、找没用的代码，还能自动写文档？',
    '找一个 Python 智能体框架，让代理通过一轮轮自我改进提高任务表现。',
    '代理任务跑很久，中间崩溃后希望接着上次继续，哪个框架支持？',
    'AI 代理正在跑，我希望人能进去看状态，必要时直接改状态，找哪个框架？',
    '网页声音想直接用 JS 对象来配置，然后一个调用就播放，有哪个音频库？',
    '找一个免费学编程的社区，已经帮助十万多人找到第一份开发工作。',
    'ESP32 想接普通红外灯和接收头来收发红外，有什么库？',
    '设备内存很小，图形界面大约只用 100 KB RAM，有哪些库符合？',
    '我想自己部署一个小型 AI 助手，能接聊天软件，也能记住长期信息，找哪个？',
    '我有位图 logo，想变成 SVG 并输出 HTML 动画和 GIF，什么流程可以做？',
    '网页做视觉扭曲特效以后，文字还要能选、链接还要能点，哪个组件库支持？',
    '想把图层和节点式生成设计放在一个绘图工具里，而且修改不能破坏原图，用什么？',
]


class Redis:
    """Small RESP client; bound only to the existing local experimental Redis."""
    def __init__(self):
        self.sock = socket.create_connection(('127.0.0.1', 6380), timeout=30)
        self.file = self.sock.makefile('rb')

    def command(self, *args):
        parts = [str(len(args)).encode()]
        wire = b'*' + parts[0] + b'\r\n'
        for arg in args:
            b = arg if isinstance(arg, bytes) else str(arg).encode('utf-8')
            wire += b'$' + str(len(b)).encode() + b'\r\n' + b + b'\r\n'
        self.sock.sendall(wire)
        return self.read()

    def read(self):
        marker = self.file.read(1)
        line = self.file.readline().rstrip(b'\r\n')
        if marker == b'-':
            raise RuntimeError(line.decode('utf-8', 'replace'))
        if marker == b'+': return line.decode()
        if marker == b':': return int(line)
        if marker == b'$':
            n = int(line)
            if n < 0: return None
            data = self.file.read(n)
            if self.file.read(2) != b'\r\n': raise ValueError('RESP terminator')
            return data
        if marker == b'*': return [self.read() for _ in range(int(line))]
        raise ValueError('Invalid RESP reply')


def create_index(redis, chunks, vectors, label):
    signature = digest([chunks, label, 'redis-hybrid-v1'])[:12]
    index = 'rag-exp-m3-' + signature
    prefix = index + ':'
    if index in redis.command('FT._LIST'):
        info = redis.command('FT.INFO', index)
        details = dict(zip(info[::2], info[1::2]))
        if int(details['num_docs']) != len(chunks): raise RuntimeError('Cached index count mismatch')
        print('CACHED_INDEX ' + index, flush=True)
        return index, prefix
    redis.command('FT.CREATE', index, 'ON', 'HASH', 'PREFIX', 1, prefix, 'SCHEMA',
        'content', 'TEXT', 'title', 'TEXT', 'sourceRef', 'TEXT', 'sourceType', 'TAG',
        'vector', 'VECTOR', 'HNSW', 10, 'TYPE', 'FLOAT32', 'DIM', vectors.shape[1],
        'DISTANCE_METRIC', 'COSINE', 'M', 16, 'EF_CONSTRUCTION', 200)
    for i, (chunk, vector) in enumerate(zip(chunks, vectors)):
        redis.command('HSET', prefix + str(i), 'content', chunk['content'], 'title', chunk['title'],
            'sourceRef', chunk['sourceRef'], 'sourceType', chunk['sourceType'],
            'vector', np.asarray(vector, dtype='<f4').tobytes())
    for _ in range(100):
        info = redis.command('FT.INFO', index)
        details = dict(zip(info[::2], info[1::2]))
        if int(details['num_docs']) == len(chunks) and float(details.get('indexing', 0)) == 0: break
        time.sleep(.1)
    else: raise RuntimeError('Index not visible')
    print('INDEX_READY ' + index + ' ' + str(len(chunks)), flush=True)
    return index, prefix


def vector_ranks(redis, index, prefix, vector, types, threshold, ef=10):
    source = '@sourceType:{' + '|'.join(types) + '}' if types else '*'
    result = redis.command('FT.SEARCH', index, '(' + source + ')=>[KNN 128 @vector $v EF_RUNTIME '+str(ef)+' AS distance]',
        'PARAMS', 2, 'v', np.asarray(vector, dtype='<f4').tobytes(), 'SORTBY', 'distance',
        'LIMIT', 0, 128, 'RETURN', 1, 'distance', 'DIALECT', 2)
    rows = []
    for i in range(1, len(result), 2):
        values = dict(zip(result[i+1][::2], result[i+1][1::2]))
        if 1-float(values.get(b'distance', values.get('distance'))) >= threshold:
            rows.append(int(result[i].decode()[len(prefix):]))
    return rows


def lexical_ranks(redis, index, prefix, query, types, fields):
    terms = list(dict.fromkeys(re.sub(r'[^\w-]+', ' ', query, flags=re.UNICODE).strip().split()))[:12]
    if not terms: return []
    alternatives = ' | '.join(term.replace('\\', '\\\\').replace('-', '\\-') for term in terms)
    expression = '(' + ' | '.join('@' + field + ':(' + alternatives + ')' for field in fields) + ')'
    if types: expression = '@sourceType:{' + '|'.join(types) + '} ' + expression
    result = redis.command('FT.SEARCH', index, expression, 'NOCONTENT', 'LIMIT', 0, 128, 'DIALECT', 2)
    return [int(x.decode()[len(prefix):]) for x in result[1:]]


def exact_name(query, chunk):
    normalize = lambda s: ''.join(c for c in s.lower() if c.isalnum())
    for value in [chunk['title'], chunk['sourceRef']]:
        name = normalize(re.split(r'[/\\]', value)[-1])
        if len(name) >= 4 and name in normalize(query): return True
    return False


def rank(redis, index, prefix, chunks, case, vector, translated_vector, hybrid, diverse, threshold,
         lexical_weight=1.15, name_weight=3, gate_names=False, ef=10, raw_weight=.5):
    vector_list = vector_ranks(redis, index, prefix, vector, case['types'], threshold, ef)
    if not hybrid and translated_vector is None: return vector_list[:8], {'vector': len(vector_list)}
    channels = [(vector_list, raw_weight if translated_vector is not None else 1.0)]
    if translated_vector is not None:
        channels.append((vector_ranks(redis, index, prefix, translated_vector, case['types'], threshold, ef), 1-raw_weight))
    trace = {'vector': len(vector_list)}
    if hybrid:
        lexical = lexical_ranks(redis, index, prefix, case['question'], case['types'], ['content'])
        names = lexical_ranks(redis, index, prefix, case['question'], case['types'], ['title', 'sourceRef'])
        if gate_names and not any(exact_name(case['question'], ch) for ch in chunks): names = []
        channels.extend([(lexical, lexical_weight), (names, name_weight)])
        trace.update(lexical=len(lexical), name=len(names))
    scores = {}
    for ids, weight in channels:
        for r, i in enumerate(ids, 1): scores[i] = scores.get(i, 0) + weight/(60+r)
    if hybrid:
        for i in scores:
            if exact_name(case['question'], chunks[i]): scores[i] += .1
    ordered = sorted(scores, key=lambda i: -scores[i])[:32]
    if diverse:
        selected = []; represented = set()
        for i in ordered:
            source = (chunks[i]['sourceType'], chunks[i]['sourceRef'])
            if source not in represented: selected.append(i); represented.add(source)
            if len(selected) == 8: break
        if len(selected) < 8: selected.extend(i for i in ordered if i not in selected)
        ordered = selected
    return ordered[:8], trace


def summarize(rows):
    return {'n': len(rows), 'evidenceRecall8': sum(r['rank'] > 0 for r in rows)/len(rows),
        'evidenceMRR8': sum(1/r['rank'] if r['rank'] else 0 for r in rows)/len(rows),
        'top1': sum(r['rank'] == 1 for r in rows)/len(rows),
        # Includes Python ranking and socket round trips, not Redis server time alone.
        'localRetrievalP95Ms': float(np.percentile([r['redisMs'] for r in rows], 95))}


def ablate():
    corpus = load_json(STAGE/'corpus.json')
    cases = load_json(OUT/'dataset.json')['cases']; original = cases[:110]; fresh = cases[110:]
    trans = load_json(OUT/'paired-english-queries.json')['queries']
    _, model, chunks, _ = next(p for p in build(corpus) if p[0] == 'bge-m3--native512')
    provider = Provider(); redis = Redis()
    vectors = provider.embed(model, [c['content'] for c in chunks], 'documents')
    q = np.concatenate([provider.embed(model, [c['question'] for c in original], 'queries'),
        provider.embed(model, [c['question'] for c in fresh], 'fresh-queries')])
    tq = provider.embed(model, [trans.get(c['key'], c['question']) for c in cases], 'translated-queries')
    index, prefix = create_index(redis, chunks, vectors, 'bge-m3--native512')
    cosine = (q/np.linalg.norm(q,axis=1,keepdims=True)) @ (vectors/np.linalg.norm(vectors,axis=1,keepdims=True)).T
    configs = [('exact', False, False, False, 0, False, 0),
        ('ann_ef64', False, False, False, 0, False, 64),
        ('ann_ef128', False, False, False, 0, False, 128),
        ('name_gated_lex115', True, False, False, 1.15, True, 64),
        ('name_gated_lex020', True, False, False, .2, True, 64),
        ('name_gated_lex020_diverse', True, True, False, .2, True, 64),
        ('name_gated_lex020_dual', True, False, True, .2, True, 64)]
    results = []
    for mode, hybrid, diverse, dual, weight, gated, ef in configs:
        rows = []
        for k, case in enumerate(cases):
            started = time.perf_counter()
            exact = [int(i) for i in np.argsort(-cosine[k],kind='stable')
                if not case['types'] or chunks[i]['sourceType'] in case['types']][:8]
            if mode == 'exact': ids, trace = exact, {}
            else: ids, trace = rank(redis,index,prefix,chunks,case,q[k],
                tq[k] if dual and case['key'] in trans else None,hybrid,diverse,0,weight,3,gated,ef)
            hits = []
            for r, i in enumerate(ids,1):
                ch = chunks[i]; gold = ch['sourceRef'] in case['refs'] and (
                    not case['anchors'] or any(norm(a) in norm(ch['content']) for a in case['anchors']))
                hits.append({'rank':r,'chunk':i,'sourceRef':ch['sourceRef'],'section':ch.get('section'),
                    'gold':gold,'start':ch['start'],'end':ch['end']})
            rows.append({'key':case['key'],'category':case['category'],
                'rank':next((h['rank'] for h in hits if h['gold']),0),'hits':hits,'trace':trace,
                'exactTop8Overlap':len(set(ids)&set(exact))/8,
                'redisMs':(time.perf_counter()-started)*1000})
        result = {'plan':'bge-m3--native512','mode':mode,
            'categories':{cat:summarize([r for r in rows if r['category']==cat])
                for cat in sorted({r['category'] for r in rows})},
            'semantic':summarize([r for r in rows if r['category']!='named_regression']),
            'meanExactTop8Overlap':float(np.mean([r['exactTop8Overlap'] for r in rows])), 'rows':rows}
        results.append(result); write_json(OUT/('ablation--'+mode+'.json'),result)
        print('ABLATION '+mode+' '+json.dumps(result['semantic'])+' overlap '+str(result['meanExactTop8Overlap']),flush=True)
    write_json(OUT/'ablation-summary.json',{'results':[{k:v for k,v in r.items() if k!='rows'} for r in results],
        'limitations':['Weights explored on this dataset; require independent validation before deployment.',
            'No real translation API, business guards, generation, or Java citation validation executed.']})


def main():
    sys.stdout.reconfigure(encoding='utf-8'); OUT.mkdir(parents=True, exist_ok=True)
    parser = argparse.ArgumentParser(); parser.add_argument('--ablate',action='store_true')
    if parser.parse_args().ablate: ablate(); return
    corpus = load_json(STAGE/'corpus.json'); original = dataset(corpus)
    fresh = []
    for i, question in enumerate(FRESH):
        base = next(c for c in original if c['key'] == f'fact-{i:02}-zh')
        fresh.append({**base, 'key': f'fresh-{i:02}', 'question': question,
                      'category': 'fresh_readme_zh' if i >= 20 else 'fresh_business_zh'})
    cases = original + fresh
    write_json(OUT/'dataset.json', {'cases': cases, 'limitations': [
        'Fresh wording only: shares the original 30 facts and gold anchors; not independent human gold.',
        'Authored before this experiment; model selection already observed the original fact set.']})
    # No translation API calls: paired English questions were already authored
    # and embedded in the previous authorized experiment. This is an optimistic
    # second-query ablation, not an evaluation of automatic translation quality.
    trans = {}
    for i in range(30):
        english = next(c['question'] for c in original if c['key'] == f'fact-{i:02}-en')
        trans[f'fact-{i:02}-zh'] = english
        trans[f'fresh-{i:02}'] = english
    write_json(OUT/'paired-english-queries.json', {'queries': trans,
        'kind': 'preauthored paired English queries; not automatic translations'})
    provider = Provider(); redis = Redis(); plans = build(corpus); results = []
    for label in ['current-small-char500', 'bge-m3--native512']:
        _, model, chunks, _ = next(p for p in plans if p[0] == label)
        vectors = provider.embed(model, [c['content'] for c in chunks], 'documents')
        q = np.concatenate([provider.embed(model, [c['question'] for c in original], 'queries'),
            provider.embed(model, [c['question'] for c in fresh], 'fresh-queries')])
        tq = provider.embed(model, [trans.get(c['key'], c['question']) for c in cases], 'translated-queries')
        index, prefix = create_index(redis, chunks, vectors, label)
        for mode, hybrid, diverse, dual, threshold in [
            ('ann_raw', False, False, False, 0),
            ('ann_dual', False, False, True, 0),
            ('hybrid_raw', True, False, False, 0),
            ('hybrid_diverse_raw', True, True, False, 0),
            ('hybrid_diverse_dual', True, True, True, 0),
            ('hybrid_diverse_raw_t055', True, True, False, .55),
            ('hybrid_diverse_dual_t055', True, True, True, .55),
        ]:
            rows = []
            for k, case in enumerate(cases):
                started = time.perf_counter()
                ids, trace = rank(redis, index, prefix, chunks, case, q[k],
                    tq[k] if dual and case['key'] in trans else None, hybrid, diverse, threshold)
                hits = []
                for r, i in enumerate(ids, 1):
                    ch = chunks[i]; gold = ch['sourceRef'] in case['refs'] and (
                        not case['anchors'] or any(norm(a) in norm(ch['content']) for a in case['anchors']))
                    hits.append({'rank': r, 'chunk': i, 'sourceRef': ch['sourceRef'], 'section': ch.get('section'),
                        'gold': gold, 'start': ch['start'], 'end': ch['end']})
                rows.append({'key': case['key'], 'category': case['category'],
                    'rank': next((h['rank'] for h in hits if h['gold']), 0), 'hits': hits, 'trace': trace,
                    'redisMs': (time.perf_counter()-started)*1000})
            result = {'plan': label, 'mode': mode, 'index': index, 'dimensions': vectors.shape[1],
                'categories': {cat: summarize([r for r in rows if r['category'] == cat])
                    for cat in sorted({r['category'] for r in rows})},
                'semantic': summarize([r for r in rows if r['category'] != 'named_regression']), 'rows': rows}
            results.append(result); write_json(OUT/(label+'--'+mode+'.json'), result)
            print('RESULT '+label+' '+mode+' '+json.dumps(result['semantic']), flush=True)
    write_json(OUT/'summary.json', {'at': datetime.now(timezone.utc).isoformat(),
        'corpusHash': digest(corpus), 'caseHash': digest(cases), 'cases': len(cases),
        'translationMode': 'preauthored paired English; optimistic, not blind automatic translation',
        'redis': {'host': '127.0.0.1:6380', 'algorithm': 'HNSW', 'M': 16,
                  'efConstruction': 200, 'efRuntime': 10, 'distance': 'COSINE', 'lexicalLanguage': 'default English'},
        'rrf': {'k': 60, 'vector': 1, 'lexical': 1.15, 'name': 3, 'nameBonus': .1,
                'dualVector': [.5, .5]}, 'results': [{k:v for k,v in r.items() if k != 'rows'} for r in results]})
    print('DONE '+str(OUT), flush=True)


if __name__ == '__main__': main()
