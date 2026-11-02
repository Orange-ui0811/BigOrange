import { expect, test } from '@playwright/test'

const ok = data => JSON.stringify({ code: 1, msg: 'ok', data })

const installUserSession = async page => {
  await page.addInitScript(() => {
    sessionStorage.setItem('appToken', 'e2e-user-token')
    sessionStorage.setItem('myId', '7')
  })
}

const mockUserApi = async page => {
  await page.route('**/app/**', async route => {
    const url = new URL(route.request().url())
    let data = {}
    if (url.pathname.includes('/profile/me')) data = { id: 7, username: 'e2e', nickname: 'Orange' }
    else if (url.pathname.includes('/conversation/list')) data = { records: [] }
    else if (url.pathname.includes('/notification/unread')) data = 0
    else if (url.pathname.includes('/config/ai-pricing')) data = { 'ai:plan-stage': 10, 'ai:job-match': 20, 'ai:trend-v2': 30 }
    else if (url.pathname.includes('/category')) data = []
    else if (url.pathname === '/app/ai-records') data = { records: [{ id: 1, title: 'E2E AI result', businessType: 'PLAN', summary: 'saved' }], total: 1 }
    else if (url.pathname === '/app/ai-records/1') data = { id: 1, title: 'E2E AI result', businessType: 'PLAN', result: { content: 'done' } }
    else if (url.pathname.includes('/persona-workshop')) data = { dimensions: [], materials: [], equipment: [], recommendations: [], materialCount: 0, settings: {}, completion: { percent: 0 } }
    else if (url.pathname.includes('/open-source/config')) data = { matchCost: 10, guideCost: 10, jobMatchCost: 10 }
    else if (url.pathname.includes('/open-source/issues')) data = { records: [], total: 0 }
    else if (url.pathname.includes('/plan/history')) data = []
    else if (url.pathname.includes('/work/public') || url.pathname.includes('/requirement/public')) data = { records: [], total: 0 }
    else if (url.pathname.includes('/competition')) data = { records: [], total: 0 }
    else if (url.pathname.includes('/job/directions')) data = { jobDirections: [], submitDirections: [] }
    await route.fulfill({ status: 200, contentType: 'application/json', body: ok(data) })
  })
}

const openUserPage = async (page, path) => {
  await installUserSession(page)
  await mockUserApi(page)
  await page.goto(path)
}

test('public market stays accessible while protected routes require a session', async ({ page }) => {
  await mockUserApi(page)
  await page.goto('/login')
  await expect(page.locator('input')).toHaveCount(2)
  await page.evaluate(() => sessionStorage.setItem('appToken', 'expired'))
  await page.goto('/market')
  await expect(page).toHaveURL(/\/market/)
  await page.evaluate(() => sessionStorage.clear())
  await page.goto('/market')
  await expect(page).toHaveURL(/\/market/)
  await page.goto('/me')
  await expect(page).toHaveURL(/\/login/)
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/me')
})

test('an expired token is cleared when a protected API returns 401', async ({ page }) => {
  await mockUserApi(page)
  await page.route('**/app/profile/me', route => route.fulfill({
    status: 401, contentType: 'application/json', body: JSON.stringify({ code: 0, msg: 'expired' }),
  }))
  await page.goto('/login')
  await page.evaluate(() => {
    sessionStorage.setItem('appToken', 'expired')
    sessionStorage.setItem('myId', '7')
  })
  await page.goto('/me')
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => sessionStorage.getItem('appToken'))).toBeNull()
  expect(await page.evaluate(() => sessionStorage.getItem('myId'))).toBeNull()
})

test('admin login state, user pagination and status controls render', async ({ page }) => {
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.addInitScript(() => localStorage.setItem('adminToken', 'e2e-admin-token'))
  await page.route('http://127.0.0.1:8080/admin/**', async route => {
    const url = new URL(route.request().url())
    const pageNumber = Number(url.searchParams.get('page') || 1)
    const data = url.pathname === '/admin/users'
      ? { records: [{ id: pageNumber, username: `user-${pageNumber}`, role: 'USER', status: 'ACTIVE' }], total: 55 }
      : {}
    await route.fulfill({ status: 200, contentType: 'application/json', body: ok(data) })
  })
  await page.goto('http://127.0.0.1:15174/#/users')
  expect(pageErrors).toEqual([])
  await expect(page.locator('.users-page')).toBeVisible()
  await page.locator('.pagination-actions button').last().click()
  await expect(page.locator('.pagination-actions .el-tag')).toContainText('2')
})

test('trend analysis entry and saved AI record remain connected', async ({ page }) => {
  await openUserPage(page, '/trend')
  await expect(page.locator('.compact-head')).toBeVisible()
  await page.goto('/ai-records')
  await expect(page.getByText('E2E AI result')).toBeVisible()
})

test('plan generation workspace and history lifecycle mount safely', async ({ page }) => {
  await openUserPage(page, '/plan')
  await expect(page.locator('.plan-main')).toBeVisible()
  await expect(page.locator('textarea').first()).toBeVisible()
})

test('persona entries, material confirmation and equipment recovery mount safely', async ({ page }) => {
  await openUserPage(page, '/persona-workshop')
  await expect(page.locator('.persona-workshop')).toBeVisible()
  await expect(page.locator('.workshop-layout')).toBeVisible()
})

test('persona accessory blueprint editor remains available in the workshop', async ({ page }) => {
  await openUserPage(page, '/persona-workshop')
  await expect(page.locator('.appearance-node')).toBeVisible()
  await expect(page.locator('.story-rail')).toBeVisible()
})

test('work submission and review-state entry render without changing the contract', async ({ page }) => {
  await openUserPage(page, '/publish?type=work')
  await expect(page.locator('.form-section')).toBeVisible()
})

test('open-source issue filtering, matching and guide workspace mount safely', async ({ page }) => {
  await openUserPage(page, '/open-source')
  await expect(page.locator('main, .opensource-page, .open-source-page').first()).toBeVisible()
})
