<template>
  <div class="login-bg">
    <div class="login-card">
      <!-- 顶部像素橙子 SVG -->
      <div class="orange-wrapper">
        <svg class="orange-mark" viewBox="0 0 40 40" aria-hidden="true">
          <rect x="14" y="1" width="3" height="8" fill="#4d230d" />
          <rect x="17" y="2" width="1" height="7" fill="#4d230d" />
          <rect x="15" y="2" width="2" height="6" fill="#79bd28" />
          <rect x="19" y="2" width="9" height="2" fill="#4d230d" />
          <rect x="18" y="4" width="12" height="2" fill="#4d230d" />
          <rect x="18" y="6" width="11" height="2" fill="#4d230d" />
          <rect x="20" y="4" width="8" height="2" fill="#8bd33a" />
          <rect x="19" y="6" width="8" height="2" fill="#78c42f" />
          <rect x="12" y="6" width="10" height="2" fill="#4d230d" />
          <rect x="9" y="8" width="4" height="2" fill="#4d230d" />
          <rect x="7" y="10" width="3" height="2" fill="#4d230d" />
          <rect x="5" y="12" width="3" height="4" fill="#4d230d" />
          <rect x="4" y="16" width="2" height="5" fill="#4d230d" />
          <rect x="3" y="21" width="2" height="7" fill="#4d230d" />
          <rect x="4" y="28" width="2" height="4" fill="#4d230d" />
          <rect x="6" y="32" width="3" height="3" fill="#4d230d" />
          <rect x="9" y="35" width="5" height="2" fill="#4d230d" />
          <rect x="14" y="37" width="13" height="1" fill="#4d230d" />
          <rect x="27" y="35" width="5" height="2" fill="#4d230d" />
          <rect x="32" y="32" width="3" height="3" fill="#4d230d" />
          <rect x="35" y="27" width="2" height="5" fill="#4d230d" />
          <rect x="36" y="18" width="1" height="9" fill="#4d230d" />
          <rect x="35" y="14" width="2" height="4" fill="#4d230d" />
          <rect x="33" y="11" width="3" height="3" fill="#4d230d" />
          <rect x="30" y="9" width="3" height="2" fill="#4d230d" />
          <rect x="27" y="7" width="3" height="2" fill="#4d230d" />
          <rect x="13" y="8" width="14" height="2" fill="#ffad3f" />
          <rect x="27" y="9" width="3" height="1" fill="#ffad3f" />
          <rect x="10" y="10" width="23" height="2" fill="#ffa739" />
          <rect x="8" y="12" width="27" height="4" fill="#ffa033" />
          <rect x="6" y="16" width="29" height="5" fill="#ff9a2e" />
          <rect x="5" y="21" width="31" height="7" fill="#ff9329" />
          <rect x="6" y="28" width="29" height="4" fill="#ff8823" />
          <rect x="9" y="32" width="23" height="3" fill="#ff7e1f" />
          <rect x="14" y="35" width="13" height="2" fill="#ef6f18" />
          <rect x="33" y="18" width="3" height="8" fill="#ff7623" />
          <!-- 高光（在身体之后，才能显示） -->
          <rect x="10" y="13" width="3" height="3" fill="#fff4cb" class="highlight-pixel" />
          <rect x="9" y="16" width="3" height="5" fill="#ffe29d" class="highlight-pixel" />
          <rect x="10" y="21" width="2" height="4" fill="#ffd17b" class="highlight-pixel" />
          <rect x="13" y="12" width="4" height="1" fill="#fff8dc" class="highlight-pixel" />
          <rect x="14" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="24" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="18" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="22" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="19" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="21" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="20" y="30" width="1" height="1" fill="#5c260f" />
        </svg>
      </div>

      <!-- 品牌标题 -->
      <h1 class="brand-title">OrangeVC</h1>

      <!-- Tab 切换 -->
      <div class="tab-wrap" v-show="resetStep === 0">
        <div
          :class="['tab-item', { active: tab === 'login' }]"
          @click="tab = 'login'"
        >登录</div>
        <div
          :class="['tab-item', { active: tab === 'register' }]"
          @click="tab = 'register'"
        >注册</div>
      </div>

      <!-- 登录表单 -->
      <form v-if="tab === 'login' && resetStep === 0" @submit.prevent="doLogin">
        <label class="form-label">用户名或邮箱</label>
        <input
          v-model="loginForm.username"
          class="form-input"
          type="text"
          placeholder="请输入用户名或注册邮箱"
          autocomplete="username"
          @keyup.enter="doLogin"
        />
        <label class="form-label">密码</label>
        <div class="pwd-wrap">
          <input
            v-model="loginForm.password"
            class="form-input"
            :type="showPwd ? 'text' : 'password'"
            autocomplete="current-password"
            @keyup.enter="doLogin"
          />
          <span class="eye-toggle" @click="showPwd = !showPwd">
            {{ showPwd ? '🙈' : '👁' }}
          </span>
        </div>
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        <p class="forgot-link" @click="resetStep = 1">忘记密码？</p>
      </form>

      <!-- ═══ 忘记密码 ═══ -->
      <div v-if="resetStep > 0" class="reset-section">
        <div class="reset-title">重置密码</div>

        <!-- Step 1: 输入邮箱 -->
        <template v-if="resetStep === 1">
          <label class="form-label">注册邮箱</label>
          <input v-model="resetEmail" class="form-input" type="email" placeholder="输入你注册时用的邮箱" />
          <button class="code-btn reset-btn" :disabled="resetCooldown > 0 || resetSending" @click="sendResetCode">
            {{ resetSending ? '发送中...' : (resetCooldown > 0 ? resetCooldown + 's' : '发送验证码') }}
          </button>
        </template>

        <!-- Step 2: 输入验证码 + 新密码 -->
        <template v-if="resetStep === 2">
          <label class="form-label">验证码</label>
          <input v-model="resetCode" class="form-input" type="text" maxlength="6" placeholder="6位验证码" />
          <label class="form-label">新密码</label>
          <input v-model="resetNewPwd" class="form-input" :type="showResetPwd ? 'text' : 'password'" placeholder="至少6位，包含字母和数字" />
          <span class="eye-toggle reset-eye" @click="showResetPwd = !showResetPwd">{{ showResetPwd ? '🙈' : '👁' }}</span>
          <button class="login-btn" :disabled="loading" @click="doResetPwd">{{ loading ? '修改中...' : '确认修改' }}</button>
        </template>

        <!-- Step 3: 成功 -->
        <div v-if="resetStep === 3" class="reset-success">
          <p>✅ 密码修改成功！</p>
          <button class="login-btn" @click="resetStep = 0; tab = 'login'">返回登录</button>
        </div>

        <p class="forgot-link" @click="resetStep = 0">← 返回登录</p>
      </div>

      <!-- 注册表单 -->
      <form v-if="tab === 'register' && resetStep === 0" @submit.prevent="doRegister">
        <label class="form-label">邮箱</label>
        <input
          v-model="regForm.email"
          class="form-input"
          type="email"
          placeholder="example@qq.com"
          autocomplete="email"
        />
        <label class="form-label">验证码</label>
        <div class="code-row">
          <input
            v-model="regForm.code"
            class="form-input code-input"
            type="text"
            maxlength="6"
            placeholder="6位验证码"
          />
          <button
            type="button"
            class="code-btn"
            :disabled="codeCooldown > 0 || codeSending"
            :aria-disabled="!legalAccepted || undefined"
            @click="sendCode"
          >{{ codeSending ? '发送中...' : (codeCooldown > 0 ? codeCooldown + 's' : '获取验证码') }}</button>
        </div>
        <label class="form-label">用户名</label>
        <input
          v-model="regForm.username"
          class="form-input"
          type="text"
          autocomplete="username"
        />
        <label class="form-label">密码</label>
        <div class="pwd-wrap">
          <input
            v-model="regForm.password"
            class="form-input"
            :type="showRegPwd ? 'text' : 'password'"
            autocomplete="new-password"
          />
          <span class="eye-toggle" @click="showRegPwd = !showRegPwd">
            {{ showRegPwd ? '🙈' : '👁' }}
          </span>
        </div>
        <label class="form-label">昵称</label>
        <input
          v-model="regForm.nickname"
          class="form-input"
          type="text"
          autocomplete="nickname"
        />
        <label class="consent-row" for="legal-consent">
          <input
            id="legal-consent"
            v-model="legalAccepted"
            name="legalAccepted"
            type="checkbox"
            required
            aria-required="true"
          />
          <span><strong class="required-mark">必选</strong> 我已阅读并同意 <router-link to="/legal/terms" target="_blank" rel="noopener noreferrer" @click.stop>《用户协议》</router-link>和<router-link to="/legal/privacy" target="_blank" rel="noopener noreferrer" @click.stop>《隐私政策》</router-link>，并已知悉<router-link to="/legal/third-parties" target="_blank" rel="noopener noreferrer" @click.stop>《第三方服务清单》</router-link></span>
        </label>
        <!-- Turnstile 人机验证 -->
        <div v-if="legalAccepted" ref="turnstileRef" class="turnstile-widget"></div>
        <button type="submit" class="login-btn" :disabled="loading || !legalAccepted">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <footer class="login-legal">
        <router-link to="/legal/terms">用户协议</router-link>
        <router-link to="/legal/privacy">隐私政策</router-link>
        <router-link to="/legal/third-parties">第三方服务</router-link>
        <router-link to="/support">举报与申诉</router-link>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { LEGAL_VERSION } from '../content/legal'

