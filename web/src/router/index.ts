import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/admin' },
    { path: '/login', name: 'login', component: () => import('../views/login/LoginView.vue') },
    {
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/admin/school-years' },
        { path: 'school-years', name: 'admin-school-years', component: () => import('../views/admin/SchoolYearView.vue') },
        { path: 'classes', name: 'admin-classes', component: () => import('../views/admin/ClassView.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../views/admin/UserView.vue') },
        { path: 'permissions', name: 'admin-permissions', component: () => import('../views/admin/PermissionView.vue') },
        { path: 'import', name: 'admin-import', component: () => import('../views/admin/ImportView.vue') }
      ]
    },
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
