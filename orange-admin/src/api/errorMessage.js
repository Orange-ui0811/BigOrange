export function toUserMessage(error, fallback = '请求失败，请稍后重试') {
  const message = error?.response?.data?.msg
  if (typeof message === 'string' && message.trim()) return message.trim()
  if (error?.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  return fallback
}
