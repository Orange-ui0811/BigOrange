"""Read-only local project corpus audit. No models, network export, or database writes."""
import collections
import datetime as dt
import hashlib
import json
import re
from pathlib import Path

from export_rag_model_corpus import query

ROOT = Path(__file__).resolve().parents[1]
PROJECT_TYPES = {'BEGINNER_PROJECT', 'CAREER_PROJECT', 'GITHUB'}
ATX = re.compile(r'^ {0,3}(#{1,6})\s+(.+)')
FENCE = re.compile(r'^\s{0,3}(`{3,}|~{3,})')
LINK = re.compile(r'https?://[^\s<>)]*')
SIGNALS = {
    'purpose_hint': r'features?|overview|introduction|what is|主要功能|核心功能|项目摘要|主要用途|项目介绍|用于|用来|旨在|allows?\s|enables?\s|provides?\s',
    'installation_hint': r'install(?:ation)?|quick\s*start|getting\s*started|安装|快速开始|快速启动|运行方式|部署|setup',
    'usage_hint': r'usage|examples?|tutorial|使用示例|使用方法|用法|调用示例|教程',
    'requirements_hint': r'prerequisites?|requirements?|dependencies|依赖|环境要求|前置|requires?\s|python\s*[>=:]|node(?:\.js)?\s*[>=:]',
    'license_hint': r'licen[cs]e|许可证|许可协议|授权|apache[- ]2|mit\s+licen',
    'limitations_hint': r'limitations?|caveats?|known\s+issues|风险|限制|注意事项|不支持',
    'docker_mention': r'\bdocker\b',
    'gpu_mention': r'\bgpu\b|\bcuda\b|显卡',
}


def sha(text):
    return hashlib.sha256(text.replace('\r\n', '\n').replace('\r', '\n').encode()).hexdigest()


def quantiles(values):
    values = sorted(values)
    if not values:
        return {'min': 0, 'p50': 0, 'p95': 0, 'max': 0, 'sum': 0}
    def percentile(p):
        at = (len(values) - 1) * p
        lo = int(at)
        return round(values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (at - lo), 2)
    return {'min': values[0], 'p50': percentile(.5), 'p95': percentile(.95), 'max': values[-1], 'sum': sum(values)}


