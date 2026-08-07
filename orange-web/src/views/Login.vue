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
      <h1 class="brand-title">{{ PUBLIC_BRAND_NAME }}</h1>
      <p class="brand-summary">面向 Vibe Coding 学习者与创作者的作品、项目和 AI 工具平台</p>
      <div class="official-site" aria-label="官方网站信息">
        <span>官方网站</span>
        <strong>{{ officialSiteHost }}</strong>
      </div>

      <!-- Tab 切换 -->
      <div class="tab-wrap" v-show="resetStep === 0">
        <div
          :class="['tab-item', { active: tab === 'login' }]"
          @click="tab = 'login'"
        >登录</div>
        <div
          v-if="REGISTRATION_ENABLED"
          :class="['tab-item', { active: tab === 'register' }]"
          @click="tab = 'register'"
        >注册</div>
      </div>
      <div v-if="!REGISTRATION_ENABLED && resetStep === 0" class="identity-gate">
        新用户注册当前暂停；已有账号仍可正常登录。
      </div>

      <aside v-if="tab === 'login' && resetStep === 0" class="login-safety" aria-label="安全登录提示">
        <strong>安全登录提示</strong>
        <span>本页仅用于 OrangeVC 本站账号。请勿输入邮箱、银行或其他网站的密码；平台不会通过私信索取密码或验证码。</span>
      </aside>

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
          <input v-model="resetCode" class="form-input" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="6位验证码" />
          <label class="form-label">新密码</label>
          <input v-model="resetNewPwd" class="form-input" :type="showResetPwd ? 'text' : 'password'" autocomplete="new-password" maxlength="128" placeholder="至少6位，包含字母和数字" />
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
      <form v-if="REGISTRATION_ENABLED && tab === 'register' && resetStep === 0" @submit.prevent="doRegister">
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
      <div class="login-identity" aria-label="网站公示信息">
        <span>{{ PUBLIC_OPERATOR_LABEL }}</span>
        <a :href="`mailto:${LEGAL_CONTACT}`">{{ LEGAL_CONTACT }}</a>
        <a v-if="LEGAL_ICP_RECORD" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ LEGAL_ICP_RECORD }}</a>
        <strong v-if="!LEGAL_IDENTITY_COMPLETE" class="identity-warning">生产上线前须配置真实运营主体与备案信息</strong>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthFlow } from '../composables/useAuthFlow'
import { PUBLIC_BRAND_NAME, PUBLIC_OPERATOR_LABEL } from '../config/brand'
import {
  LEGAL_CONTACT,
  LEGAL_ICP_RECORD,
  LEGAL_IDENTITY_COMPLETE,
  OFFICIAL_SITE_URL,
  REGISTRATION_ENABLED,
} from '../content/legal'

const officialSiteHost = computed(() => {
  try { return new URL(OFFICIAL_SITE_URL).host }
  catch { return OFFICIAL_SITE_URL }
})

const { tab, turnstileRef, loading, showPwd, showRegPwd, loginForm, codeCooldown, codeSending,
  regForm, legalAccepted, resetStep, resetEmail, resetCode, resetNewPwd, resetCooldown,
  resetSending, showResetPwd, sendResetCode, doResetPwd, sendCode, doLogin, doRegister } = useAuthFlow()
</script>

<style scoped src="../styles/views/login.css"></style>
