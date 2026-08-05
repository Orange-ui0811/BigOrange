import { ref, reactive, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { LEGAL_VERSION } from '../content/legal'
import { useAuthStore } from '../stores/authStore'

export const useAuthFlow = () => {
const router = useRouter()
const authStore = useAuthStore()
const tab = ref('login')
const turnstileRef = ref(null)
let turnstileId = null
const loading = ref(false)
const showPwd = ref(false)
const showRegPwd = ref(false)
const loginForm = reactive({ username: '', password: '' })
const codeCooldown = ref(0)
const codeSending = ref(false)
const regForm = reactive({ username: '', password: '', nickname: '', email: '', code: '', turnstileToken: '' })
const legalAccepted = ref(false)
const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '1x00000000000000000000AA'

// 忘记密码
const resetStep = ref(0)
const resetEmail = ref('')
const resetCode = ref('')
const resetNewPwd = ref('')
const resetCooldown = ref(0)
const resetSending = ref(false)
const showResetPwd = ref(false)
let codeTimer = null
let resetTimer = null

const sendResetCode = async () => {
  if (resetSending.value || resetCooldown.value > 0) return
  if (!resetEmail.value || !resetEmail.value.includes('@')) { ElMessage.error('请输入正确的邮箱地址'); return }
  resetSending.value = true
  try {
    const res = await request.post('/app/auth/send-reset-code', { email: resetEmail.value })
    if (res.code !== 1) return
    ElMessage.success('验证码已发送')
    resetCooldown.value = 60
    clearInterval(resetTimer)
    resetTimer = setInterval(() => {
      resetCooldown.value--
      if (resetCooldown.value <= 0) clearInterval(resetTimer)
    }, 1000)
    resetStep.value = 2
  } catch {
    // 网络和服务端错误由统一响应拦截器展示。
  } finally {
    resetSending.value = false
  }
}

const doResetPwd = async () => {
  if (!resetCode.value) { ElMessage.error('请输入验证码'); return }
  if (resetNewPwd.value.length < 6 || !/[a-zA-Z]/.test(resetNewPwd.value) || !/\d/.test(resetNewPwd.value)) {
    ElMessage.error('密码至少6位且包含字母和数字'); return
  }
  loading.value = true
  try {
    const res = await request.post('/app/auth/reset-password', {
      email: resetEmail.value, code: resetCode.value, newPassword: resetNewPwd.value
    })
    if (res.code === 1) { resetStep.value = 3 }
    else ElMessage.error(res.msg)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '网络连接失败') }
  finally { loading.value = false }
}

const sendCode = async () => {
  if (codeSending.value || codeCooldown.value > 0) return
  if (!legalAccepted.value) { ElMessage.warning('请先阅读并同意用户协议和隐私政策'); return }
  if (!regForm.email || !regForm.email.includes('@')) { ElMessage.error('请输入正确的邮箱地址'); return }
  codeSending.value = true
  try {
    const res = await request.post('/app/auth/send-code', { email: regForm.email })
    if (res.code !== 1) return
    ElMessage.success('验证码已发送')
    codeCooldown.value = 60
    clearInterval(codeTimer)
    codeTimer = setInterval(() => {
      codeCooldown.value--
      if (codeCooldown.value <= 0) clearInterval(codeTimer)
    }, 1000)
  } catch {
    // 网络和服务端错误由统一响应拦截器展示。
  } finally {
    codeSending.value = false
  }
}

onBeforeUnmount(() => {
  clearInterval(codeTimer)
  clearInterval(resetTimer)
})
let turnstileLoader = null
const loadTurnstile = () => {
  if (window.turnstile) return Promise.resolve()
  if (turnstileLoader) return turnstileLoader
  turnstileLoader = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
    script.async = true
    script.defer = true
    script.onload = resolve
    script.onerror = () => reject(new Error('人机验证加载失败'))
    document.head.appendChild(script)
  })
  return turnstileLoader
}

// 仅在用户主动勾选协议后加载第三方人机验证脚本。
watch([tab, legalAccepted], async ([currentTab, accepted]) => {
  regForm.turnstileToken = ''
  if (currentTab !== 'register' || !accepted) {
    if (turnstileId !== null && window.turnstile) {
      try { window.turnstile.remove(turnstileId) } catch {}
    }
    turnstileId = null
    return
  }
  try {
    await loadTurnstile()
    await nextTick()
    if (!window.turnstile || !turnstileRef.value) return
    if (turnstileId !== null) window.turnstile.remove(turnstileId)
    turnstileId = window.turnstile.render(turnstileRef.value, {
      sitekey: TURNSTILE_SITE_KEY,
      callback: (token) => { regForm.turnstileToken = token },
      'expired-callback': () => { regForm.turnstileToken = '' },
    })
  } catch {
    ElMessage.error('人机验证加载失败，请检查网络后重试')
  }
})

const doLogin = async () => {
  if (!loginForm.username || !loginForm.password) { ElMessage.warning('请输入用户名或邮箱和密码'); return }
  loading.value = true
  try {
    const res = await request.post('/app/auth/login', loginForm)
    if (res.code === 1) { authStore.setSession(res.data); router.push('/market') }
    else ElMessage.error(res.msg)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '网络连接失败') }
  finally { loading.value = false }
}

const doRegister = async () => {
  if (!legalAccepted.value) { ElMessage.warning('请先阅读并同意用户协议和隐私政策'); return }
  if (regForm.password.length < 6 || !/[a-zA-Z]/.test(regForm.password) || !/\d/.test(regForm.password)) { ElMessage.error('密码至少6位且包含字母和数字'); return }
  if (!regForm.email || !regForm.email.includes('@')) { ElMessage.error('请输入正确的邮箱地址'); return }
  if (!regForm.code) { ElMessage.error('请输入验证码'); return }
  if (!regForm.username) { ElMessage.warning('请输入用户名'); return }
  if (!regForm.turnstileToken) { ElMessage.warning('请先完成人机验证'); return }
  loading.value = true
  try {
    const res = await request.post('/app/auth/register', {
      ...regForm,
      turnstileToken: regForm.turnstileToken,
      legalAccepted: legalAccepted.value,
      legalVersion: LEGAL_VERSION,
    })
    if (res.code === 1) { authStore.setSession(res.data); router.push('/market') }
    else ElMessage.error(res.msg)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '网络连接失败') }
  finally { loading.value = false }
}
  return { tab, turnstileRef, loading, showPwd, showRegPwd, loginForm, codeCooldown, codeSending,
    regForm, legalAccepted, resetStep, resetEmail, resetCode, resetNewPwd, resetCooldown,
    resetSending, showResetPwd, sendResetCode, doResetPwd, sendCode, doLogin, doRegister }}
