import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'
import TrendPersonaAnalysisCard from './TrendPersonaAnalysisCard.vue'

const api = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())
vi.mock('../api/personaWorkshop', () => ({ getPersonaWorkshop: api }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const mountCard = (props = {}) => mount(TrendPersonaAnalysisCard, {
  props: { useRag: true, ...props },
  global: { plugins: [pinia], stubs: { OrangePersonaScene: true } },
})

describe('TrendPersonaAnalysisCard', () => {
  beforeEach(() => {
    usePersonaStore(pinia).reset()
    api.mockReset()
    push.mockReset()
  })

  it('guides a user without persona data to the workshop', async () => {
    api.mockResolvedValue({ code: 1, data: { dimensions: [], completion: { completedDimensions: 0 } } })
    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain('请先去橙子画像工坊完善您的橙子形象')
    await wrapper.find('.persona-primary-toggle').trigger('click')
    expect(push).toHaveBeenCalledWith('/persona-workshop')
    expect(wrapper.emitted('update:usePersona')).toBeUndefined()
  })

  it('enables persona context and couples project preferences to knowledge retrieval', async () => {
    api.mockResolvedValue({ code: 1, data: {
      dimensions: [{ code: 'GOAL', entries: [{ id: 1, content: '学习 Agent' }] }],
      completion: { completedDimensions: 1, totalDimensions: 7, percent: 14 },
    } })
    const wrapper = mountCard({ usePersona: false, useRag: false })
    await flushPromises()

    await wrapper.find('.persona-primary-toggle').trigger('click')
    await wrapper.findAll('.analysis-options button')[1].trigger('click')
    expect(wrapper.emitted('update:usePersona')?.at(-1)).toEqual([true])
    expect(wrapper.emitted('update:useRag')?.at(-1)).toEqual([true])
    expect(wrapper.emitted('update:preferBeginnerProjects')?.at(-1)).toEqual([true])
    await wrapper.find('.start-analysis-button').trigger('click')
    expect(wrapper.emitted('analyze')).toHaveLength(1)
  })
})
