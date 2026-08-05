import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { deleteAiRecord, getAiRecordDetail, getAiRecords } from '../api/aiRecords'

export const useAiRecordStore = defineStore('aiRecord', () => {
  const data = reactive({ records: [], total: 0, details: {} })
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = ref(0)
  const detailRequestId = ref(0)

  const load = async (query, fetcher = getAiRecords) => {
    const currentRequestId = ++requestId.value
    loading.value = true
    error.value = null
    try {
      const response = await fetcher(query)
      if (currentRequestId === requestId.value && response?.code === 1) {
        data.records = response.data?.records || []
        data.total = Number(response.data?.total || 0)
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

  const loadDetail = async (id, fetcher = getAiRecordDetail) => {
    if (data.details[id]) return data.details[id]
    const currentRequestId = ++detailRequestId.value
    const response = await fetcher(id)
    if (currentRequestId === detailRequestId.value && response?.code === 1) {
      data.details[id] = response.data
      lastUpdatedAt.value = Date.now()
    }
    return response?.data
  }

  const remove = async (id, remover = deleteAiRecord) => {
    await remover(id)
    data.records = data.records.filter(record => record.id !== id)
    data.total = Math.max(0, data.total - 1)
    delete data.details[id]
  }

  const reset = () => {
    data.records = []; data.total = 0; data.details = {}
    loading.value = false; error.value = null; lastUpdatedAt.value = null
    requestId.value += 1; detailRequestId.value += 1
  }

  return { data, loading, error, lastUpdatedAt, requestId, load, loadDetail, remove, reset }
})