def profile(text):
    text = text or ''
    lines = text.replace('\r\n', '\n').replace('\r', '\n').splitlines(keepends=True)
    code_chars = decoration_chars = prose_chars = nonblank = tables = trees = 0
    fence = None
    fence_count = 0
    headings = []
    prose = []
    offset = 0
    for i, line in enumerate(lines):
        stripped = line.strip()
        nonblank += bool(stripped)
        trees += bool(re.search(r'[├└]──|[│┃]', line))
        f = FENCE.match(line)
        if f:
            code_chars += len(line)
            if fence is None:
                fence = f.group(1)
                fence_count += 1
            elif f.group(1)[0] == fence[0] and len(f.group(1)) >= len(fence):
                fence = None
            offset += len(line)
            continue
        if fence is not None:
            code_chars += len(line)
            offset += len(line)
            continue
        heading = ATX.match(line)
        setext = (stripped and not re.match(r'^\s*[-*+]', line) and i + 1 < len(lines)
                  and re.fullmatch(r' {0,3}(?:=+|-+)\s*', lines[i + 1]))
        if heading or setext:
            headings.append({'title': heading.group(2).strip() if heading else stripped, 'offset': offset})
        visible = re.sub(r'!\[[^\]]*\]\([^)]*\)', '', line)
        visible = re.sub(r'<[^>]*>', '', visible)
        visible = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', visible)
        visible = LINK.sub('', visible).strip()
        linked = bool(LINK.search(line))
        decoration = (not visible or bool(re.search(r'\[!\[|shields\.io|badge\.svg|<img\b', line, re.I))
                      or linked and len(visible) < 35)
        tables += bool(stripped.startswith('|') and stripped.count('|') >= 2)
        if decoration:
            decoration_chars += len(line)
        elif not heading and not setext and stripped and not re.fullmatch(r'[-=|:\s]+', stripped):
            prose.append(visible)
            prose_chars += len(visible)
        offset += len(line)
    body = '\n'.join(prose)
    han = len(re.findall(r'[\u3400-\u9fff]', body))
    latin = len(re.findall(r'[A-Za-z]', body))
    total = han + latin
    language = ('unknown' if total < 20 else 'zh_dominant' if han / total >= .8
                else 'en_dominant' if latin / total >= .8 else 'mixed')
    n = len(text)
    code_ratio = code_chars / max(1, n)
    decoration_ratio = decoration_chars / max(1, n)
    flags = []
    if not text.strip(): flags.append('empty')
    elif n < 300: flags.append('short_under_300_chars')
    if text.strip() and not headings: flags.append('no_markdown_heading')
    if text.strip() and prose_chars < 200: flags.append('low_noncode_explanatory_text')
    if code_ratio >= .6: flags.append('fenced_code_dominant')
    if decoration_ratio >= .6: flags.append('links_badges_html_dominant')
    if fence: flags.append('unclosed_fence')
    if trees >= 5: flags.append('directory_tree_present')
    generated = ('## 知识分类' in text and any(x in text for x in ['新手友好项目', '入门项目', '职业成长项目', '学习项目', '职业项目']))
    positions = [h['offset'] for h in headings]
    if not positions or positions[0] != 0: positions.insert(0, 0)
    positions.append(len(text.replace('\r\n', '\n').replace('\r', '\n')))
    spans = [positions[i + 1] - positions[i] for i in range(len(positions) - 1)]
    return {
        'characters': n, 'utf8Bytes': len(text.encode()), 'sha256': sha(text),
        'languageHeuristic': language, 'headings': len(headings),
        'headingExamples': [h['title'][:100] for h in headings[:8]],
        'maxSectionCharacters': max(spans, default=0), 'fencedBlocks': fence_count,
        'fencedCodeRatio': round(code_ratio, 4), 'decorationRatio': round(decoration_ratio, 4),
        'noncodeVisibleCharacters': prose_chars, 'nonblankLines': nonblank,
        'tableLines': tables, 'treeLines': trees, 'generatedBusinessTemplate': generated,
        'signals': {k: bool(re.search(v, body, re.I)) for k, v in SIGNALS.items()}, 'flags': flags,
    }


def aggregate(rows):
    return {
        'records': len(rows), 'nonempty': sum(r['profile']['characters'] > 0 for r in rows),
        'characters': quantiles([r['profile']['characters'] for r in rows]),
        'languageHeuristic': dict(collections.Counter(r['profile']['languageHeuristic'] for r in rows)),
        'flags': dict(collections.Counter(f for r in rows for f in r['profile']['flags'])),
        'withHeadings': sum(r['profile']['headings'] > 0 for r in rows),
        'withFencedCode': sum(r['profile']['fencedBlocks'] > 0 for r in rows),
        'withTables': sum(r['profile']['tableLines'] > 0 for r in rows),
        'generatedBusinessTemplate': sum(r['profile']['generatedBusinessTemplate'] for r in rows),
        'signals': {k: sum(r['profile']['signals'][k] for r in rows) for k in SIGNALS},
        'uniqueNonemptyNormalizedTexts': len({r['profile']['sha256'] for r in rows if r['profile']['characters']}),
    }


