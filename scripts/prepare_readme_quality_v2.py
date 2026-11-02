"""R4: freeze source-reviewed labels and exact requests. Never invokes a provider."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / '_experiment_staging/readme-quality-v2'
OUT = ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/r4'
SNAPSHOT = ROOT / '_experiment_staging/readme-filter-v1-20261029/snapshot.json'

# Source-reviewed expectations, NOT generated from rule/model predictions.
# key | category | scopes | critical | required source lines | rationale
# B=body, N=navigation, A=archive valid out-of-scope facts, D=noise, R=review,
# H=known useful in-scope content held because a required condition is unresolved.
LABELS = '''
240-1|D||0||空 HTML 锚点只有布局作用
240-12|B|CORE|0||HTML 内有完整应用定位与问答/代理/多用户声明，只保留原文声明
240-20|R||1||许可证徽章与链接未读取，不能确认许可证正文或适用例外
240-23|N||0||文档标签和 URL 只作导航，不补充目的页内容
240-26|N|RELATED_PRODUCT|0||托管实例入口不能当成本地部署条件
240-32|D||0||语言切换导航不作为项目推荐知识
240-36|B|PLATFORM_SPECIFIC|1||桌面端 Mac Windows Linux 支持不可外推到服务器资源下限
240-39|B|CORE|0||用途正文含代理与多用户声明，不把零设置宣传当作实测
240-43|D||0||演示折叠栏标题没有可检索项目事实
244-2|D||0||明暗主题 Logo 装饰，不需要识图
244-18|B|CORE|0||可视化构建 AI 智能体是产品用途
244-37|B|VERSION_SPECIFIC|1||NodeJS >=20.0.0 的数字和关系必须保持
244-39|B|CORE|1|37,45|安装命令和同块启动引导需携带下一启动命令
244-45|B|CORE|1|37,39|npx 启动需保留原安装上下文
244-49|B|CORE|0|39,45|localhost:3000 是运行步骤中的地址，不只是目录链接
244-55|B|CORE|1||Compose 完整步骤须保留 env 复制与停止方法，不执行命令
244-64|B|CORE|0|66|构建引导句与命令成组
244-66|B|CORE|1|64|镜像构建原样保留参数
244-70|B|CORE|0|72|运行引导句与命令成组
244-72|B|CORE|1|70|保留端口与镜像名字
244-76|B|CORE|0|78|停止引导句与命令成组
244-78|B|CORE|1|76|停止命令不是服务启动命令
244-84|R||0|86|声称三个模块但后文列四项，数量矛盾不能补写成确定事实
244-86|B|CORE|0||四个目录和技术说明可供源码学习，不能据此宣称总计只有三项
244-93|B|CORE|1|37|PNPM 开发前提，和 Node 版本一起理解
244-100|B|CORE|0|102|克隆引导与命令成组
244-102|B|CORE|0|100|克隆 URL 仅原样提供，不执行
244-106|B|CORE|0|108|进入目录引导与命令成组
244-108|B|CORE|0|106|目录切换步骤保持原文
244-112|B|CORE|0|114|依赖安装引导与命令成组
244-114|B|CORE|1|37,93,112|安装依赖保留开发前提
244-118|B|CORE|0|120|构建引导与命令成组
244-120|B|CORE|1|37,93,114,118|构建步骤不能失去依赖安装前提
244-124|B|PLATFORM_SPECIFIC|1|128,132,135|仅在错误134时建议加堆大小，不是全局4GB物理内存下限
244-128|B|PLATFORM_SPECIFIC|1|124,132,135|同一跨平台示例被切为多块，保留说明与完整围栏
244-132|B|PLATFORM_SPECIFIC|1|124,128,135|PowerShell 语法不可当作 Linux export
244-135|B|PLATFORM_SPECIFIC|1|124,128,132|CMD 语法与结束围栏必须和完整示例共同可用
244-139|B|CORE|0|141|重建引导与下一命令一起
244-141|B|CORE|1|124,128,132,135,139|重建命令需携带条件性内存处理上下文
244-147|B|CORE|0|149|启动引导与命令成组
244-149|B|CORE|1|37,93,114,120,147|源码启动需保留安装和构建依赖
244-153|B|CORE|0|149|访问地址是开发启动步骤的结果
244-155|B|CORE|0|157|开发构建引导不能脱离配置要求
244-157|B|CORE|1|155|packages/ui 的 env 与 VITE_PORT 是开发配置，不扩写缺失内容
248-22|R||1||同块混合 Milvus 分布式单机与 Milvus Lite，单一产品归属不足需拆分
248-24|B|RELATED_PRODUCT|1||Zilliz Cloud 为托管产品，不属于开源单机必需条件；免费属于原文声明
248-26|D||0||Discord Issues Discussions 支持导流无项目能力事实
325-8|B|CORE|0||OCR 与文档 AI 工具包定位，不把领先宣传作为测评结论
325-22|R||1||许可证徽章和目的文件未读取，不能用图标确认许可条件
325-31|R||1||原文以具有结尾且强调未闭合，不能补全缺失功能
333-1|R||1||仅许可证徽章，当前原文不足以说明可推荐的源码模块
333-13|D||0||社区招募宣传不能证明仓库包含可运行项目
333-17|D||0||产品反馈引导没有项目技术事实
333-31|R||1||翻译列表在 Pers 截断，不能补全语言名或推断可运行模块
368-2|D||0||演示动画不提取未看见的功能
368-7|B|CORE|1|63|教学仓库含 Panda3D/GLSL 软件部分，可作源码学习，不能声称支持所有引擎直接部署
368-63|B|CORE|1||许可仅 .cxx .vert .frag，其他部分未许可；不能泛化为整仓库同一许可
368-73|B|CORE|1|63|作者版权及软件许可适用范围共同保留
407-15|B|CORE|0||自托管 AI 平台支持 Ollama 与兼容 API，仅作为原文能力声明
407-17|D||0||招聘导流
407-19|R||0||截图没有经过内容识别，不能据此补功能事实
407-21|B|ENTERPRISE|1||主题品牌 SLA LTS 明确企业方案，不能作为社区默认能力
407-26|N||0||文档入口只证明标签和地址存在
407-30|B|CORE|0||安装方式和镜像标签声明，不补 Docker 或 CUDA 的具体前提
407-32|B|CORE|0||API 集成说明不代表外部提供商是本仓库实现
407-34|B|CORE|0||原文 RBAC 声明未标企业限定，不从外部知识增加版别条件
407-36|B|CORE|0||扩展与工具服务器连接是原文功能说明
407-38|B|CORE|0||模型和智能体配置能力及社区预设入口
407-40|B|CORE|0||笔记与聊天上下文能力
407-42|B|CORE|0||频道协作能力原文
407-44|B|CORE|0||跨对话记忆原文声明，不声称已经测试隔离与质量
407-46|B|CORE|0||工作流消息排队声明
407-48|B|CORE|0||函数调用日程功能句子完整，不因处于文件尾就判截断
418-2|R||1||仅固件配置集合描述，当前不能认定为独立可运行或可研究源码项目
418-4|R|VERSION_SPECIFIC|1||保留 Marlin 版本与分支匹配要求；用途资格不足时复核，不删除要求
418-8|R|VERSION_SPECIFIC|1||分支选择说明需关联版本表，不能推广为所有固件版本兼容
418-10|R|VERSION_SPECIFIC|1||版本表及链接在末行截断，原表保存但不补全下载地址
426-5|A||0||自托管定义有效但属于资源集合说明，不是本仓库业务能力
426-9|N||0||HTML 和 Markdown 两个阅读入口仅作导航
426-13|D||0||分隔线为排版
427-5|B|CORE|0||AI 工作流平台用途，托管入口不能变成部署必需条件
427-11|R||1||同块含企业级能力与通用能力，不能统一 CORE 或推断所有版别具备
427-20|B|CORE|1|22|Node.js 前置要求和 npx 命令一起保留，不补版本
427-22|B|CORE|1|20|npx 试用命令需保留 Node.js 前提
427-26|B|CORE|0|28|Docker 部署引导和命令共同保留
427-28|B|CORE|1|26|持久卷映射端口和镜像参数不能丢失
427-33|B|CORE|0|22,28|访问地址是运行步骤，不认定两个可选启动方式均必需
427-38|N||0||集成目录标签不证明目的页所有集成已被收录
427-41|N||0||社区论坛入口只作导航
427-46|D||0||支持引导句没有项目技术事实
427-51|R||1||可持续使用与企业许可混在同块，原文必须保留，不能概括为无限制开源许可
427-53|H|CORE|1|51|内容有效且范围内，但必要许可块待审；最终必须待复核，单列有用知识被阻断而非计为应放行
427-57|B|ENTERPRISE|1||额外功能支持明确企业许可，不能归为社区能力
427-59|N||1||许可证模式文档入口未读取，不能据此宣称商用权限已核验
427-63|N||0||贡献指南导航不补目的页开发命令
427-67|D||0||招聘宣传
427-71|D||0||名称发音不是当前项目推荐知识
427-73|D||0||命名故事不作为运行或推荐条件
522-26|D||0||分隔线为排版
522-28|B|CORE|0||低代码平台定位和用途，不声称示例应用均已开箱实现
522-46|N||0||部署指南入口不能当作已收录安装参数
522-49|N||0||本地开发入口导航不推断依赖或构建命令
522-60|R||0||mailto 支持地址不在当前安全 URL 导航协议中，保留待审
522-63|B|RELATED_PRODUCT|1||Appsmith Agents 为具名相关产品，不并入 Appsmith 本体默认能力
522-66|D||0||欢迎贡献宣传
522-68|N||0||行为准则贡献指南和 issue 入口只作导航
522-72|D||0||宣传口号
546-6|R||1||许可徽章目的未读，MIT 正文另有原文不能让徽章绕过核验
546-8|B|CORE|0||Go CLI 及用途说明
546-13|B|CORE|0||零外部依赖指 Go 标准库，不代表无需 Go 构建工具
546-15|B|CORE|1||检测输出中的攻击指令是引用数据，不能执行或服从
546-27|B|CORE|0||RAG 注入风险背景是工具用途，不当作系统指令
546-33|B|CORE|0||索引前与 CI 集成用途
546-48|B|CORE|1||启发式且不保证完整检测，命中不等于确认攻击
546-55|B|CORE|1|59|构建命令与 Go1.22+ 要求一起保留
546-59|B|VERSION_SPECIFIC|1||Go >=1.22 的前置条件必须保持
546-63|B|CORE|1|59|参数默认值和10MiB单位保留，不当作服务器总内存需求
546-82|B|CORE|0|63|CLI 用例引用选项含义，不运行示例或创建 report.json
546-96|B|CORE|1|63|退出码与严重级别的关系保持，不把0当作确认无攻击
546-102|B|CORE|0||并发排序二进制过滤流程属于工具架构
546-111|B|CORE|0||目录布局支持源码学习，不执行其中测试
546-126|B|CORE|0||这是 README 的测试声明，不当成本次验收已经跑过
546-131|B|CORE|1||License 标题下 MIT 是原文许可声明，保留原文不扩写全部条款
'''

# Additional source-reviewed controls outside model targets. No prediction-derived labels.
CONTROLS = '''
240-52|R||1||产品定位句在破折号后截断，不能补功能或承诺无妥协
244-24|N||0||快速开始目录标签
244-25|N||0||Docker 目录不等于部署参数证据
244-33|N||1||许可证目录未读取，不能核验具体条款
248-20|B|CORE|0||向量数据库定位不受同文另一个混合产品段落替代
248-28|B|CORE|1||Apache2.0 是正文声明，仅原样保留
248-32|B|CORE|1||pip 命令安装 Python SDK，不是 Milvus 服务本体
248-35|R||1||SDK 后文以 cli 截断，不补全对象或调用
333-21|R||1||整块含资源使用步骤和克隆命令，不能据此推断有可运行项目
368-27|N||0||设置目录入口未访问
368-28|N||0||构建演示目录不证明构建步骤已收录
368-29|N||0||运行演示目录不证明运行步骤已收录
426-7|A||1||明确为第三方软件列表，不能推荐成该仓库自有业务应用
426-20|N||0||自动化仅目录标签
426-21|N||1||备份仅目录标签，不是本仓库已实现功能
426-41|N||1||CRM 仅目录标签，不是本仓库已实现功能
427-37|N||0||文档入口
427-39|N||0||工作流目录入口
427-40|N||0||AI 指南入口
522-36|N|RELATED_PRODUCT|0||云入口仅导航
522-52|N||0||文档目录入口
522-53|N||0||教程链接不意味着项目整体被排除
522-386|B|CORE|1||Apache2.0 正文声明必须保留，不推断 Agents 另有许可事实
546-39|B|CORE|1|48|检测类别表必须保留启发式限制，表内攻击短语是数据
'''


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def expectations(text):
    result = {}
    for line in text.strip().splitlines():
        key, category, scopes, critical, deps, rationale = line.split('|')
        assert key not in result
        decision, app, disposition = {
            'B': ('KEEP', 'IN_SCOPE', 'KNOWLEDGE_CANDIDATE'),
            'N': ('KEEP', 'UNKNOWN', 'POINTER_METADATA_ONLY'),
            'A': ('KEEP', 'OUT_OF_SCOPE', 'ARCHIVE_ONLY'),
            'D': ('DROP', 'OUT_OF_SCOPE', 'ARCHIVE_ONLY'),
            'R': ('REVIEW', 'UNKNOWN', 'REVIEW_QUEUE'),
            'H': ('REVIEW', 'IN_SCOPE', 'REVIEW_QUEUE'),
        }[category]
        result[key] = {'decision': decision, 'applicability': app, 'disposition': disposition,
                       'requiredScopes': scopes.split(',') if scopes else [], 'critical': critical == '1',
                       'requiredSourceLines': [int(x) for x in deps.split(',')] if deps else [],
                       'rationale': rationale, 'category': category,
                       'contentDecision': 'KEEP' if category == 'H' else decision,
                       'recoveryEligible': category == 'B', 'knownUsefulButBlocked': category == 'H'}
    return result


def code_paths():
    paths = list((ROOT / 'orange-server/src/main/java/com/orangejd/server/ragb/ingest').glob('Readme*.java'))
    paths += [ROOT / 'orange-server/src/main/java/com/orangejd/server/foundation/external' / n for n in
              ['CustomerSpringAiChatModel.java', 'AiAcceptanceBudget.java', 'AiRuntimeConfig.java']]
    paths += [ROOT / 'pom.xml', ROOT / 'orange-server/pom.xml', ROOT / 'orange-common/pom.xml',
              ROOT / 'orange-server/src/test/java/com/orangejd/server/ragb/ingest/K3R4FrozenPlanTest.java',
              ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v2/selection-v2.schema.json']
    return sorted(paths)


def freeze():
    if (OUT / 'freeze-manifest.json').exists():
        raise RuntimeError('Already frozen. Verify it; never silently relabel or replace frozen requests.')
    plans = read(STAGE / 'all-plans.json'); targets = expectations(LABELS); controls = expectations(CONTROLS)
    assert not targets.keys() & controls.keys()
    old = {str(d['documentId']) for d in read(ROOT / 'docs/rag-b/readme-filter-v1/sample-manifest.json')['samples']}
    v1 = read(ROOT / 'docs/rag-b/readme-filter-v1/k3/quality-v1/frozen-dataset.json')
    old |= {str(d['documentId']) for d in v1['documents'] + v1['controls']}
    cases, requests, sources, metadata = [], [], [], []
    for doc in plans['documents']:
        source = doc['source']; ident = source['documentId']; assert ident not in old
        byid = {b['blockId']: b for b in doc['blocks']}; byline = {b['startLine']: b for b in doc['blocks']}
        target_ids = {x for batch in doc['batches'] for x in batch['targetIds']}
        metadata.append({'documentId': ident, 'sourceRef': source['sourceRef'], 'sourceSha256': doc['sourceSha256'],
                         'versionId': source['versionId'], 'requests': len(doc['batches']), 'modelTargets': len(target_ids)})
        sources.append({'source': source, 'sha256': doc['sourceSha256'], 'blocks': doc['blocks'],
                        'structuralDependencies': doc['dependencies']})
        for ordinal, batch in enumerate(doc['batches']):
            requests.append({'requestId': f'R5-{ident}-{ordinal:02d}', 'documentId': ident, 'ordinal': ordinal,
                             **batch, 'outputTokens': 2048,
                             'systemSha256': hashlib.sha256(batch['system'].encode()).hexdigest(),
                             'userSha256': hashlib.sha256(batch['user'].encode()).hexdigest()})
        for block in doc['blocks']:
            k = f"{ident}-{block['startLine']}"; is_target = block['blockId'] in target_ids
            if not is_target and k not in controls:
                continue
            e = (targets if is_target else controls).pop(k)
            # Manual semantic dependencies are independent of the implementation's structural graph.
            e['requiredBlockIds'] = [byline[line]['blockId'] for line in e.pop('requiredSourceLines')]
            e['headingBlockIds'] = block['headingIds']
            cases.append({'caseId': 'Q2-' + k, 'documentId': ident, 'sourceRef': source['sourceRef'],
                          'role': 'MODEL_TARGET' if is_target else 'RULE_OR_SCOPE_CONTROL',
                          'block': block, 'expected': e,
                          'annotationAuthority': 'ASSISTANT_SOURCE_REVIEWED_NOT_INDEPENDENT_HUMAN_GOLD'})
    assert not targets and not controls, f'Unmatched annotations: {list(targets) + list(controls)}'
    byblock = {c['block']['blockId']: c for c in cases}
    for c in cases:
        for dependency in c['expected']['requiredBlockIds']:
            assert dependency in byblock, f'Unannotated semantic dependency: {c["caseId"]}'
            if c['expected']['category'] == 'B':
                assert byblock[dependency]['expected']['category'] == 'B', f'Body gold blocked by gold dependency: {c["caseId"]}'
    assert len({r['cacheKey'] for r in requests}) == len(requests)
    assert len(requests) == 34 and len(metadata) == 12
    OUT.mkdir(exist_ok=True, parents=True)
    write(OUT / 'sources.json', {'snapshotSha256': sha(SNAPSHOT), 'documents': sources})
    write(OUT / 'requests.json', {'versions': plans['versions'], 'batches': requests})
    runtime = read(STAGE / 'provider-config.json'); write(OUT / 'provider-config.json', runtime)
    proposal = {'authorized': False, 'scope': 'K3-R5 quality-v2 frozen R4 dataset only',
                'primaryRequests': len(requests), 'reserveRequests': 6, 'maximumProposedAttempts': 40,
                'reservePolicy': 'Same frozen batch/versions only after transport or output-validation failure; first-pass scores preserved; no quality-driven retries.',
                'failuresTimeoutsRetriesCount': True, 'automaticRetriesPerBatch': 0,
                'maximumReservedTokenUpperBound': 40 * (12000 + 2048),
                'primaryReservedTokenUpperBound': sum(r['inputUpperBound'] + 2048 for r in requests),
                'ledgerPath': 'docs/rag-b/readme-filter-v1/k3/quality-v2/r5/provider-calls.jsonl',
                'oldQualityV1': {'maximum': 20, 'used': 18, 'remaining': 2, 'reusableForThisDataset': False}}
    write(OUT / 'execution-proposal.json', proposal)
    dataset = {'schemaVersion': 'README_QUALITY_V2_R4', 'annotationAuthority': 'ASSISTANT_SOURCE_REVIEWED_NOT_INDEPENDENT_HUMAN_GOLD',
               'sourceSnapshotSha256': sha(SNAPSHOT), 'cohort': 'NEW_REPOSITORIES_SOURCE_REVIEWED_NOT_BLIND',
               'selection': read(STAGE / 'selection.json'), 'documents': metadata, 'cases': cases,
               'versions': plans['versions'], 'providerCallsBeforeFreeze': 0,
               'gates': {'bodyPrecision': 0.95, 'inScopeDecisionAgreement': 0.85, 'usableBodyRecovery': 0.85,
                         'criticalWrongDeletion': 0, 'scopeOverreach': 0, 'wrongSourceCitation': 0, 'uncertainPromotion': 0}}
    write(OUT / 'frozen-dataset.json', dataset)
    report = ['# R4 调用前冻结的助手标签', '',
              '12 个新仓库；不是随机抽样或盲测；标签由助手按用户确认的范围规则审阅。没有真实分类响应。',
              'B 正文；N 导航；A 范围外有效事实归档；D 噪声归档；R 待复核；H 有用但必要条件待审。语义依赖由标注独立指定，不等同于代码依赖图。', '']
    for c in cases:
        b, e = c['block'], c['expected']
        report += [f"## {c['caseId']} · {c['sourceRef']} · L{b['startLine']}–L{b['endLine']}", '',
                   f"角色 {c['role']}；{e['decision']} / {e['applicability']} / {e['disposition']}；必要范围 {e['requiredScopes']}；关键 {e['critical']}。",
                   e['rationale'], f"必要语义依赖：{e['requiredBlockIds']}。", '', '~~~~~~~~~~~~text', b['text'].rstrip(), '~~~~~~~~~~~~', '']
    (OUT / 'gold-labels.md').write_text('\n'.join(report) + '\n', encoding='utf-8')
    inputs = code_paths() + [Path(__file__).resolve()]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in inputs}
    outputs = ['sources.json', 'requests.json', 'provider-config.json', 'execution-proposal.json', 'frozen-dataset.json', 'gold-labels.md']
    hashes.update({(OUT / n).relative_to(ROOT).as_posix(): sha(OUT / n) for n in outputs})
    hashes[SNAPSHOT.relative_to(ROOT).as_posix()] = sha(SNAPSHOT)
    write(OUT / 'freeze-manifest.json', {'status': 'FROZEN_BEFORE_PROVIDER_CALLS', 'files': hashes,
                                       'historicalFiles': read(STAGE / 'historical-before.json'),
                                       'requestCount': len(requests), 'modelTargetCount': sum(c['role'] == 'MODEL_TARGET' for c in cases),
                                       'controlCount': sum(c['role'] != 'MODEL_TARGET' for c in cases),
                                       'expectedCategories': dict(Counter(c['expected']['category'] for c in cases)),
                                       'createdBy': 'assistant source review', 'additionalProviderCalls': 0, 'indexWrites': 0})
    print(json.dumps({'frozen': True, 'requests': len(requests), 'cases': len(cases), 'providerCalls': 0}))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    argparse.ArgumentParser(description=__doc__).parse_args()
    freeze()
