<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 100%)">
    <el-card style="width:400px;border-radius:14px;padding:8px 12px 24px" shadow="always">
      <div style="text-align:center;margin-bottom:24px">
        <h2 style="font-weight:800;letter-spacing:-1px;color:#1a1a2e;margin:0">OrangeJD</h2>
        <p style="color:#909399;font-size:13px;margin-top:4px">创意服务交易平台</p>
      </div>
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top" size="large">
            <el-form-item label="用户名"><el-input v-model="loginForm.username" placeholder="用户名" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password placeholder="密码" @keyup.enter="doLogin" /></el-form-item>
            <el-button type="primary" :loading="loading" @click="doLogin" style="width:100%;background:#e65100;border-color:#e65100;font-weight:600;border-radius:8px">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-position="top" size="large">
            <el-form-item label="手机号"><el-input v-model="regForm.phone" placeholder="11位手机号" /></el-form-item>
            <el-form-item label="验证码"><div style="display:flex;gap:8px"><el-input v-model="regForm.code" placeholder="6位验证码" style="flex:1"/><el-button :disabled="sending||countdown>0" @click="sendCode" style="width:120px" size="default">{{countdown>0?countdown+'s':'发送验证码'}}</el-button></div></el-form-item>
            <el-form-item label="用户名"><el-input v-model="regForm.username" placeholder="用户名" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="regForm.password" type="password" show-password placeholder="至少6位，含字母和数字" /></el-form-item>
            <el-form-item label="昵称"><el-input v-model="regForm.nickname" placeholder="怎么称呼" /></el-form-item>
            <el-button type="primary" :loading="loading" @click="doRegister" style="width:100%;background:#e65100;border-color:#e65100;font-weight:600;border-radius:8px">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: 'testphase3', password: '123456' })
const regForm = reactive({ username: '', password: '', nickname: '', phone: '', code: '' })
const sending = ref(false), countdown = ref(0)

const sendCode = async () => {
  if (!regForm.phone || regForm.phone.length!==11) { ElMessage.error('手机号格式不正确'); return }
  sending.value = true
  try { await axios.post(API + '/app/auth/send-code', { phone: regForm.phone }); ElMessage.success('验证码已发送（查看后端控制台）'); countdown.value = 60; const t = setInterval(() => { countdown.value--; if (countdown.value<=0) clearInterval(t) }, 1000) } catch { ElMessage.error('发送失败') }
  finally { sending.value = false }
}

const doLogin = async () => {
  loading.value = true
  try {
    const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const res = await axios.post(API + '/app/auth/login', loginForm)
    if (res.data.code === 1) { sessionStorage.setItem('appToken', res.data.data.token); sessionStorage.setItem('myId', res.data.data.id); router.push('/market') }
    else ElMessage.error(res.data.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}

const doRegister = async () => {
  if (regForm.password.length < 6 || !/[a-zA-Z]/.test(regForm.password) || !/\d/.test(regForm.password)) { ElMessage.error('密码至少6位且包含字母和数字'); return }
  if (!regForm.phone) { ElMessage.error('请输入手机号'); return }
  if (!regForm.code) { ElMessage.error('请输入验证码'); return }
  loading.value = true
  try {
    const res = await axios.post(API + '/app/auth/register', regForm)
    if (res.data.code === 1) { sessionStorage.setItem('appToken', res.data.data.token); sessionStorage.setItem('myId', res.data.data.id); router.push('/market') }
    else ElMessage.error(res.data.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}
</script>
