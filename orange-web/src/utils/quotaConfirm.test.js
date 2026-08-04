import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getBalance: vi.fn(),
  confirm: vi.fn(),
  alert: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('../api/quota', () => ({ getBalance: mocks.getBalance }))
vi.mock('element-plus', () => ({
  ElMessage: { warning: mocks.warning, error: mocks.error },
  ElMessageBox: { confirm: mocks.confirm, alert: mocks.alert },
}))

import { confirmQuotaUsage } from './quotaConfirm'

describe('confirmQuotaUsage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getBalance.mockResolvedValue({ code: 1, data: { balance: 125 } })
    mocks.confirm.mockResolvedValue('confirm')
    mocks.alert.mockResolvedValue('confirm')
  })

  it('loads the latest balance and includes it in the confirmation content', async () => {
    const accepted = await confirmQuotaUsage({
      actionLabel: '本次趋势分析', cost: 40, estimatedTime: '1～3 分钟',
    })

    expect(accepted).toBe(true)
    expect(mocks.getBalance).toHaveBeenCalledOnce()
    expect(mocks.confirm).toHaveBeenCalledOnce()
    const [content, title, options] = mocks.confirm.mock.calls[0]
    expect(content.props).toMatchObject({ actionLabel: '本次趋势分析', balance: 125, cost: 40 })
    expect(title).toBe('确认使用额度')
    expect(options.customClass).toBe('quota-confirm-dialog')
    expect(mocks.alert).toHaveBeenCalledOnce()
    expect(mocks.alert.mock.calls[0][0].props).toMatchObject({ estimatedTime: '1～3 分钟' })
    expect(mocks.alert.mock.calls[0][1]).toBe('分析已开始')
  })

  it('shows an insufficient-balance dialog and never starts the action', async () => {
    mocks.getBalance.mockResolvedValue({ code: 1, data: { balance: 15 } })

    const accepted = await confirmQuotaUsage({ actionLabel: '生成贡献指南', cost: 40 })

    expect(accepted).toBe(false)
    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(mocks.alert).toHaveBeenCalledOnce()
    expect(mocks.alert.mock.calls[0][0].props).toMatchObject({ balance: 15, cost: 40 })
    expect(mocks.alert.mock.calls[0][1]).toBe('额度不足')
  })

  it('does not open a confirmation when the current balance cannot be read', async () => {
    mocks.getBalance.mockRejectedValue(new Error('network error'))

    const accepted = await confirmQuotaUsage({ actionLabel: '本次趋势提问', cost: 10 })

    expect(accepted).toBe(false)
    expect(mocks.error).toHaveBeenCalledWith('当前可用额度读取失败，请稍后重试')
    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(mocks.alert).not.toHaveBeenCalled()
  })

  it('returns false when the user cancels', async () => {
    mocks.confirm.mockRejectedValue('cancel')

    const accepted = await confirmQuotaUsage({ actionLabel: '生成阶段报告', cost: 40 })

    expect(accepted).toBe(false)
    expect(mocks.alert).not.toHaveBeenCalled()
  })
})
