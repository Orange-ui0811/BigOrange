import request from './request'

export const createAgentLinkCode = (deviceName) =>
  request.post('/app/agent-devices/link-code', { deviceName })

export const listAgentDevices = () => request.get('/app/agent-devices')

export const listAgentTasks = () => request.get('/app/agent-devices/tasks')

export const revokeAgentDevice = (id) => request.delete(`/app/agent-devices/${id}`)
