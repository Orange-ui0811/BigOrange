import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AiHub from './AiHub.vue'

const { push, get } = vi.hoisted(() => ({
  push: vi.fn(),
  get: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

vi.mock('../api/request', () => ({
  default: { get },
}))

const mountHub = () => mount(AiHub, {
  global: {
    stubs: {
      PageTour: true,
      PixelOrangeLogo: true,
      'el-button': { template: '<button><slot /></button>' },
    },
  },
})

describe('AiHub moving leaderboards', () => {
  beforeEach(() => {
    push.mockReset()
    get.mockReset()
    get.mockImplementation(url => {
      if (url === '/app/leaderboard/github') return Promise.resolve({ code: 1, data: { records: [] } })
      if (url === '/app/beginner-projects/weekly') return Promise.resolve({ code: 1, data: [{ id: 1, descriptionZh: '新手项目', beginnerScore: 92, difficulty: 'BEGINNER', primaryLanguage: 'Vue' }] })
      if (url === '/app/career-projects/weekly') return Promise.resolve({ code: 1, data: [{ id: 2, summaryZh: '职业项目', overallScore: 88, bestRoleName: '前端工程师' }] })
      if (url === '/app/open-source/ticker') return Promise.resolve({ code: 1, data: [] })
      return Promise.resolve({ code: 1, data: { records: [] } })
    })
  })

  it('replaces the all-time row with beginner recommendations and adds career growth', async () => {
    const wrapper = mountHub()
    await flushPromises()

    expect(wrapper.text()).toContain('新手推荐')
    expect(wrapper.text()).toContain('职业成长')
    expect(wrapper.text()).toContain('新手项目')
    expect(wrapper.text()).toContain('职业项目')
    expect(wrapper.text()).not.toContain('长期热门')
    expect(get).toHaveBeenCalledWith('/app/beginner-projects/weekly')
    expect(get).toHaveBeenCalledWith('/app/career-projects/weekly')

    expect(wrapper.get('.ticker-track').attributes('style')).toContain('--ticker-duration: 30s')

    await wrapper.get('.beginner-ticker').trigger('click')
    expect(push).toHaveBeenCalledWith({ path: '/leaderboard', query: { source: 'beginner' } })
  })
})
