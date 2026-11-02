import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAiRecordStore } from './aiRecordStore'

describe('aiRecordStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('loads records, caches detail and removes a record', async () => {
    const store = useAiRecordStore()
    await store.load({}, vi.fn().mockResolvedValue({ code: 1, data: { records: [{ id: 1 }], total: 1 } }))
    const detail = vi.fn().mockResolvedValue({ code: 1, data: { id: 1, result: {} } })
    await store.loadDetail(1, detail); await store.loadDetail(1, detail)
    await store.remove(1, vi.fn().mockResolvedValue({ code: 1 }))
    expect(detail).toHaveBeenCalledOnce()
    expect(store.data.total).toBe(0)
  })

  it('uses latest-request-wins and exposes errors', async () => {
    const store = useAiRecordStore()
    let resolveFirst
    const first = store.load({}, () => new Promise(resolve => { resolveFirst = resolve }))
    await store.load({}, vi.fn().mockResolvedValue({ code: 1, data: { records: [{ id: 2 }], total: 1 } }))
    resolveFirst({ code: 1, data: { records: [{ id: 1 }], total: 1 } }); await first
    expect(store.data.records[0].id).toBe(2)
    await expect(store.load({}, vi.fn().mockRejectedValue(new Error('offline')))).rejects.toThrow('offline')
    expect(store.error.message).toBe('offline')
    store.reset(); expect(store.data.records).toEqual([])
  })
})

