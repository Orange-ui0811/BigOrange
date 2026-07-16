<template>
  <el-container class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <!-- ═══ 侧边栏 ═══ -->
    <el-aside width="252px" class="app-sidebar">

      <!-- 品牌 + 像素橙子 -->
      <div class="sidebar-brand">
        <svg class="sidebar-logo" viewBox="0 0 40 40" aria-hidden="true">
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
          <rect x="14" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="24" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="18" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="22" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="19" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="21" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="20" y="30" width="1" height="1" fill="#5c260f" />
        </svg>
        <div class="sidebar-brand-text">
          <span class="brand-name">OrangeVC</span>
          <span class="brand-sub">vibe coding market</span>
        </div>
      </div>

      <!-- 用户面板 -->
      <div class="sidebar-user">
        <el-avatar :size="44" class="sidebar-avatar" :src="avatarUrl || undefined">
          {{ (nickname || me.nickname || me.username || '?')[0] }}
        </el-avatar>
        <div class="sidebar-user-info">
          <strong>{{ nickname || me.nickname || me.username }}</strong>
          <span>创作者</span>
        </div>
      </div>

      <div class="sidebar-divider"></div>

      <!-- ═══ 5 项主导航 ═══ -->
      <div class="nav-section-label">主导航</div>
      <el-menu :default-active="activeMenu" class="sidebar-menu" router>
        <el-menu-item index="/market">
          <span class="nav-item-content">
            <el-icon><Shop /></el-icon>
            <span>发现</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <span class="nav-item-content">
            <el-icon><Cpu /></el-icon>
            <span>AI 工具</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/publish">
          <span class="nav-item-content">
            <el-icon><Edit /></el-icon>
            <span>发布</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <span class="nav-item-content">
            <el-icon><ChatDotRound /></el-icon>
            <span>消息</span>
          </span>
          <span v-if="chatUnread" class="menu-badge">{{ chatUnread }}</span>
        </el-menu-item>
        <el-menu-item index="/me">
          <span class="nav-item-content">
            <el-icon><User /></el-icon>
            <span>我的</span>
          </span>
        </el-menu-item>
      </el-menu>

      <!-- 橙子使用指南 -->
      <GuideBubble />

      <!-- 功能归属提示 -->
      <div class="nav-hint">
        <strong>功能归属</strong>
        榜单、趋势分析、方案设计 → AI 工具<br/>
        通知、收藏、历史、配额、设置 → 我的
      </div>

      <!-- ═══ 底部工具 ═══ -->
      <div class="sidebar-tools">
        <div class="sidebar-divider"></div>
        <button class="tool-btn" type="button" @click="$router.push('/me/notifications')">
          <span>系统通知</span>
          <span v-if="unreadCount" class="menu-badge">{{ unreadCount }}</span>
        </button>
        <button class="tool-btn" type="button" @click="$router.push('/me/settings')">
          <span>设置</span>
        </button>
        <button class="tool-btn logout-btn" type="button" @click="logout">
          <span>退出</span>
        </button>
      </div>
    </el-aside>

    <!-- ═══ 主区域 ═══ -->
    <el-container>
      <el-header class="app-header">
        <div class="topbar-title">
          <h1>{{ route.meta.title || 'OrangeVC' }}</h1>
        </div>
        <div class="top-actions"></div>
      </el-header>
      <el-main id="main-content" class="app-main" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>

    <nav class="mobile-nav" aria-label="主导航">
      <button v-for="item in mobileNavItems" :key="item.path" type="button"
              :class="{ active: activeMenu === item.path }" :aria-current="activeMenu === item.path ? 'page' : undefined"
              @click="router.push(item.path)">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
        <span v-if="item.path === '/chat' && chatUnread" class="mobile-badge">{{ chatUnread }}</span>
      </button>
    </nav>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Shop, Cpu, Edit, ChatDotRound, User } from '@element-plus/icons-vue'
