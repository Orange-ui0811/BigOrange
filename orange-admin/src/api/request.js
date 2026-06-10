import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: 'http://127.0.0.1:8080',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('adminToken')
  if (token) config.headers['Admin-Token'] = token
  return config
})

request.interceptors.response.use(
  res => {
    if (res.data && res.data.code === 0) {
      ElMessage.warning(res.data.msg || '操作失败')
    }
    return res.data
  },
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('adminToken')
      window.location.href = '/login'
    }
    ElMessage.error(err.response?.data?.msg || '请求失败')
    return Promise.reject(err)
  }
)

export default request
