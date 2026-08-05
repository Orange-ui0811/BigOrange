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
})

