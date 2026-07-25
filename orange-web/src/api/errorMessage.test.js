import { describe, expect, it } from 'vitest'
import { toUserMessage } from './errorMessage'

describe('toUserMessage', () => {
  it('prefers a server business message', () => {
    expect(toUserMessage({ response: { data: { msg: '配额不足' } } })).toBe('配额不足')
  })

  it('distinguishes timeouts from generic failures', () => {
    expect(toUserMessage({ code: 'ECONNABORTED' })).toBe('请求超时，请稍后重试')
    expect(toUserMessage({})).toBe('请求失败，请稍后重试')
  })
})
