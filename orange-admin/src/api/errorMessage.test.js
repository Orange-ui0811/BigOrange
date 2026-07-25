import { describe, expect, it } from 'vitest'
import { toUserMessage } from './errorMessage'

describe('toUserMessage', () => {
  it('uses the server message when available', () => {
    expect(toUserMessage({ response: { data: { msg: '没有权限' } } })).toBe('没有权限')
  })

  it('uses a specific timeout message', () => {
    expect(toUserMessage({ code: 'ECONNABORTED' })).toBe('请求超时，请稍后重试')
  })
})
