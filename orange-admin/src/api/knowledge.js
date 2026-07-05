import request from './request'

export const uploadDocument = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/admin/knowledge/document/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const listDocuments = () => request.get('/admin/knowledge/document/list')

export const deleteDocument = (id) => request.delete(`/admin/knowledge/document/${id}`)
