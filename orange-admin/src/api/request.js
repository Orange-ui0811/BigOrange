import axios from 'axios'
import { ElMessage } from 'element-plus'
import { toUserMessage } from './errorMessage'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('adminToken')
  if (token) config.headers['Admin-Token'] = token
  return config
})

request.interceptors.response.use(
  (response) => {
    if (response.data?.code === 0) ElMessage.warning(response.data.msg || '操作失败')
    return response.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('adminToken')
      window.location.href = '/admin/#/login'
    }
    ElMessage.error(toUserMessage(error))
    return Promise.reject(error)
  },
)

export default request
