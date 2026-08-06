import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'
import LeaderboardPersonaCard from './LeaderboardPersonaCard.vue'

const api = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())
vi.mock('../api/personaWorkshop', () => ({ getPersonaWorkshop: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const mountCard = source => mount(LeaderboardPersonaCard, {
  props: { source },
  global: { plugins: [pinia], stubs: { OrangePersonaScene: true } },
})

describe('LeaderboardPersonaCard', () => {
  beforeEach(() => {
    usePersonaStore(pinia).reset()
    api.mockReset(); push.mockReset()
    localStorage.clear()
  })

  it('shows the current leaderboard context and emits a persona recommendation', async () => {
    api.mockResolvedValue({ code: 1, data: {
      dimensions: [{ code: 'INTEREST', entries: [{ id: 1, content: 'Agent' }] }],
      completion: { completedDimensions: 1 },
    } })
    const wrapper = mountCard('career')
    await flushPromises()

    expect(wrapper.text()).toContain('职业榜 · 个性推荐')
    await wrapper.get('.recommend-button').trigger('click')
    expect(wrapper.emitted('recommend')).toHaveLength(1)
  })

  it('sends an empty persona to the workshop instead of starting analysis', async () => {
    api.mockResolvedValue({ code: 1, data: { dimensions: [], completion: { completedDimensions: 0 } } })
    const wrapper = mountCard('github')
    await flushPromises()

    expect(wrapper.text()).toContain('请先去橙子画像工坊')
    await wrapper.get('.recommend-button').trigger('click')
    expect(push).toHaveBeenCalledWith('/persona-workshop')
    expect(wrapper.emitted('recommend')).toBeUndefined()
  })

  it('lets the user hide and restore the 3D orange model', async () => {
    api.mockResolvedValue({ code: 1, data: { dimensions: [], completion: { completedDimensions: 0 } } })
    const wrapper = mountCard('local')
    await flushPromises()

    expect(wrapper.find('.mini-stage').exists()).toBe(true)
    await wrapper.get('.model-visibility-button').trigger('click')
    expect(wrapper.find('.mini-stage').exists()).toBe(false)
    expect(localStorage.getItem('orangevc.leaderboardPersonaModelVisible')).toBe('false')
    expect(wrapper.get('.model-visibility-button').text()).toContain('显示3D橙子')
  })
})
