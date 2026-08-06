import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PersonaWorkshop from './PersonaWorkshop.vue'

const api = vi.hoisted(() => ({
  getPersonaWorkshop: vi.fn(),
  createPersonaEntry: vi.fn(),
  deletePersonaEntry: vi.fn(),
  updatePersonaEntry: vi.fn(),
  acceptPersonaMaterial: vi.fn(),
  rejectPersonaMaterial: vi.fn(),
  updatePersonaEquipment: vi.fn(),
  updatePersonaAccessoryBlueprint: vi.fn(),
  updatePersonaSettings: vi.fn(),
}))

vi.mock('../api/personaWorkshop', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const dimensions = [
  ['GOAL', '当前目标'],
  ['INTEREST', '兴趣方向'],
  ['SKILL', '技能与经验'],
  ['RESOURCE', '可投入资源'],
  ['GROWTH', '成长轨迹'],
].map(([code, label], index) => ({
  code,
  label,
  completed: index === 0,
  entries: index === 0 ? [{ id: 1, dimensionCode: code, content: '完成一个真实项目' }] : [],
}))

const equipment = dimensions.map((dimension, index) => ({
  slotCode: `${dimension.code}_SLOT`,
  dimension: dimension.code,
  itemCode: `${dimension.code}_ITEM`,
  name: `${dimension.label}装备`,
  visualMeaning: '稳定视觉含义',
  variants: ['CLASSIC'],
  variantCode: 'CLASSIC',
  colorHex: '#F37021',
  unlocked: index === 0,
  equipped: false,
}))

const mountWorkshop = () => mount(PersonaWorkshop, {
  global: {
    directives: { loading: () => {} },
    stubs: {
      OrangePersonaScene: true,
      PersonaOrangeBadge: true,
      'el-tag': { template: '<span><slot /></span>' },
      'el-input': true,
      'el-button': { template: '<button><slot /></button>' },
      'el-select': true,
      'el-option': true,
      'el-switch': true,
      'el-badge': { template: '<span><slot /></span>' },
      'el-empty': true,
      'el-dialog': true,
    },
  },
})

describe('PersonaWorkshop', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    window.matchMedia = vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    api.getPersonaWorkshop.mockResolvedValue({
      code: 1,
      data: {
        dimensions,
        equipment,
        materials: [{ id: 9, eventType: 'AI_RESULT_COMPLETED', evidenceSummary: '完成趋势分析', suggestedContent: '我完成了一次趋势分析' }],
        materialCount: 1,
        completion: { percent: 20, completedDimensions: 1, totalDimensions: 5 },
        recommendations: [{ type: 'BEGINNER', title: '从新手项目开始', description: '选择已核验项目', route: '/leaderboard' }],
        settings: { showcaseEnabled: false, avatarEnabled: false },
      },
    })
  })

  it('renders all stable dimensions, locked equipment and pending materials from the API', async () => {
    const wrapper = mountWorkshop()
    await flushPromises()

    expect(api.getPersonaWorkshop).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('橙子画像工坊')
    expect(wrapper.text()).toContain('先设计你的橙子')
    expect(wrapper.text()).toContain('果皮颜色')
    expect(wrapper.text()).toContain('亮面')
    expect(wrapper.text()).toContain('当前目标')
    expect(wrapper.text()).toContain('兴趣方向')
    expect(wrapper.text()).toContain('技能与经验')
    expect(wrapper.text()).toContain('可投入资源')
    expect(wrapper.text()).toContain('成长轨迹')
    expect(wrapper.text()).toContain('先写下一条真实内容，即可解锁')
    expect(wrapper.text()).toContain('完成趋势分析')
    expect(wrapper.text()).toContain('从新手项目开始')
    expect(wrapper.text()).not.toContain('使用橙子画像头像')
  })
})
