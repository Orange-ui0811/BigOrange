import axios from 'axios'
import { ElMessage } from 'element-plus'
import { toUserMessage } from './errorMessage'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/authStore'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = useAuthStore(pinia).token
  if (token) config.headers['App-Token'] = token
  return config
})

request.interceptors.response.use(
  (response) => {
    if (response.data?.code === 0) ElMessage.warning(response.data.msg || '操作失败')
    return response.data
  },
  (error) => {
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
