import { ref } from 'vue'
import { defineStore } from 'pinia'
import request from '../api/request'

export const useNotificationStore = defineStore('notification', () => {
  const data = ref(0)
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = ref(0)

  const setUnread = value => { data.value = Math.max(0, Number(value) || 0) }
  const increment = (amount = 1) => setUnread(data.value + amount)
  const decrement = (amount = 1) => setUnread(data.value - amount)

  const loadUnread = async (fetcher = () => request.get('/app/notification/unread-count')) => {
    const currentRequestId = ++requestId.value
    loading.value = true
    error.value = null
    try {
      const response = await fetcher()
      if (currentRequestId !== requestId.value) return response
      if (response?.code === 1) {
        setUnread(response.data)
        lastUpdatedAt.value = Date.now()
      }
      return response
    } catch (cause) {
      if (currentRequestId === requestId.value) error.value = cause
      throw cause
    } finally {
      if (currentRequestId === requestId.value) loading.value = false
    }
  }

  const reset = () => {
    data.value = 0
    loading.value = false
    error.value = null
    lastUpdatedAt.value = null
    requestId.value += 1
  }

  return { data, loading, error, lastUpdatedAt, requestId, setUnread, increment, decrement, loadUnread, reset }
})
