"""Freeze source-reviewed labels BEFORE any expanded provider evaluation. No model calls."""
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/readme-filter-v1/k3/quality-v1'
STAGE=ROOT/'_experiment_staging/readme-quality-v1'
INDICES={46:0,335:0,517:1,543:3,518:0,523:1,329:0,51:2,58:0,235:1,237:0,258:1,261:2,267:3,286:0}
# doc-line | decision | allowed KEEP kinds | allowed KEEP scopes | critical | source-review rationale
LABELS='''
46-12|KEEP|PURPOSE,ARCHITECTURE|CORE|0|框架定位，不是现成客服应用
46-26|KEEP|USAGE,TECHNICAL_TEXT|CORE|1|完整安装命令需连同原始前置说明保留
46-30|KEEP|SOURCE_POINTER,PURPOSE,TECHNICAL_TEXT|RELATED_PRODUCT|1|Deep Agents 为相关包，不能归入 LangGraph 本体
46-39|REVIEW|||1|同一块混合 LangGraph 与 LangSmith，现有 scope 枚举不能表达多产品归属，需要拆块
335-5|KEEP|TECHNICAL_TEXT,PURPOSE,LIMITATION|CORE|1|保留 50 Mb 原始单位，不换算成已经验证的运行内存
335-7|KEEP|TECHNICAL_TEXT,PURPOSE|CORE|0|语言绑定是完整能力声明
335-9|KEEP|PURPOSE,TECHNICAL_TEXT|CORE|0|应用场景原文，不推导这些业务产品已实现
335-11|KEEP|TECHNICAL_TEXT,PURPOSE,LIMITATION|PLATFORM_SPECIFIC,CORE|1|运行平台范围不能代替具体硬件下限
517-71|KEEP|LIMITATION,TECHNICAL_TEXT|PLATFORM_SPECIFIC|1|IE11 需要 polyfill，不能变成直接兼容
517-75|KEEP|USAGE,SOURCE_POINTER,TECHNICAL_TEXT|RELATED_PRODUCT,PLATFORM_SPECIFIC,CORE|0|调试扩展的使用说明与入口
517-79|KEEP|USAGE,TECHNICAL_TEXT|CORE|0|UMD 加载说明应携带后文使用上下文
517-135|KEEP|LIMITATION,USAGE,TECHNICAL_TEXT|PLATFORM_SPECIFIC|1|Node 环境中 canvas 或 skia 为可选后端，不用于浏览器要求
543-27|DROP|||0|联系目录是社群导航，不是部署或知识能力
543-161|KEEP|LIMITATION,USAGE,TECHNICAL_TEXT|CORE,VERSION_SPECIFIC|1|先启动推理框架和模型的前置要求
543-238|KEEP|USAGE,TECHNICAL_TEXT|CORE|0|CLI 帮助入口有开发使用价值
518-4|KEEP|SOURCE_POINTER|RELATED_PRODUCT,CORE|0|仅保留云版入口，不承诺云功能或优惠
518-5|KEEP|SOURCE_POINTER|CORE|0|自托管文档入口，不补抓链接正文
518-7|KEEP|SOURCE_POINTER|RELATED_PRODUCT,CORE|0|版本价格入口，不当作已核实价格和许可
518-128|KEEP|TECHNICAL_TEXT,SOURCE_POINTER,PURPOSE|RELATED_PRODUCT,ENTERPRISE,PLATFORM_SPECIFIC|1|AWS Premium 是部署产品选项，不能变成社区版必需条件
523-36|KEEP|USAGE|CORE,PLATFORM_SPECIFIC|1|这是网站预览，不是 Home Assistant 核心服务启动
523-42|KEEP|TECHNICAL_TEXT,USAGE|CORE|0|临时隔离博客是网站构建优化背景
523-44|KEEP|USAGE|CORE|1|isolate 必须附带后续 integrate 恢复步骤
523-48|KEEP|USAGE|CORE|1|恢复指令的介绍句需与下一原文代码一起保留
329-1|REVIEW|||1|社区能力与企业 AI 混合在一个块，先拆分作用域再进入候选证据
329-3|DROP|||0|求 Star 话术
329-38|KEEP|TECHNICAL_TEXT,PURPOSE|ENTERPRISE|1|标题明确企业版，不能丢失范围
51-42|KEEP|TECHNICAL_TEXT,LIMITATION,USAGE|CORE|1|认证前置考试要求，不能删掉资格条件
51-44|KEEP|TECHNICAL_TEXT,PURPOSE|CORE|0|证书展示声明需连同下一段学术诚信例外理解
51-46|KEEP|LIMITATION|CORE|1|撤销证书和违规例外必须保留
51-48|KEEP|TECHNICAL_TEXT,SOURCE_POINTER,PURPOSE|CORE,RELATED_PRODUCT|0|课程资源合集，不代表其平台实现了所有业务应用
58-7|KEEP|PURPOSE,TECHNICAL_TEXT|CORE|0|库的定位，不是现成网站
58-17|KEEP|TECHNICAL_TEXT,USAGE,PURPOSE|CORE|1|源码复制分发、框架兼容和零配置含义需要原样保留
58-41|KEEP|ARCHITECTURE,TECHNICAL_TEXT|CORE|0|库源码、文档站和 registry 的目录与技术栈
58-43|KEEP|USAGE|CORE|1|开发、构建、部署是不同命令，保留注释与顺序
235-29|DROP|||0|Slack 社群导流
235-30|DROP|||0|X 社交链接
235-33|DROP|||0|空白实体为布局
235-37|KEEP|PURPOSE,TECHNICAL_TEXT|CORE,VERSION_SPECIFIC|1|保留功能声明并附上停止维护警告；90ms 是原文声明不是本轮测量
237-18|DROP|||0|README 翻译导航不是 Agent 产品功能
237-20|KEEP|USAGE,TECHNICAL_TEXT|PLATFORM_SPECIFIC,CORE|1|稀疏克隆课程的完整命令，不能视为客服部署
258-29|KEEP|SOURCE_POINTER|CORE|0|配置和可选技能入口，不执行其中指令
258-35|KEEP|PURPOSE,LIMITATION,TECHNICAL_TEXT|CORE|1|个人记录和学习参考，不是部署指南，需要改实体与密钥
258-40|KEEP|ARCHITECTURE,LIMITATION,TECHNICAL_TEXT|CORE|1|仓库文件与运行生成文件区别，secrets 需自己生成
261-55|KEEP|USAGE|CORE|1|云账户交互命令示例，不使用示例账户代替用户信息
261-59|KEEP|USAGE|CORE,PLATFORM_SPECIFIC|0|示例输出是使用材料，不是当前用户的设备凭据和状态
261-71|KEEP|USAGE|CORE,PLATFORM_SPECIFIC|1|设备 IP 和 token 是参数占位符
261-73|KEEP|USAGE|CORE,PLATFORM_SPECIFIC|0|设备型号与支持信息是示例，不能泛化到全部设备
267-70|KEEP|SOURCE_POINTER|CORE|0|仅作目录导航元数据，不能分类为已有业务管理功能
267-71|KEEP|SOURCE_POINTER|CORE|0|仅作目录导航元数据，不能分类为协作应用已实现
267-73|KEEP|SOURCE_POINTER|CORE|0|仅作目录导航元数据，不能分类为备份应用已实现
286-8|KEEP|PURPOSE,TECHNICAL_TEXT|CORE|0|Laravel Horizon 的插件，不是独立队列服务
286-26|KEEP|LIMITATION,TECHNICAL_TEXT|VERSION_SPECIFIC|1|Laravel 与包版本对应表不能失去表头与版本
286-31|KEEP|USAGE|CORE|1|配置位置和服务提供者上下文
286-33|KEEP|USAGE|CORE|1|服务提供者配置示例
'''


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8'))


