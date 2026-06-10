import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    name: 'Main',
    component: () => import('../layouts/MainLayout.vue'),
    redirect: '/market',
    children: [
      { path: 'market', name: 'Market', component: () => import('../views/Market.vue'), meta: { title: '市场' } },
      { path: 'work/:id', name: 'WorkDetail', component: () => import('../views/WorkDetail.vue'), meta: { title: '作品详情' } },
      { path: 'chat', name: 'Chat', component: () => import('../views/Chat.vue'), meta: { title: '聊天' } },
      { path: 'chat/:id', name: 'ChatWith', component: () => import('../views/Chat.vue'), meta: { title: '聊天' } },
      { path: 'publish', name: 'Publish', component: () => import('../views/Publish.vue'), meta: { title: '发布' } },
      { path: 'me/works', name: 'MyWorks', component: () => import('../views/MyWorks.vue'), meta: { title: '我的作品' } },
      { path: 'me/orders', name: 'MyOrders', component: () => import('../views/MyOrders.vue'), meta: { title: '我的订单' } },
      { path: 'me/favorites', name: 'MyFavorites', component: () => import('../views/MyFavorites.vue'), meta: { title: '收藏夹' } },
      { path: 'me/history', name: 'MyHistory', component: () => import('../views/MyHistory.vue'), meta: { title: '浏览历史' } },
      { path: 'me/edit', name: 'EditProfile', component: () => import('../views/EditProfile.vue'), meta: { title: '编辑资料' } },
      { path: 'me/notifications', name: 'MeNotifications', component: () => import('../views/MeNotifications.vue'), meta: { title: '通知' } },
      { path: 'me/settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: '设置' } },
      { path: 'profile/:id', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '作者主页' } },
      { path: 'requirement/:id', name: 'ReqDetail', component: () => import('../views/ReqDetail.vue'), meta: { title: '需求详情' } },
      { path: 'order/:id', name: 'OrderDetail', component: () => import('../views/OrderDetail.vue'), meta: { title: '订单详情' } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('appToken')
  if (to.path !== '/login' && !token) next('/login')
  else if (to.path === '/login' && token) next('/market')
  else next()
})

export default router
