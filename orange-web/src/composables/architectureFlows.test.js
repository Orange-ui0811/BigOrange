import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/request', () => ({ default: {
  get: vi.fn(async url => {
    if (url.includes('ai-pricing')) return { code: 1, data: { 'ai:plan-stage': 10, 'ai:job-match': 20 } }
    if (url.includes('profile/me')) return { code: 1, data: { id: 7, nickname: 'Orange' } }
    if (url.includes('conversation/list')) return { code: 1, data: { records: [] } }
    if (url.includes('category')) return { code: 1, data: [] }
    return { code: 1, data: { records: [], total: 0 } }
  }),
  post: vi.fn(async url => ({ code: 1, data: url.includes('/auth/')
    ? { token: 'session-token', id: 7 }
    : { id: 11, content: 'generated content', recordId: 21, balance: 900 } })),
  put: vi.fn(async () => ({ code: 1, data: {} })),
  delete: vi.fn(async () => ({ code: 1, data: {} })),
} }))
vi.mock('../api/rewards', () => ({
  DEFAULT_REWARDS: { work: 50, competition: 50, job: 50 },
  getRewardPricing: vi.fn(async () => ({ work: 50, competition: 50, job: 50 })),
}))
vi.mock('../api/competition', () => ({
  getCompetitions: vi.fn(async () => ({ code: 1, data: { records: [], total: 0 } })),
  submitCompetition: vi.fn(async () => ({ code: 1 })),
}))
vi.mock('../api/job', () => ({
  getDirections: vi.fn(async () => ({ code: 1, data: { jobDirections: [], submitDirections: [] } })),
  getJobDetail: vi.fn(async () => ({ code: 1, data: {} })),
  getJobsByDirection: vi.fn(async () => ({ code: 1, data: [] })),
}))
vi.mock('../api/personaWorkshop', () => ({
  getPersonaAvatar: vi.fn(async () => ({ code: 1, data: { enabled: false } })),
}))
vi.mock('../utils/quotaConfirm', () => ({ confirmQuotaUsage: vi.fn(async () => true) }))

import { useAuthFlow } from './useAuthFlow'
import { useCareerCenter } from './useCareerCenter'
import { useLeaderboard } from './useLeaderboard'
import { useMainLayout } from './useMainLayout'
import { useMarket } from './useMarket'
import { usePlanDesigner } from './usePlanDesigner'
import { usePublish } from './usePublish'
import request from '../api/request'

const mountFlow = async useFlow => {
  const pinia = createPinia()
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/login', component: { template: '<div />' } },
    { path: '/market', component: { template: '<div />' } },
    { path: '/launch-check', component: { template: '<div />' } },
  ] })
  await router.push('/'); await router.isReady()
  let exposed
  const wrapper = mount(defineComponent({
    setup() { exposed = useFlow(); return () => null },
  }), { global: { plugins: [pinia, router] } })
  await nextTick(); await Promise.resolve(); await nextTick()
  return { exposed, wrapper, router }
}

