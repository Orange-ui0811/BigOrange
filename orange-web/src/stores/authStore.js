import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const TOKEN_KEY = 'appToken'
const ACCOUNT_KEY = 'myId'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) || '')
  const accountId = ref(sessionStorage.getItem(ACCOUNT_KEY) || '')
  const currentUser = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = ref(0)
  const authenticated = computed(() => Boolean(token.value))

  const setSession = session => {
    token.value = String(session?.token || '')
    accountId.value = String(session?.id || session?.accountId || '')
    if (token.value) sessionStorage.setItem(TOKEN_KEY, token.value)
    else sessionStorage.removeItem(TOKEN_KEY)
    if (accountId.value) sessionStorage.setItem(ACCOUNT_KEY, accountId.value)
    else sessionStorage.removeItem(ACCOUNT_KEY)
    lastUpdatedAt.value = Date.now()
  }

  const setCurrentUser = user => {
    currentUser.value = user || null
    lastUpdatedAt.value = Date.now()
  }

  const runLatest = async operation => {
    const currentRequestId = ++requestId.value
    loading.value = true
    error.value = null
    try {
      const result = await operation()
      if (currentRequestId === requestId.value) lastUpdatedAt.value = Date.now()
      return result
    } catch (cause) {
      if (currentRequestId === requestId.value) error.value = cause
      throw cause
    } finally {
      if (currentRequestId === requestId.value) loading.value = false
    }
  }

  const clear = () => {
    token.value = ''
    accountId.value = ''
    currentUser.value = null
    loading.value = false
    error.value = null
    lastUpdatedAt.value = null
    requestId.value += 1
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(ACCOUNT_KEY)
  }

  return { token, accountId, currentUser, authenticated, loading, error, lastUpdatedAt,
    requestId, setSession, setCurrentUser, runLatest, clear }
})

