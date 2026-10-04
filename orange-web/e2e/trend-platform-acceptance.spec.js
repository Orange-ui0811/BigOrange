import { test, expect } from '@playwright/test'

// Real browser and frontend, synthetic API fixtures. This does not certify the live AI backend.
const result = unsupported => ({
  analysis: { trend: '验收趋势', prospect: '验收前景', demand: '验收需求', summary: '链路验收结果' },
  learning: { timeline: '两周', steps: ['运行示例'] },
  recommendations: [{ name: '验收项目', description: '合成资料项目', difficulty: '入门', score: 80,
    recommendReason: '用于验证展示', prerequisites: 'Java', evidenceStatus: unsupported ? 'UNSUPPORTED' : 'VERIFIED_CANDIDATE',
    references: unsupported ? [] : [{ name: 'acceptance/synthetic', url: 'https://github.com/acceptance/synthetic' }] }],
  historySaved: true, historyId: 900002,
})
async function setup(page, unsupported = false) {
  let submitted, saved = false
  await page.addInitScript(() => {
    sessionStorage.setItem('appToken', 'synthetic-acceptance-token')
    sessionStorage.setItem('myId', '900001')
    localStorage.setItem('tour_done_trend_900001', '1')
  })
  await page.route('**/app/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data = {}
    if (path.includes('/profile/me')) data = { id: 900001, username: 'synthetic', nickname: '验收账号' }
    else if (path.includes('/config/ai-pricing')) data = { trendV1: 50, trendV2: 50 }
    else if (path.includes('/quota/balance')) data = { balance: 1000 }
    else if (path.includes('/trend/graph')) data = { thisWeek: { nodes: [], edges: [] }, lastWeek: { nodes: [], edges: [] } }
    else if (path.includes('/trend/analyze-v2')) { submitted = route.request().postDataJSON(); saved = true; data = result(unsupported) }
    else if (path.includes('/trend/history')) data = saved ? [{ id: 900002, topics: submitted.topics, languages: submitted.languages,
      purpose: submitted.purpose, experience: submitted.experience, timeCommitment: submitted.timeCommitment, useRag: true, result: result(unsupported), createTime: '2026-10-02 21:00:00' }] : []
    else if (path.includes('/persona-workshop')) data = { dimensions: [], equipment: [], settings: {}, completion: { percent: 0 } }
    else if (path.includes('/conversation/list')) data = { records: [] }
    else if (path.includes('/notification/unread')) data = 0
    else if (path.includes('/job/directions')) data = { jobDirections: [], submitDirections: [] }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 1, data }) })
  })
  await page.goto('/trend')
  await page.getByRole('dialog', { name: 'OrangeVC 功能更新' }).getByRole('button', { name: '知道了', exact: true }).click()
  return () => submitted
}
async function choose(page, index, label) {
  await page.locator('.step-card .el-select').nth(index).click()
  await page.locator('.el-select-dropdown:visible').getByText(label, { exact: true }).click()
  await page.keyboard.press('Escape')
}
async function submit(page) {
  await choose(page, 0, 'RAG检索增强')
  await page.getByRole('button', { name: '下一步 →' }).click()
  await expect(page.getByText('为什么想做这个方向的项目？')).toBeVisible()
  await choose(page, 0, '🎓 学习 AI 编程知识')
  await page.getByRole('button', { name: '下一步 →' }).click()
  await expect(page.getByText('你的编程基础怎么样？')).toBeVisible()
  await choose(page, 0, '新手入门'); await choose(page, 1, 'Java')
  await page.getByRole('button', { name: '下一步 →' }).click()
  await expect(page.getByText('你每周能投入多少时间？')).toBeVisible()
  await choose(page, 0, '📅 业余投入（每周5-15h）')
  await page.getByRole('button', { name: '开始智能分析' }).click()
  await page.getByRole('button', { name: '确认分析', exact: true }).click()
  await page.getByRole('button', { name: '知道了', exact: true }).click()
  await expect(page.locator('.rec-name')).toHaveText('验收项目')
}
test('synthetic API: form, quota confirmation, grounded result and history recovery', async ({ page }) => {
  const errors = []; page.on('pageerror', e => errors.push(e.message))
  const submitted = await setup(page)
  await submit(page)
  expect(submitted()).toMatchObject({ topics: ['RAG检索增强'], languages: ['Java'], useRag: true, usePersona: false })
  await expect(page.locator('.ref-link')).toHaveAttribute('href', 'https://github.com/acceptance/synthetic')
  await page.locator('.trend-history-trigger').first().click()
  await page.locator('.history-item').first().click()
  await expect(page.locator('.rec-name')).toHaveText('验收项目')
  expect(errors).toEqual([])
})
test('synthetic API: unsupported evidence is labelled but remains importable', async ({ page }) => {
  await setup(page, true); await submit(page)
  await expect(page.getByText('本次资料未能支持推荐链接，当前内容为未验证建议。')).toBeVisible()
  await expect(page.locator('.import-plan-btn')).toBeEnabled()
  await expect(page.locator('.ref-link')).toHaveCount(0)
})
