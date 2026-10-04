"""Freeze comparable A/B contexts locally; no answer/judge LLM calls."""
import json
from datetime import datetime, timezone
from pathlib import Path
from run_rag_b_first_round import call
from export_rag_model_corpus import query

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'docs/rag-b/ragas-ab'
REFERENCES = {
    'fact-00-zh':'SidhuK/Glyph 是私密桌面工作区，可记录笔记、文档和创意，内置 Markdown 编辑与 AI 工具。',
    'fact-02-zh':'bashalarmistalt/decimen-optical-transfer 将文件编码为屏幕动态二维码流，由接收设备摄像头扫描解码，实现无网络文件传输。',
    'fact-03-zh':'mikiarlo3/ai-copywriter 是便携式 AI 技能文件，可生成营销标题与简介，减少机器写作痕迹。',
    'fact-04-zh':'SeanJ1ang/design-judge-skills 是设计奖申报技能集，可评价设计、匹配竞赛并准备申报文案。',
    'fact-07-zh':'nyblnet/bento 是单文件 HTML 应用，包含幻灯片编辑、查看与演示功能，数据保存在文件自身。',
    'fact-12-zh':'CesiumGS/cesium 是用于创建三维地球仪和地图的开源 JavaScript 库，可作为三维地理可视化项目参考。',
    'fact-16-zh':'dylanneve1/talon 支持 Telegram、Discord、Teams 和终端，具有可插拔后端和持久化后台智能体功能；资料未保证所有后台任务都持久化。',
    'fact-18-zh':'repowise-dev/repowise 提供代码仓库健康评分、自动文档生成和死代码检测。',
    'fact-20-zh':'langchain-ai/langgraph 的 Durable execution 支持智能体在故障后从中断位置恢复，并持续执行长期任务。',
    'fact-21-zh':'langchain-ai/langgraph 提供 Human-in-the-loop，可在执行中检查和修改智能体状态。',
    'fact-24-zh':'crankyoldgit/IRremoteESP8266 可在 Arduino 框架下使用 ESP8266 或 ESP32 配合常见 940nm 红外 LED 与接收模块发送和接收红外信号。',
    'fact-25-zh':'lvgl/lvgl 是嵌入式图形界面库；README 表述典型 UI 需要约 100kB RAM，该数字不是所有界面的固定内存要求。',
    'fact-26-zh':'HKUDS/nanobot 是轻量、开源、可自托管的 Python 个人 AI 智能体框架，支持多种聊天应用与长期记忆。',
    'fact-28-zh':'DavidHDev/canvas-ui 利用 HTML-in-canvas 让 WebGL 读取并重绘实时 DOM；画面可被效果扭曲，同时文字仍可选中、链接仍可点击。',
    'fact-29-zh':'GraphiteEditor/Graphite 将基于图层的合成与基于节点的生成设计结合，支持完全无损编辑工作流。',
}
REFERENCES['fresh-02']=REFERENCES['fact-02-zh']
REFERENCES['fresh-03']=REFERENCES['fact-03-zh']
REFERENCES['fresh-21']=REFERENCES['fact-21-zh']

def save(name, data):
    (OUT/name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    questions = json.loads((ROOT/'docs/rag-b/full-original-validation/fixed-answers.json').read_text(encoding='utf-8'))['rows']
    documents = query("SELECT JSON_OBJECT('id',d.id,'sourceRef',d.source_ref,'title',d.title,'content',v.original_content) "
                      "FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id WHERE d.status='READY'")
    snapshot_path = OUT/'retrieval-snapshot.json'
    snapshots = json.loads(snapshot_path.read_text(encoding='utf-8')) if snapshot_path.exists() else {}
    samples = []
    references = []
    for q in questions:
        key = q['key']
        if key not in snapshots:
            snapshots[key] = call('POST','/admin/rag-lab/compare/retrieval',
                {'query':q['question'],'topK':8,'threshold':.55}, timeout=120)
            save('retrieval-snapshot.json',snapshots)
        for side in ['a','b']:
            value = snapshots[key][side]
            if value.get('error'):
                raise RuntimeError(key+' '+side+' '+value['error'])
            samples.append({'key':key,'system':side.upper(),'user_input':q['question'],
                'retrieved_contexts':[h['content'] for h in value['hits']],
                'response':None,'reference':REFERENCES[key],'retrieval_duration_ms':value['durationMs'],
                'context_metadata':value['hits']})
        excerpts = []
        for d in documents:
            if d['sourceRef'].split('|',1)[0] not in [r.split('|',1)[0] for r in q['expectedRefs']]:
                continue
            for anchor in q['goldAnchors']:
                pos = d['content'].lower().find(anchor.lower())
                if pos >= 0:
                    excerpts.append({'documentId':d['id'],'sourceRef':d['sourceRef'],'title':d['title'],
                        'anchor':anchor,'start':max(0,pos-600),'end':min(len(d['content']),pos+len(anchor)+1000),
                        'text':d['content'][max(0,pos-600):pos+len(anchor)+1000]})
        references.append({'key':key,'question':q['question'],'expectedRefs':q['expectedRefs'],
                           'reference':REFERENCES[key],'referenceStatus':'SOURCE_GROUNDED_ASSISTANT_REVIEW_NOT_BLIND_HUMAN_GOLD',
                           'sourceExcerpts':excerpts})
        print('PREPARED '+key,flush=True)
    save('samples.json',samples)
    save('reference-review.json',references)
    save('protocol.json',{'at':datetime.now(timezone.utc).isoformat(),'questions':len(questions),'samples':len(samples),
        'stage':'CONTEXTS_FROZEN_NO_LLM_SCORING','topK':8,'threshold':.55,'explicitSourceFilters':False,
        'generationControl':'Same model, temperature, evidence template and refusal instruction for A/B.',
        'plannedMetrics':['ContextPrecision','ContextRecall','Faithfulness','FactualCorrectness'],
        'limits':['A is live VectorStore retrieval baseline, not the full production recommendation pipeline.',
                  'B keeps its inherent validity filtering; both have no explicit source/technology/preference constraint.',
                  'Different live corpora compare current knowledge services, not an isolated ranking algorithm.',
                  'Reference answers must be reviewed against source excerpts before scoring; anchor strings are not reference answers.',
                  '18 reused questions are diagnostic, not a blind held-out test; no tuning on their RAGAS scores.',
                  'Faithfulness considers all contexts; per-citation support needs a separate claim/pointer audit.',
                  'Refusal can score as faithful while failing to answer. Track answer/refusal and reference coverage separately.'],
        'externalModelCallsMadeByThisPreparation':0})

if __name__=='__main__':
    main()
