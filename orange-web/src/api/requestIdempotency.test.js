// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { warning, errorMessage } = vi.hoisted(() => ({
  warning: vi.fn(),
  errorMessage: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning, error: errorMessage },
}))
vi.mock('./errorMessage', () => ({ toUserMessage: () => 'network error' }))
vi.mock('../stores', () => ({ pinia: {} }))
vi.mock('../stores/authStore', () => ({
  useAuthStore: () => ({ token: 'app-token', clear: vi.fn() }),
}))

import request from './request'

const response = config => ({
  data: { code: 1, data: { ok: true } },
  status: 200,
  statusText: 'OK',
  headers: {},
  config,
})

describe('quota request idempotency', () => {
  beforeEach(() => {
    sessionStorage.clear()
    warning.mockClear()
    errorMessage.mockClear()
  })

  it('reuses the key after a network failure and rotates it after success', async () => {
    const seen = []
    request.defaults.adapter = async config => {
      seen.push(config.headers['Idempotency-Key'])
      if (seen.length < 3) throw { config, request: {}, message: 'timeout' }
      return response(config)
    }
    const payload = { phase: 1, summary: 'same request' }

    await expect(request.post('/app/plan/generate', payload)).rejects.toBeTruthy()
    await expect(request.post('/app/plan/generate', payload)).rejects.toBeTruthy()
    await request.post('/app/plan/generate', payload)
    await request.post('/app/plan/generate', payload)

    expect(seen[0]).toBeTruthy()
    expect(seen[1]).toBe(seen[0])
    expect(seen[2]).toBe(seen[0])
    expect(seen[3]).not.toBe(seen[0])
  })

  it('does not attach a quota key to unrelated writes', async () => {
    let seen
    request.defaults.adapter = async config => {
      seen = config.headers['Idempotency-Key']
      return response(config)
    }

    await request.post('/app/profile', { nickname: 'Orange' })

    expect(seen).toBeUndefined()
  })
})
