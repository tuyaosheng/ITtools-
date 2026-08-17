import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/admin' },
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { requiresAuth: true } },
    { path: '/teacher', name: 'teacher', component: () => import('../views/ComingSoonView.vue'), meta: { requiresAuth: true } },
    { path: '/student', name: 'student', component: () => import('../views/ComingSoonView.vue'), meta: { requiresAuth: true } }
  ]
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (!auth.isAuthenticated) {
    await auth.fetchMe()
  }
  if (!auth.isAuthenticated) {
    return { name: 'login' }
  }
  return true
})

export default router