import GuideBubble from '../components/GuideBubble.vue'
import request from '../api/request'
import { unreadCount, avatarUrl, nickname } from '../stores/unread'
import { tradeEnabled } from '../stores/tradeMode'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const router = useRouter()
const me = ref({ username: '', nickname: '' })
const chatUnread = ref(0)
const mobileNavItems = [
  { path: '/market', label: '发现', icon: Shop },
  { path: '/ai', label: 'AI 工具', icon: Cpu },
  { path: '/publish', label: '发布', icon: Edit },
  { path: '/chat', label: '消息', icon: ChatDotRound },
  { path: '/me', label: '我的', icon: User },
]
const logout = async () => {
  try { await request.get('/app/auth/logout') } catch {}
  finally {
    sessionStorage.removeItem('appToken')
    await router.replace('/login')
  }
}

/* ---- 根据子路由高亮父级菜单 ---- */
const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/market') || p.startsWith('/work/') || p.startsWith('/req/') || p.startsWith('/order/')) return '/market'
  if (p.startsWith('/ai') || p.startsWith('/leaderboard') || p.startsWith('/trend') || p.startsWith('/plan') || p.startsWith('/job')) return '/ai'
  if (p.startsWith('/publish')) return '/publish'
  if (p.startsWith('/chat')) return '/chat'
  if (p.startsWith('/me') || p.startsWith('/profile')) return '/me'
  return '/market'
})

onMounted(async () => {
  try { const res = await request.get('/app/notification/unread-count'); if (res.code === 1) unreadCount.value = res.data } catch {}
  try { const res = await request.get('/app/profile/me'); if (res.code === 1) { me.value = res.data; nickname.value = res.data.nickname || ''; avatarUrl.value = res.data.avatarUrl || '' } } catch {}
  try {
    const res = await request.get('/app/conversation/list?page=1&size=50')
    if (res.code === 1 && res.data?.records) chatUnread.value = res.data.records.reduce((s, c) => s + (c.unreadCount || 0), 0)
  } catch {}
  try { const r = await request.get('/app/config/trade-mode'); if (r.code === 1) tradeEnabled.value = r.data.enabled } catch {}
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  min-height: 100dvh;
}

.skip-link {
  position: fixed;
  top: 8px;
  left: 12px;
  z-index: 1000;
  padding: 9px 14px;
  border-radius: 10px;
  background: var(--text-primary);
  color: #fff;
  text-decoration: none;
  transform: translateY(-160%);
  transition: transform .15s ease;
}
.skip-link:focus { transform: translateY(0); }

/* ===== 侧边栏 ===== */
.app-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  height: 100dvh;
  background: rgba(255, 255, 255, .86) !important;
  border-right: 1px solid rgba(254, 215, 170, .85) !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
}

/* 品牌 */
.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
}

.sidebar-logo {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  filter: drop-shadow(0 7px 0 rgba(92, 38, 15, .14));
}

.sidebar-brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 900;
  color: var(--brand-strong);
  letter-spacing: -.045em;
}

.brand-sub {
  font-family: var(--font-body);
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 700;
  margin-top: 1px;
}

/* 用户面板 */
.sidebar-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  margin: 0;
  border: 1px solid var(--border-orange);
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(255, 237, 213, 0.7), rgba(255, 255, 255, 0.7));
  box-shadow: var(--shadow-pixel-sm);
}

