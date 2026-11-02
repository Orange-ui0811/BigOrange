import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePersonaStore } from './personaStore'

describe('personaStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('loads a snapshot and fills appearance defaults', async () => {
    const store = usePersonaStore()
    await store.load(vi.fn().mockResolvedValue({ code: 1, data: {
      materialCount: 3, settings: { showcaseEnabled: true },
    } }))
    expect(store.data.materialCount).toBe(3)
    expect(store.data.settings.bodyColor).toBe('#F37021')
    expect(store.loading).toBe(false)
    expect(store.lastUpdatedAt).toEqual(expect.any(Number))
  })

  it('uses latest-request-wins for concurrent refreshes', async () => {
    const store = usePersonaStore()
    let resolveFirst
    const first = store.load(() => new Promise(resolve => { resolveFirst = resolve }))
    await store.load(vi.fn().mockResolvedValue({ code: 1, data: { materialCount: 8 } }))
    resolveFirst({ code: 1, data: { materialCount: 1 } })
    await first
    expect(store.data.materialCount).toBe(8)
  })

  it('exposes failures and reset invalidates pending work', async () => {
    const store = usePersonaStore()
    const failure = new Error('offline')
    await expect(store.load(vi.fn().mockRejectedValue(failure))).rejects.toThrow('offline')
    expect(store.error).toBe(failure)
    store.saving = true
    store.reset()
    expect(store.data.dimensions).toEqual([])
    expect(store.data.settings.faceStyle).toBe('FRIENDLY')
    expect(store.saving).toBe(false)
    expect(store.error).toBeNull()
  })
})
