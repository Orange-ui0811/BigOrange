import { test, expect } from '@playwright/test'

// Explicit opt-in; credentials and tokens never enter traces, screenshots or report attachments.
test.use({ trace: 'off', screenshot: 'off', video: 'off' })
test('真实客服问答、项目与历史恢复', async ({ page }) => {
  test.skip(!process.env.CUSTOMER_LIVE_BROWSER_TOKEN, '需要已授权本地测试身份')
  test.setTimeout(150000)
  await page.addInitScript(({ token, login }) => {
    if (!login) { sessionStorage.setItem('appToken', token); sessionStorage.setItem('myId', '1') }
    localStorage.setItem('tour_done_trend_1', '1')
  }, { token: process.env.CUSTOMER_LIVE_BROWSER_TOKEN, login: !!process.env.CUSTOMER_LIVE_BROWSER_PASSWORD })
  if (process.env.CUSTOMER_LIVE_BROWSER_PASSWORD) {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名或注册邮箱').fill('admin')
    await page.locator('input[autocomplete="current-password"]').fill(process.env.CUSTOMER_LIVE_BROWSER_PASSWORD)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page).not.toHaveURL(/\/login/)
  }
  await page.goto('/customer')
  const notice = page.getByRole('dialog', { name: 'OrangeVC 功能更新' })
  await notice.getByRole('button', { name: '知道了', exact: true }).click()
  const customer = page.getByRole('region', { name: '项目推荐客服' })
  await expect(customer).toBeVisible()
  await customer.getByRole('button', { name: '新会话', exact: true }).click()
  await customer.getByRole('textbox').fill('我想学习Python，我是新手')
  await customer.getByRole('button', { name: '发送', exact: true }).click()
  await expect(customer.locator('.customer-project').first()).toBeVisible({ timeout: 100000 })
  await expect(customer.getByRole('button', { name: '发送', exact: true })).not.toBeDisabled()
  const name = await customer.locator('.customer-project a').first().innerText()
  await page.reload()
  await expect(customer.getByRole('link', { name, exact: true }).first()).toBeVisible()
  const importButton = customer.getByRole('button', { name: '带入方案编辑器', exact: true }).first()
  if (await importButton.isVisible()) {
    await importButton.click()
    await expect(page).toHaveURL(/\/plan/)
    expect(await page.getByPlaceholder('描述你的项目想法，越详细越好...').inputValue()).toContain(name)
  }
})