.sidebar-avatar {
  background: linear-gradient(135deg, var(--brand), #fbbf24) !important;
  border: 2px solid #fff !important;
  flex-shrink: 0;
  border-radius: 16px !important;
}

.sidebar-user-info strong {
  display: block;
  font-size: 13px;
  color: var(--text-primary);
}

.sidebar-user-info span {
  font-size: 11px;
  color: var(--text-secondary);
}

/* 分割线 */
.sidebar-divider {
  height: 1px;
  background: var(--border-orange);
  margin: 8px 14px;
  opacity: 0.7;
}

/* 导航标签 */
.nav-section-label {
  margin: 6px 14px 4px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* 菜单 */
.sidebar-menu {
  flex: 0 0 auto;
  overflow-y: visible;
  max-height: 320px;
  border-right: none !important;
  background: transparent !important;
  padding: 2px 8px;
}
.sidebar-menu .el-menu-item {
  font-family: var(--font-mono) !important;
  font-size: 13px !important;
  min-height: 42px !important;
  line-height: 42px !important;
  color: var(--text-secondary) !important;
  border-radius: 14px !important;
  padding: 0 12px !important;
  margin: 2px 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  border-left: 3px solid transparent !important;
  transition: background 0.15s, color 0.15s, border-color 0.15s !important;
}
.sidebar-menu .el-menu-item:hover {
  background: #fff7ed !important;
  color: var(--brand-strong) !important;
  transform: translateX(2px);
}
.sidebar-menu .el-menu-item.is-active {
  color: var(--brand-strong) !important;
  background: var(--brand-soft) !important;
  border-left: 3px solid var(--brand) !important;
  font-weight: 800 !important;
}
.sidebar-menu .el-menu-item .el-icon {
  font-size: 18px !important;
  margin-right: 0 !important;
}
.nav-item-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 功能归属提示 */
.nav-hint {
  margin: 10px 10px 6px;
  padding: 10px 12px;
  border: 1px solid rgba(254, 215, 170, 0.7);
  border-radius: 16px;
  background: rgba(255, 250, 244, 0.6);
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}
.nav-hint strong {
  display: block;
  margin-bottom: 3px;
  color: var(--brown);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: -0.3px;
}

/* 徽章 */
.menu-badge {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #fff;
  background: var(--brand);
  font-size: 11px;
  font-family: var(--font-mono);
  font-weight: 900;
  line-height: 1;
}

/* 底部工具区 */
.sidebar-tools {
  padding: 14px 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-top: 1px solid rgba(254, 215, 170, .85);
  margin-top: auto;
}

.tool-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 38px;
  border: none;
  border-radius: 12px;
  padding: 0 12px;
  background: transparent;
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  text-align: left;
}
.tool-btn:hover {
  background: #fff7ed;
  color: var(--brand-strong);
}
.logout-btn:hover {
  color: #e53935;
  background: #fff5f5;
}

/* ===== 顶栏 ===== */
.app-header {
  position: sticky;
  top: 0;
  z-index: 5;
  background: rgba(255, 250, 244, 0.88) !important;
  border-bottom: 1px solid rgba(254, 215, 170, 0.7) !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  padding: 0 28px !important;
  height: 52px !important;
}

.topbar-title h1 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 900;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ===== 内容区 ===== */
.app-main {
  background: transparent !important;
  padding: 24px 32px 48px !important;
}

.mobile-nav { display: none; }

@media (max-width: 860px) {
  .app-sidebar { display: none !important; }
  .app-header {
    padding: 0 18px !important;
    height: 50px !important;
  }
  .topbar-title h1 { font-size: 16px; }
  .app-main {
    width: 100%;
    padding: 18px 14px calc(92px + env(safe-area-inset-bottom)) !important;
  }
  .mobile-nav {
    position: fixed;
    z-index: 50;
    right: 10px;
    bottom: max(10px, env(safe-area-inset-bottom));
    left: 10px;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    min-height: 62px;
    padding: 6px;
    border: 1px solid rgba(254, 215, 170, .92);
    border-radius: 20px;
    background: rgba(255, 255, 255, .96);
    box-shadow: 0 12px 32px rgba(92, 38, 15, .16);
    backdrop-filter: blur(16px);
  }
  .mobile-nav button {
    position: relative;
    display: flex;
    min-width: 0;
    min-height: 50px;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    gap: 3px;
    border: 0;
    border-radius: 14px;
    background: transparent;
    color: var(--text-secondary);
    font: 700 10px/1.2 var(--font-body);
    cursor: pointer;
  }
  .mobile-nav button.active {
    background: var(--brand-soft);
    color: var(--brand-strong);
  }
  .mobile-nav .el-icon { font-size: 20px; }
  .mobile-badge {
    position: absolute;
    top: 2px;
    right: max(8px, calc(50% - 23px));
    min-width: 17px;
    height: 17px;
    padding: 0 4px;
    border: 2px solid #fff;
    border-radius: 999px;
    background: var(--brand);
    color: #fff;
    font-size: 9px;
    line-height: 13px;
  }
}
</style>
