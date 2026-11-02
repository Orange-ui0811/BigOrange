import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkInlineEditor from './WorkInlineEditor.vue'

const request = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }))
const confirm = vi.hoisted(() => vi.fn())

vi.mock('../api/request', () => ({ default: request }))
vi.mock('element-plus', async importOriginal => {
  const original = await importOriginal()
  return { ...original, ElMessage: messages, ElMessageBox: { confirm } }
})

const slotStub = { template: '<div><slot name="header"/><slot/><slot name="footer"/></div>' }
const mountEditor = () => mount(WorkInlineEditor, {
  props: { modelValue: false, workId: 9 },
  global: {
    stubs: {
      'el-drawer': slotStub,
      'el-form': slotStub,
      'el-form-item': slotStub,
      'el-upload': slotStub,
      'el-select': slotStub,
      'el-input': true,
      'el-option': true,
      'el-button': true,
      'el-alert': true,
      'el-image': true,
      'el-progress': true,
      VideoShowcase: true,
    },
    directives: { loading: () => {} },
  },
})

describe('WorkInlineEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    confirm.mockResolvedValue(true)
    request.get.mockImplementation(url => Promise.resolve(url.startsWith('/app/work/')
      ? { code: 1, data: {
          title: '橙子作品', summary: '摘要', detail: '详情', categoryId: 2,
          coverUrl: '/uploads/cover/a.png', demoUrl: 'https://example.com/demo',
          videoUrl: '/uploads/video/7/123e4567-e89b-12d3-a456-426614174000.mp4',
          tags: 'Vue', auditStatus: 'APPROVED', saleOptions: [{ price: 199 }],
        } }
      : { code: 1, data: [{ id: 2, name: '网站' }] }))
    request.put.mockResolvedValue({ code: 1 })
    request.post.mockResolvedValue({ code: 1 })
  })

  it('loads an existing approved work when the editor opens', async () => {
    const wrapper = mountEditor()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(request.get).toHaveBeenCalledWith('/app/work/9')
    expect(request.get).toHaveBeenCalledWith('/app/category?type=WORK')
    expect(wrapper.vm.form.title).toBe('橙子作品')
    expect(wrapper.vm.form.price).toBe(199)
    expect(wrapper.vm.requiresReviewAgain).toBe(true)
  })

  it('updates the same work and submits it for review once', async () => {
    const wrapper = mountEditor()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    await wrapper.vm.save(true)

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(request.put).toHaveBeenCalledWith('/app/work/9', expect.objectContaining({
      title: '橙子作品', videoUrl: '/uploads/video/7/123e4567-e89b-12d3-a456-426614174000.mp4',
      saleOptions: [{ saleType: 'FIXED_PRICE', price: 199, description: '公开参考价格' }],
    }))
    expect(request.post).toHaveBeenCalledTimes(1)
    expect(request.post).toHaveBeenCalledWith('/app/work/9/submit')
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })
})