const router = useRouter()
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
    if (res.code === 1) { sessionStorage.setItem('appToken', res.data.token); sessionStorage.setItem('myId', res.data.id); router.push('/market') }
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
    if (res.code === 1) { sessionStorage.setItem('appToken', res.data.token); sessionStorage.setItem('myId', res.data.id); router.push('/market') }
    else ElMessage.error(res.msg)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '网络连接失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  image-rendering: pixelated;
  font-family: "SimHei", monospace;
}

/* ===== 背景 ===== */
.login-bg {
  width: 100vw;
  min-height: 100vh;
  min-height: 100dvh;
  height: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #ff8a33 0%, #fff4d9 100%);
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 90px 0 40px;
}

.login-bg::after {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(#ff9944 1px, transparent 1px);
  background-size: 6px 6px;
  opacity: 0.12;
  pointer-events: none;
}

/* ===== 卡片 ===== */
.login-card {
  width: 420px;
  background: #ffffff;
  box-shadow:
    4px 0 0 #ff7b22,
    -4px 0 0 #ff7b22,
    0 4px 0 #ff7b22,
    0 -4px 0 #ff7b22;
  padding: 36px 30px 28px;
  position: relative;
}

/* ===== 像素橙子 SVG LOGO ===== */
.orange-wrapper {
  position: absolute;
  top: -46px;
  left: 50%;
  transform: translateX(-50%);
  filter: drop-shadow(0 7px 0 rgba(92, 38, 15, .14));
}
.orange-mark {
  width: 100px;
  height: 100px;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  animation: orange-float 3s ease-in-out infinite;
}

.highlight-pixel {
  animation: highlight-shimmer 2.5s ease-in-out infinite;
}
@keyframes orange-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
@keyframes highlight-shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

/* ===== 品牌名 ===== */
.brand-title {
  text-align: center;
  font-size: 32px;
  color: #ff7b22;
  font-weight: 900;
  margin-bottom: 24px;
  margin-top: 30px;
  letter-spacing: 3px;
  font-family: "Courier New", "SimHei", monospace;
  text-shadow: 2px 2px 0 #c55312, 4px 4px 0 rgba(197,83,18,0.15);
}

/* ===== Tab ===== */
.tab-wrap {
  display: flex;
  margin-bottom: 22px;
  border-bottom: 2px solid #e2e2e2;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding-bottom: 8px;
  font-size: 16px;
  color: #777;
  cursor: pointer;
  font-family: "Courier New", "SimHei", monospace;
  letter-spacing: 1px;
}

.tab-item.active {
  color: #ff7b22;
  border-bottom: 2px solid #ff7b22;
  margin-bottom: -2px;
}

/* ===== 表单 ===== */
.form-label {
  font-size: 14px;
  color: #333;
  margin-bottom: 6px;
  display: block;
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  letter-spacing: 1px;
}

.form-input {
  width: 100%;
  height: 44px;
  border: 2px solid #ffb270;
  background: #fff;
  padding: 0 12px;
  font-size: 14px;
  margin-bottom: 18px;
  outline: none;
  font-family: "Courier New", "SimHei", monospace;
  color: #333;
  letter-spacing: 0.5px;
}

.form-input:focus {
  border-color: #ff7b22;
}

.form-input::placeholder {
  color: #ccc;
  font-family: "Courier New", monospace;
  font-size: 12px;
}

/* ===== 密码眼睛 ===== */
.pwd-wrap {
  position: relative;
}

.pwd-wrap .form-input {
  padding-right: 40px;
}

.eye-toggle {
  position: absolute;
  right: 10px;
  top: 10px;
  font-size: 18px;
  cursor: pointer;
  opacity: 0.5;
  transition: opacity 0.15s;
  user-select: none;
}

.eye-toggle:hover {
  opacity: 0.9;
}

/* ===== 验证码行 ===== */
.code-row {
  display: flex;
  gap: 8px;
}

.code-input {
  flex: 1;
  min-width: 0;
}

.code-btn {
  height: 44px;
  padding: 0 14px;
  border: 2px solid #ffb270;
  font-family: "Courier New", "SimHei", monospace;
  background: #fff;
  color: #ff7b22;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
  margin-bottom: 18px;
}

.code-btn:hover:not(:disabled) {
  border-color: #ff7b22;
  background: #fff7f0;
}

.code-btn:disabled {
  color: #ccc;
  cursor: not-allowed;
}

/* ===== 登录按钮 ===== */
.login-btn {
  width: 100%;
  height: 50px;
  background: #ff7b22;
  border: none;
  color: #ffffff;
  font-size: 18px;
  font-weight: 900;
  box-shadow: 0 4px 0 #e66e00;
  cursor: pointer;
  margin-top: 6px;
  font-family: "Courier New", "SimHei", monospace;
  letter-spacing: 2px;
}

.login-btn:hover:not(:disabled) {
  background: #ff8a33;
}

.login-btn:active:not(:disabled) {
  transform: translateY(3px);
  box-shadow: 0 1px 0 #e66e00;
}

.turnstile-widget {
  margin: 12px 0 18px;
  display: flex;
  justify-content: center;
}
.consent-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 2px 0 14px;
  color: #6b5a50;
  font-size: 12px;
  line-height: 1.65;
}
.consent-row input { width: 16px; height: 16px; margin-top: 2px; accent-color: #ff7b22; flex: 0 0 auto; }
.required-mark {
  color: #c2410c;
  font-size: 11px;
}
.consent-row a, .login-legal a { color: #c2410c; text-decoration: none; }
.login-legal {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 5px 12px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #ffedd5;
  font-size: 11px;
}
.forgot-link {
  text-align: center;
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  font-family: var(--font-mono);
}
.forgot-link:hover { color: var(--brand-strong); }
.reset-section { margin-top: 20px; }
.reset-title {
  text-align: center;
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 900;
  color: var(--brand-strong);
  margin-bottom: 18px;
}
.reset-btn { width: 100%; margin-top: 8px; }
.reset-eye {
  position: absolute;
  right: 10px;
  top: -42px;
  font-size: 18px;
  cursor: pointer;
  opacity: 0.5;
}
.reset-success { text-align: center; margin-top: 10px; }
.reset-success p { font-family: var(--font-mono); font-size: 15px; color: var(--brand-strong); margin-bottom: 16px; }
.login-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ===== 响应式 ===== */
@media (max-width: 460px) {
  .login-card {
    width: 92%;
    padding: 30px 18px 22px;
  }
  .brand-title {
    font-size: 26px;
    margin-top: 24px;
  }
}
</style>
