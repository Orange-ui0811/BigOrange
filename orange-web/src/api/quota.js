import request from './request'

export const getBalance = () => request.get('/app/quota/balance')
export const redeemCode = (code) => request.post('/app/quota/redeem', { code })
export const getLog = (limit = 20) => request.get('/app/quota/log', { params: { limit } })
export const createEpayOrder = (amount, method = 'wxpay') => request.post('/app/epay/create', { amount, method })
