import { createRouter, createWebHistory } from 'vue-router'
import { transactionUiEnabled } from '../stores/tradeMode'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/authStore'
import { OFFICIAL_SITE_URL, PUBLIC_BRAND_NAME } from '../config/brand'

export const canonicalUrlForPath = path => `${OFFICIAL_SITE_URL}${path === '/' ? '/' : path}`

export const routes = [
  { path: '/', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '首页', description: '连接独立开发者与真实需求方，让作品、合作与交付在一个地方发生。', public: true } },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录与注册', description: `登录或注册 ${PUBLIC_BRAND_NAME} 官方账号。`, robots: 'noindex,nofollow' } },
  { path: '/legal/:document(terms|privacy|third-parties)', name: 'LegalDocument', component: () => import('../views/LegalDocument.vue'), meta: { title: '法律与隐私', description: 'OrangeVC 用户协议、隐私政策与第三方服务清单。', public: true } },
  { path: '/support', name: 'SupportCenter', component: () => import('../views/SupportCenter.vue'), meta: { title: '账号、数据、举报与申诉', description: 'OrangeVC 账号、数据权利、安全举报与申诉渠道。', public: true } },
  {
    path: '/app',
    name: 'Main',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/ai',
    children: [
      { path: '/market', name: 'Market', component: () => import('../views/Market.vue'), meta: { title: '公开作品与需求市场', description: '公开浏览 Vibe Coding 作品与真实项目需求，登录后再进行互动。', public: true } },
      { path: '/ai', name: 'AiHub', component: () => import('../views/AiHub.vue'), meta: { title: 'AI 工具', description: `${PUBLIC_BRAND_NAME} AI 工具、新手项目、职业成长与开源机会入口。`, public: true } },
      { path: '/ai-records', name: 'AiRecords', component: () => import('../views/AiRecords.vue'), meta: { title: 'AI 分析记录' } },
      { path: '/persona-workshop', name: 'PersonaWorkshop', component: () => import('../views/PersonaWorkshop.vue'), meta: { title: '橙子画像工坊' } },
      { path: '/project-tools', name: 'ProjectTools', component: () => import('../views/ProjectTools.vue'), meta: { title: '做自己的项目' } },
      { path: '/me', name: 'MeHub', component: () => import('../views/MeHub.vue'), meta: { title: '我的' } },
      { path: '/leaderboard', name: 'Leaderboard', component: () => import('../views/Leaderboard.vue'), meta: { title: '榜单排行' } },
      { path: '/beginner-projects', redirect: '/leaderboard?source=beginner' },
      { path: '/trend', name: 'TrendAnalysis', component: () => import('../views/TrendAnalysis.vue'), meta: { title: '趋势分析' } },
      { path: '/customer', name: 'CustomerService', component: () => import('../views/CustomerService.vue'), meta: { title: '项目客服' } },
      { path: '/trend-analysis', redirect: '/trend' },
      { path: '/plan', name: 'PlanDesigner', component: () => import('../views/PlanDesigner.vue'), meta: { title: '方案设计' } },
      { path: '/launch-check', name: 'LaunchCheck', component: () => import('../views/LaunchCheck.vue'), meta: { title: '上线体检' } },
      { path: '/open-source', name: 'OpenSourceContribution', component: () => import('../views/OpenSourceContribution.vue'), meta: { title: '开源贡献' } },
      { path: '/ai/career', name: 'CareerCenter', component: () => import('../views/CareerCenter.vue'), meta: { title: 'AI 大赛与能力参考' } },
      { path: '/work/:id', name: 'WorkDetail', component: () => import('../views/WorkDetail.vue'), meta: { title: '作品详情' } },
      { path: '/chat', name: 'Chat', component: () => import('../views/Chat.vue'), meta: { title: '聊天' } },
      { path: '/chat/:id', name: 'ChatWith', component: () => import('../views/Chat.vue'), meta: { title: '聊天' } },
      { path: '/publish', name: 'Publish', component: () => import('../views/Publish.vue'), meta: { title: '发布' } },
      { path: '/edit/:type/:id', name: 'Edit', component: () => import('../views/Publish.vue'), meta: { title: '编辑' } },
      { path: '/me/works', name: 'MyWorks', component: () => import('../views/MyWorks.vue'), meta: { title: '我的作品' } },
      { path: '/me/orders', name: 'MyOrders', component: () => import('../views/MyOrders.vue'), meta: { title: '我的订单', requiresTransactionUi: true } },
      { path: '/me/favorites', name: 'MyFavorites', component: () => import('../views/MyFavorites.vue'), meta: { title: '收藏夹' } },
      { path: '/me/history', name: 'MyHistory', component: () => import('../views/MyHistory.vue'), meta: { title: '浏览历史' } },
      { path: '/me/edit', name: 'EditProfile', component: () => import('../views/EditProfile.vue'), meta: { title: '编辑资料' } },
      { path: '/me/notifications', name: 'MeNotifications', component: () => import('../views/MeNotifications.vue'), meta: { title: '通知' } },
      { path: '/submit-job', redirect: '/ai/career' },
      { path: '/me/quota', name: 'MeQuota', component: () => import('../views/MeQuota.vue'), meta: { title: '我的配额' } },
      { path: '/me/settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: '设置' } },
      { path: '/me/agent-devices', name: 'AgentDevices', component: () => import('../views/AgentDevices.vue'), meta: { title: '智能设备' } },
      { path: '/profile/:id', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '作者主页' } },
      { path: '/requirement/:id', name: 'ReqDetail', component: () => import('../views/ReqDetail.vue'), meta: { title: '需求详情' } },
      { path: '/order/:id', name: 'OrderDetail', component: () => import('../views/OrderDetail.vue'), meta: { title: '订单详情', requiresTransactionUi: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '页面不存在', description: '您访问的 OrangeVC 页面不存在。', robots: 'noindex,nofollow', public: true } },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = useAuthStore(pinia).token
  if (to.meta.public) next()
  else if (to.path !== '/login' && !token) next({ path: '/login', query: { redirect: to.fullPath } })
  else if (to.path === '/login' && token) next('/market')
  else if (to.meta.requiresTransactionUi && !transactionUiEnabled) next('/market')
  else next()
})

router.afterEach(to => {
  document.title = `${to.meta.title || PUBLIC_BRAND_NAME} · ${PUBLIC_BRAND_NAME}`
  const setMeta = (name, content) => {
    let element = document.head.querySelector(`meta[name="${name}"]`)
    if (!element) {
      element = document.createElement('meta')
      element.setAttribute('name', name)
      document.head.appendChild(element)
    }
    element.setAttribute('content', content)
  }
  setMeta('description', to.meta.description || `${PUBLIC_BRAND_NAME} · Vibe Coding 作品、项目与 AI 工具平台`)
  setMeta('robots', to.meta.robots || 'index,follow')
  let canonical = document.head.querySelector('link[rel="canonical"]')
  if (!canonical) {
    canonical = document.createElement('link')
    canonical.setAttribute('rel', 'canonical')
    document.head.appendChild(canonical)
  }
  canonical.setAttribute('href', canonicalUrlForPath(to.path))
  window.scrollTo({ top: 0, left: 0 })
  requestAnimationFrame(() => (document.getElementById('main-content') || document.getElementById('home-main'))?.focus({ preventScroll: true }))
})

export default router
