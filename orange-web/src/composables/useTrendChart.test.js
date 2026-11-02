import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTrendChart } from './useTrendChart'

const request = vi.hoisted(() => ({ get: vi.fn() }))
const chart = vi.hoisted(() => ({ setOption: vi.fn(), off: vi.fn(), on: vi.fn(), resize: vi.fn(), dispose: vi.fn() }))
vi.mock('../api/request', () => ({ default: request }))
vi.mock('echarts', () => ({ init: vi.fn(() => chart) }))

describe('useTrendChart', () => {
  beforeEach(() => { request.get.mockReset(); Object.values(chart).forEach(mock => mock.mockClear()) })

  it('loads graph data, renders nodes and releases the chart', async () => {
    request.get.mockResolvedValue({ code: 1, data: {
      insights: { hotTop3: [{ name: 'Agent' }] },
      thisWeek: { nodes: [{ id: 'a', name: 'Agent', group: 'AI 智能', totalStars: 1000,
        repoCount: 2, topLanguages: [{ name: 'Java' }] }], edges: [] },
      lastWeek: { nodes: [{ id: 'a', totalStars: 900 }], edges: [] },
    } })
    let feature
    const wrapper = mount(defineComponent({ setup() { feature = useTrendChart(); return () => null } }))
    feature.chartRef.value = document.createElement('div')
    await feature.loadGraph()
    expect(chart.setOption).toHaveBeenCalled()
    expect(feature.insightCards.value[0].items[0].name).toBe('Agent')
    expect(feature.formatNum(1000)).toBe('1.0K')
    wrapper.unmount()
    expect(chart.dispose).toHaveBeenCalledOnce()
  })
})
