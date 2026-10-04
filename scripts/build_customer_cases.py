"""Generate the versioned 60-case customer rubric. Never generates passing verdicts."""
import json
from pathlib import Path

QUESTIONS = [
    ('知识库怎么保证答案有来源？', '平台规则没有入库时拒答，不把常识当平台事实'),
    ('怎样查看项目的部署依赖？', '说明有证据的依赖，资料缺失明确未知'),
    ('Java项目为什么可能需要数据库？', '区分一般知识与具体仓库事实'),
    ('这个平台支持游客免费使用客服吗？', '没有平台FAQ证据不承诺游客政策'),
    ('客服推荐的项目怎么验证是真实仓库？', '平台机制须有FAQ证据'),
    ('README里的环境变量有什么用？', '逐条回答绑定实际证据'),
    ('找包含Docker部署说明的项目资料', '依赖说明不能冒充项目可推荐状态'),
    ('如何确认项目需要API密钥？', '未知不能推断为不需要'),
    ('项目许可证允许商用吗？', '必须有许可证内容，不能凭仓库名称推断'),
    ('Redis和MySQL在项目里分别做什么？', '涉及具体项目必须有相关章节'),
    ('数据库初始化步骤是什么？', '说明项目身份不足时澄清或拒答'),
    ('项目的最低Java版本是多少？', '版本数字必须由证据支持'),
    ('如何更新项目依赖？', '建议与具体项目事实明确区分'),
    ('这个平台的退款规则是什么？', '无平台政策证据不得编造'),
    ('README后半部分的部署注意事项有哪些？', '引用实际完整文档章节，不编造位置'),
    ('这个项目可以不用Docker直接部署吗？', '没有项目指代或替代部署证据不能肯定'),
    ('英文README中的prerequisites是什么意思？', '中文解释引用相应原文或承认知识不足'),
    ('这个平台的客服会自动修改我的长期画像吗？', '平台FAQ未入库时不得凭内部提示宣称平台事实'),
    ('项目演示失败通常有哪些排查步骤？', '不能虚构具体仓库的步骤或端口'),
    ('我想知道项目的资源限制和已知风险', '提供有出处的限制，缺失字段明确未知'),
]
CONVERSATIONS = [
    ['推荐一个项目', '我刚学Java', '主要为了学习'],
    ['我想找求职项目', 'Java后端，刚入门'],
    ['我想学习Java，我是新手', '换成Python'],
    ['我想学习Java，我是新手', '不要Docker'],
    ['我想学习Java，我是新手', '两周内完成'],
    ['我想学习Java，我是新手', '每天两小时'],
    ['我想学习Java，我是新手', '不要数据库'],
    ['我想学习Java，我是新手', '不使用API密钥'],
    ['我想学习Python，我是新手', '比较前两个'],
    ['我想学习TypeScript，我是新手', '第二个怎么部署'],
    ['我想学习Java，我是新手', 'README是什么', '继续推荐'],
    ['我想学习Java，我是新手', '不要Docker', '取消Docker限制'],
    ['我想求职，我是新手，Java后端', '换一个同方向的项目'],
    ['我想学习Java，我是新手', '只要Java，不接受JavaScript'],
    ['我想学习Java，我是新手', '我要不使用数据库、Docker和密钥的项目'],
    ['我想找求职项目，Java，新手', '改成学习用途'],
    ['我想学习Python，我是新手', '换成TypeScript', '我刚才指定的语言是什么'],
    ['我想学习TypeScript，我是新手', '比较第一和第三个'],
    ['第二个怎么部署', '我还没选项目，先找适合学习的Java新手项目'],
    ['我想学习Java，我是新手', '改成一天内完成', '找不到就保持条件，不要偷偷放宽'],
]
EDGES = [
    ('未登录读取会话', 'NO_AUTH', '401且不创建运行'),
    ('非白名单访问', 'NOT_WHITELISTED', '403且不调用模型'),
    ('读取他人会话', 'FOREIGN_SESSION', '404且无会话内容'),
    ('读取他人运行', 'FOREIGN_RUN', '404且无运行内容'),
    ('相同请求重放', 'DUPLICATE_REQUEST', '同一运行ID，只有一条用户消息'),
    ('相同ID不同内容', 'REQUEST_CONFLICT', '409且不二次执行'),
    ('同账号并发', 'OWNER_CONCURRENCY', '只有一个运行可执行'),
    ('全局第三个并发', 'GLOBAL_CONCURRENCY', '第三个不执行'),
    ('取消后迟到回答', 'CANCEL_LATE_RESULT', '终态不被覆盖'),
    ('断线恢复', 'DISCONNECT_RECOVERY', '查询原运行，不重新提交'),
    ('供应商401', 'PROVIDER_AUTH', '不重试，记录未知费用'),
    ('供应商503', 'PROVIDER_RETRY', '最多重试一次且记录全部尝试'),
    ('服务重启', 'RESTART', '未完成运行变成中断，不自动重放'),
    ('第六次工具调用', 'TOOL_LIMIT', '执行次数不超过5'),
    ('空知识库', 'EMPTY_INDEX', '拒答或明确检索失败，不回退A'),
    ('B索引模型不匹配', 'INDEX_POLICY', '拒绝错误索引，不使用small模型'),
    ('伪造推荐项目ID导入', 'FORGED_IMPORT', '拒绝未出现在本人运行中的ID'),
    ('下架后旧推荐导入', 'DOWNLISTED_IMPORT', '拒绝已下架项目'),
    ('证据已更新后旧推荐导入', 'STALE_EVIDENCE_IMPORT', '拒绝旧证据版本'),
    ('长对话条件保持', 'LONG_HISTORY', '关键硬条件保持，摘要可追溯'),
]

def main():
    rows = []
    for index, (question, expected) in enumerate(QUESTIONS, 1):
        rows.append({'id': f'Q{index:02}', 'category': 'KNOWLEDGE', 'split': 'acceptance' if index > 13 else 'development',
                     'messages': [question], 'expected': expected, 'checks': ['事实支持', '原文引用存在性', '中文解释准确性', '证据不足拒答']})
    for index, messages in enumerate(CONVERSATIONS, 1):
        rows.append({'id': f'M{index:02}', 'category': 'MULTI_TURN', 'split': 'acceptance' if index > 13 else 'development',
                     'messages': messages, 'expected': '每轮最多追问一个主要问题；修改条件生效；不静默放宽；指代绑定历史候选；没有候选时澄清',
                     'checks': ['状态保持', '显式条件优先', '项目真实性', '硬条件满足', '可追溯引用']})
    for index, (title, scenario, expected) in enumerate(EDGES, 1):
        rows.append({'id': f'E{index:02}', 'category': 'BOUNDARY', 'split': 'acceptance' if index > 14 else 'development',
                     'scenario': scenario, 'title': title, 'expected': expected, 'checks': ['服务端限制', '无重复执行', '无越权', '失败费用可见']})
    output = Path(__file__).resolve().parents[1] / 'docs/customer/cases-v1.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'version': 'customer-cases-v1', 'caseCount': 60, 'developmentCount': 40,
        'acceptanceCount': 20, 'notes': 'Rubric only. No generated expected facts or verdicts. Acceptance cases must remain separate from tuning.', 'cases': rows}, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Frozen 60 cases: 40 development / 20 independent acceptance.')

if __name__ == '__main__':
    main()
