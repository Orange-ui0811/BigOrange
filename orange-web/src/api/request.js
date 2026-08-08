import axios from 'axios'
import { ElMessage } from 'element-plus'
import { toUserMessage } from './errorMessage'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/authStore'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
  timeout: 15000,
})

const QUOTA_ENDPOINTS = [
  /^\/app\/trend\/analyze(?:-v2)?$/,
  /^\/app\/plan\/generate$/,
  /^\/app\/job\/match$/,
  /^\/app\/launch-check\/projects\/\d+\/stages\/\d+\/generate$/,
  /^\/app\/open-source\/match$/,
  /^\/app\/open-source\/issues\/\d+\/(?:job-match|guide)$/,
]

const stableStringify = (value) => {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  return `{${Object.keys(value).sort().map(key =>
    `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`
}

const fingerprint = (value) => {
  let first = 0x811c9dc5
  let second = 0x9e3779b9
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    first = Math.imul(first ^ code, 0x01000193)
    second = Math.imul(second ^ code, 0x85ebca6b)
  }
  return `${value.length.toString(36)}-${(first >>> 0).toString(36)}-${(second >>> 0).toString(36)}`
}

const quotaStorageKey = (config) => {
  const method = String(config.method || 'get').toLowerCase()
  const path = String(config.url || '').split('?')[0]
  if (method !== 'post' || !QUOTA_ENDPOINTS.some(pattern => pattern.test(path))) return null
  const identity = stableStringify({
    method, path, params: config.params || null, data: config.data || null,
  })
  return `orange:quota-idempotency:v1:${fingerprint(identity)}`
}

const newIdempotencyKey = () => globalThis.crypto?.randomUUID?.()
  || `ovc-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-quota`

const clearQuotaKey = (config) => {
  if (config?.quotaStorageKey) sessionStorage.removeItem(config.quotaStorageKey)
}

request.interceptors.request.use((config) => {
  const token = useAuthStore(pinia).token
  if (token) config.headers['App-Token'] = token
  const storageKey = quotaStorageKey(config)
  if (storageKey) {
    const idempotencyKey = sessionStorage.getItem(storageKey) || newIdempotencyKey()
    sessionStorage.setItem(storageKey, idempotencyKey)
    config.headers['Idempotency-Key'] = idempotencyKey
    config.quotaStorageKey = storageKey
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    clearQuotaKey(response.config)
    if (response.data?.code === 0) ElMessage.warning(response.data.msg || '操作失败')
    return response.data
  },
  (error) => {
    const status = error.response?.status
    if (status && status !== 408 && status !== 429 && status < 500) {
      clearQuotaKey(error.config)
    }
    if (error.response?.status === 401) {
      useAuthStore(pinia).clear()
      window.location.href = '/login'
    } else {
      ElMessage.error(toUserMessage(error))
    }
    return Promise.reject(error)
  },
)

export default request
