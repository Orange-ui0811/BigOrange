import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { transactionUiEnabled, useTradeModeStore } from './tradeModeStore'

describe('tradeModeStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('applies the server flag without bypassing the build-time safety switch', () => {
    const store = useTradeModeStore()
    store.applyServerEnabled(true)
    expect(store.data).toBe(true)
    expect(store.tradeEnabled).toBe(transactionUiEnabled)
    expect(store.lastUpdatedAt).toEqual(expect.any(Number))
  })

  it('resets all shared state', () => {
    const store = useTradeModeStore()
    store.applyServerEnabled(true)
    store.reset()
    expect(store.data).toBe(false)
    expect(store.tradeEnabled).toBe(false)
    expect(store.lastUpdatedAt).toBeNull()
  })

  it('loads the server flag and exposes failures without changing the UI contract', async () => {
    const store = useTradeModeStore()
    await store.load(vi.fn().mockResolvedValue({ code: 1, data: { enabled: true } }))
    expect(store.data).toBe(true)
    expect(store.loading).toBe(false)

    const failure = new Error('offline')
    await expect(store.load(vi.fn().mockRejectedValue(failure))).rejects.toThrow('offline')
    expect(store.error).toBe(failure)
  })

  it('keeps the newest trade-mode response', async () => {
    const store = useTradeModeStore()
    let resolveFirst
    const first = store.load(() => new Promise(resolve => { resolveFirst = resolve }))
    await store.load(vi.fn().mockResolvedValue({ code: 1, data: { enabled: true } }))
    resolveFirst({ code: 1, data: { enabled: false } })
    await first
    expect(store.data).toBe(true)
  })
})
