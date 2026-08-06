import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import Leaderboard from './Leaderboard.vue'

vi.mock('../composables/useLeaderboard', async () => {
  const { computed, ref } = await import('vue')
  return { useLeaderboard: () => ({
    PageTour: { template: '<div />' }, BeginnerProjects: { template: '<div />' }, CareerProjects: { template: '<div />' },
    tourSteps: [], source: ref('local'), period: ref('all'), category: ref(null), type: ref('works'),
    list: ref([]), loading: ref(false), page: ref(1), pageSize: ref(20), total: ref(0), categories: ref([]),
    warning: ref(''), emptyText: computed(() => '暂无内容'), load: vi.fn(), onSourceChange: vi.fn(),
    onClick: vi.fn(), recommendWithPersona: vi.fn(),
  }) }
})

describe('Leaderboard display controls', () => {
  it('exposes a visible 3D orange switch and passes its state to the persona card', async () => {
    localStorage.clear()
    const wrapper = mount(Leaderboard, { global: { stubs: {
      LeaderboardPersonaCard: { props: ['modelVisible'], template: '<div class="persona-model-state">{{ modelVisible }}</div>' },
      'el-switch': { props: ['modelValue'], emits: ['change'], template: '<button class="model-switch" @click="$emit(\'change\', false)">{{ modelValue }}</button>' },
      'el-radio-group': { template: '<div><slot /></div>' }, 'el-radio-button': true,
      'el-select': true, 'el-option': true, 'el-empty': true, 'el-tabs': { template: '<div><slot /></div>' },
      'el-tab-pane': true, 'el-row': { template: '<div><slot /></div>' }, 'el-col': { template: '<div><slot /></div>' },
    } } })

    expect(wrapper.text()).toContain('3D橙子')
    expect(wrapper.text()).toContain('正在显示')
    expect(wrapper.get('.persona-model-state').text()).toBe('true')
    await wrapper.get('.model-switch').trigger('click')
    expect(wrapper.text()).toContain('已隐藏')
    expect(wrapper.get('.persona-model-state').text()).toBe('false')
  })
})
