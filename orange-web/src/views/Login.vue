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
            <el-form-item label="用户名"><el-input v-model="regForm.username" placeholder="用户名" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="regForm.password" type="password" show-password placeholder="密码" /></el-form-item>
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
const regForm = reactive({ username: '', password: '', nickname: '' })

const doLogin = async () => {
  loading.value = true
  try {
    const res = await axios.post('http://127.0.0.1:8080/app/auth/login', loginForm)
    if (res.data.code === 1) { sessionStorage.setItem('appToken', res.data.data.token); sessionStorage.setItem('myId', res.data.data.id); router.push('/market') }
    else ElMessage.error(res.data.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}

const doRegister = async () => {
  loading.value = true
  try {
    const res = await axios.post('http://127.0.0.1:8080/app/auth/register', regForm)
    if (res.data.code === 1) { sessionStorage.setItem('appToken', res.data.data.token); sessionStorage.setItem('myId', res.data.data.id); router.push('/market') }
    else ElMessage.error(res.data.msg)
  } catch { ElMessage.error('网络连接失败') }
  finally { loading.value = false }
}
</script>
