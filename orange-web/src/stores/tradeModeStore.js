import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import request from '../api/request'

export const transactionUiEnabled = String(import.meta.env.VITE_TRANSACTION_UI_ENABLED || 'false').toLowerCase() === 'true'

export const useTradeModeStore = defineStore('trade-mode', () => {
  const data = ref(false)
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = ref(0)
  const tradeEnabled = computed(() => transactionUiEnabled && data.value)

  const applyServerEnabled = serverEnabled => {
    data.value = Boolean(serverEnabled)
    error.value = null
    lastUpdatedAt.value = Date.now()
  }

  const load = async (fetcher = () => request.get('/app/config/trade-mode')) => {
    const currentRequestId = ++requestId.value
    loading.value = true
    error.value = null
    try {
      const response = await fetcher()
      if (currentRequestId !== requestId.value) return response
      if (response?.code === 1) applyServerEnabled(response.data?.enabled)
      return response
    } catch (cause) {
      if (currentRequestId === requestId.value) error.value = cause
      throw cause
    } finally {
      if (currentRequestId === requestId.value) loading.value = false
    }
  }

  const reset = () => {
    data.value = false
    loading.value = false
    error.value = null
    lastUpdatedAt.value = null
    requestId.value = 0
  }

  return { data, loading, error, lastUpdatedAt, requestId, tradeEnabled, applyServerEnabled, load, reset }
})
