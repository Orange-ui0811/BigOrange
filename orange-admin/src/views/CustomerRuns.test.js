import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import CustomerRuns from './CustomerRuns.vue'
import request from '../api/request'
vi.mock('../api/request',()=>({default:{get:vi.fn(),post:vi.fn()}}))
describe('Customer run diagnostics',()=>{
  beforeEach(()=>{vi.clearAllMocks();request.get.mockResolvedValue({code:1,data:{items:[],total:0}})})
  it('shows a real empty state and does not request raw payloads on load',async()=>{
    const wrapper=mount(CustomerRuns,{global:{plugins:[ElementPlus]}})
    await flushPromises()
    expect(wrapper.text()).toContain('没有符合筛选条件的运行')
    expect(request.get).toHaveBeenCalledWith('/admin/customer/runs',expect.objectContaining({params:expect.objectContaining({page:1,size:20})}))
    expect(request.post).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('shows a retryable error without pretending the service returned an empty list',async()=>{
    request.get.mockRejectedValue(new Error('offline'))
    const wrapper=mount(CustomerRuns,{global:{plugins:[ElementPlus]}})
    await flushPromises()
    expect(wrapper.text()).toContain('无法加载运行列表')
    expect(request.post).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
