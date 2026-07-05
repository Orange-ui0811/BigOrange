import request from './request'

export const uploadDocument = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/app/knowledge/document/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const listDocuments = () => request.get('/app/knowledge/document/list')

export const deleteDocument = (id) => request.delete(`/app/knowledge/document/${id}`)
