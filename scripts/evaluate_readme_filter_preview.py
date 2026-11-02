"""Compare Java K2 predictions with frozen K1 annotations; never used by the filter itself."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-filter-k2'
OUT = ROOT / 'docs/rag-b/readme-filter-v1/k2'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def evaluate(split, ledger_receipt=None, prediction_dir=None, output_dir=None):
    stage = prediction_dir or STAGE
    out = output_dir or OUT
    predictions = read(stage / f'{split}-preview.json')
    audit = read(OUT.parent / 'audit-cases.json')
    snapshot = read(ROOT / audit['sourceSnapshot'])
    manifest = read(OUT.parent / 'sample-manifest.json')
    if predictions['summary']['snapshotSha256'] != audit['snapshotSha256']:
        raise ValueError('Snapshot mismatch')
    sources = {str(d['documentId']): d for d in snapshot['documents']}
    eligible = {str(d['documentId']) for d in manifest['samples'] if split == 'all' or d['split'] == split}
    previews = {p['documentId']: p for p in predictions['previews']}
    if set(previews) != eligible:
        raise ValueError('Wrong split or missing source')
    review_leaks = []
    source_errors = []
    masks = {}
    decision_blocks = Counter()
    for doc_id, preview in previews.items():
        doc = sources[doc_id]
        if str(doc['versionId']) != preview['versionId'] or doc['sourceRef'] != preview['sourceRef'] or doc['sha256'] != preview['sourceSha256']:
            raise ValueError('Cross source identity')
        raw16 = doc['content'].encode('utf-16-le')
        states = ['UNMAPPED'] * (len(raw16) // 2)
        owners = [None] * len(states)
        candidate = [False] * len(states)
        block_map = {b['blockId']: b for b in preview['blocks']}
        previous = 0
        for block in preview['blocks']:
            a, b = block['startUtf16'], block['endUtf16']
            if a != previous or a >= b or raw16[a*2:b*2].decode('utf-16-le') != block['text']:
                source_errors.append(block['blockId'])
            if hashlib.sha256(block['text'].encode('utf-8')).hexdigest() != block['sha256']:
                source_errors.append(block['blockId'])
            previous = b
            states[a:b] = [block['decision']] * (b-a)
            owners[a:b] = [block['blockId']] * (b-a)
            if block['kind'] != 'SPACE':
                decision_blocks[block['decision']] += 1
        if previous != len(states):
            raise ValueError('Incomplete source coverage')
        for packet in preview['knowledgeCandidates'] + preview['pointerCandidates']:
            if packet['documentId'] != doc_id or packet['versionId'] != preview['versionId'] or packet['sourceRef'] != preview['sourceRef'] or packet['sourceSha256'] != preview['sourceSha256']:
                raise ValueError('Cross-source packet')
            for block_id in packet['primaryBlockIds'] + packet['contextBlockIds']:
                if block_id not in block_map or block_map[block_id]['decision'] != 'KEEP':
                    review_leaks.append(block_id)
                else:
                    block = block_map[block_id]
                    candidate[block['startUtf16']:block['endUtf16']] = [True] * (block['endUtf16']-block['startUtf16'])
        masks[doc_id] = (states, owners, candidate, block_map)
    outcomes = []
    for case in audit['cases']:
        doc_id = str(case['documentId'])
        if doc_id not in previews:
            continue
        states, owners, candidate, block_map = masks[doc_id]
        span = case['sourceSpan']
        # Weight non-whitespace UTF-16 code units; the denominator is explicit, not a claim of semantic accuracy.
        cursor = span['startUtf16']
        counts = Counter()
        substantive = Counter()
        comparable = Counter()
        approved = 0
        for char in span['text']:
            width = len(char.encode('utf-16-le')) // 2
            if not char.isspace():
                for pos in range(cursor, cursor+width):
                    counts[states[pos]] += 1
                    if case['scope'] == 'WRAPPER_ONLY' or block_map[owners[pos]]['kind'] not in {'SPACE','LAYOUT','WRAPPER'}:
                        comparable[states[pos]] += 1
                    if block_map[owners[pos]]['kind'] not in {'HEADING','LAYOUT','SPACE'}:
                        substantive[states[pos]] += 1
                    approved += int(candidate[pos])
            cursor += width
        if cursor != span['endUtf16Exclusive']:
            raise ValueError('Audit offset mismatch')
        involved = sorted(set(owners[span['startUtf16']:span['endUtf16Exclusive']]))
        expected = case['decision']
        if expected == 'KEEP':
            outcome = 'WRONG_DROP' if comparable['DROP'] else 'DEFERRED_REVIEW' if comparable['REVIEW'] else 'KEPT'
        elif expected == 'DROP':
            outcome = 'NOISE_KEPT' if counts['KEEP'] else 'NOISE_DEFERRED' if counts['REVIEW'] else 'DROPPED'
        else:
            outcome = 'REVIEW_APPROVED' if substantive['KEEP'] else 'REVIEW_DROPPED' if substantive['DROP'] else 'REVIEWED'
        outcomes.append({'caseId': case['caseId'], 'documentId': case['documentId'], 'sourceRef': case['sourceRef'],
                         'expected': expected, 'kind': case['kind'], 'scope': case['scope'], 'outcome': outcome,
                         'lineRange': [case['startLine'], case['endLine']], 'nonWhitespaceUtf16': sum(counts.values()),
                         'predictedCoverage': dict(counts), 'candidateEvidenceUnits': approved,
                         'substantiveCoverage': dict(substantive),
                         'comparableCoverage': dict(comparable),
                         'mixedParentAnnotation': case['scope'] == 'MIXED',
                         'reason': case['reason'], 'predictedBlocks': [{'blockId': i, 'decision': block_map[i]['decision'],
                           'reason': block_map[i]['reason'], 'lines': [block_map[i]['startLine'], block_map[i]['endLine']]} for i in involved]})
    critical = [o for o in outcomes if o['expected'] == 'KEEP' and o['kind'] in {'LICENSE','LIMITATION','COMPATIBILITY','DEPLOYMENT','MAINTENANCE'}]
    ledger_checks = []
    for name, baseline in snapshot['ledgersBefore'].items():
        path = ROOT / name.replace('\\', '/')
        current = {'sha256': sha(path), 'entries': sum(bool(s.strip()) for s in path.read_text(encoding='utf-8').splitlines())}
        check={'path': name, 'unchangedSinceK1': current == baseline, 'baseline': baseline, 'current': current}
        if current != baseline:
            if ledger_receipt is None:
                raise ValueError('Provider ledger changed; supply an explicit authorized append receipt, do not reset the baseline')
            receipt=read(ledger_receipt)
            prefix=ROOT/'_experiment_staging/readme-filter-k3/legacy40-before-k3.jsonl'
            if not (receipt.get('before')==baseline and receipt.get('after')==current and receipt.get('budgetLimit')==40
                    and receipt.get('newAttempts')==current['entries']-baseline['entries'] and 0<=receipt['newAttempts']<=2
                    and receipt.get('prior38PrefixPreserved') is True and receipt.get('original50Unchanged') is True
                    and prefix.exists() and sha(prefix)==baseline['sha256'] and path.read_bytes().startswith(prefix.read_bytes())):
                raise ValueError('Append receipt or historical prefix mismatch')
            check['authorizedAppendReceipt']=str(ledger_receipt.relative_to(ROOT))
        ledger_checks.append(check)
    implementation_files = [ROOT / f'orange-server/src/main/java/com/orangejd/server/ragb/ingest/{n}.java'
                            for n in ['ReadmeKnowledgeFilter', 'ReadmeFilterOfflineCli']]
    summary = {'split': split, 'documents': len(previews), 'annotatedCases': len(outcomes),
               'caseOutcomes': dict(Counter(o['outcome'] for o in outcomes)),
               'nonBlankBlockDecisions': dict(decision_blocks),
               'criticalKeepCases': len(critical), 'criticalWrongDropCases': sum(o['outcome']=='WRONG_DROP' for o in critical),
               'criticalDeferredCases': sum(o['outcome']=='DEFERRED_REVIEW' for o in critical),
               'reviewApprovalCases': sum(o['outcome']=='REVIEW_APPROVED' for o in outcomes),
               'sourceMappingErrors': len(source_errors), 'nonKeepCandidateLeaks': len(review_leaks),
               'indexWrites': 0, 'additionalProviderCalls': 0, 'ledgerChecks': ledger_checks,
               'predictionSha256': sha(stage/f'{split}-preview.json'), 'annotationSha256': sha(OUT.parent/'audit-cases.json'),
               'implementationHashes': {p.relative_to(ROOT).as_posix(): sha(p) for p in implementation_files},
               'interpretation': 'K1 assistant labels; KEEP loss ignores layout-only marks; REVIEW promotion ignores headings/layout; all raw coverage also reported; not blind or retrieval evaluation'}
    out.mkdir(parents=True, exist_ok=True)
    (out/f'{split}-evaluation.json').write_text(json.dumps({'summary':summary,'cases':outcomes},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    md = [f'# K2 规则预览对照 · {split}', '',
          '按 K1 已标注原文范围统计；未标注部分不算正确样本。分歧包含父块/子块边界差异，不能直接等同语义错误率。',
          '标注为助手审阅；尚未独立人工确认，也不是检索效果评测。', '',
          '| 仓库 | 已标注 | KEEP 被删 | KEEP 待复核 | DROP 被保留 | REVIEW 被放行 |',
          '|---|---:|---:|---:|---:|---:|']
    for doc_id,p in previews.items():
        group=[o for o in outcomes if str(o['documentId'])==doc_id]
        c=Counter(o['outcome'] for o in group)
        md.append(f"| {p['sourceRef']} | {len(group)} | {c['WRONG_DROP']} | {c['DEFERRED_REVIEW']} | {c['NOISE_KEPT']} | {c['REVIEW_APPROVED']} |")
    md += ['', '## 分歧清单', '']
    for o in outcomes:
        if o['outcome'] in {'KEPT','DROPPED','REVIEWED'}:
            continue
        md += [f"### {o['caseId']} · {o['sourceRef']} · {o['outcome']}", '',
               f"原标注 {o['expected']}；L{o['lineRange'][0]}–L{o['lineRange'][1]}；覆盖 {o['predictedCoverage']}。",o['reason'],'']
        for b in o['predictedBlocks']:
            md.append(f"- L{b['lines'][0]}–L{b['lines'][1]}：{b['decision']} / {b['reason']}")
        md.append('')
    (out/f'{split}-evaluation.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in {'ledgerChecks','implementationHashes'}},ensure_ascii=False))
    if source_errors or review_leaks:
        raise ValueError('Source or candidate integrity failure')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--split',choices=['development','validation','all'],default='development')
    parser.add_argument('--ledger-receipt',type=Path,help='Explicit receipt for the authorized two K3 append attempts; historical baseline stays unchanged')
    parser.add_argument('--prediction-dir',type=Path,help='Read a new offline run without overwriting K2 preview artifacts')
    parser.add_argument('--output-dir',type=Path,help='Write a separate regression report; frozen annotations remain unchanged')
    args=parser.parse_args()
    evaluate(args.split,args.ledger_receipt.resolve() if args.ledger_receipt else None,
             args.prediction_dir.resolve() if args.prediction_dir else None,args.output_dir.resolve() if args.output_dir else None)
