import { ref, onUnmounted } from 'vue'
import { customerCapabilities, customerSessions, createCustomerSession, deleteCustomerSession, customerMessages, submitCustomerRun, customerRun, cancelCustomerRun, customerEvents } from '../api/customer'

const terminal = value => !['QUEUED', 'RUNNING'].includes(value)
export const decodeCustomerMessage = message => {
  if (message.role === 'user') return { ...message, text: message.content }
  try { return { ...message, result: JSON.parse(message.content) } }
  catch { return { ...message, result: { answer: '历史回答无法解析，请重新提问。' } } }
}

export function useCustomerChat() {
  const enabled = ref(false), sessions = ref([]), sessionId = ref(null), messages = ref([])
  const input = ref(''), running = ref(null), progress = ref(''), error = ref(''), state = ref({}), loading = ref(false)
  const initializing = ref(false), availabilityMessage = ref('')
  const capabilities = ref({}), lastRun = ref(null)
  const deleting = ref(null)
  let timer, stream, pending, pollBusy = false
  const storageKey = name => `orange:customer:${sessionStorage.getItem('myId') || 'none'}:${name}`
  const clearPending = () => { pending = null; sessionStorage.removeItem(storageKey('pending')) }
  const stop = () => { clearInterval(timer); stream?.abort(); stream = null }
  const checked = response => { if (response?.code !== 1) throw new Error(response?.msg || '客服请求失败'); return response.data }
  const reload = async () => {
    if (!sessionId.value) return
    const data = checked(await customerMessages(sessionId.value))
    messages.value = data.messages.map(decodeCustomerMessage); state.value = data.state
    if (data.activeRun && !running.value) watchRun(data.activeRun)
  }
  const select = async id => {
    if (running.value || deleting.value !== null) return
    sessionId.value = id; sessionStorage.setItem(storageKey('session'), String(id)); error.value = ''; await reload()
  }
  const init = async () => {
    if (initializing.value || running.value) return
    initializing.value = true; availabilityMessage.value = ''; error.value = ''
    try {
      capabilities.value = checked(await customerCapabilities())
      enabled.value = capabilities.value.enabled
      if (!enabled.value) {
        availabilityMessage.value = '客服暂不可用，请稍后重试。'
        initializing.value = false
        return
      }
    } catch {
      enabled.value = false
      availabilityMessage.value = '客服连接检查失败，请确认后端连接后点击重试。'
      initializing.value = false
      return
    }
    try {
      sessions.value = checked(await customerSessions())
      const saved = sessionStorage.getItem(storageKey('session'))
      const selected = sessions.value.find(item => String(item.id) === saved) || sessions.value[0]
      try {
        const uncertain = JSON.parse(sessionStorage.getItem(storageKey('pending')) || 'null')
        if (uncertain && String(uncertain.session) === String(selected?.id)) pending = uncertain
      } catch { clearPending() }
      if (selected) await select(selected.id)
      if (pending && !running.value) input.value = pending.message
      const draft = sessionStorage.getItem('customerDraft')
      if (draft && !running.value) { input.value = draft; sessionStorage.removeItem('customerDraft') }
    } catch {
      error.value = '客服已开放，但历史会话加载失败。可以重试加载，或点击“新建会话”开始。'
    } finally { initializing.value = false }
  }
  const handle = async row => {
    if (running.value?.id !== row.id) return
    running.value = row
    progress.value = ({ ACCEPTED: '已收到，正在准备', UNDERSTANDING: '正在理解需求', SEARCHING: '正在检索资料', FILTERING_PROJECTS: '正在筛选真实项目', GENERATING: '正在整理证据', VALIDATING: '正在校验结果' })[row.stage] || '正在处理'
    if (terminal(row.status)) {
      lastRun.value = row
      stop(); running.value = null; progress.value = ''; clearPending()
      if (row.status !== 'SUCCEEDED') error.value = customerRunError(row)
      await reload()
    }
  }
  const watchRun = row => {
    stop(); running.value = row; progress.value = '已收到，正在准备'
    stream = new AbortController()
    customerEvents(row.id, stream.signal, value => { handle(value).catch(() => { error.value = '结果刷新失败，请刷新会话。' }) }).catch(() => {})
    timer = setInterval(async () => {
      if (pollBusy || !running.value) return
      pollBusy = true
      try { await handle(checked(await customerRun(row.id))) }
      catch { error.value = '连接暂时中断，正在查询原运行。' }
      finally { pollBusy = false }
    }, 2000)
    if (terminal(row.status)) handle(row)
  }
  const send = async (binding = {}) => {
    if (loading.value || running.value || deleting.value !== null || !input.value.trim()) return
    loading.value = true; error.value = ''
    try {
      if (!sessionId.value) { const created = checked(await createCustomerSession(input.value.trim().slice(0,45))); sessionId.value = created.id; sessions.value = checked(await customerSessions()) }
      const context = Object.fromEntries(['taskId','questionId','listId'].filter(key => typeof binding?.[key] === 'string').map(key => [key,binding[key]]))
      if (!pending || pending.message !== input.value || (Object.keys(context).length && JSON.stringify(context)!==JSON.stringify(pending.context))) pending = { requestId: crypto.randomUUID(), message: input.value, session: sessionId.value, context }
      sessionStorage.setItem(storageKey('pending'), JSON.stringify(pending))
      sessionStorage.setItem(storageKey('session'), String(sessionId.value))
      const row = checked(await submitCustomerRun(sessionId.value, { requestId: pending.requestId, message: pending.message, ...(Object.keys(pending.context || {}).length ? {context:pending.context} : {}) }))
      input.value = ''; watchRun(row); await reload()
    } catch (ex) { error.value = ex.message || '提交未确认，请重试；相同消息将复用请求标识。' }
    finally { loading.value = false }
  }
  const cancel = async () => { if (running.value) await handle(checked(await cancelCustomerRun(running.value.id))) }
  const fresh = () => { if (!running.value) { sessionId.value = null; messages.value = []; state.value = {}; lastRun.value = null; clearPending(); sessionStorage.removeItem(storageKey('session')); error.value = '' } }
  const remove = async id => {
    if (running.value || loading.value || deleting.value !== null) return
    deleting.value = id
    try {
      checked(await deleteCustomerSession(id))
      sessions.value = sessions.value.filter(item => String(item.id) !== String(id))
      if (String(sessionId.value) === String(id)) fresh()
    } finally { deleting.value = null }
  }
  onUnmounted(stop)
  return { enabled, sessions, sessionId, messages, input, running, progress, error, state, loading, initializing, availabilityMessage, capabilities, lastRun, deleting, init, select, send, cancel, fresh, remove }
}

export const customerRunError = row => {
  try { const result = typeof row.result_json === 'string' ? JSON.parse(row.result_json) : row.result_json; if (result?.answer) return result.answer } catch { /* Historic errors use the same recoverable presentation. */ }
  if (row.status === 'CANCELLED') return '已取消这次回答，历史和已确认条件都保留着，可以继续聊天。'
  if (row.status === 'TIMED_OUT') return '这次等待超过时限，暂未取得完整回答。会话仍然保留，可以继续补充需求或稍后再查。'
  if (row.status === 'INTERRUPTED') return '服务重启打断了这次回答，已有会话保留，可以继续。'
  if (/CONDITION|DURATION|GOAL|LANGUAGE/.test(row.error_code || '')) return '有一项条件尚未准确理解，未擅自更改你的要求。请继续说明最重要的一项，其他条件会保留。'
  return '这次服务暂时没有返回完整回复，聊天记录和已确认条件仍然保留。可以继续发消息；未完成的内容不会被当作已核实结果。'
}
