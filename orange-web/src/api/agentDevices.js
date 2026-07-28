import request from './request'

export const createAgentLinkCode = (deviceName) =>
  request.post('/app/agent-devices/link-code', { deviceName })

export const listAgentDevices = () => request.get('/app/agent-devices')

export const listAgentTasks = (limit = 100) =>
  request.get('/app/agent-devices/tasks', { params: { limit } })

export const revokeAgentDevice = (id) => request.delete(`/app/agent-devices/${id}`)

export const deleteAgentTask = (id) => request.delete(`/app/agent-devices/tasks/${id}`)
