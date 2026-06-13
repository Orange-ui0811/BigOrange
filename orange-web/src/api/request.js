import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: 'http://127.0.0.1:8080', timeout: 15000 })

api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('appToken')
  if (token) config.headers['App-Token'] = token
  return config
})

api.interceptors.response.use(
  res => { if (res.data?.code === 0) ElMessage.warning(res.data.msg || '操作失败'); return res.data },
  err => {
    if (err.response?.status === 401) { sessionStorage.removeItem('appToken'); window.location.href = '/login' }
    return Promise.reject(err)
  }
)
export default api
