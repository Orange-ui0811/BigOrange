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
            <el-form-item label="邮箱"><el-input v-model="regForm.email" placeholder="example@qq.com" /></el-form-item>
            <el-form-item label="验证码">
              <div style="display:flex;gap:8px">
                <el-input v-model="regForm.code" placeholder="6位验证码" style="flex:1" />
                <el-button :disabled="codeCooldown>0" @click="sendCode" style="white-space:nowrap">{{ codeCooldown>0 ? codeCooldown+'s' : '发送验证码' }}</el-button>
              </div>
            </el-form-item>
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
import request from '../api/request'

const router = useRouter()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: 'testphase3', password: '123456' })
const codeCooldown = ref(0)
const regForm = reactive({ username: '', password: '', nickname: '', email: '', code: '' })
const sendCode = async () => {
  if (!regForm.email || !regForm.email.includes('@')) { ElMessage.error('请输入正确的邮箱地址'); return }
  try {
    await request.post('/app/auth/send-code', { email: regForm.email })
    ElMessage.success('验证码已发送')
    codeCooldown.value = 60
    const timer = setInterval(() => { codeCooldown.value--; if (codeCooldown.value<=0) clearInterval(timer) }, 1000)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '发送失败')
  }
}
const doLogin = async () => {
  loading.value = true
  try {
const res = await request.post('/app/auth/login', loginForm)
    if (res.code === 1) { sessionStorage.setItem('appToken', res.data.token); sessionStorage.setItem('myId', res.data.id); router.push('/market') }
    else ElMessage.error(res.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}

const doRegister = async () => {
  if (regForm.password.length < 6 || !/[a-zA-Z]/.test(regForm.password) || !/\d/.test(regForm.password)) { ElMessage.error('密码至少6位且包含字母和数字'); return }
  if (!regForm.email || !regForm.email.includes('@')) { ElMessage.error('请输入正确的邮箱地址'); return }
  if (!regForm.code) { ElMessage.error('请输入验证码'); return }
  loading.value = true
  try {
    const res = await request.post('/app/auth/register', regForm)
    if (res.code === 1) { sessionStorage.setItem('appToken', res.data.token); sessionStorage.setItem('myId', res.data.id); router.push('/market') }
    else ElMessage.error(res.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}
</script>
