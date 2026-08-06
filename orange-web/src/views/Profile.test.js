import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/authStore'
import Profile from './Profile.vue'

const { get, getPublicPersona, route } = vi.hoisted(() => ({
  get: vi.fn(),
  getPublicPersona: vi.fn(),
  route: { params: { id: 'me' } },
}))

vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('../api/request', () => ({ default: { get } }))
vi.mock('../api/personaWorkshop', () => ({ getPublicPersonaWorkshop: getPublicPersona }))

const mountProfile = () => mount(Profile, {
  global: {
    plugins: [pinia],
    mocks: { $router: { push: vi.fn() } },
    stubs: {
      PersonaProfileShowcase: { props: ['persona'], template: '<section class="profile-persona-3d">3D橙子画像</section>' },
      PersonaOrangeBadge: { template: '<div class="persona-avatar" />' },
      'el-card': { template: '<section><slot /></section>' },
      'el-avatar': { template: '<div><slot /></div>' },
      'el-button': { template: '<button><slot /></button>' },
      'el-row': { template: '<div><slot /></div>' },
      'el-col': { template: '<div><slot /></div>' },
    },
  },
})

describe('Profile persona showcase', () => {
  beforeEach(() => {
    get.mockReset(); getPublicPersona.mockReset()
    useAuthStore(pinia).clear()
    useAuthStore(pinia).setSession({ token: 'token', id: 17 })
    get.mockResolvedValue({ code: 1, data: {
      account: { username: 'orange-user' }, profile: { nickname: '橙子用户' },
      works: { total: 0, records: [] },
    } })
  })

  it('loads the signed-in account public persona and renders its 3D showcase and avatar', async () => {
    getPublicPersona.mockResolvedValue({ code: 1, data: {
      enabled: true, avatarEnabled: true, equipment: [], appearance: {},
      dimensions: [{ code: 'GOAL', label: '当前目标', entries: [{ content: '学习 Agent' }] }],
      completion: { percent: 20 },
    } })
    const wrapper = mountProfile()
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/app/profile/17')
    expect(getPublicPersona).toHaveBeenCalledWith('17')
    expect(wrapper.find('.profile-persona-3d').exists()).toBe(true)
    expect(wrapper.find('.persona-avatar').exists()).toBe(true)
  })

  it('keeps private persona content off the personal homepage', async () => {
    getPublicPersona.mockResolvedValue({ code: 1, data: { enabled: false, avatarEnabled: false } })
    const wrapper = mountProfile()
    await flushPromises()

    expect(wrapper.find('.profile-persona-3d').exists()).toBe(false)
    expect(wrapper.find('.persona-avatar').exists()).toBe(false)
  })
})
