import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './authStore'

describe('authStore', () => {
  beforeEach(() => { sessionStorage.clear(); setActivePinia(createPinia()) })

  it('persists and restores a session', () => {
    const store = useAuthStore()
    store.setSession({ token: 'token-1', id: 7 })
    expect(store.authenticated).toBe(true)
    expect(sessionStorage.getItem('appToken')).toBe('token-1')
    setActivePinia(createPinia())
    expect(useAuthStore().accountId).toBe('7')
  })

  it('protects latest request state and clears all session data', async () => {
    const store = useAuthStore()
    let resolveFirst
    const first = store.runLatest(() => new Promise(resolve => { resolveFirst = resolve }))
    await store.runLatest(async () => 'new')
    resolveFirst('old'); await first
    store.setSession({ token: 'token', id: 3 }); store.setCurrentUser({ id: 3 })
    store.clear()
    expect(store.authenticated).toBe(false)
    expect(store.currentUser).toBeNull()
    expect(sessionStorage.getItem('myId')).toBeNull()
  })

  it('supports accountId sessions and removes missing credentials', () => {
    const store = useAuthStore()
    store.setSession({ token: 'token', accountId: 9 })
    expect(store.accountId).toBe('9')
    expect(sessionStorage.getItem('myId')).toBe('9')
    store.setSession(null)
    expect(store.token).toBe('')
    expect(store.accountId).toBe('')
    expect(store.authenticated).toBe(false)
    expect(sessionStorage.getItem('appToken')).toBeNull()
    expect(sessionStorage.getItem('myId')).toBeNull()
    store.setCurrentUser(undefined)
    expect(store.currentUser).toBeNull()
  })

  it('records the latest failure and clears it when a new request starts', async () => {
    const store = useAuthStore()
    const failure = new Error('login unavailable')
    await expect(store.runLatest(async () => { throw failure })).rejects.toBe(failure)
    expect(store.error).toBe(failure)
    expect(store.loading).toBe(false)
    await expect(store.runLatest(async () => {
      expect(store.error).toBeNull()
      expect(store.loading).toBe(true)
      return 'recovered'
    })).resolves.toBe('recovered')
    expect(store.lastUpdatedAt).toEqual(expect.any(Number))
    expect(store.loading).toBe(false)
  })

  it('ignores an older failure while the newer request is still loading', async () => {
    const store = useAuthStore()
    let rejectFirst
    let resolveLatest
    const first = store.runLatest(() => new Promise((resolve, reject) => { rejectFirst = reject }))
    const failure = new Error('obsolete request')
    const firstAssertion = expect(first).rejects.toBe(failure)
    const latest = store.runLatest(() => new Promise(resolve => { resolveLatest = resolve }))
    rejectFirst(failure)
    await firstAssertion
    expect(store.error).toBeNull()
    expect(store.loading).toBe(true)
    expect(store.lastUpdatedAt).toBeNull()
    resolveLatest('latest')
    await latest
    expect(store.loading).toBe(false)
  })

  it('does not restore request state after logout invalidates an in-flight request', async () => {
    const store = useAuthStore()
    let resolveRequest
    const pending = store.runLatest(() => new Promise(resolve => { resolveRequest = resolve }))
    store.clear()
    resolveRequest('old session response')
    await pending
    expect(store.lastUpdatedAt).toBeNull()
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.authenticated).toBe(false)
  })
})

