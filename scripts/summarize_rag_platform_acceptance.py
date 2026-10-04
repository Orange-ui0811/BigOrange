"""Combine live guards, component characterization and browser fixtures; no AI calls."""
import json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/rag-b/platform-acceptance'
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
def main():
    live=read('live-guards.json');components=read('component-chain-probes.json');browser=read('browser-fixture-results.json')
    suites=[]
    selected={'AppTrendControllerTest','PersonaPromptContextServiceTest','QuotaRequestIdentityTest','QuotaServiceImplTest','AiToolRouterTest'}
    for path in (ROOT/'orange-server/target/surefire-reports').glob('TEST-*.xml'):
        suite=ET.parse(path).getroot();name=suite.attrib['name'].split('.')[-1]
        if (name.startswith('Trend') or name in selected) and name!='TrendPlatformChainProbeTest':
            suites.append({'name':suite.attrib['name'],'tests':int(suite.attrib['tests']),'failures':int(suite.attrib['failures']),
                           'errors':int(suite.attrib['errors']),'skipped':int(suite.attrib['skipped']),
                           'failureCases':[{'name':c.attrib['name'],'message':c.find('failure').attrib.get('message')} for c in suite.findall('testcase') if c.find('failure') is not None]})
    stats={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
    blockers=[
        {'id':'B_PLATFORM_ENTRY','status':'BLOCKED','detail':'B已在实验台启用M3，但用户精准推荐仍固定使用TrendContextBuilder/平台VectorStore，DTO与编排没有B可选入口。','code':'orange-server/src/main/java/com/orangejd/server/service/TrendV2Analyzer.java:85'},
        {'id':'EMPTY_EVIDENCE','status':'FAIL','detail':'模拟模型给出未经支持项目时，空候选集合仍保留UNSUPPORTED推荐；检索失败分支也可保存并展示该结果。','code':'orange-server/src/main/java/com/orangejd/server/service/TrendEvidenceBinder.java:61'},
        {'id':'PERSONA_TO_RETRIEVAL','status':'FAIL','detail':'已确认画像进入Prompt，但不进入检索输入。仅画像、不重复填方向时检索NO_TOPIC。','code':'orange-server/src/main/java/com/orangejd/server/service/TrendContextBuilder.java:263'},
        {'id':'FINAL_CONSTRAINT_VALIDATION','status':'FAIL','detail':'合成模型输出Python项目时，即使请求明确只要Java，只要候选ID匹配仍会绑定并保存。目录语言匹配也主要用于加分，明确限制尚缺最终校验。','code':'orange-server/src/main/java/com/orangejd/server/service/TrendCandidateMatcher.java:21'},
        {'id':'BEGINNER_PREFERENCE','status':'FAIL','detail':'既有beginnerPreferenceBoostsUnifiedResultsWithoutFilteringGeneralKnowledge回归失败，手动资料加分影响新手偏好排序。','code':'orange-server/src/main/java/com/orangejd/server/service/TrendContextBuilder.java:199'},
        {'id':'B_PLATFORM_SOURCE_LIFECYCLE','status':'BLOCKED','detail':'平台上传、项目资料更新和删除仍写平台VectorStore，未找到自动驱动B文档版本/删除任务的桥接。B自身版本与有效来源过滤不能替代平台事件接入。','code':'orange-server/src/main/java/com/orangejd/server/service/impl/ProjectKnowledgeDocumentWriter.java:49'},
        {'id':'UNSUPPORTED_IMPORT','status':'FAIL','detail':'浏览器使用合成UNSUPPORTED结果，能显示未验证提示，但一键导入方案按钮仍可点击。','code':'orange-web/src/views/TrendAnalysis.vue:346'}]
    report={'decision':'NOT_ACCEPTED_FOR_FULL_B_PLATFORM_REPLACEMENT','date':'2026-10-02',
            'regression':{'stats':stats,'suites':suites},'liveGuards':live,'componentChain':components,
            'browserFixtureTests':{'stats':browser['stats'],'scope':'真实浏览器和前端，所有业务API为合成响应；不证明真实后端生成和落库'},
            'frontendComposableTests':{'tests':3,'passed':3,'scope':'前端提交、定价确认、结果处理；接口依赖替身'},
            'blockers':blockers,'newExternalAiRequests':0,'additionalProviderCostUsd':0,
            'notCertified':['B版完整请求的真实LLM生成与输出校验','真实登录账号的端到端扣费、重试退款和历史落库','平台上传/更新/删除到B的自动同步','A/B请求级路由与故障回退','持续负载和长期容量'],
            'nextAcceptanceCriteria':['保留A默认入口，提供经过权限控制的B完整链路实验入口，并记录检索代际和模型。',
              '已确认画像驱动查询与候选筛选，明确排除条件经过可验证的服务端校验。',
              '空证据或检索服务失败时不保存或展示为已验证真实项目推荐；区分失败和资料不足。',
              '候选验证与逐句事实支持分开；未验证项目不能直接进入正式方案。',
              '平台知识生命周期同步B版本，删除下架即时不可检索并可补偿。',
              '随后才使用独立合成测试账号进行真实外部模型、配额历史和持续负载验收。'],
            'limits':['探针使用合成错误模型输出验证防线，不表示这些错误在真实用户请求中的发生率。',
                      '5个JUnit探针执行成功；其中业务验收判据有失败，不能称为全部验收通过。',
                      '未更改A默认链路、B索引、平台推荐行为或真实账号资料。']}
    (OUT/'acceptance-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'decision':report['decision'],'regression':stats,'liveGuardPassed':sum(x['passed'] for x in live['cases']),
                      'browserFixturePassed':browser['stats']['expected'],'blockers':len(blockers)},ensure_ascii=False))
if __name__=='__main__':main()
