"""Isolated, cached real-provider embedding/chunk experiment; never changes B indexes.

Exact cosine retrieval isolates embedding/chunk quality from ANN, RRF and generation.
Gold questions and evidence are fixed before any provider request. Not a human blind test.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import re
import sys
import time
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path

import numpy as np
import requests
from tokenizers import Tokenizer

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/rag-model-chunk-20261002'
REPORT = ROOT / 'docs/rag-b/model-chunk-experiment'
MODELS = ['bge-small-zh-v1.5','bge-large-zh-v1.5','bge-m3',
          'Qwen3-Embedding-0.6B','Qwen3-Embedding-4B']
TOKEN_FILES = ['bge-small-zh-v1.5','bge-m3','Qwen3-Embedding-0.6B']

def digest(value):
    return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True).encode()).hexdigest()

def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))

def tokenizers():
    return [Tokenizer.from_file(str(STAGE/'tokenizers'/f'{n}.json')) for n in TOKEN_FILES]

def norm(text):
    return re.sub(r'\s+','',text).casefold()

def instructed_query(model,text):
    if model.startswith('Qwen3-'):
        return 'Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery:'+text
    if model in ['bge-small-zh-v1.5','bge-large-zh-v1.5']:
        return '为这个句子生成表示以用于检索相关文章：'+text
    return text

# Each row was authored from the local snapshot, before embedding results were observed.
# English/Chinese pairs share a fact, so uncertainty resampling groups pairs together.
FACTS = [
 ('beginner:55','BEGINNER_PROJECT','私密的桌面工作区','我想找一个私密桌面笔记空间，能编辑 Markdown 并调用 AI，有什么项目？','Which private desktop workspace combines Markdown notes with AI assistance?'),
 ('beginner:77','BEGINNER_PROJECT','玻璃破碎','哪些前端组件能让网页出现碎玻璃和液体效果，同时保持页面可以交互？','Which frontend components add shattered glass and fluid effects while keeping the page interactive?'),
 ('beginner:80','BEGINNER_PROJECT','动态二维码流','有没有不用网络传文件的方法，把发送端屏幕交给另一台设备摄像头读取？','Which project transfers files offline from an animated screen to another device camera?'),
 ('beginner:88','BEGINNER_PROJECT','机器写作痕迹','我需要改善营销标题和介绍文案，让输出不那么像机器写的，应该参考什么？','Which project helps marketing copy sound human rather than machine-generated?'),
 ('beginner:91','BEGINNER_PROJECT','设计奖申报','有没有能评价设计作品、选择合适竞赛并准备申报文案的智能体技能？','Which agent skills assess design work, match suitable competitions, and prepare award submissions?'),
 ('beginner:94','BEGINNER_PROJECT','无需后端','有没有完全在浏览器运行的视频编辑器，支持字幕和聊天式创作？','Which video editor runs entirely in a browser and supports captions plus conversational creation?'),
 ('beginner:101','BEGINNER_PROJECT','无需网格文件','想把参考图片做成可旋转的网页三维模型，不依赖下载网格文件，可以参考什么？','Which tool reconstructs a reference image into a rotatable browser 3D model without mesh assets?'),
 ('beginner:102','BEGINNER_PROJECT','保存在文件自身','有没有把幻灯片编辑、播放和保存都装进一个 HTML 文件的应用？','Which slide editor and presenter stores its document inside a single HTML file?'),
 ('beginner:104','BEGINNER_PROJECT','无需任何外部资源文件','哪个浏览器射击游戏的纹理、模型和音效都由程序生成，不需要外部素材？','Which browser shooter procedurally generates textures, models and sound without external asset files?'),
 ('beginner:106','BEGINNER_PROJECT','手写','想用白板同时手写、输入公式和画图来与 AI 交流，有什么项目？','Which AI whiteboard supports handwriting, formulas, and drawings as interaction inputs?'),
 ('beginner:108','BEGINNER_PROJECT','虚拟模特','有没有从照片提取衣物，并生成虚拟人物穿搭效果的工具？','Which tool extracts garments from photos and renders outfits on a virtual model?'),
 ('beginner:119','BEGINNER_PROJECT','撕拉','想在个人网站添加能被用户撕开的交互贴纸，应参考哪个工具？','Which web tool creates interactive peelable stickers for a personal website?'),
 ('career:249','CAREER_PROJECT','3D地球仪和地图','想展示三维地球和地图可视化能力，应该研究什么 JavaScript 项目？','Which JavaScript project can demonstrate 3D globe and map visualization skills?'),
 ('career:255','CAREER_PROJECT','HTML/XML','我需要在 Node 服务里解析和修改 HTML 或 XML，哪个库采用类似 jQuery 的语法？','Which Node.js library parses and manipulates HTML/XML with a jQuery-like syntax?'),
 ('career:256','CAREER_PROJECT','检测账号共享','想统一监控多个媒体服务器的播放流，并发现共享账号，有什么项目？','Which dashboard tracks media server streams and detects account sharing?'),
 ('career:257','CAREER_PROJECT','C 盘空间不足','想做一个带交互界面的 Windows 清理工具，解决系统盘空间不足，应参考什么？','Which Windows cleanup project offers an interactive interface for freeing system drive space?'),
 ('career:259','CAREER_PROJECT','持久化后台智能体','哪个 AI 代理框架同时接入聊天平台与终端，并提供可插拔后端和持久化后台任务？','Which agent framework spans chat platforms and terminal, with pluggable backends and persistent background agents?'),
 ('career:261','CAREER_PROJECT','编译、测试、部署智能合约','想学习 Rust 工具如何覆盖智能合约编译、测试和部署全流程，有什么项目？','Which Rust toolkit covers smart contract compilation, testing, and deployment?'),
 ('career:262','CAREER_PROJECT','死代码检测','有没有能给代码仓库健康评分、生成文档并检查无用代码的分析工具？','Which repository analyzer scores code health, generates documentation, and detects dead code?'),
 ('career:265','CAREER_PROJECT','递归自我改进','哪个 Python 框架通过反复自我迭代提高智能体在任务上的表现？','Which Python agent framework improves task performance through recursive self-improvement?'),
 ('langchain-ai/langgraph','GITHUB','Durable execution','什么智能体框架能在运行故障后恢复并继续长期任务？','Which agent framework resumes long-running execution after failures?'),
 ('langchain-ai/langgraph','GITHUB','Human-in-the-loop','什么框架允许人在执行中检查或修改智能体状态？','Which framework lets humans inspect and modify agent state during execution?'),
 ('raphaelsalaja/audio','GITHUB','plain objects','有没有用普通对象描述声音、一次函数调用就能播放的网页音频库？','Which web audio library defines sounds as plain objects and plays them with one call?'),
 ('freeCodeCamp/freeCodeCamp','GITHUB','100,000','哪个免费编程社区已经帮助超过十万人获得第一份开发工作？','Which free coding community has helped over one hundred thousand people get their first developer job?'),
 ('crankyoldgit/IRremoteESP8266','GITHUB','940nm','想在 ESP32 上通过常见红外 LED 和接收模块收发信号，可以参考什么库？','Which library transmits and receives infrared on ESP32 using common LEDs and receiver modules?'),
 ('lvgl/lvgl','GITHUB','100kB RAM','哪种嵌入式图形界面库的典型界面只需要约一百 KB 内存？','Which embedded graphics library needs roughly one hundred kilobytes of RAM for a typical UI?'),
 ('HKUDS/nanobot','GITHUB','long-term memory','有没有能自托管、连接多种聊天应用并保留长期记忆的轻量个人 AI 助手？','Which lightweight self-hosted personal AI assistant connects chat apps and retains long-term memory?'),
 ('nolangz/pixel2motion','GITHUB','PNG, JPG, WebP','怎样把位图标志重建成 SVG，再生成网页动画和 GIF 预览？','Which workflow reconstructs raster logos into SVG and exports animated HTML plus GIF previews?'),
 ('DavidHDev/canvas-ui','GITHUB','Text stays selectable','哪个视觉组件库会扭曲网页画面，但仍让文字可以选中、链接可以点击？','Which visual library distorts live page imagery while keeping text selectable and links clickable?'),
 ('GraphiteEditor/Graphite','GITHUB','fully nondestructive','想把图层合成和节点生成设计结合起来，并保持无损编辑，应该参考什么图形工具？','Which graphics tool combines layer compositing and node-based generative design with nondestructive editing?'),
]

def dataset(corpus):
    docs = corpus['documents']
    old = load_json(ROOT/'orange-server/src/main/resources/rag-b/evaluation-benchmark-v1.json')
    cases = []
    for c in old['cases']:
        if len(c.get('expectedSourceRefs',[]))==1 and c.get('expectedAnswerPolicy')!='REFUSE_NO_MATCH':
            cases.append({'key':c['key'],'question':c['question'],'category':'named_regression',
                          'types':c.get('expectedSourceTypes',[]),'refs':c['expectedSourceRefs'],
                          'group':c['key'],'anchors':[]})
    for i,(ref,source,anchor,zh,en) in enumerate(FACTS):
        doc = next(d for d in docs if d['sourceRef']==ref and d['sourceType']==source)
        if norm(anchor) not in norm(doc['content']):
            raise ValueError(f'Gold anchor absent: {ref} / {anchor}')
        for language,question in [('zh',zh),('en',en)]:
            cases.append({'key':f'fact-{i:02}-{language}','question':question,
                'category':('readme_cross_language' if source=='GITHUB' and language=='zh'
                            else 'readme_english' if source=='GITHUB'
                            else 'business_zh' if language=='zh' else 'business_cross_language'),
                'types':[source],'refs':[ref],'group':f'fact-{i:02}',
                'anchors':[anchor]})
    return cases

def sections(text):
    """ATX/Setext boundaries outside fenced code; retain every original character."""
    lines = text.splitlines(keepends=True)
    offsets=[]; at=0
    for line in lines: offsets.append(at);at+=len(line)
    starts=[(0,'')];path=['']*6;fence=None
    for i,line in enumerate(lines):
        s=line.rstrip('\r\n')
        f=re.match(r'^\s{0,3}(`{3,}|~{3,})',s)
        if f:
            mark=f.group(1)
            if fence is None:fence=mark
            elif mark[0]==fence[0] and len(mark)>=len(fence):fence=None
            continue
        if fence is not None:continue
        h=re.match(r'^\s{0,3}(#{1,6})\s+(.+?)\s*#*$',s)
        start=offsets[i]
        if h: level=len(h.group(1));title=h.group(2)
        elif i>0 and re.match(r'^\s{0,3}(=+|-+)\s*$',s) and lines[i-1].strip() and not re.match(r'^\s*[-*+]',lines[i-1]):
            level=1 if s.strip()[0]=='=' else 2;title=lines[i-1].strip();start=offsets[i-1]
        else:continue
        path[level-1]=title
        for j in range(level,6):path[j]=''
        label=' > '.join(x for x in path if x)
        if start==starts[-1][0]:starts[-1]=(start,label)
        elif start>starts[-1][0]:starts.append((start,label))
    return [(a,starts[i+1][0] if i+1<len(starts) else len(text),label)
            for i,(a,label) in enumerate(starts)]

def blocks(text,start,end):
    """Paragraphs and fenced code blocks; contiguous raw spans, no HTML cleaning."""
    result=[];block_start=start;at=start;fence=None
    for line in text[start:end].splitlines(keepends=True):
        f=re.match(r'^\s{0,3}(`{3,}|~{3,})',line)
        if f:
            mark=f.group(1)
            if fence is None:
                if at>block_start:result.append((block_start,at))
                block_start=at;fence=mark
            elif mark[0]==fence[0] and len(mark)>=len(fence):
                result.append((block_start,at+len(line)));block_start=at+len(line);fence=None
        elif fence is None and not line.strip():
            if at+len(line)>block_start:result.append((block_start,at+len(line)))
            block_start=at+len(line)
        at+=len(line)
    if block_start<end:result.append((block_start,end))
    return result

def split_document(doc,ts,target,cap,overlap):
    text=doc['content'];chunks=[]
    @lru_cache(maxsize=4096)
    def length(a,b):return max(len(t.encode(text[a:b],add_special_tokens=False).ids) for t in ts)
    for a,b,label in sections(text):
        units=blocks(text,a,b);boundaries={x for u in units for x in u}
        # Split oversized atomic blocks with sentence / line boundaries before hard cuts.
        for u,v in units:
            if length(u,v)>cap:
                for m in re.finditer(r'[。！？.!?](?:\s+|(?=[^\x00-\x7f]))|\n',text[u:v]):boundaries.add(u+m.end())
        ordered=sorted(boundaries);pos=a
        while pos<b:
            if length(pos,b)<=target:end=b
            else:
                lo=pos+1;hi=b
                while lo<hi:
                    mid=(lo+hi+1)//2
                    if length(pos,mid)<=target:lo=mid
                    else:hi=mid-1
                natural=[x for x in ordered if pos<x<=lo]
                end=natural[-1] if natural else lo
                # Preserve the next complete paragraph/code/table if within the cap.
                following=next((x for x in ordered if x>lo),None)
                if following and length(pos,following)<=cap:end=following
            if end<=pos:raise ValueError('No chunk progress')
            chunks.append({'documentId':doc['id'],'sourceRef':doc['sourceRef'],
                'sourceType':doc['sourceType'],'title':doc['title'],'section':label,
                'start':pos,'end':end,'content':text[pos:end]})
            if end==b:break
            next_pos=end
            # Overlap only complete trailing paragraphs/sentences, without forcing a partial sentence.
            options=[x for x in ordered if pos<x<end and length(x,end)<=overlap]
            if options:next_pos=options[0]
            pos=next_pos
    # Verify complete coverage of nonempty source, and enforce actual token cap.
    covered=0
    for c in chunks:
        if c['start']>covered or c['end']<=c['start']:raise ValueError('Coverage gap')
        covered=max(covered,c['end'])
        if length(c['start'],c['end'])>cap:raise ValueError('Token cap exceeded')
    if covered!=len(text):raise ValueError('Incomplete coverage')
    return chunks

def build(corpus):
    ts=tokenizers();docs=corpus['documents'];lookup={d['id']:d for d in docs}
    common=[c for d in docs for c in split_document(d,ts,256,384,32)]
    plans=[]
    old=[]
    for c in corpus['chunks']:
        d=lookup[c['documentId']];old.append({**c,'sourceRef':d['sourceRef'],'sourceType':d['sourceType'],'title':d['title']})
    plans.append(('current-small-char500',MODELS[0],old,'existing stored chunks, raw text'))
    for model in MODELS:
        plans.append((model+'--common256',model,common,'identical boundaries, max across all three tokenizers'))
    for model,idx in [('bge-m3',1),('Qwen3-Embedding-0.6B',2),('Qwen3-Embedding-4B',2)]:
        chunks=[c for d in docs for c in split_document(d,[ts[idx]],512,768,64)]
        plans.append((model+'--native512',model,chunks,'model tokenizer, target512 cap768 overlap<=64'))
    chunks=[c for d in docs for c in split_document(d,[ts[2]],768,1024,64)]
    plans.append(('Qwen3-Embedding-4B--native768',MODELS[4],chunks,'Qwen tokenizer, target768 cap1024 overlap<=64'))
    return plans

class Provider:
    def __init__(self):
        env={}
        for line in (ROOT/'.env.local').read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                k,v=line.split('=',1);env[k.strip()]=v.strip().strip('\"').strip("'")
        endpoint=env.get('AI_EMBEDDING_ENDPOINT') or 'https://ai.gitee.com/v1'
        if endpoint.rstrip('/')!='https://ai.gitee.com/v1':raise ValueError('Unexpected provider endpoint')
        self.key=env['AI_EMBEDDING_KEY'];self.url=endpoint.rstrip('/')+'/embeddings'
        self.session=requests.Session();self.calls=0
        (STAGE/'vectors').mkdir(exist_ok=True)

    def embed(self,model,texts,purpose):
        arrays=[]
        for begin in range(0,len(texts),16):
            batch=texts[begin:begin+16];hash_=digest({'model':model,'input':batch,'encodingVersion':1})
            cache=STAGE/'vectors'/f'{hash_}.npy'
            if cache.exists():arrays.append(np.load(cache));continue
            for attempt in range(3):
                started=time.perf_counter();status=None
                try:
                    r=self.session.post(self.url,headers={'Authorization':'Bearer '+self.key},
                        json={'model':model,'input':batch},timeout=120)
                    status=r.status_code
                    if status!=200:raise RuntimeError(f'HTTP_{status}')
                    data=r.json();rows=sorted(data['data'],key=lambda row:row['index'])
                    if [row['index'] for row in rows]!=list(range(len(batch))):raise ValueError('Invalid row indices')
                    arr=np.asarray([row['embedding'] for row in rows],dtype=np.float32)
                    if arr.ndim!=2 or not np.isfinite(arr).all() or np.any(np.linalg.norm(arr,axis=1)==0):raise ValueError('Invalid vectors')
                    event={'at':datetime.now(timezone.utc).isoformat(),'model':model,'purpose':purpose,
                        'items':len(batch),'inputCharacters':sum(map(len,batch)),
                        'requestHash':hash_,'durationMs':round((time.perf_counter()-started)*1000,2),
                        'status':status,'dimensions':arr.shape[1],'usage':data.get('usage'),'attempt':attempt+1}
                    with (STAGE/'calls.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
                    self.calls+=1;np.save(cache,arr);arrays.append(arr);break
                except Exception as e:
                    with (STAGE/'errors.jsonl').open('a',encoding='utf-8') as f:
                        f.write(json.dumps({'model':model,'purpose':purpose,'status':status,'errorType':type(e).__name__,
                            'attempt':attempt+1,'requestHash':hash_})+'\n')
                    if status is not None and status not in [429,500,502,503,504]:raise RuntimeError(f'{model} rejected batch: HTTP {status}') from None
                    if attempt==2:raise RuntimeError(f'{model}: request failed after 3 attempts') from None
                    time.sleep(2**(attempt+1))
            if (begin//16)%10==0:print(f'EMBED {model} {purpose} {min(begin+16,len(texts))}/{len(texts)}',flush=True)
        return np.concatenate(arrays,axis=0)

def evaluate(chunks,vectors,cases,queries):
    v=vectors/np.linalg.norm(vectors,axis=1,keepdims=True)
    q=queries/np.linalg.norm(queries,axis=1,keepdims=True)
    started=time.perf_counter();scores=q@v.T;compute_ms=(time.perf_counter()-started)*1000
    rows=[]
    for i,c in enumerate(cases):
        order=np.argsort(-scores[i],kind='stable')
        order=[int(j) for j in order if not c['types'] or chunks[j]['sourceType'] in c['types']]
        hits=[];source_rank=0;evidence_rank=0;relevant_count=0;dcg=0.0
        for rank,j in enumerate(order,1):
            ch=chunks[j];correct=ch['sourceRef'] in c['refs']
            evidence=correct and (not c['anchors'] or any(norm(a) in norm(ch['content']) for a in c['anchors']))
            if evidence:
                relevant_count+=1
                if rank<=8:dcg+=1/math.log2(rank+1)
            if correct and not source_rank:source_rank=rank
            if evidence and not evidence_rank:evidence_rank=rank
            if rank<=8:hits.append({'rank':rank,'score':float(scores[i,j]),'sourceRef':ch['sourceRef'],
                'documentId':ch['documentId'],'section':ch.get('section'),'start':ch['start'],'end':ch['end'],
                'correctSource':correct,'goldEvidence':evidence})
        ideal=sum(1/math.log2(r+1) for r in range(1,min(relevant_count,8)+1))
        rows.append({'key':c['key'],'group':c['group'],'category':c['category'],'sourceRank':source_rank,
                     'evidenceRank':evidence_rank,'ndcg8':dcg/ideal if ideal else 0,'hits':hits})
    def summarize(group):
        n=len(group)
        return {'n':n,'sourceRecall8':sum(0<r['sourceRank']<=8 for r in group)/n,
            'evidenceRecall8':sum(0<r['evidenceRank']<=8 for r in group)/n,
            'evidenceMRR8':sum(1/r['evidenceRank'] if 0<r['evidenceRank']<=8 else 0 for r in group)/n,
            'evidenceNDCG8':sum(r['ndcg8'] for r in group)/n}
    categories={cat:summarize([r for r in rows if r['category']==cat]) for cat in sorted({r['category'] for r in rows})}
    return {'overall':summarize(rows),'semantic':summarize([r for r in rows if r['category']!='named_regression']),
            'categories':categories,'rows':rows,'localMatrixMs':compute_ms}

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('--prepare',action='store_true')
    parser.add_argument('--plan',default='all');args=parser.parse_args()
    corpus=load_json(STAGE/'corpus.json');cases=dataset(corpus);plans=build(corpus)
    write_json(REPORT/'dataset.json',{'version':'model-chunk-v1','authorship':'code-reading assistant; not independent human blind gold',
        'cases':cases,'limitations':['source-scoped retrieval','one anchor per fact, exact normalized containment',
        'paired questions not independent','no business rules, RRF, rerank, or generation']})
    summary={'corpusHash':digest(corpus),'datasetHash':digest(cases),'documents':len(corpus['documents']),
        'cases':len(cases),'semanticCases':sum(c['category']!='named_regression' for c in cases),
        'plans':[{'name':name,'model':model,'chunks':len(chunks),'description':desc} for name,model,chunks,desc in plans]}
    write_json(REPORT/'manifest.json',summary)
    print(json.dumps(summary,ensure_ascii=False),flush=True)
    if args.prepare:return
    provider=Provider()
    for name,model,chunks,description in plans:
        if args.plan!='all' and args.plan not in name:continue
        output=REPORT/f'{name}.json'
        if output.exists() and load_json(output).get('experimentHash')==digest([summary['corpusHash'],cases,chunks,model]):
            print('CACHED_RESULT '+name,flush=True);continue
        print('START_PLAN '+name,flush=True)
        vectors=provider.embed(model,[c['content'] for c in chunks],'documents')
        queries=provider.embed(model,[c['question'] for c in cases],'queries')
        result=evaluate(chunks,vectors,cases,queries)
        result.update({'plan':name,'model':model,'chunks':len(chunks),'dimensions':vectors.shape[1],
            'vectorBytes':vectors.nbytes,'description':description,
            'experimentHash':digest([summary['corpusHash'],cases,chunks,model]),
            'at':datetime.now(timezone.utc).isoformat()})
        write_json(output,result)
        print('RESULT '+json.dumps({k:result[k] for k in ['plan','chunks','semantic','overall']},ensure_ascii=False),flush=True)
    print('DONE',flush=True)

if __name__=='__main__':main()