def main():
    started = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
    stamp = started[:10].replace('-', '') + '-' + started[11:19].replace(':', '')
    out = ROOT / 'docs' / 'rag-b' / ('original-content-inventory-' + stamp)
    staging = ROOT / '_experiment_staging' / ('original-content-inventory-' + stamp)
    out.mkdir(parents=True, exist_ok=True)
    staging.mkdir(parents=True, exist_ok=True)
    schema = query("SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('leaderboard_snapshot','rag_b_generation_chunk')")
    legacy = query("SELECT JSON_OBJECT('id',id,'source',source,'sourceRef',source_ref,'title',title,'original',original_content,'content',content,'translationStatus',translation_status,'created',create_time) FROM knowledge_document ORDER BY id")
    bdocs = query("""SELECT JSON_OBJECT('id',d.id,'source',d.source_type,'sourceRef',d.source_ref,'title',d.title,'status',d.status,
        'deleted',d.deleted_at,'activeVersionId',d.active_version_id,'versionId',COALESCE(v.id,l.id),
        'versionStatus',COALESCE(v.status,l.status),'content',COALESCE(v.original_content,l.original_content),
        'chunkCount',COALESCE(v.chunk_count,l.chunk_count),'indexedGeneration',COALESCE(v.indexed_generation_id,l.indexed_generation_id),
        'updated',d.update_time) FROM rag_b_document d
        LEFT JOIN rag_b_document_version v ON v.id=d.active_version_id
        LEFT JOIN rag_b_document_version l ON l.id=(SELECT MAX(v2.id) FROM rag_b_document_version v2 WHERE v2.document_id=d.id)
        ORDER BY d.id""")
    projects = query("""SELECT JSON_OBJECT('id',id,'repository',full_name,'url',html_url,'status',status,'archived',archived,
        'language',primary_language,'descriptionOriginal',description_original,'readmeExcerpt',readme_excerpt,
        'descriptionZh',description_zh,'whatIsIt',what_is_it,'duration',estimated_duration,'difficulty',difficulty,
        'docker',requires_docker,'database',requires_database,'secrets',requires_secrets,'gpu',requires_gpu,
        'hasReadme',has_readme,'hasQuickStart',has_quick_start,'license',license_spdx,'updated',update_time)
        FROM beginner_project ORDER BY id""")
    careers = query("SELECT JSON_OBJECT('projectId',project_id,'status',status,'summary',summary_zh,'duration',estimated_duration,'difficulty',difficulty) FROM career_project_profile ORDER BY project_id")
    chunks = query("""SELECT JSON_OBJECT('id',c.id,'doc',c.document_id,'section',c.section_path,'content',c.content,
        'start',c.start_offset,'end',c.end_offset,'status',c.index_status) FROM rag_b_chunk c
        JOIN rag_b_document d ON d.active_version_id=c.version_id WHERE d.status='READY' AND d.deleted_at IS NULL ORDER BY c.document_id,c.chunk_index""")
    generations = query("SELECT JSON_OBJECT('id',id,'generation',generation_key,'model',embedding_model,'provider',embedding_provider,'dimensions',embedding_dimensions,'policy',chunk_policy,'documents',document_count,'chunks',chunk_count) FROM rag_b_index_generation WHERE active=1")
    versions = query("SELECT JSON_OBJECT('count',COUNT(*),'characters',SUM(CHAR_LENGTH(original_content))) FROM rag_b_document_version")[0]
    raw = {'started': started, 'legacy': legacy, 'bDocuments': bdocs, 'projects': projects, 'careerProfiles': careers, 'chunks': chunks, 'generations': generations, 'otherSchema': schema}
    (staging / 'snapshot.json').write_text(json.dumps(raw, ensure_ascii=False), encoding='utf-8')
    bp = {p['id']: p for p in projects}

    def identity(source, ref):
        if source == 'GITHUB': return (ref or '').split('|')[0].rstrip('/').lower()
        if source in {'BEGINNER_PROJECT', 'CAREER_PROJECT'}:
            m = re.fullmatch(r'(?:beginner|career):(\d+)', ref or '')
            if m and int(m.group(1)) in bp: return bp[int(m.group(1))]['repository'].lower()
        return None

    rows = []
    for d in legacy:
        original = d['original'] or ''
        effective = original if original.strip() else d['content'] or ''
        rows.append({'layer': 'A_EFFECTIVE', 'id': d['id'], 'source': d['source'], 'sourceRef': d['sourceRef'],
            'repository': identity(d['source'], d['sourceRef']), 'title': d['title'],
            'originalPresent': bool(original.strip()), 'originalEqualsContent': bool(original.strip()) and sha(original) == sha(d['content'] or ''),
            'translationStatus': d['translationStatus'], 'profile': profile(effective)})
    for d in bdocs:
        active = d['status'] == 'READY' and not d['deleted'] and d['activeVersionId'] is not None and d['versionStatus'] == 'READY'
        candidates = [k for k in legacy if k['source'] == d['source'] and
                      ((k['sourceRef'] or '') == (d['sourceRef'] or '') or (k['sourceRef'] or '').split('|')[0] == (d['sourceRef'] or ''))]
        content_hash = sha(d['content'] or '')
        orig_matches = [k['id'] for k in candidates if (k['original'] or '').strip() and sha(k['original']) == content_hash]
        content_matches = [k['id'] for k in candidates if (k['content'] or '').strip() and sha(k['content']) == content_hash]
        rows.append({'layer': 'B_SELECTED_VERSION', 'id': d['id'], 'source': d['source'], 'sourceRef': d['sourceRef'],
            'repository': identity(d['source'], d['sourceRef']), 'title': d['title'], 'active': active,
            'status': d['status'], 'versionId': d['versionId'], 'versionRole': 'ACTIVE' if d['activeVersionId'] else 'LATEST_INACTIVE',
            'chunkCount': d['chunkCount'], 'profile': profile(d['content']),
            'legacyOriginalMatches': orig_matches, 'legacyContentMatches': content_matches})
    for p in projects:
        rows.append({'layer': 'BUSINESS_README_EXCERPT', 'id': p['id'], 'source': 'BEGINNER_PROJECT', 'repository': p['repository'].lower(),
            'title': p['repository'], 'status': p['status'], 'archived': p['archived'], 'profile': profile(p['readmeExcerpt']),
            'descriptionOriginalLength': len(p['descriptionOriginal'] or '')})

    grouped = collections.defaultdict(list)
    for r in rows:
        grouped[(r['layer'], r['source'])].append(r)
    groups = [{'layer': k[0], 'source': k[1], **aggregate(v)} for k, v in sorted(grouped.items())]
    active = [r for r in rows if r['layer'] == 'B_SELECTED_VERSION' and r['active']]
    active_projects = [r for r in active if r['source'] in PROJECT_TYPES]
    github = [r for r in active_projects if r['source'] == 'GITHUB']
    business = [p for p in projects if p['status'] in {'PUBLISHED', 'CANDIDATE'} and not p['archived']]
    hidden_career = {c['projectId'] for c in careers if c['status'] in {'HIDDEN', 'INVALID'}}
    excluded_repo = {p['repository'].lower() for p in projects if p['status'] in {'HIDDEN','INVALID'} or p['archived'] or p['id'] in hidden_career}
    external = [r for r in github if r['repository'] not in excluded_repo]
    inventory_repo = {p['repository'].lower() for p in projects}
    all_repo = inventory_repo | {r['repository'] for r in rows if r['source'] in PROJECT_TYPES and r.get('repository')}
    business_summary = {
        'projects': len(projects), 'uniqueRepositories': len(inventory_repo), 'status': dict(collections.Counter(p['status'] for p in projects)),
        'readmeExcerptNonempty': sum(bool((p['readmeExcerpt'] or '').strip()) for p in projects),
        'descriptionOriginalNonempty': sum(bool((p['descriptionOriginal'] or '').strip()) for p in projects),
        'readmeExcerptLength': quantiles([len(p['readmeExcerpt'] or '') for p in projects]),
        'readmeExcerptExactly6000': sum(len(p['readmeExcerpt'] or '') == 6000 for p in projects),
        'careerProfiles': len(careers), 'careerStatus': dict(collections.Counter(c['status'] for c in careers)),
        'eligibleBusinessProjects': len(business), 'eligibleExternalDocuments': len(external),
        'eligibleUniqueRepositories': len({p['repository'].lower() for p in business} | {r['repository'] for r in external}),
        'businessLanguages': dict(collections.Counter(p['language'] or 'UNKNOWN' for p in business)),
        'missingFieldsAmongEligible': {k: sum(p[k] is None or p[k] == '' for p in business) for k in ['language','duration','difficulty','docker','database','secrets','gpu','license']},
    }
    project_chunks = [c for c in chunks if any(r['id'] == c['doc'] for r in active_projects)]
    chunk_stats = {'activeAllChunks': len(chunks), 'activeProjectChunks': len(project_chunks),
        'projectChunkCharacters': quantiles([len(c['content'] or '') for c in project_chunks]),
        'withSectionPath': sum(bool(c['section']) for c in project_chunks),
        'indexStatus': dict(collections.Counter(c['status'] for c in project_chunks))}
    try:
        from tokenizers import Tokenizer
        tokenizer = Tokenizer.from_file(str(ROOT / 'orange-server/src/main/resources/rag-b/tokenizers/bge-m3.json'))
        tokenizer.no_truncation(); tokenizer.no_padding()
        lengths = [len(e.ids) for e in tokenizer.encode_batch([c['content'] or '' for c in project_chunks], add_special_tokens=False)]
        chunk_stats.update({'tokenLength': quantiles(lengths), 'under32Tokens': sum(n < 32 for n in lengths),
            'under64Tokens': sum(n < 64 for n in lengths), 'over768Tokens': sum(n > 768 for n in lengths)})
    except ImportError:
        chunk_stats['tokenAudit'] = 'UNAVAILABLE_LOCAL_TOKENIZER'
    comparisons = collections.Counter()
    for r in active_projects:
        comparisons['matches_legacy_original' if r['legacyOriginalMatches'] else 'matches_legacy_content_only' if r['legacyContentMatches'] else 'no_exact_legacy_text_match'] += 1
    examples = {}
    for flag in ['short_under_300_chars','low_noncode_explanatory_text','fenced_code_dominant','links_badges_html_dominant','unclosed_fence','directory_tree_present','no_markdown_heading']:
        chosen = sorted([r for r in github if flag in r['profile']['flags']], key=lambda r: r['profile']['characters'])[:8]
        examples[flag] = [{'id': r['id'], 'repository': r['repository'], 'characters': r['profile']['characters'],
            'codeRatio': r['profile']['fencedCodeRatio'], 'decorationRatio': r['profile']['decorationRatio'],
            'headings': r['profile']['headings']} for r in chosen]
    summary = {'started': started, 'finished': dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),
        'method': 'All local project records scanned; local deterministic textual heuristics, no model scoring. No database mutation.',
        'scope': {'legacyAllRecords': len(legacy), 'legacyProjectRecords': sum(d['source'] in PROJECT_TYPES for d in legacy),
            'bAllDocuments': len(bdocs), 'bActiveAllDocuments': len(active), 'bActiveProjectDocuments': len(active_projects),
            'bHistoricalVersions': versions, 'uniqueRepositoriesAcrossProjectLayers': len(all_repo)},
        'business': business_summary, 'groups': groups,
        'activeProjectSummary': aggregate(active_projects), 'activeGithubSummary': aggregate(github),
        'activeIndex': generations, 'chunks': chunk_stats, 'originComparison': dict(comparisons), 'flagExamples': examples,
        'limitations': ['Heuristic flags identify review priorities, not proof of uselessness or factual accuracy.',
            'Language is a Han/Latin visible non-fenced character heuristic, not a language model.',
            'Topic and section keywords are hints, not proof that a requirement is mandatory or absent.',
            'Current document version contents scanned; historical versions counted, not independently quality classified.',
            'Unique repository identity from business IDs and GITHUB source refs; no external repository fetch.',
            'Sequential read queries are time-bounded, not a transactional snapshot.',
            'README excerpt presence is not proof of complete upstream README preservation.'],
        'artifacts': {'inventory': str(out / 'document-inventory.json'), 'localIgnoredSnapshot': str(staging / 'snapshot.json')}}
    (out / 'document-inventory.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
    (out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'report': str(out / 'summary.json'), 'scope': summary['scope'], 'business': business_summary,
        'github': summary['activeGithubSummary'], 'chunks': chunk_stats}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    main()
