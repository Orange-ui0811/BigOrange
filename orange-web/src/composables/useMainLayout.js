import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Shop, Cpu, Edit, ChatDotRound, User, Document, MagicStick } from '@element-plus/icons-vue'
import GuideBubble from '../components/GuideBubble.vue'
import ReleaseAnnouncementDialog from '../components/ReleaseAnnouncementDialog.vue'
import request from '../api/request'
import { unreadCount, avatarUrl, nickname, loadUnreadCount, resetNotificationState } from '../stores/unread'
import { loadTradeMode, resetTradeMode } from '../stores/tradeMode'
import { useAuthStore } from '../stores/authStore'
import { LEGAL_ICP_RECORD, OFFICIAL_SITE_URL } from '../content/legal'
import { PUBLIC_OPERATOR_LABEL } from '../config/brand'

export const useMainLayout = () => {
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const authenticated = computed(() => authStore.authenticated)
const me = ref({ username: '', nickname: '' })
const chatUnread = ref(0)
const mobileNavItems = [
  { path: '/market', label: '发现', icon: Shop },
  { path: '/ai', label: 'AI 工具', icon: Cpu },
  { path: '/customer', label: '客服', icon: ChatDotRound },
  { path: '/ai-records', label: '记录', icon: Document },
  { path: '/persona-workshop', label: '画像', icon: MagicStick },
  { path: '/publish', label: '发布', icon: Edit },
  { path: '/chat', label: '消息', icon: ChatDotRound },
  { path: '/me', label: '我的', icon: User },
]
const guestMobileNavItems = [
  { path: '/ai', label: 'AI 工具', icon: Cpu },
  { path: '/market', label: '公开市场', icon: Shop },
  { path: '/login', label: '登录', icon: User },
]
const visibleMobileNavItems = computed(() => authenticated.value ? mobileNavItems : guestMobileNavItems)
const logout = async () => {
  try { await request.get('/app/auth/logout') } catch {}
  finally {
    authStore.clear()
    resetNotificationState(); resetTradeMode()
    await router.replace('/login')
  }
}

/* ---- 根据子路由高亮父级菜单 ---- */
const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/customer')) return '/customer'
  if (p.startsWith('/ai-records')) return '/ai-records'
  if (p.startsWith('/persona-workshop')) return '/persona-workshop'
  if (p.startsWith('/market') || p.startsWith('/work/') || p.startsWith('/req/') || p.startsWith('/order/')) return '/market'
  if (p.startsWith('/ai') || p.startsWith('/submit-job') || p.startsWith('/project-tools') || p.startsWith('/leaderboard') || p.startsWith('/beginner-projects') || p.startsWith('/trend') || p.startsWith('/plan') || p.startsWith('/launch-check') || p.startsWith('/open-source') || p.startsWith('/job')) return '/ai'
  if (p.startsWith('/publish')) return '/publish'
  if (p.startsWith('/chat')) return '/chat'
  if (p.startsWith('/me') || p.startsWith('/profile')) return '/me'
  return '/ai'
})

onMounted(async () => {
  if (!authenticated.value) return
  try { await loadUnreadCount() } catch {}
  try { const res = await request.get('/app/profile/me'); if (res.code === 1) { me.value = res.data; authStore.setCurrentUser(res.data); nickname.value = res.data.nickname || ''; avatarUrl.value = res.data.avatarUrl || '' } } catch {}
  try {
    const res = await request.get('/app/conversation/list?page=1&size=50')
    if (res.code === 1 && res.data?.records) chatUnread.value = res.data.records.reduce((s, c) => s + (c.unreadCount || 0), 0)
  } catch {}
  try { await loadTradeMode() } catch {}
})
  return { GuideBubble, ReleaseAnnouncementDialog, Shop, Cpu, Edit, ChatDotRound, User, Document, route,
    MagicStick, unreadCount, avatarUrl, nickname, me, chatUnread, visibleMobileNavItems,
    accountId: authStore.accountId, authenticated, router, logout, activeMenu,
    legalIcpRecord: LEGAL_ICP_RECORD, publicOperatorLabel: PUBLIC_OPERATOR_LABEL,
    officialSiteUrl: OFFICIAL_SITE_URL }}
