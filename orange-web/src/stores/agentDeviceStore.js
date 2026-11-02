import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { listAgentDevices, listAgentTasks } from '../api/agentDevices'

export const useAgentDeviceStore = defineStore('agentDevice', () => {
  const data = reactive({ devices: [], tasks: [] })
  const loading = reactive({ devices: false, tasks: false })
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = reactive({ devices: 0, tasks: 0 })

  const loadCollection = async (kind, fetcher) => {
    const currentRequestId = ++requestId[kind]
    loading[kind] = true
    error.value = null
    try {
      const response = await fetcher()
      if (currentRequestId === requestId[kind] && response?.code === 1) {
        data[kind] = response.data || []
        lastUpdatedAt.value = Date.now()
      }
      return response
    } catch (cause) {
      if (currentRequestId === requestId[kind]) error.value = cause
      throw cause
    } finally {
      if (currentRequestId === requestId[kind]) loading[kind] = false
    }
  }

  const loadDevices = (fetcher = listAgentDevices) => loadCollection('devices', fetcher)
  const loadTasks = (fetcher = listAgentTasks) => loadCollection('tasks', fetcher)
  const reset = () => {
    data.devices = []; data.tasks = []; loading.devices = false; loading.tasks = false
    error.value = null; lastUpdatedAt.value = null
    requestId.devices += 1; requestId.tasks += 1
  }

  return { data, loading, error, lastUpdatedAt, requestId, loadDevices, loadTasks, reset }
})
