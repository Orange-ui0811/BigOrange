<template>
  <div class="login-bg">
    <div class="login-card">
      <!-- 顶部像素橙子 SVG -->
      <div class="orange-wrapper">
        <svg class="orange-mark" viewBox="0 0 40 40" aria-hidden="true">
          <rect x="14" y="1" width="3" height="8" fill="#4d230d" />
          <rect x="17" y="2" width="1" height="7" fill="#4d230d" />
          <rect x="15" y="2" width="2" height="6" fill="#79bd28" class="leaf-pixel" />
          <rect x="19" y="2" width="9" height="2" fill="#4d230d" />
          <rect x="18" y="4" width="12" height="2" fill="#4d230d" />
          <rect x="18" y="6" width="11" height="2" fill="#4d230d" />
          <rect x="20" y="4" width="8" height="2" fill="#8bd33a" class="leaf-pixel" />
          <rect x="19" y="6" width="8" height="2" fill="#78c42f" class="leaf-pixel" />
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
          <!-- 橙子身体渐变 -->
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
          <!-- 高光（在身体之后） -->
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
          <rect x="8" y="38" width="24" height="1" fill="#6b2c11" opacity="0.35" class="shadow-pixel" />
          <rect x="12" y="37" width="17" height="1" fill="#6b2c11" opacity="0.28" class="shadow-pixel" />
        </svg>
      </div>

      <!-- 品牌标题 + 管理端 -->
      <div class="brand-row">
        <h1 class="brand-title">OrangeVC</h1>
        <span class="admin-badge">管理端</span>
      </div>

      <!-- 登录表单 -->
      <form @submit.prevent="login">
        <label class="form-label">用户名</label>
        <input
          v-model="form.username"
          class="form-input"
          type="text"
          autocomplete="username"
          @keyup.enter="login"
        />
        <label class="form-label">密码</label>
        <div class="pwd-wrap">
          <input
            v-model="form.password"
            class="form-input"
            :type="showPwd ? 'text' : 'password'"
            autocomplete="current-password"
            @keyup.enter="login"
          />
          <span class="eye-toggle" @click="showPwd = !showPwd">
            {{ showPwd ? '🙈' : '👁' }}
          </span>
        </div>
        <button type="submit" class="login-btn" :disabled="loading">
          {{ loading ? '验证中...' : '登 录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const loading = ref(false)
const showPwd = ref(false)
const form = reactive({ username: '', password: '' })

const login = async () => {
  if (!form.username || !form.password) { ElMessage.warning('请输入用户名和密码'); return }
  loading.value = true
  try {
    const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
    const res = await axios.post(API + '/admin/auth/login', form)
    if (res.data.code === 1) {
      localStorage.setItem('adminToken', res.data.data.token)
      router.push('/dashboard')
    } else {
      ElMessage.error(res.data.msg || '登录失败')
    }
  } catch (e) {
    ElMessage.error('网络连接失败，请确认后端已启动')
  } finally { loading.value = false }
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
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #ff8a33 0%, #fff4d9 100%);
  position: relative;
  overflow: hidden;
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

.leaf-pixel {
  animation: leaf-sway 3.5s ease-in-out infinite;
}

.shadow-pixel {
  animation: shadow-pulse 3s ease-in-out infinite;
}

@keyframes orange-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}

@keyframes highlight-shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

@keyframes leaf-sway {
  0%, 100% { transform: rotate(0deg); transform-origin: 17px 4px; }
  50% { transform: rotate(3deg); transform-origin: 17px 4px; }
}

@keyframes shadow-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 0.55; }
}

/* ===== 品牌行 ===== */
.brand-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
  margin-top: 30px;
}

.brand-title {
  font-size: 28px;
  color: #ff7b22;
  font-weight: 900;
  letter-spacing: 3px;
  font-family: "Courier New", "SimHei", monospace;
  text-shadow: 2px 2px 0 #c55312, 4px 4px 0 rgba(197,83,18,0.15);
}

.admin-badge {
  font-size: 12px;
  color: #fff;
  background: #ff7b22;
  padding: 3px 10px;
  border-radius: 2px;
  letter-spacing: 1px;
  box-shadow: 0 2px 0 #cc6600;
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
}

/* ===== 表单 ===== */
.form-label {
  font-size: 15px;
  color: #333;
  margin-bottom: 6px;
  display: block;
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  letter-spacing: 1px;
  font-size: 14px;
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
    font-size: 24px;
  }
}
</style>
