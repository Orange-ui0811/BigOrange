"""R5 frozen-label scorer: final candidates, including context, are the admission truth."""
import argparse
from collections import Counter
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
R4 = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/r4'
R5 = R4.parent / 'r5'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def metric(n, d):
    return {'numerator': n, 'denominator': d, 'rate': round(n / d, 6) if d else None}


def check_freeze():
    """Execution-aware integrity check; the sealed R4 verifier correctly forbids started R5 runs."""
    seal, freeze = read(R4 / 'r4-seal.json'), read(R4 / 'freeze-manifest.json')
    for name, digest in {**seal['files'], **freeze['files'], **freeze['historicalFiles']}.items():
        if sha(ROOT / name) != digest:
            raise ValueError(f'Frozen/historical file changed: {name}')
    return seal, freeze


class Anchors(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.current = None
        self.entries = []

    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            href = dict(attrs).get('href')
            self.current = [href, []] if href else None

    def handle_data(self, value):
        if self.current:
            self.current[1].append(value)

    def handle_endtag(self, tag):
        if tag == 'a' and self.current:
            self.entries.append((''.join(self.current[1]).strip(), self.current[0]))
            self.current = None


def expected_pointers(raw):
    # Independent source parser includes HTML anchors, which the production Markdown parser may miss.
    values = re.findall(r'(?<!!)\[([^\]\r\n]+)\]\(([^\s()]+)\)', raw)
    html = Anchors()
    html.feed(raw)
    return list(dict.fromkeys(values + html.entries))


def scopes_correct(expected, actual):
    required, actual = set(expected), set(actual)
    if not required <= actual:
        return False
    # A project/core claim may not silently become an enterprise or unrelated product claim.
    if 'CORE' in required and (actual & {'ENTERPRISE', 'RELATED_PRODUCT'} - required):
        return False
    return True


def evaluate(data, source_rows, results):
    """Pure scorer, used by both real reports and adversarial synthetic regression checks."""
    source_by_doc = {s['source']['documentId']: s for s in source_rows}
    gold = {c['block']['blockId']: c for c in data['cases']}
    final, proposals, occurrences, pointers = {}, {}, {}, {}
    source_errors, candidate_errors, scope_errors, missing_conditions = [], [], [], []
    version_rows = []
    for ident, result in results.items():
        source = source_by_doc[ident]
        original = {b['blockId']: b for b in source['blocks']}
        p, projection = result['preview'], result['recommendation']
        if any(p.get(k) != source['source'][k] for k in ['documentId', 'versionId', 'sourceRef']) or p['sourceSha256'] != source['sha256']:
            source_errors.append({'documentId': ident, 'reason': 'source_identity'})
        expected_versions = dict(data['versions'])
        actual_versions = dict(projection['versions'], projectionPolicy=projection['policyVersion'])
        if any(actual_versions.get(k) != v for k, v in expected_versions.items()):
            source_errors.append({'documentId': ident, 'reason': 'implementation_versions'})
        version_rows.append(actual_versions)
        actual_blocks = {b['blockId']: b for b in p['blocks']}
        if set(actual_blocks) != set(original) or len(actual_blocks) != len(p['blocks']):
            source_errors.append({'documentId': ident, 'reason': 'block_set'})
        immutable = ['kind', 'startUtf16', 'endUtf16', 'startLine', 'endLine', 'text', 'sha256', 'sectionPath', 'headingIds']
        for bid, block in actual_blocks.items():
            if bid not in original or any(block.get(k) != original[bid].get(k) for k in immutable):
                source_errors.append({'blockId': bid, 'reason': 'original_changed'})
        assessments = {a['blockId']: a for a in projection['assessments']}
        if set(assessments) != set(original) or len(assessments) != len(projection['assessments']):
            source_errors.append({'documentId': ident, 'reason': 'assessment_set'})
        final.update(assessments)
        proposals.update({s['blockId']: s for s in result['selections']})
        for candidate in projection['knowledgeCandidates'] + projection['pointerCandidates']:
            packet = candidate['packet']
            if any(packet.get(k) != source['source'][k] for k in ['documentId', 'versionId', 'sourceRef']) or packet['sourceSha256'] != source['sha256']:
                source_errors.append({'documentId': ident, 'reason': 'packet_source'})
            refs = packet['primaryBlockIds'] + packet['contextBlockIds']
            metadata = {a['blockId']: a for a in candidate['evidence']}
            if set(refs) != set(metadata) or not set(refs) <= set(original) or len(refs) != len(set(refs)):
                source_errors.append({'documentId': ident, 'reason': 'packet_evidence'})
            for bid, a in metadata.items():
                if assessments.get(bid) != a or not set(a.get('evidenceBlockIds', [])) <= set(original):
                    source_errors.append({'blockId': bid, 'reason': 'stale_or_cross_source_evidence'})
            for bid in packet['primaryBlockIds']:
                if metadata.get(bid, {}).get('scopes') != candidate['subjectScopes']:
                    scope_errors.append({'blockId': bid, 'reason': 'mixed_subject_scopes'})
        if [c['packet'] for c in projection['knowledgeCandidates']] != p['knowledgeCandidates'] or [c['packet'] for c in projection['pointerCandidates']] != p['pointerCandidates']:
            source_errors.append({'documentId': ident, 'reason': 'preview_projection_mismatch'})
        for candidate in projection['knowledgeCandidates']:
            packet = candidate['packet']
            refs = set(packet['primaryBlockIds'] + packet['contextBlockIds'])
            for a in candidate['evidence']:
                bid = a['blockId']
                if bid not in original:
                    continue
                if original[bid]['kind'] == 'HEADING':
                    continue
                occurrences.setdefault(bid, []).append((refs, a))
                if a['disposition'] != 'KNOWLEDGE_CANDIDATE' or a['effectiveDecision'] != 'KEEP' or a['applicability'] != 'IN_SCOPE':
                    candidate_errors.append({'blockId': bid, 'reason': 'unapproved_body_context'})
                if bid in gold:
                    e = gold[bid]['expected']
                    if e['category'] != 'B':
                        scope_errors.append({'blockId': bid, 'reason': 'non_body_in_body', 'category': e['category']})
                    if not scopes_correct(e['requiredScopes'], a['scopes']):
                        scope_errors.append({'blockId': bid, 'reason': 'lost_or_wrong_scope'})
                    missing = set(e['requiredBlockIds']) - refs
                    if missing:
                        missing_conditions.append({'blockId': bid, 'missing': sorted(missing), 'packetId': packet['packetId']})
        for pointer in projection['pointers']:
            bid = pointer['evidenceBlockId']
            pointers.setdefault(bid, []).append((pointer['label'], pointer['href']))
            if bid not in original or (pointer['label'], pointer['href']) not in expected_pointers(original[bid]['text']):
                source_errors.append({'blockId': bid, 'reason': 'invented_pointer'})
        for candidate in projection['pointerCandidates']:
            if candidate['packet']['contextBlockIds']:
                candidate_errors.append({'reason': 'pointer_has_body_context', 'documentId': ident})
    rows = []
    for case in data['cases']:
        bid, e = case['block']['blockId'], case['expected']
        a, proposal = final.get(bid), proposals.get(bid)
        locations = occurrences.get(bid, [])
        required = set(e['requiredBlockIds'])
        usable = e['recoveryEligible'] and bool(locations) and all(
            required <= refs and scopes_correct(e['requiredScopes'], assessment['scopes'])
            and assessment['disposition'] == 'KNOWLEDGE_CANDIDATE'
            and assessment['effectiveDecision'] == 'KEEP' and assessment['applicability'] == 'IN_SCOPE'
            for refs, assessment in locations)
        actual = a['effectiveDecision'] if a else 'MISSING'
        nav_expected = expected_pointers(case['block']['text']) if e['category'] == 'N' else []
        nav_actual = pointers.get(bid, [])
        nav_ok = e['category'] == 'N' and bool(nav_expected) and set(nav_expected) <= set(nav_actual) and bool(a) and a['disposition'] == 'POINTER_METADATA_ONLY' and not locations
        rows.append({'caseId': case['caseId'], 'blockId': bid, 'documentId': case['documentId'], 'sourceRef': case['sourceRef'],
                     'role': case['role'], 'category': e['category'], 'critical': e['critical'], 'expected': e,
                     'proposal': proposal, 'assessment': a, 'finalDecision': actual, 'bodyOccurrences': len(locations),
                     'inBody': bool(locations), 'usableBody': bool(usable), 'decisionCorrect': actual == e['decision'],
                     'applicabilityCorrect': bool(a) and a['applicability'] == e['applicability'],
                     'dispositionCorrect': bool(a) and a['disposition'] == e['disposition'],
                     'scopeCorrect': bool(a) and scopes_correct(e['requiredScopes'], a['scopes']),
                     'navigationExpected': nav_expected, 'navigationActual': nav_actual, 'navigationComplete': bool(nav_ok),
                     'lineRange': [case['block']['startLine'], case['block']['endLine']]})
    targets = [r for r in rows if r['role'] == 'MODEL_TARGET']
    inside = [r for r in targets if r['expected']['applicability'] == 'IN_SCOPE']
    body = [r for r in rows if r['inBody']]
    eligible = [r for r in rows if r['expected']['recoveryEligible']]
    nav = [r for r in rows if r['category'] == 'N']
    critical = [r for r in rows if r['critical']]
    uncertain = [r for r in rows if r['category'] in {'R', 'H'} and r['inBody']]
    wrong_drop = [r for r in critical if r['expected']['decision'] != 'DROP' and r['finalDecision'] == 'DROP']
    unjudged = sorted(set(occurrences) - set(gold))
    metrics = {
        'bodyPrecision': metric(sum(r['usableBody'] for r in body), len(body)),
        'inScopeDecisionAgreement': metric(sum(r['decisionCorrect'] for r in inside), len(inside)),
        'usableBodyRecovery': metric(sum(r['usableBody'] for r in eligible), len(eligible)),
        'allModelTargetDecisionAgreement': metric(sum(r['decisionCorrect'] for r in targets), len(targets)),
        'allTargetProposalDecisionAgreement': metric(sum(bool(r['proposal']) and r['proposal']['decision'] == r['expected']['decision'] for r in targets), len(targets)),
        'applicabilityAgreement': metric(sum(r['applicabilityCorrect'] for r in targets), len(targets)),
        'dispositionAgreement': metric(sum(r['dispositionCorrect'] for r in targets), len(targets)),
        'requiredScopeRetention': metric(sum(r['scopeCorrect'] for r in targets if r['expected']['requiredScopes']), sum(bool(r['expected']['requiredScopes']) for r in targets)),
        'navigationComplete': metric(sum(r['navigationComplete'] for r in nav), len(nav)),
        'criticalCorrectlyUsable': sum(r['usableBody'] or r['navigationComplete'] for r in critical),
        'criticalStillReview': sum(r['finalDecision'] == 'REVIEW' for r in critical),
        'criticalWrongDeletion': len(wrong_drop), 'missingConditionsInUsedEvidence': len(missing_conditions),
        'scopeOverreach': len({e.get('blockId', e.get('documentId', 'unknown')) for e in scope_errors}),
        'wrongSourceCitation': len(source_errors), 'unapprovedCandidateEvidence': len(candidate_errors),
        'uncertainPromotion': len(uncertain), 'unjudgedBodyBlocks': len(unjudged),
        'repositoriesPresent': len(results), 'repositoriesExpected': len(source_rows),
        'labelledCases': len(rows), 'modelTargets': len(targets), 'controls': len(rows) - len(targets),
        'bodyRecoveryByRole': {},
        'usefulButBlocked': [{'caseId': r['caseId'], 'finalDecision': r['finalDecision'], 'inBody': r['inBody']} for r in rows if r['category'] == 'H'],
        'usefulKnowledgeCoverageIncludingBlocked': metric(sum(r['usableBody'] for r in rows), sum(r['category'] in {'B', 'H'} for r in rows)),
        'finalDecisionCounts': dict(Counter(r['finalDecision'] for r in rows)),
    }
    for role in ['MODEL_TARGET', 'RULE_OR_SCOPE_CONTROL']:
        group = [r for r in eligible if r['role'] == role]
        metrics['bodyRecoveryByRole'][role] = metric(sum(r['usableBody'] for r in group), len(group))
    gates = data['gates']
    metrics['qualityGatePassed'] = bool(len(results) == len(source_rows)
        and all(metrics[k]['rate'] is not None and metrics[k]['numerator'] / metrics[k]['denominator'] >= gates[g]
                for k, g in [('bodyPrecision', 'bodyPrecision'), ('inScopeDecisionAgreement', 'inScopeDecisionAgreement'), ('usableBodyRecovery', 'usableBodyRecovery')])
        and not any([wrong_drop, missing_conditions, scope_errors, source_errors, candidate_errors, uncertain]))
    return {'metrics': metrics, 'cases': rows, 'violations': {'source': source_errors, 'candidate': candidate_errors,
            'scope': scope_errors, 'missingConditions': missing_conditions}, 'unjudgedBodyBlockIds': unjudged, 'versions': version_rows}


def write_report(phase):
    check_freeze()
    auth = read(R5 / 'authorization.json')
    if auth['datasetSha256'] != sha(R4 / 'frozen-dataset.json') or auth['sealSha256'] != sha(R4 / 'r4-seal.json'):
        raise ValueError('Authorization identity changed')
    data, sources = read(R4 / 'frozen-dataset.json'), read(R4 / 'sources.json')['documents']
    results = {}
    for s in sources:
        ident = s['source']['documentId']
        path = R5 / f'results/{phase}/{ident}.json'
        if path.exists():
            entry = read(path)
            if entry['datasetSha256'] != auth['datasetSha256'] or entry['sealSha256'] != auth['sealSha256']:
                raise ValueError('Wrong result identity')
            results[ident] = entry['result']
    report = evaluate(data, sources, results)
    ledger = R5 / 'provider-calls.jsonl'
    calls = [json.loads(row) for row in ledger.read_text(encoding='utf-8').splitlines() if row.strip()] if ledger.exists() else []
    attempts = [read(p) for p in sorted((R5 / 'attempts').glob('*/*/outcome.json'))]
    usages, elapsed = Counter(), []
    for path in (R5 / 'attempts').glob('*/*/provider-response.json'):
        usages.update({k: v for k, v in (read(path).get('usage') or {}).items() if isinstance(v, int) and k in {'prompt_tokens', 'completion_tokens', 'total_tokens'}})
    for a in attempts:
        elapsed.append(a['elapsedMs'])
    report.update({'phase': phase, 'annotationAuthority': data['annotationAuthority'], 'datasetSha256': auth['datasetSha256'],
                   'sealSha256': auth['sealSha256'], 'implementationChangedDuringRun': False, 'indexWrites': 0,
                   'budgetUsedAtScoring': len(calls), 'budgetLimit': auth['maximumAttempts'], 'providerReportedUsage': dict(usages),
                   'attemptStatuses': {p: dict(Counter(a['status'] for a in attempts if a['phase'] == p)) for p in ['primary', 'reserve']},
                   'requestElapsedMs': {'count': len(elapsed), 'min': min(elapsed) if elapsed else None, 'max': max(elapsed) if elapsed else None,
                                        'mean': round(sum(elapsed) / len(elapsed)) if elapsed else None},
                   'limitations': ['Assistant-labeled purposive set; not independent human gold, random sampling or a blind test.',
                                   'Classification/admission only; not retrieval or end-to-end recommendation performance.',
                                   'Diagnostic may add valid transport/protocol retries; primary results remain immutable.']})
    if len(calls) > auth['maximumAttempts']:
        raise ValueError('Budget exceeded')
    destination = R5 / f'{phase}-evaluation.json'
    if destination.exists():
        if read(destination) == report:
            return report
        raise ValueError('Evaluation is immutable; use a distinct report for later diagnostics')
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    m = report['metrics']
    lines = [f'# K3-R5 · {phase} 验收', '', '标签由助手按已确认规则提前标注；本集不是独立人工金标准、盲测或随机总体抽样。', '',
             f"质量门槛：{'通过' if m['qualityGatePassed'] else '未通过'}。保留首轮结果；本轮不切换正式索引。", '',
             '| 指标 | 分子 / 分母 | 比例 |', '|---|---:|---:|']
    for key in ['bodyPrecision', 'inScopeDecisionAgreement', 'usableBodyRecovery', 'allModelTargetDecisionAgreement', 'allTargetProposalDecisionAgreement', 'navigationComplete']:
        v = m[key]
        lines.append(f"| {key} | {v['numerator']} / {v['denominator']} | {v['rate']} |")
    lines += ['', f"已评分 {m['repositoriesPresent']}/{m['repositoriesExpected']} 个仓库、{m['labelledCases']} 条；计分时真实出站 {len(calls)}/40。",
              f"零容忍项：关键误删 {m['criticalWrongDeletion']}；必要条件缺失但仍使用 {m['missingConditionsInUsedEvidence']}；范围越界 {m['scopeOverreach']}；错引 {m['wrongSourceCitation']}；不确定放行 {m['uncertainPromotion']}。",
              f"关键项中正确可用 {m['criticalCorrectlyUsable']}、仍待审 {m['criticalStillReview']}；未标注正文 {m['unjudgedBodyBlocks']} 块单列，不算正确。", '',
              '请求耗时是分类接口耗时，不代表网站问答耗时；实际 usage 与输入保守预留分别记录。', '', '## 逐条分歧与遗漏', '']
    for r in report['cases']:
        e = r['expected']
        if r['decisionCorrect'] and r['dispositionCorrect'] and r['scopeCorrect'] and (not e['recoveryEligible'] or r['usableBody']) and (r['category'] != 'N' or r['navigationComplete']):
            continue
        a, p = r['assessment'] or {}, r['proposal'] or {}
        lines += [f"### {r['caseId']} · {r['sourceRef']} · L{r['lineRange'][0]}–{r['lineRange'][1]}", '',
                  f"预期 {e['decision']} / {e['disposition']}；模型 {p.get('decision', '无有效提案')}；最终 {r['finalDecision']} / {a.get('disposition', '缺失')}。",
                  f"原因码：{a.get('reason', '缺失')}；正文可用：{r['usableBody']}；归属满足：{r['scopeCorrect']}；导航完整：{r['navigationComplete']}。", e['rationale'], '']
    (R5 / f'{phase}-evaluation.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase', choices=['primary', 'diagnostic'], default='primary')
    result = write_report(parser.parse_args().phase)
    print(json.dumps({'phase': result['phase'], **result['metrics'], 'budgetUsedAtScoring': result['budgetUsedAtScoring']}, ensure_ascii=False))
