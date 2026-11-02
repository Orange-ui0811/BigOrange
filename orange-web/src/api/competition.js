import request from './request'

export const getCompetitions = (scope = 'ACTIVE', page = 1, size = 12) =>
  request.get('/app/competition/list', { params: { scope, page, size } })

export const submitCompetition = payload =>
  request.post('/app/competition/submit', payload)
