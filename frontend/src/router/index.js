import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getAccessToken, isProfileComplete } from '@/utils/auth'
import { isTokenUsable } from '@/utils/jwt'
import { recordBootstrapMetric } from '@/utils/metrics'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

async function clearStaleAuth() {
  useAuthStore().clearAuth()
}

router.beforeEach(async (to, _from) => {
  const token = getAccessToken()
  const hasUsableToken = isTokenUsable(token)
  const profileComplete = isProfileComplete()

  if (token && !hasUsableToken) {
    await clearStaleAuth()
  }

  recordBootstrapMetric('router_before_each', {
    to: to.name || to.path,
    hasUsableToken,
    profileComplete,
  })

  if (to.meta.requiresAuth && !hasUsableToken) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (to.name === 'Login' && hasUsableToken) {
    if (!profileComplete) {
      recordBootstrapMetric('router_profile_incomplete', {
        to: to.name || to.path,
      })
    }
    return { name: 'Dashboard' }
  }

  return true
})

export default router
