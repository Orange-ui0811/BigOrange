import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useNotificationStore } from './notificationStore'

describe('notificationStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('loads and normalizes the unread count', async () => {
    const store = useNotificationStore()
    await store.loadUnread(vi.fn().mockResolvedValue({ code: 1, data: 4 }))
    expect(store.data).toBe(4)
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.lastUpdatedAt).toEqual(expect.any(Number))
  })

  it('prevents an older request from overwriting a newer result', async () => {
    const store = useNotificationStore()
    let resolveFirst
    const first = store.loadUnread(() => new Promise(resolve => { resolveFirst = resolve }))
    await store.loadUnread(vi.fn().mockResolvedValue({ code: 1, data: 8 }))
    resolveFirst({ code: 1, data: 2 })
    await first
    expect(store.data).toBe(8)
  })

  it('records failures and reset invalidates pending work', async () => {
    const store = useNotificationStore()
    const failure = new Error('offline')
    await expect(store.loadUnread(vi.fn().mockRejectedValue(failure))).rejects.toThrow('offline')
    expect(store.error).toBe(failure)
    store.increment(3)
    store.decrement()
    expect(store.data).toBe(2)
    store.reset()
    expect(store.data).toBe(0)
    expect(store.error).toBeNull()
  })
})
