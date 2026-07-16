import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    name: 'Main',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '控制台' } },
      { path: 'audit', name: 'Audit', component: () => import('../views/Audit.vue'), meta: { title: '审核管理' } },
      { path: 'works', name: 'Works', component: () => import('../views/Works.vue'), meta: { title: '内容管理' } },
      { path: 'users', name: 'Users', component: () => import('../views/Users.vue'), meta: { title: '用户管理' } },
      { path: 'orders', name: 'Orders', component: () => import('../views/Orders.vue'), meta: { title: '订单管理' } },
      { path: 'knowledge', name: 'Knowledge', component: () => import('../views/Knowledge.vue'), meta: { title: '知识库' } },
      { path: 'redemption', name: 'Redemption', component: () => import('../views/Redemption.vue'), meta: { title: '充值码管理' } },
      { path: 'job-audit', name: 'JobAudit', component: () => import('../views/JobAudit.vue'), meta: { title: '岗位审核' } },
      { path: 'ai-config', name: 'AIConfig', component: () => import('../views/AIConfig.vue'), meta: { title: 'AI 调用设置' } },
      { path: 'pricing', name: 'Pricing', component: () => import('../views/Pricing.vue'), meta: { title: 'AI 消费定价' } },
      { path: 'usage', name: 'UsageStats', component: () => import('../views/UsageStats.vue'), meta: { title: '用量统计' } },
      { path: 'settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: '修改密码' } },
      { path: 'work/:id', name: 'WorkDetail', component: () => import('../views/WorkDetail.vue'), meta: { title: '作品详情' } },
      { path: 'requirement/:id', name: 'ReqDetail', component: () => import('../views/WorkDetail.vue'), meta: { title: '需求详情' } }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('adminToken')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

router.afterEach(to => {
  document.title = `${to.meta.title || '管理端'} · OrangeJD`
  window.scrollTo({ top: 0, left: 0 })
  requestAnimationFrame(() => document.getElementById('admin-main-content')?.focus({ preventScroll: true }))
})

export default router
