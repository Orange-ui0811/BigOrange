import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAgentDeviceStore } from './agentDeviceStore'

describe('agentDeviceStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('loads devices and tasks independently', async () => {
    const store = useAgentDeviceStore()
    await Promise.all([
      store.loadDevices(vi.fn().mockResolvedValue({ code: 1, data: [{ id: 1 }] })),
      store.loadTasks(vi.fn().mockResolvedValue({ code: 1, data: [{ id: 2 }] })),
    ])
    expect(store.data.devices[0].id).toBe(1)
    expect(store.data.tasks[0].id).toBe(2)
    expect(store.loading.devices).toBe(false)
  })

  it('prevents stale results and resets state', async () => {
    const store = useAgentDeviceStore()
    let resolveFirst
    const first = store.loadDevices(() => new Promise(resolve => { resolveFirst = resolve }))
    await store.loadDevices(vi.fn().mockResolvedValue({ code: 1, data: [{ id: 9 }] }))
    resolveFirst({ code: 1, data: [{ id: 1 }] }); await first
    expect(store.data.devices[0].id).toBe(9)
    store.reset(); expect(store.data.devices).toEqual([])
  })
})
