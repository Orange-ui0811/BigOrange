import request from './request'

export const getRagLabStatus = () => request.get('/admin/rag-lab/status')
export const listRagLabDocuments = (limit = 100) => request.get('/admin/rag-lab/documents', { params: { limit } })
export const getRagLabDocument = id => request.get(`/admin/rag-lab/documents/${id}`)
export const retryRagLabDocument = id => request.post(`/admin/rag-lab/documents/${id}/retry`)
export const deleteRagLabDocument = id => request.delete(`/admin/rag-lab/documents/${id}`)
export const searchRagLab = payload => request.post('/admin/rag-lab/search', payload, { timeout: 60000 })
export const compareRagRetrieval = payload => request.post('/admin/rag-lab/compare/retrieval', payload, { timeout: 90000 })
export const previewRagAnswer = payload => request.post('/admin/rag-lab/answer-preview', payload, { timeout: 150000 })
export const getRagEvaluationDataset = () => request.get('/admin/rag-lab/evaluation/dataset')
export const runRagEvaluation = () => request.post('/admin/rag-lab/evaluation/run', null, { timeout: 300000 })
export const runRagBenchmark = () => request.post('/admin/rag-lab/evaluation/benchmark/run', null, { timeout: 600000 })
export const uploadRagLabDocument = file => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/admin/rag-lab/documents/upload', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}
export const importLegacyDocument = id => request.post(`/admin/rag-lab/imports/legacy/${id}`)
export const importGithubReadme = repository => request.post('/admin/rag-lab/imports/github', { repository }, { timeout: 60000 })
export const listRagGenerations = () => request.get('/admin/rag-lab/generations')
export const rebuildRagGeneration = (adapter = 'HNSW_HYBRID') => request.post('/admin/rag-lab/generations/rebuild', null, { params: { adapter } })
