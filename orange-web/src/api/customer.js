import request from './request'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/authStore'

export const customerCapabilities = () => request.get('/app/customer/capabilities')
export const customerSessions = () => request.get('/app/customer/sessions')
export const deleteCustomerSession = id => request.delete(`/app/customer/sessions/${id}`)
export const createCustomerSession = title => request.post('/app/customer/sessions', title ? { title } : {})
export const customerMessages = (id, before) => request.get(`/app/customer/sessions/${id}/messages`, { params: { before, limit: 50 } })
export const submitCustomerRun = (id, input) => request.post(`/app/customer/sessions/${id}/runs`, input)
export const customerRun = id => request.get(`/app/customer/runs/${id}`)
export const cancelCustomerRun = id => request.post(`/app/customer/runs/${id}/cancel`)
export const customerImportDraft = (id, project) => request.post(`/app/customer/runs/${id}/import-draft`, { type: project.type, projectId: project.id })

// Fetch permits the existing App-Token header; EventSource cannot send it.
export async function customerEvents(id, signal, onStatus) {
  const base = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
  const response = await fetch(`${base}/app/customer/runs/${id}/events`, {
    headers: { 'App-Token': useAuthStore(pinia).token || '', Accept: 'text/event-stream' }, signal,
  })
  if (!response.ok || !response.body) throw new Error('过程连接未建立')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let end
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, end)
        buffer = buffer.slice(end + 2)
        const data = block.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
        if (data) onStatus(JSON.parse(data))
      }
    }
  } finally { reader.releaseLock() }
}