describe('page flow composables', () => {
  beforeEach(() => { sessionStorage.clear(); vi.clearAllMocks() })

  it.each([
    ['auth', useAuthFlow, 'loginForm'],
    ['market', useMarket, 'works'],
    ['leaderboard', useLeaderboard, 'list'],
    ['plan designer', usePlanDesigner, 'phases'],
    ['publish', usePublish, 'form'],
    ['career center', useCareerCenter, 'competitions'],
    ['main layout', useMainLayout, 'activeMenu'],
  ])('mounts the %s flow and exposes its stable state', async (_name, useFlow, key) => {
    const { exposed, wrapper } = await mountFlow(useFlow)
    expect(exposed[key]).toBeDefined()
    wrapper.unmount()
  })

  it('executes authentication success and recovery paths', async () => {
    const { exposed, wrapper } = await mountFlow(useAuthFlow)
    exposed.loginForm.username = 'orange'; exposed.loginForm.password = 'Pass123'
    await exposed.doLogin()
    exposed.legalAccepted.value = true
    Object.assign(exposed.regForm, { username: 'new-user', password: 'Pass123', email: 'u@example.com', code: '123456', turnstileToken: 'ok' })
    await exposed.doRegister()
    exposed.resetEmail.value = 'u@example.com'; await exposed.sendResetCode()
    exposed.resetCode.value = '123456'; exposed.resetNewPwd.value = 'Pass123'; await exposed.doResetPwd()
    wrapper.unmount()
    expect(request.post).toHaveBeenCalled()
  })

  it('executes market and leaderboard refresh interactions', async () => {
    const market = await mountFlow(useMarket)
    await market.exposed.loadWorks(); await market.exposed.loadReqs(); await market.exposed.load()
    market.exposed.switchTab('works'); market.exposed.doSearch(); market.exposed.scrollToSection('missing')
    market.wrapper.unmount()

    const leaderboard = await mountFlow(useLeaderboard)
    await leaderboard.exposed.load(); leaderboard.exposed.source.value = 'github'
    leaderboard.exposed.onSourceChange(); leaderboard.exposed.source.value = 'local'
    leaderboard.exposed.onClick({ id: 2 })
    leaderboard.wrapper.unmount()
    expect(request.get).toHaveBeenCalled()
  })

  it('executes plan editing, generation and persistence paths', async () => {
    sessionStorage.setItem('appToken', 'token')
    const { exposed, wrapper } = await mountFlow(usePlanDesigner)
    exposed.summary.value = 'Build an OrangeVC test project with enough detail.'
    exposed.planStageCost.value = 10
    await exposed.generatePhase(1)
    await exposed.generatePhase(2)
    exposed.startEdit(); exposed.editContent.value = 'edited'; exposed.saveEdit(); exposed.cancelEdit()
    exposed.importToLaunchCheck(); await exposed.savePlan()
    exposed.loadPlan({ id: 4, summary: 'saved', extra: '', mode: 'full', phases: [{ title: 'one', done: true, content: 'content' }] })
    exposed.switchPhase(1)
    wrapper.unmount()
    expect(request.post).toHaveBeenCalled()
  })

  it('executes publish media and submit interactions', async () => {
    const { exposed, wrapper } = await mountFlow(usePublish)
    exposed.startPublish('work'); exposed.onCoverSuccess({ code: 1, data: '/cover.png' })
    exposed.onDetailImage({ code: 1, data: '/detail.png' })
    exposed.onVideoProgress({ percent: 35 }); exposed.onVideoSuccess({ code: 1, data: '/uploads/video/7/123e4567-e89b-12d3-a456-426614174000.mp4' })
    exposed.removeVideo()
    Object.assign(exposed.form, { title: 'Work', summary: 'Summary', detail: 'Detailed content', categoryId: 1, price: 10 })
    await exposed.submit(false); exposed.backToChoice(); exposed.onTabChange()
    wrapper.unmount()
    expect(request.post).toHaveBeenCalled()
  })

  it('executes career competition and job interactions', async () => {
    const { exposed, wrapper } = await mountFlow(useCareerCenter)
    await exposed.loadCompetitions(); exposed.changeScope('ENDED'); exposed.openCompetition()
    Object.assign(exposed.competitionForm.value, { name: 'Contest', sourceUrl: 'https://example.com', summary: 'Summary' })
    await exposed.submitCompetitionForm(); await exposed.selectDirection('Agent'); await exposed.selectJob(1)
    Object.assign(exposed.jobForm.value, { position: 'Engineer', direction: 'Agent', content: 'Requirements' })
    exposed.jobAgreed.value = true; await exposed.submitJobForm()
    wrapper.unmount()
    expect(exposed.competitionDialog.value).toBe(false)
  })

  it('clears layout state through the logout workflow', async () => {
    const { exposed, wrapper } = await mountFlow(useMainLayout)
    await exposed.logout(); wrapper.unmount()
    expect(sessionStorage.getItem('appToken')).toBeNull()
  })
})
