import request from './request'

export const getAiRecords = ({ page = 1, size = 20, businessType = '' } = {}) =>
  request.get('/app/ai-records', { params: { page, size, businessType: businessType || undefined } })

export const getAiRecordDetail = id => request.get(`/app/ai-records/${id}`)

export const deleteAiRecord = id => request.delete(`/app/ai-records/${id}`)
