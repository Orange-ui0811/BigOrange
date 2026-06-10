<template>
  <el-container style="height:100vh">
    <el-aside width="220px" style="background:#1a1a2e;overflow:hidden">
      <div style="padding:20px 16px 12px;color:#fff;font-size:18px;font-weight:700;letter-spacing:-0.5px;border-bottom:1px solid rgba(255,255,255,0.08)">
        OrangeJD
        <span style="font-size:11px;color:#e65100;font-weight:500;margin-left:8px">管理端</span>
      </div>
      <el-menu :default-active="route.path" background-color="#1a1a2e" text-color="#8890a0" active-text-color="#e65100" router style="border-right:none">
        <el-menu-item index="/dashboard"><el-icon><DataAnalysis /></el-icon> 控制台</el-menu-item>
        <el-menu-item index="/audit"><el-icon><Checked /></el-icon> 审核管理</el-menu-item>
        <el-menu-item index="/works"><el-icon><Document /></el-icon> 内容管理</el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon> 用户管理</el-menu-item>
        <el-menu-item index="/orders"><el-icon><Tickets /></el-icon> 订单管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #ebeef5;display:flex;align-items:center;justify-content:space-between;padding:0 24px;height:56px">
        <span style="font-weight:600;color:#303133;font-size:15px">{{ route.meta.title }}</span>
        <el-button text @click="logout" style="color:#909399">退出登录</el-button>
      </el-header>
      <el-main style="background:#f5f6fa;padding:20px 24px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const logout = () => { localStorage.removeItem('adminToken'); router.push('/login') }
</script>
