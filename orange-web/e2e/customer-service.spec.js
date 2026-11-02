import { test, expect } from '@playwright/test'
const screenshotDirectory = process.env.CUSTOMER_E2E_SCREENSHOTS || '../docs/customer/acceptance'

// UI fixtures only: no real account, outbound model call or production mutation.
async function setup(page, enabled) {
  let submitted = 0, finished = false, deleted = false
  const answer = { answer: '找到符合当前条件的项目。\\n\\n以下是本轮项目。', status: 'VERIFIED_PROJECTS',
    recommendationReport: {direction:'AI应用开发包含RAG、智能体等实践方向。',approach:'建议从可演示的小功能开始。',scopeNote:'本轮不包含市场增长率统计。',timeline:'半年是用户预算上限，不代表项目一定能完成。',learning:[{key:'BEGINNER_PROJECT:1',name:'acceptance/java-demo',personalized:true,steps:['先核对并运行示例','实现小改造','补充测试与演示']}]},
    rankingNotice: '语义排序暂不可用，本次按已确认条件、基础偏好和项目质量筛选。',
    projects: [{ type: 'BEGINNER_PROJECT', id: 1, name: 'acceptance/java-demo', url: 'https://github.com/acceptance/java-demo',
      language: 'Java', difficulty: '入门', duration: '未知', status: 'CANDIDATE', description: '用于界面验收的合成项目', analysisReason:'适合练习接口组织与日志追踪', modificationPlan:'增加一个完整的接口测试示例', mainRisks:'先确认本地环境与依赖', quality:81, importable: false }],
    references: [{ evidenceId: 'E1', quote: '项目支持本地运行，但部署前需要准备完整的依赖和配置。', title: 'java-demo', sectionPath: '部署', documentId: 1, chunkId: 1 }],
  }
  await page.addInitScript(() => {
    sessionStorage.setItem('appToken', 'synthetic-customer-token')
    sessionStorage.setItem('myId', '900001')
    localStorage.setItem('tour_done_trend_900001', '1')
  })
  await page.route('**/app/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path.endsWith('/customer/capabilities')) data = { enabled, accountId: 900001, model: 'deepseek-flash', thinking: false }
    else if (path.endsWith('/customer/sessions/1') && route.request().method() === 'DELETE') { deleted = true; data = null }
    else if (path.endsWith('/customer/sessions')) data = route.request().method() === 'POST' ? { id: 1 } : deleted ? [] : [{ id: 1, title: '验收会话' }]
    else if (path.endsWith('/sessions/1/messages')) data = { messages: submitted ? [
      { id: 1, role: 'user', content: '推荐Java项目' }, ...(finished ? [{ id: 2, role: 'assistant', content: JSON.stringify(answer), run_duration_ms: 2794, metrics_json: JSON.stringify({ tokenUsage: { inputTokens: 100, outputTokens: 25, totalTokens: 125, calls: 1, complete: true, models: ['deepseek-flash'] }, costUsd: 'UNKNOWN' }) }] : []),
    ] : [], state: { language: 'Java' }, activeRun: submitted && !finished ? { id: 'r1', status: 'RUNNING' } : null }
    else if (path.endsWith('/sessions/1/runs')) { submitted++; data = { id: 'r1', status: 'RUNNING', stage: 'SEARCHING' } }
    else if (path.endsWith('/runs/r1/events')) {
      await route.fulfill({ status: 200, contentType: 'text/event-stream', body: 'event: status\ndata: {"id":"r1","status":"RUNNING","stage":"SEARCHING"}\n\n' }); return
    } else if (path.endsWith('/runs/r1')) { finished = true; data = { id: 'r1', status: 'SUCCEEDED', stage: 'SUCCEEDED' } }
    else if (path.includes('/profile/me')) data = { id: 900001, username: 'synthetic', nickname: '验收账号' }
    else if (path.includes('/config/ai-pricing')) data = { trendV1: 50, trendV2: 50 }
    else if (path.includes('/quota/balance')) data = { balance: 1000 }
    else if (path.includes('/trend/graph')) data = { thisWeek: { nodes: [], edges: [] }, lastWeek: { nodes: [], edges: [] } }
    else if (path.includes('/trend/history')) data = []
    else if (path.includes('/persona-workshop')) data = { dimensions: [], equipment: [], settings: {}, completion: { percent: 0 } }
    else if (path.includes('/job/directions')) data = { jobDirections: [], submitDirections: [] }
    else if (path.includes('/conversation/list')) data = { records: [] }
    else if (path.includes('/notification/unread')) data = 0
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 1, data }) })
  })
  await page.goto('/customer')
  const notice = page.getByRole('dialog', { name: 'OrangeVC 功能更新' })
  await notice.getByRole('button', { name: '知道了', exact: true }).click()
  return () => submitted
}
test('白名单客服提交、过程与证据展示，刷新恢复不重复运行', async ({ page }) => {
  await page.setViewportSize({width:1440,height:1000})
  const submissions = await setup(page, true)
  const customer = page.getByRole('region', { name: '项目推荐客服' })
  await expect(customer).toBeVisible()
  await customer.getByRole('textbox').fill('推荐Java项目')
  await customer.getByRole('button', { name: '发送', exact: true }).click()
  await expect(customer.getByRole('status')).toContainText('正在检索资料')
  await expect(customer.getByRole('link', { name: 'acceptance/java-demo' })).toBeVisible()
  await expect(customer.getByText('语义排序暂不可用，本次按已确认条件、基础偏好和项目质量筛选。')).toBeVisible()
  await expect(customer.getByText('尚未在项目专区发布，按已有资料推荐；已有分析并不等于本轮引用已核实')).toBeVisible()
  await expect(customer.getByText(/V4\.1 Flash|思考模式|Token|2\.8s/)).toHaveCount(0)
  await expect(customer.getByText('增加一个完整的接口测试示例')).toBeVisible()
  await expect(customer.getByRole('heading', {name:'领域洞察'})).toBeVisible()
  await expect(customer.getByRole('heading', {name:'学习路径'})).toBeVisible()
  await expect(customer.getByText('半年是用户预算上限，不代表项目一定能完成。')).toBeVisible()
  await expect(customer.locator('.assistant-markdown')).not.toContainText('\\n')
  await expect(customer.getByRole('button', {name:'条件或证据待核实，暂不能导入'})).toBeDisabled()
  await customer.getByText('查看证据与章节').click()
  await expect(customer.getByText('项目支持本地运行，但部署前需要准备完整的依赖和配置。')).toBeVisible()
  await expect(customer.getByRole('button', {name:'条件或证据待核实，暂不能导入'})).toBeDisabled()
  await page.reload()
  await expect(page.getByRole('link', { name: 'acceptance/java-demo' })).toBeVisible()
  await expect(customer.getByText(/Token|V4\.1 Flash|思考模式/)).toHaveCount(0)
  const user = await page.locator('.message-row.user').boundingBox()
  const userBubble = await page.locator('.user-text').boundingBox()
  const assistant = await page.locator('.message-row.assistant .message-body').boundingBox()
  expect(userBubble.x).toBeGreaterThan(assistant.x)
  expect(userBubble.x + userBubble.width).toBeGreaterThan(user.x + user.width - 70)
  await page.locator('.transcript').evaluate(el=>{el.scrollTop=0})
  await page.screenshot({ path: `${screenshotDirectory}/customer-v412-chat.png`, fullPage: true })
  expect(submissions()).toBe(1)
})
test('功能关闭明确显示客服不可用原因', async ({ page }) => {
  await setup(page, false)
  await expect(page.getByText('客服暂不可用，请稍后重试。')).toBeVisible()
  await expect(page.getByRole('button', { name: '重新检查客服' })).toBeVisible()
})

