import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/utils/auth'
import { isTokenUsable } from '@/utils/jwt'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

async function clearStaleAuth() {
  const { useAuthStore } = await import('@/stores/auth')
  useAuthStore().clearAuth()
}

// 路由守卫
router.beforeEach(async (to, _from) => {
  const token = getAccessToken()
  const hasUsableToken = isTokenUsable(token)

  if (token && !hasUsableToken) {
    await clearStaleAuth()
  }

  if (to.meta.requiresAuth && !hasUsableToken) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  } else if (to.name === 'Login' && hasUsableToken) {
    return { name: 'Dashboard' }
  }
})

export default router
