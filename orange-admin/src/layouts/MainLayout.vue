<template>
  <el-container class="admin-shell">
    <a class="skip-link" href="#admin-main-content">跳到主要内容</a>
    <el-aside width="220px" class="admin-sidebar">
      <div class="admin-brand">
        OrangeJD
        <span>管理端</span>
      </div>
      <el-menu :default-active="route.path" background-color="#1a1a2e" text-color="#8890a0" active-text-color="#e65100" router>
        <el-menu-item v-for="item in visibleNavItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div class="header-title">
          <el-button class="mobile-menu-button" text aria-label="打开管理菜单" @click="mobileMenuOpen = true">
            <el-icon><Menu /></el-icon>
          </el-button>
          <span>{{ route.meta.title }}</span>
        </div>
        <el-button text @click="logout" class="logout-button">退出登录</el-button>
      </el-header>
      <el-main id="admin-main-content" class="admin-main" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="mobileMenuOpen" direction="ltr" size="280px" :with-header="false" class="admin-mobile-drawer">
      <div class="drawer-brand">OrangeJD <span>管理端</span></div>
      <el-menu :default-active="route.path" router @select="mobileMenuOpen = false">
        <el-menu-item v-for="item in visibleNavItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>{{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'
const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const navItems = [
  { path: '/dashboard', label: '控制台', icon: 'DataAnalysis' },
  { path: '/audit', label: '审核管理', icon: 'Checked' },
  { path: '/works', label: '内容管理', icon: 'Document' },
  { path: '/users', label: '用户管理', icon: 'User' },
  { path: '/orders', label: '订单管理', icon: 'Tickets', tradeOnly: true },
  { path: '/knowledge', label: '知识库', icon: 'Collection' },
  { path: '/redemption', label: '充值码管理', icon: 'Ticket' },
  { path: '/ai-config', label: 'AI 调用设置', icon: 'Setting' },
  { path: '/pricing', label: '配额定价', icon: 'Coin' },
  { path: '/job-audit', label: '投稿审核', icon: 'Checked' },
  { path: '/usage', label: '用量统计', icon: 'DataAnalysis' },
  { path: '/launch-checks', label: '上线体检', icon: 'DocumentChecked' },
  { path: '/open-source', label: '开源贡献', icon: 'Connection' },
  { path: '/settings', label: '修改密码', icon: 'Setting' },
]
const visibleNavItems = computed(() => navItems.filter(item => !item.tradeOnly || tradeEnabled.value))
const logout = async () => {
  try { await request.get('/admin/auth/logout') } catch {}
  finally {
    localStorage.removeItem('adminToken')
    await router.replace('/login')
  }
}
onMounted(async () => {
  try { const r = await request.get('/admin/config/trade-mode'); if (r.code === 1) tradeEnabled.value = r.data.enabled } catch {}
})
</script>

<style scoped>
.admin-shell { min-height: 100vh; min-height: 100dvh; }
.skip-link {
  position: fixed; top: 8px; left: 12px; z-index: 3000;
  padding: 9px 14px; border-radius: 8px; background: #1a1a2e; color: #fff;
  text-decoration: none; transform: translateY(-160%); transition: transform .15s ease;
}
.skip-link:focus { transform: translateY(0); }
.admin-sidebar { height: 100vh; height: 100dvh; overflow-y: auto; background: #1a1a2e; }
.admin-brand { padding: 20px 16px 12px; color: #fff; font-size: 18px; font-weight: 700; letter-spacing: -.5px; border-bottom: 1px solid rgba(255,255,255,.08); }
.admin-brand span, .drawer-brand span { margin-left: 8px; color: #e65100; font-size: 11px; font-weight: 500; }
.admin-sidebar .el-menu { border-right: none; }
.admin-header {
  height: 56px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; border-bottom: 1px solid #ebeef5; background: #fff;
}
.header-title { display: flex; align-items: center; gap: 8px; color: #303133; font-size: 15px; font-weight: 600; }
.logout-button { color: #606266; }
.mobile-menu-button { display: none; margin-left: -10px; font-size: 20px; }
.admin-main { min-width: 0; padding: 20px 24px; background: #f5f6fa; }
.admin-main:focus { outline: none; }
.drawer-brand { padding: 18px 12px; color: #1a1a2e; font-size: 18px; font-weight: 700; }

@media (max-width: 900px) {
  .admin-sidebar { display: none; }
  .admin-header { height: 52px; padding: 0 14px; }
  .mobile-menu-button { display: inline-flex; }
  .admin-main { width: 100%; padding: 14px; overflow-x: auto; }
}
</style>

<style>
.admin-mobile-drawer .el-drawer__body { padding: 0; }
.admin-mobile-drawer .el-menu { border-right: 0; }
</style>
