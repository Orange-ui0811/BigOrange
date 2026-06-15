<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;background:#1a1a2e">
    <el-card style="width:380px;border-radius:12px;padding:8px 8px 20px" shadow="always">
      <div style="text-align:center;margin-bottom:28px">
        <h2 style="font-weight:800;letter-spacing:-1px;color:#1a1a2e;margin:0">OrangeJD<span style="color:#e65100;font-size:14px;margin-left:6px">管理端</span></h2>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="admin" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="login" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%;margin-top:8px;background:#e65100;border-color:#e65100;font-weight:600;border-radius:8px" :loading="loading" @click="login">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })
const rules = { username: [{ required: true, message: '请输入用户名' }], password: [{ required: true, message: '请输入密码' }] }
const formRef = ref(null)

const login = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
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
