import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Shop, Cpu, Edit, ChatDotRound, User, Document, MagicStick } from '@element-plus/icons-vue'
import GuideBubble from '../components/GuideBubble.vue'
import PersonaOrangeBadge from '../components/PersonaOrangeBadge.vue'
import ReleaseAnnouncementDialog from '../components/ReleaseAnnouncementDialog.vue'
import request from '../api/request'
import { unreadCount, avatarUrl, nickname, loadUnreadCount, resetNotificationState } from '../stores/unread'
import { loadTradeMode, resetTradeMode } from '../stores/tradeMode'
import { useAuthStore } from '../stores/authStore'
import { usePersonaStore } from '../stores/personaStore'

export const useMainLayout = () => {
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const personaStore = usePersonaStore()
const me = ref({ username: '', nickname: '' })
const chatUnread = ref(0)
const personaAvatar = computed(() => ({
  enabled: Boolean(personaStore.data.settings.showcaseEnabled && personaStore.data.settings.avatarEnabled),
  equipment: personaStore.data.equipment,
  appearance: personaStore.data.settings,
}))
const mobileNavItems = [
  { path: '/market', label: '发现', icon: Shop },
  { path: '/ai', label: 'AI 工具', icon: Cpu },
  { path: '/ai-records', label: '记录', icon: Document },
  { path: '/persona-workshop', label: '画像', icon: MagicStick },
  { path: '/publish', label: '发布', icon: Edit },
  { path: '/chat', label: '消息', icon: ChatDotRound },
  { path: '/me', label: '我的', icon: User },
]
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
  if (p.startsWith('/ai-records')) return '/ai-records'
  if (p.startsWith('/persona-workshop')) return '/persona-workshop'
  if (p.startsWith('/market') || p.startsWith('/work/') || p.startsWith('/req/') || p.startsWith('/order/')) return '/market'
  if (p.startsWith('/ai') || p.startsWith('/submit-job') || p.startsWith('/project-tools') || p.startsWith('/leaderboard') || p.startsWith('/beginner-projects') || p.startsWith('/trend') || p.startsWith('/plan') || p.startsWith('/launch-check') || p.startsWith('/open-source') || p.startsWith('/job')) return '/ai'
  if (p.startsWith('/publish')) return '/publish'
  if (p.startsWith('/chat')) return '/chat'
  if (p.startsWith('/me') || p.startsWith('/profile')) return '/me'
  return '/market'
})

onMounted(async () => {
  try { await loadUnreadCount() } catch {}
  try { const res = await request.get('/app/profile/me'); if (res.code === 1) { me.value = res.data; authStore.setCurrentUser(res.data); nickname.value = res.data.nickname || ''; avatarUrl.value = res.data.avatarUrl || '' } } catch {}
  try { await personaStore.load() } catch {}
  try {
    const res = await request.get('/app/conversation/list?page=1&size=50')
    if (res.code === 1 && res.data?.records) chatUnread.value = res.data.records.reduce((s, c) => s + (c.unreadCount || 0), 0)
  } catch {}
  try { await loadTradeMode() } catch {}
})
  return { GuideBubble, PersonaOrangeBadge, ReleaseAnnouncementDialog, Shop, Cpu, Edit, ChatDotRound, User, Document, route,
    MagicStick, unreadCount, avatarUrl, nickname, me, chatUnread, personaAvatar, mobileNavItems,
    accountId: authStore.accountId, logout, activeMenu }}
