"""Build the reviewable report from completed offline statistics."""
import json,statistics
from pathlib import Path
from summarize_analysis_customer_b import OUT,main as summarize
from customer_local_smoke import sql

def main():
    summarize();s=json.loads((OUT/'summary.json').read_text(encoding='utf-8'));rows=s['rows'];groups=s['groups']
    if len(rows)!=32 or not (OUT/'after.json').exists():raise RuntimeError('Experiment not complete; refuse final report')
    before=json.loads((OUT/'before.json').read_text(encoding='utf-8'));after=json.loads((OUT/'after.json').read_text(encoding='utf-8'))
    session_ids=[int(json.loads(p.read_text(encoding='utf-8'))['sessionId']) for p in OUT.glob('P*-*-customerB.json')]
    cleaned=int(sql('SELECT COUNT(*) FROM customer_session WHERE deleted_at IS NOT NULL AND id IN ('+','.join(map(str,session_ids))+')'))
    audit=dict(customerConfigUnchanged=before['customer']==after['customer'],modelConfigsUnchanged=before['snapshot']['models']==after['snapshot']['models'],
        ragConfigUnchanged=before['snapshot']['rag']==after['snapshot']['rag'],documentCountsUnchanged=all(before['snapshot'][k]==after['snapshot'][k] for k in ['documentsA','documentsB','generation']),
        indexVectorCountsUnchanged=all(before['snapshot']['indexes'][k]['num_docs']==after['snapshot']['indexes'][k]['num_docs'] for k in ['A','B']),
        syntheticSessions=len(session_ids),softDeletedSessions=cleaned,activeRunsAccount1=int(sql("SELECT COUNT(*) FROM customer_run WHERE account_id=1 AND status IN ('QUEUED','RUNNING')")),
        allTerminal=all(r['status'] in ['SUCCEEDED','FAILED','TIMED_OUT','CANCELLED','INTERRUPTED'] for r in rows if r['product']=='customerB'))
    (OUT/'audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
    a=groups['analysisB'];c=groups['customerB'];lines=['# 智能分析＋B 与智能客服＋B：同画像真实产品对照','',
      '结论：当前客服还不能稳定输出和智能分析一样完整、贴合需求且有本轮证据支持的项目卡片。共享前端组件已经具备同样的展示样式，差距主要在条件理解、候选选择、证据生成预算和卡片内容编排。智能分析卡片更完整，但也发现明确语言条件未强制执行、证据薄弱时继续补充功能与周期的缺陷。两侧均不适合据本轮结果宣称验收通过。','',
      '2026-10-03 实测 8 个合成画像，各重复两轮，共 32 个产品请求。智能分析 Gitee DeepSeek-V4-Pro；客服官方 deepseek-flash、思考开启、原生动作开启。B 为 bge-m3＋STRUCTURE_M3_512_768_64_V1＋HNSW_HYBRID，索引代际 20261002164953-ec113424；现有 B READY 文档 565 份，Redis 向量 8660 条。未重建索引。两轮不是 16 个独立场景。','',
      '同画像指两侧收到同样明确需求；分析直接接收结构化画像，客服必须从自然语言理解。为避免隐藏这个产品差异，没有给客服预填状态。账号确认画像为空，未使用真实历史。不同模型、提示词、候选池和约束规则均保留，因此不是单因素 RAG 或模型实验。分析通过实验接口进入实际分析器，不含积分和页面历史；客服通过真实会话运行接口。','',
      '## 整体结果','', '| 指标 | 智能分析＋B | 智能客服＋B |','|---|---:|---:|',
      f"|运行成功终态|{a['success']}/16|{c['success']}/16|",
      f"|返回推荐卡片的请求|{a['withCards']}/16|{c['withCards']}/16|",
      f"|卡片总数|{a['cards']}|{c['cards']}|",
      f"|卡片含引用|{a['withReferences']}/{a['cards']}|{c['withReferences']}/{c['cards']}|",
      f"|平均完整服务端链路耗时|{a['meanSeconds']:.2f}秒|{c['meanSeconds']:.2f}秒|",
      f"|有卡片请求平均耗时|{a['meanSecondsWithCards']:.2f}秒|{c['meanSecondsWithCards']:.2f}秒|",
      f"|样本 P95（最近秩）|{a['p95Seconds']:.2f}秒|{c['p95Seconds']:.2f}秒|",
      f"|实际底层生成模型调用，含失败和重试|{a['calls']}|{c['calls']}|",
      f"|输入／输出 Token|{a['inputTokens']}／{a['outputTokens']}|{c['inputTokens']}／{c['outputTokens']}|",
      f"|其中思考 Token，已包括在输出中|{a['reasoningTokens']}|{c['reasoningTokens']}|",
      f"|本轮生成调用估算金额|¥{a['estimatedCny']:.6f}|¥{c['estimatedCny']:.6f}|",'',
      '成功终态包含待澄清、无结果和证据生成降级，不能视为推荐质量通过。客服 3 次结构化理解失败、2 次主题待澄清，11 次推荐均因 EMPTY_MODEL_OUTPUT 降级；所有卡片的本轮引用均为空、均不可导入。客服 13 次成功终态均正确保存本轮期待的语言、最长周期及适用的 Docker 条件，但这不代表主题、目标与未支持的资源条件都完整，也不代表推荐正确。condition-checks.json 保留逐次结果。分析引用主要核验候选身份和链接，不代表逐事实正确。分析卡片组件默认允许导入，不能与客服的已校验可导入计数直接比较。','',
      '耗时从请求提交到结果返回，包括服务端检索、模型、校验；客服有约 0.5 秒轮询间隔及一次 0.25 秒终态复读，不包括测试会话创建和最终清理。未包含前端动画、页面渲染，未做浏览器验收或持续并发容量测试。客服更快、更便宜的数字同时包含失败和证据降级，不能解释为等质量优化。','',
      '## 八种画像逐项核对','', '|画像|智能分析两轮卡片数|客服两轮情况|内容判断|','|---|---|---|---|']
    findings={
      'P01_spring_beginner':'分析推荐用户认证教程和集成架构示例，内容完整但 E3 实际 Prompt 只有标题，功能与周期说明缺少本轮证据。客服给 Nacos、JeecgBoot、教程合集；大型基础设施与刚学、小功能目标不够贴合。',
      'P02_rag_career':'分析两轮均空。客服正确提取半年为 180 天，但两轮都拒绝项目主题，转入重复澄清，未检索推荐。多主题提取协议与单字符串校验需对齐；记录未保留原始动作，具体拒绝值类型无法回溯。',
      'P03_rag_python':'分析给 Quivr，基本介绍与 YAML/API 配置有实际证据；两个月适合完成属于建议而非已核实周期。客服给 Azure 文档资源、Headroom、Python 算法合集，本轮证据生成失败，不能视为完整 RAG 作品推荐。',
      'P04_agent_multi':'分析两轮分别选 Agent Teams/MonkeyCode 与 Pireel/MonkeyCode，非空项目集合改变。客服首轮条件理解失败，次轮理解双目标并返回 Uni-CLI、llm-space、Cindy；职业资料卡较丰富，但仍无本轮引用。',
      'P05_vue_no_docker':'分析推荐 Vue 配置生成器，一轮另带未核实语言/框架的 Canvas UI。客服能提取禁止 Docker，但另带便签和 deck.gl 探索候选；后者缺语言、主题、周期及 Docker 核实，不应等同符合明确限制。',
      'P06_data_career':'分析首轮给 Doris，业务库主语言标记 Java，与只要 Python 冲突；解释成 Python 对接并不满足原条件。次轮空。客服给算法/教程/JSON 工具等泛候选，欠缺数据分析作品的具体证据。',
      'P07_custom_inventory':'分析两轮空；客服第一轮结构化失败，第二轮正确保存自定义库存目标、120天和禁止 Docker，却推荐 build-your-own-x、ToolJet、教程合集三张元数据未知卡。没有形成可核实库存小作品的具体介绍。',
      'P08_unavailable':'分析两轮空，符合无已核实项目的边界。客服首轮理解失败、次轮展示教程/JSON 工具/聊天仓库等待核实候选；没有保存“不能使用硬件”限制，也没处理更具体的“只推荐可核实符合条件”要求。该案例说明未知字段不能无限扩张探索池。'}
    for case in sorted({r['case'] for r in rows}):
        ar=sorted([r for r in rows if r['case']==case and r['product']=='analysisB'],key=lambda r:r['round']);cr=sorted([r for r in rows if r['case']==case and r['product']=='customerB'],key=lambda r:r['round'])
        lines.append('|'+case+'|'+'／'.join(str(r['cardCount']) for r in ar)+'|'+'；'.join(f"第{r['round']}轮 {r['resultStatus']}，{r['cardCount']}张" for r in cr)+'|'+findings[case]+'|')
    lines+=['','所有项目身份按返回的规范化仓库链接核对；人工审阅只判断已保存的 Prompt 与业务事实支持范围，未启动新的模型裁判或访问所有仓库最新版。上述是具体缺陷分析，不捏造综合事实正确率。','',
      '## 卡片丰富度：相同样式，不同内容','', '|共享组件可见字段|分析卡片有内容|客服卡片有内容|','|---|---:|---:|']
    labels=['描述','实践参考','推荐理由','前置条件','已有分析','改造方向','能力证明','风险与门槛','本轮证据']
    for field,label in zip(a['fieldPresence'],labels):lines.append(f"|{label}|{a['fieldPresence'][field]}/{a['cards']}|{c['fieldPresence'][field]}/{c['cards']}|")
    lines+=['',f"九个字段字符量平均为分析 {a['meanCardChars']:.1f}、客服 {c['meanCardChars']:.1f}。不含全局趋势和学习路径、条件缺口及引用文字。字段有值包含通用说明，不能等同具体事实丰富度。",'',
      '分析所有卡片都有四项核心内容；客服职业资料可额外展示已有分析、改造、能力证明和风险，个别卡片比分析更长，但抓取仓库只有通用描述、通用推荐理由与未知提示。客服推荐理由主要由规则模板组成，不会自动变成分析中按画像生成的完整理由。两侧共享 ProjectRecommendationCard.vue，所以瓶颈主要在后台内容供应。分析主卡片当前没有顶层 url，链接保存在 references；标题和“查看仓库”使用顶层 url，存在链接展示适配问题。','',
      '## 稳定性与原因','']
    for side,label in [('analysisB','分析'),('customerB','客服')]:
        rs=[r for r in s['repeatStability'] if r['product']==side];nonempty=[r for r in rs if not r['emptyBoth']]
        lines.append(f"- {label}：两轮均空 {sum(r['emptyBoth'] for r in rs)}/8；至少一轮非空的 {len(nonempty)} 个场景中，项目集合相同 {sum(r['exact'] for r in nonempty)} 个。空结果稳定不代表推荐稳定。")
    paired=s['pairedOverlap'];lines+=['',f"两产品同画像同轮共 16 对，有共同项目的 {sum(r['intersection']>0 for r in paired)} 对；两侧都空的对照不当作项目一致。详见 summary.json。",'',
      f"客服 {c['calls']} 次模型尝试中 {c['outputCapHits']} 次返回输出 Token 达到或超过配置的 1500。成功进入推荐的请求全部缺本轮证据，多条证据调用恰好 1500 输出/1500 思考/正文为空。当前适配器丢弃 finish_reason，不能逐次严格断言截断，但输出预算挤占是强烈且可重复的线索。原生动作失败后的 JSON 回退继续沿用同一低预算和思考配置。不能将这种失败归因于 bge-m3 检索质量。",'',
      '候选方面，客服先选三个项目再生成证据；文档资源、通用算法或缺元数据仓库可能挤占真正作品。未知条件只扣分、缺少主题最低相关性和“只要已核实”的模式开关，会产生不合理探索推荐。智能分析则将语言当加分项、业务候选独立加入，能写出更好看的理由，却不能保证硬条件。','',
      '## 建议的修复次序','',
      '1. 首先修复客服模型返回协议与预算：保留实际响应模型、finish_reason、截断类型和各阶段用量；动作提取使用简洁严格 Schema 和足够的可见输出预算，思考强度按任务标定，不以全局开关替代实验。将证据生成与核验的预算分开，超限时明确降级，不发布未校验内容。',
      '2. 条件支持多主题，并对齐提取 Schema、Java 校验和检索查询；同义表达与复合画像作为确定性回归。保存拒绝值的安全类型与原因；用户明确要求只要已核实时禁止未知探索候选，未支持资源条件应明示并澄清。',
      '3. 统一严格候选筛选、语义相关性最低门槛、项目用途分类；教程/目录/基础设施与可做作品区分，允许选择子任务但不能冒充仓库自身满足周期或语言。补齐外部仓库可信元数据，先检查候选证据覆盖再形成最终三项。',
      '4. 复用卡片数据合同：功能、为什么适合、最小实践范围、前置依赖、风险、改造与能力证明。已有分析与本轮核验分开标注；模型只能补充证据支持的事实，实践建议标为建议。不能靠编造信息凑字段。',
      '5. 智能分析同时修复硬条件执行、薄证据事实防线与顶层链接映射。修复后重跑原开发案例，再使用未参与调参的独立画像验收；不能用本轮重复结果证明生产稳定。','',
      '## 费用与边界','',
      f"本轮共 {a['calls']+c['calls']} 次底层生成调用，低于授权 112 次上限；未做额外评分调用。全部返回用量的已知估算合计 ¥{a['estimatedCny']+c['estimatedCny']:.6f}，不是实际充值余额扣费。Gitee 沿用此前已记录的请求 SKU 标价（输入12/输出24元每百万），本轮网页重查未成功，故该侧金额是固定价格口径估算，不能当成已再次核实的实时账单。",'',
      'Flash 按本轮周末空闲时段人民币价：缓存命中输入0.02、未命中1、输出4元每百万，已核对[官方价格说明](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)。[Gitee 请求 SKU 页面](https://ai.gitee.com/serverless-api?model=DeepSeek-V4-Pro)。输出含思考，不重复计费；Embedding 用量未报告，未加入库、存储、翻译或服务器成本。客服运行记录金额仍为 UNKNOWN，报告是离线估算，不掩盖平台缺少费用计算的问题。','',
      '## 实验收尾','', '```json',json.dumps(audit,ensure_ascii=False,indent=2),'```','',
      '原模型与索引配置未修改；仅软删除本轮创建的 16 个测试会话，运行与步骤审计保留。测试脚本语法检查通过。未修改生产行为，未执行发布。','',
      '文件：protocol.md（口径）、cases.json（输入）、outputs.md（逐卡片原文）、summary.json（明细与统计）、before.json/after.json/audit.json（审计），及每次产品请求原始 JSON。']
    (OUT/'report.md').write_text('\n'.join(lines),encoding='utf-8')
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
