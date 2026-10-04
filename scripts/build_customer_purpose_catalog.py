"""Freeze explicitly source-reviewed public facts. Local read-only DB; no model calls.

This is an approval manifest, NOT an automatic keyword classifier. Extending the
manifest requires reviewing a functional source sentence and its topic relation.
Only hashes and short public quotes are written to the application resource.
"""
import hashlib
import json
from pathlib import Path
from customer_local_smoke import sql

ROOT = Path(__file__).resolve().parents[1]
APPROVALS = [
    ('langchain-ai/langchain', 'catalogText',
     'LangChain 是一个用于构建智能体和 LLM 驱动应用的框架。', {'ai-application': 'DIRECT', 'agent': 'DIRECT'}),
    ('langchain-ai/langgraph', 'catalogText',
     'LangGraph is a low-level orchestration framework for building, managing, and deploying long-running, stateful agents.', {'ai-application': 'SUBTOPIC', 'agent': 'DIRECT'}),
    ('langgenius/dify', 'catalogText',
     'Extensive RAG capabilities that cover everything from document ingestion to retrieval', {'ai-application': 'SUBTOPIC', 'rag': 'DIRECT'}),
    ('QuivrHQ/quivr', 'catalogText',
     '我们负责处理 RAG，让你专注于产品。只需安装 quivr-core 并将其添加到你的项目中，即可开始摄入文件并提问。', {'ai-application': 'SUBTOPIC', 'rag': 'DIRECT'}),
    ('XYZ-AI-Lab/AxisAgentic', 'whatIsIt',
     'AxisAgentic 是一个可扩展的 AI 智能体运行框架，能够执行多轮对话任务并自动记录每一步的上下文和动作轨迹，便于回放、评估和训练导出。', {'ai-application': 'SUBTOPIC', 'agent': 'DIRECT'}),
]


def build():
    path = ROOT / 'orange-server/src/main/resources/customer/purpose-evidence.json'
    existing = {f['repository']: f for f in json.loads(path.read_text(encoding='utf-8'))['facts']} if path.exists() else {}
    facts = []
    for repo, field, quote, relations in APPROVALS:
        # Repository names are the fixed reviewed literals above, never user input.
        if field == 'catalogText':
            rows = sql("SELECT JSON_OBJECT('text',LEFT(v.original_content,12000),'versionId',v.id) "
                       "FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id "
                       "WHERE d.source_type='GITHUB' AND d.status='READY' AND d.deleted_at IS NULL "
                       "AND d.source_ref='" + repo + "' ORDER BY d.id DESC LIMIT 1")
        else:
            rows = sql("SELECT JSON_OBJECT('text',what_is_it,'projectId',id) FROM beginner_project "
                       "WHERE status IN ('PUBLISHED','CANDIDATE') AND COALESCE(archived,0)=0 "
                       "AND full_name='" + repo + "' LIMIT 1")
        if not rows:
            raise RuntimeError('Reviewed source missing: ' + repo)
        row = json.loads(rows)
        raw = row.pop('text')
        if quote not in raw or not 12 <= len(quote) <= 180:
            raise RuntimeError('Reviewed quote changed: ' + repo)
        old = existing.get('https://github.com/' + repo)
        if old and (old['sourceHash'] != hashlib.sha256(raw.encode()).hexdigest() or old['reviewedSource'] != row):
            raise RuntimeError('Source changed; review the complete source and explicitly update approval before rebuilding: ' + repo)
        facts.append(dict(id='source-review-20261004-' + repo.replace('/', '-').lower(),
                          repository='https://github.com/' + repo, field=field,
                          sourceHash=hashlib.sha256(raw.encode()).hexdigest(), quote=quote,
                          relations=relations, reviewedSource=row,
                          reviewMethod='CODEX_SOURCE_REVIEW', reviewedAt='2026-10-04'))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(dict(schemaVersion=1, facts=facts), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'facts': len(facts), 'output': str(path), 'modelCalls': 0}))


if __name__ == '__main__':
    build()
