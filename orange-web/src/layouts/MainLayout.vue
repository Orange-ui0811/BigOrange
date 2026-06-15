<template>
  <el-container style="height:100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background:#1a1a2e;display:flex;flex-direction:column;overflow:hidden">
      <div style="padding:20px 16px 12px;color:#fff;font-size:18px;font-weight:700;letter-spacing:-0.5px;border-bottom:1px solid rgba(255,255,255,0.08);margin-bottom:4px">
        OrangeJD
      </div>
      <!-- 用户信息 -->
      <div style="padding:16px;text-align:center;border-bottom:1px solid rgba(255,255,255,0.06)">
        <el-avatar :size="48" style="background:#e65100;font-size:20px" :src="avatarUrl ? API+avatarUrl : undefined">{{ nickname ? nickname[0] : (me.nickname || me.username || '?')[0] }}</el-avatar>
        <div style="color:#d0d5dd;font-size:13px;margin-top:6px;font-weight:500">{{ nickname || me.nickname || me.username }}</div>
      </div>
      <el-menu :default-active="route.path" background-color="#1a1a2e" text-color="#8890a0" active-text-color="#e65100" router style="border-right:none;flex:1">
        <el-menu-item index="/market"><el-icon><Shop /></el-icon> 市场</el-menu-item>
        <el-menu-item index="/publish"><el-icon><Edit /></el-icon> 发布</el-menu-item>
        <el-menu-item index="/profile/me"><el-icon><User /></el-icon> 个人主页</el-menu-item>
        <el-menu-item index="/me/works"><el-icon><Folder /></el-icon> 我的作品</el-menu-item>
        <el-menu-item index="/me/orders"><el-icon><Tickets /></el-icon> 我的订单</el-menu-item>
        <el-menu-item index="/me/notifications"><el-icon><Bell /></el-icon> 通知<span v-if="unreadCount" style="margin-left:6px;background:#e65100;color:#fff;font-size:10px;padding:1px 6px;border-radius:10px">{{ unreadCount }}</span></el-menu-item>
        <el-menu-item index="/chat"><el-icon><ChatDotRound /></el-icon> 聊天<span v-if="chatUnread" style="margin-left:6px;background:#e65100;color:#fff;font-size:10px;padding:1px 6px;border-radius:10px">{{ chatUnread }}</span></el-menu-item>
        <el-menu-item index="/me/favorites"><el-icon><Star /></el-icon> 收藏夹</el-menu-item>
        <el-menu-item index="/me/history"><el-icon><Clock /></el-icon> 浏览历史</el-menu-item>
        <el-menu-item index="/me/settings"><el-icon><Setting /></el-icon> 设置</el-menu-item>
      </el-menu>
      <div style="padding:12px 16px;border-top:1px solid rgba(255,255,255,0.08)">
        <el-button text style="color:#8890a0;width:100%" @click="logout">退出登录</el-button>
      </div>
    </el-aside>
    <!-- 主区域 -->
    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #ebeef5;display:flex;align-items:center;justify-content:space-between;padding:0 24px;height:56px">
        <span style="font-weight:600;color:#303133;font-size:15px">{{ route.meta.title }}</span>
        <div style="display:flex;align-items:center;gap:16px">
          <!-- 搜索栏（市场页显示） -->
          <el-input v-if="route.name==='Market'" v-model="searchKeyword" placeholder="搜索作品..." size="small" style="width:220px" @keyup.enter="$emit('search',searchKeyword)" clearable />
        </div>
      </el-header>
      <el-main style="background:#faf9f7;padding:20px 24px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { unreadCount, avatarUrl, nickname } from '../stores/unread'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const router = useRouter()
const me = ref({ username: '', nickname: '' })
const chatUnread = ref(0)
const logout = () => { sessionStorage.removeItem('appToken'); router.push('/login') }

onMounted(async () => {
  try { const res = await request.get('/app/notification/unread-count'); if (res.code === 1) unreadCount.value = res.data } catch {}
  try { const res = await request.get('/app/profile/me'); if (res.code === 1) { me.value = res.data; nickname.value = res.data.nickname || ''; avatarUrl.value = res.data.avatarUrl || '' } } catch {}
  try {
    const res = await request.get('/app/conversation/list?page=1&size=50')
    if (res.code === 1 && res.data?.records) chatUnread.value = res.data.records.reduce((s, c) => s + (c.unreadCount || 0), 0)
  } catch {}
})
</script>
