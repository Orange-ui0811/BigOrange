import request from './request'

export const getDirections = () => request.get('/app/job/directions')
export const getJobsByDirection = (direction) => request.get('/app/job/list', { params: { direction } })
export const getJobDetail = (id) => request.get(`/app/job/${id}`)
