import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Users from './Users.vue'
import request from '../api/request'

vi.mock('../api/request', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

const passthrough = { template: '<div><slot /></div>' }
const emptyStub = { template: '<div />' }
describe('Users', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.get.mockResolvedValue({
      code: 1,
      data: { records: Array.from({ length: 35 }, (_, index) => ({ id: index + 1 })), total: 35 },
    })
  })

  it('shows previous and next beside the page count and requests 50 rows by default', async () => {
    const wrapper = mount(Users, {
      global: {
        directives: { loading: () => {} },
        stubs: {
          'el-table': passthrough,
          'el-table-column': emptyStub,
          'el-tag': passthrough,
          'el-button': {
            props: ['disabled', 'ariaLabel'],
            emits: ['click'],
            template: '<button :disabled="disabled" :aria-label="ariaLabel" @click="$emit(\'click\')"><slot /></button>',
          },
        },
      },
    })

    await flushPromises()

    expect(request.get).toHaveBeenCalledWith('/admin/users', { params: { page: 1, size: 50 } })
    expect(wrapper.text()).toContain('第 1 / 1 页')
    expect(wrapper.get('[aria-label="进入上一页"]').text()).toBe('上一页')
    expect(wrapper.get('[aria-label="进入下一页"]').text()).toBe('下一页')
    expect(wrapper.get('[aria-label="进入上一页"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="进入下一页"]').attributes('disabled')).toBeDefined()
  })
})