def main():
    require_path=OUT/'frozen-dataset.json'
    if require_path.exists():raise RuntimeError('Frozen dataset exists; create a versioned dataset instead of relabeling after model output')
    plans=read(STAGE/'all-plans.json');labels={}
    for row in LABELS.strip().splitlines():
        key,decision,kinds,scopes,critical,reason=row.split('|')
        labels[key]={'decision':decision,'kinds':kinds.split(',') if kinds else [],'scopes':scopes.split(',') if scopes else [],'critical':critical=='1','reason':reason}
    docs=[];cases=[];controls=[]
    for d in plans['documents']:
        ident=int(d['documentId']);m={b['blockId']:b for b in d['blocks']}
        if ident==561:
            for b in d['blocks']:
                if b['startLine'] in [168,173,180] or 'No RFID' in b['text'] or 'No authentication' in b['text']:
                    controls.append({'documentId':ident,'block':b,'expectedDecision':b['decision'],'reason':'单设备、认证或重量估计限制应在无模型调用时保持原文'})
            continue
        batch=d['batches'][INDICES[ident]]
        doc={k:d[k] for k in ['documentId','versionId','sourceRef','sourceSha256','cohort','focus']}
        doc.update({'targetIds':batch['targetIds'],'inputUpperBound':batch['inputUpperBound'],'selectedBatchOrdinal':INDICES[ident]})
        docs.append(doc)
        for bid in batch['targetIds']:
            b=m[bid];key=f"{ident}-{b['startLine']}";gold=labels.pop(key)
            cases.append({'caseId':'Q1-'+key,'documentId':ident,'cohort':d['cohort'],'sourceRef':d['sourceRef'],
                          'block':b,'expected':gold,'annotationAuthority':'ASSISTANT_SOURCE_REVIEWED_NOT_USER_CONFIRMED'})
    if labels or len(cases)!=54 or len(docs)!=15:raise ValueError('Dataset mismatch')
    for d in plans['documents']:
        for b in d['blocks']:
            if b['decision']=='REVIEW' and b['reason'] in ['INCOMPLETE_STRUCTURE','POSSIBLY_TRUNCATED_TEXT','TIME_SENSITIVE_OR_UNVERIFIED_CLAIM','CAPTION_AND_UNREAD_IMAGE']:
                controls.append({'documentId':int(d['documentId']),'block':b,'expectedDecision':'REVIEW','reason':'规则隔离内容不能被语义判断放行'})
    frozen={'schemaVersion':1,'annotationAuthority':'ASSISTANT_SOURCE_REVIEWED_NOT_USER_CONFIRMED','providerCallsBeforeFreeze':0,
            'sourceSnapshotSha256':digest(ROOT/'_experiment_staging/readme-filter-v1-20261029/snapshot.json'),
            'policySourceSha256':digest(ROOT/'orange-server/src/main/java/com/orangejd/server/ragb/ingest/ReadmeModelReview.java'),
            'primaryRequests':15,'maximumProposedRequests':20,'reserveRequests':5,'documents':docs,'cases':cases,'controls':controls}
    OUT.mkdir(parents=True,exist_ok=True);require_path.write_text(json.dumps(frozen,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    report=['# 分类质量验收：调用前冻结的原文标签','','助手审阅；用户尚未人工确认。8 个新增仓库与原 38 份仓库不重叠；新仓库内容已为标注阅读，不能称为盲测。',
            '54 个模型目标、15 个请求批次，另有规则控制项。原文类别与范围允许合理同义枚举；scope 仍按原始产品、平台和版本区分。','']
    for c in cases:
        b=c['block'];g=c['expected'];report += [f"## {c['caseId']} · {c['sourceRef']} · L{b['startLine']}–L{b['endLine']}",'',
            f"预期 {g['decision']}；KEEP 类型 {g['kinds']}；KEEP 范围 {g['scopes']}；关键项 {g['critical']}。",g['reason'],'',
            '~~~~~~~~~~~~text',b['text'].rstrip(),'~~~~~~~~~~~~','']
    (OUT/'gold-labels.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
    old={d['documentId'] for d in read(ROOT/'docs/rag-b/readme-filter-v1/sample-manifest.json')['samples']}
    assert all(int(d['documentId']) not in old for d in docs if d['cohort']=='new_repository')
    for name in ['docs/customer/context-v3/live-provider-calls.jsonl','docs/customer/workflow-v2/provider-calls.jsonl']:
        if sum(bool(x.strip()) for x in (ROOT/name).read_text(encoding='utf-8').splitlines()) not in [40,50]:raise ValueError('Unexpected historical ledger')
    print(json.dumps({'modelTargets':len(cases),'modelRequests':len(docs),'newRepositories':sum(d['cohort']=='new_repository' for d in docs),'controls':len(controls),'datasetSha256':digest(require_path)},ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');main()
