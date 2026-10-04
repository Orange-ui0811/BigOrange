import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { useCustomerChat, decodeCustomerMessage } from './useCustomerChat'
import * as api from '../api/customer'
vi.mock('../api/customer', () => ({
  deleteCustomerSession: vi.fn(), customerCapabilities: vi.fn(), customerSessions: vi.fn(), createCustomerSession: vi.fn(), customerMessages: vi.fn(),
  submitCustomerRun: vi.fn(), customerRun: vi.fn(), cancelCustomerRun: vi.fn(), customerEvents: vi.fn(),
}))
const ok = data => ({ code: 1, data })
let chat, wrapper
beforeEach(() => {
  vi.clearAllMocks(); vi.useFakeTimers()
  sessionStorage.clear()
  api.customerCapabilities.mockResolvedValue(ok({ enabled: true }))
  api.customerSessions.mockResolvedValue(ok([{ id: 1, title: '测试' }]))
  api.customerMessages.mockResolvedValue(ok({ messages: [], state: {}, activeRun: null }))
  api.customerEvents.mockImplementation(() => new Promise(() => {}))
  wrapper = mount(defineComponent({ setup() { chat = useCustomerChat(); return () => null } }))
})
afterEach(() => { wrapper.unmount(); vi.useRealTimers() })
describe('客服运行与恢复', () => {
  it('刷新后保留提交未确认消息的请求标识', async () => {
    await chat.init(); chat.input.value = '推荐Java项目'
    api.submitCustomerRun.mockRejectedValue(new Error('连接断开')); await chat.send()
    const original = api.submitCustomerRun.mock.calls[0][1].requestId
    wrapper.unmount()
    wrapper = mount(defineComponent({ setup() { chat = useCustomerChat(); return () => null } }))
    await chat.init(); expect(chat.input.value).toBe('推荐Java项目'); await chat.send()
    expect(api.submitCustomerRun.mock.calls[1][1].requestId).toBe(original)
  })
  it('未开放时不创建会话', async () => {
    api.customerCapabilities.mockResolvedValue(ok({ enabled: false }))
    await chat.init(); expect(chat.enabled.value).toBe(false); expect(api.customerSessions).not.toHaveBeenCalled()
  })
  it('初始化加载本人会话', async () => {
    await chat.init(); expect(chat.sessionId.value).toBe(1); expect(api.customerMessages).toHaveBeenCalledWith(1)
  })
  it('历史加载失败不会隐藏已开放客服，重试可恢复', async () => {
    api.customerMessages.mockRejectedValueOnce(new Error('network'))
    await chat.init()
    expect(chat.enabled.value).toBe(true)
    expect(chat.error.value).toContain('历史会话加载失败')
    await chat.init()
    expect(chat.enabled.value).toBe(true)
    expect(chat.error.value).toBe('')
  })
  it('客服关闭时显示可用性提示，检查状态会结束', async () => {
    sessionStorage.setItem('myId', '9')
    api.customerCapabilities.mockResolvedValue(ok({ enabled: false }))
    await chat.init()
    expect(chat.availabilityMessage.value).toBe('客服暂不可用，请稍后重试。')
    expect(chat.initializing.value).toBe(false)
  })
  it('普通登录账号按服务端能力启用客服，不限定测试账号', async () => {
    sessionStorage.setItem('myId', '9')
    api.customerCapabilities.mockResolvedValue(ok({ enabled: true, accountId: 9 }))
    await chat.init()
    expect(chat.enabled.value).toBe(true)
    expect(chat.availabilityMessage.value).toBe('')
    expect(api.customerSessions).toHaveBeenCalledOnce()
    expect(api.customerMessages).toHaveBeenCalledWith(1)
  })
  it('能力接口失败提供重试提示，不请求会话', async () => {
    api.customerCapabilities.mockRejectedValueOnce(new Error('network'))
    await chat.init()
    expect(chat.availabilityMessage.value).toContain('连接检查失败')
    expect(api.customerSessions).not.toHaveBeenCalled()
    expect(chat.initializing.value).toBe(false)
  })
  it('网络不确定时相同消息重用请求标识', async () => {
    await chat.init(); chat.input.value = '推荐Java项目'
    api.submitCustomerRun.mockRejectedValueOnce(new Error('连接断开')).mockResolvedValueOnce(ok({ id: 'r', status: 'RUNNING' }))
    await chat.send(); const first = api.submitCustomerRun.mock.calls[0][1].requestId
    await chat.send(); expect(api.submitCustomerRun.mock.calls[1][1].requestId).toBe(first)
  })
  it('运行期间不能重复提交', async () => {
    await chat.init(); chat.input.value = '推荐项目'; api.submitCustomerRun.mockResolvedValue(ok({ id: 'r', status: 'RUNNING' }))
    await chat.send(); chat.input.value = '再推荐'; await chat.send(); expect(api.submitCustomerRun).toHaveBeenCalledTimes(1)
  })
  it('刷新恢复运行只查询，不提交模型请求', async () => {
    api.customerMessages.mockResolvedValue(ok({ messages: [], state: {}, activeRun: { id: 'r', status: 'RUNNING' } }))
    api.customerRun.mockResolvedValue(ok({ id: 'r', status: 'RUNNING' }))
    await chat.init(); await vi.advanceTimersByTimeAsync(2000)
    expect(api.customerRun).toHaveBeenCalledWith('r'); expect(api.submitCustomerRun).not.toHaveBeenCalled()
  })
  it('取消终态停止轮询并恢复历史', async () => {
    await chat.init(); chat.input.value = '项目'; api.submitCustomerRun.mockResolvedValue(ok({ id: 'r', status: 'RUNNING' }))
    api.cancelCustomerRun.mockResolvedValue(ok({ id: 'r', status: 'CANCELLED' }))
    await chat.send(); await chat.cancel(); await nextTick()
    expect(chat.running.value).toBeNull(); expect(chat.error.value).toContain('已取消')
    await vi.advanceTimersByTimeAsync(4000); expect(api.customerRun).not.toHaveBeenCalled()
  })
  it('用户消息不解析为HTML或项目对象', () => {
    expect(decodeCustomerMessage({ role: 'user', content: '<script>bad()</script>' }).text).toContain('<script>')
  })
  it('删除当前会话后清理历史、条件和刷新恢复位置', async () => {
    await chat.init(); chat.state.value = {language:'Java'}; chat.messages.value = [{id:1}]
    api.deleteCustomerSession.mockResolvedValue(ok(null))
    await chat.remove(1)
    expect(chat.sessions.value).toEqual([]); expect(chat.sessionId.value).toBeNull()
    expect(chat.state.value).toEqual({}); expect(chat.messages.value).toEqual([])
    expect(sessionStorage.getItem('orange:customer:none:session')).toBeNull()
  })
  it('删除失败保留原会话，运行中不发送删除请求', async () => {
    await chat.init(); api.deleteCustomerSession.mockRejectedValueOnce(new Error('failed'))
    await expect(chat.remove(1)).rejects.toThrow('failed')
    expect(chat.sessionId.value).toBe(1); expect(chat.sessions.value).toHaveLength(1)
    expect(chat.deleting.value).toBeNull()
    chat.running.value = {id:'r',status:'RUNNING'}
    await chat.remove(1); expect(api.deleteCustomerSession).toHaveBeenCalledTimes(1)
  })

})