test('手机客服输入和历史可访问，页面不横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 844 })
  await setup(page, true)
  await page.getByRole('button', {name:'打开会话历史'}).click()
  await page.getByRole('button', {name:'新建会话'}).click()
  await expect(page.getByRole('textbox', { name: '发送给项目客服的消息' })).toBeVisible()
  await page.getByRole('button', { name: '打开会话历史' }).click()
  await expect(page.getByRole('button', { name: '新建会话' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name: '关闭历史' }).click()
  await page.screenshot({ path: `${screenshotDirectory}/customer-v412-mobile.png`, fullPage: true })
  await page.getByRole('textbox', {name:'发送给项目客服的消息'}).fill('推荐Java项目')
  await page.getByRole('button', {name:'发送',exact:true}).click()
  await expect(page.getByRole('link', {name:'acceptance/java-demo'})).toBeVisible()
  expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.locator('.transcript').evaluate(el=>{el.scrollTop=0})
  await page.screenshot({path:`${screenshotDirectory}/customer-v412-mobile-chat.png`,fullPage:true})
})

test('会话删除需确认，删除后刷新不恢复，取消保留', async ({ page }) => {
  await setup(page, true)
  await page.getByRole('button', {name:'删除会话 验收会话',exact:true}).click()
  let dialog = page.getByRole('dialog', {name:'删除会话',exact:true})
  await dialog.getByRole('button', {name:'保留会话',exact:true}).click()
  await expect(page.getByRole('button', {name:'验收会话',exact:true})).toBeVisible()
  await page.getByRole('button', {name:'删除会话 验收会话',exact:true}).click()
  dialog = page.getByRole('dialog', {name:'删除会话',exact:true})
  await dialog.getByRole('button', {name:'删除会话',exact:true}).click()
  await expect(page.getByRole('button', {name:'验收会话',exact:true})).toHaveCount(0)
  await expect(page.getByRole('heading', {name:'聊聊你想做的项目'})).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', {name:'验收会话',exact:true})).toHaveCount(0)
})

test('主导航保持菜单尺寸并可滚动到底部', async ({ page }) => {
  await page.setViewportSize({width:1440,height:640})
  await setup(page,true)
  const nav = page.locator('.sidebar-scroll-area')
  expect(await nav.evaluate(el=>getComputedStyle(el).overflowY)).toBe('auto')
  const items = page.locator('.sidebar-menu .el-menu-item')
  await expect(items).toHaveCount(8)
  const finalItem = items.last()
  await finalItem.scrollIntoViewIfNeeded()
  const bounds = await finalItem.boundingBox()
  expect(bounds.y + bounds.height).toBeLessThanOrEqual(640)
  expect(bounds.height).toBeGreaterThanOrEqual(42)
  expect(await nav.evaluate(el=>el.scrollHeight)).toBeGreaterThan(await nav.evaluate(el=>el.clientHeight))
})

test('客服目标可以多选并发送自定义目标，其他条件保留', async({page})=>{
  await setup(page,true)
  await page.getByRole('button',{name:'目标设置',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'项目目标',exact:true})
  await dialog.locator('label.el-checkbox').filter({hasText:/^学习$/}).click()
  await dialog.locator('label.el-checkbox').filter({hasText:/^求职$/}).click()
  await dialog.getByRole('textbox',{name:'自定义目标'}).fill('给家里的店做库存管理')
  const requestPromise=page.waitForRequest(req=>req.url().endsWith('/customer/sessions/1/runs') && req.method()==='POST')
  await dialog.getByRole('button',{name:'发送并保存目标'}).click()
  const request=await requestPromise
  expect(request.postDataJSON().message).toContain('学习；求职；给家里的店做库存管理')
  expect(request.postDataJSON().message).toContain('其他条件保持不变')
})
