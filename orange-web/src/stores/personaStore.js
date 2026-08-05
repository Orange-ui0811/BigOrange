import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPersonaWorkshop } from '../api/personaWorkshop'

const defaultSettings = () => ({
  showcaseEnabled: false, avatarEnabled: false, bodyColor: '#F37021',
  faceStyle: 'FRIENDLY', leafStyle: 'CLASSIC', surfaceStyle: 'NATURAL', faceConfig: {},
})

export const usePersonaStore = defineStore('persona', () => {
  const data = reactive({
    dimensions: [], materials: [], equipment: [], recommendations: [],
    settings: defaultSettings(), completion: {}, materialCount: 0,
  })
  const loading = ref(false)
  const error = ref(null)
  const lastUpdatedAt = ref(null)
  const requestId = ref(0)
  const saving = ref(false)

  const load = async (fetcher = getPersonaWorkshop) => {
    const currentRequestId = ++requestId.value
    loading.value = true
    error.value = null
    try {
      const response = await fetcher()
      if (currentRequestId === requestId.value && response?.code === 1) {
        Object.assign(data, response.data || {})
        data.settings = { ...defaultSettings(), ...(response.data?.settings || {}) }
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
    Object.assign(data, { dimensions: [], materials: [], equipment: [], recommendations: [],
      settings: defaultSettings(), completion: {}, materialCount: 0 })
    loading.value = false
    saving.value = false
    error.value = null
    lastUpdatedAt.value = null
    requestId.value += 1
  }

  return { data, loading, error, lastUpdatedAt, requestId, saving, load, reset }
})
