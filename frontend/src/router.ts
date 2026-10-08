import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/LoginView.vue') },
    { path: '/', component: () => import('./views/DashboardView.vue') },
    { path: '/devices', component: () => import('./views/DevicesView.vue') },
    { path: '/waylines', component: () => import('./views/WaylinesView.vue') },
    { path: '/jobs', component: () => import('./views/JobsView.vue') },
    { path: '/flights', component: () => import('./views/FlightsView.vue') },
    { path: '/plots', component: () => import('./views/PlotsView.vue') },
    { path: '/system', component: () => import('./views/SystemView.vue') }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('feiji_token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  return true
})

export default router
