import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/utils/auth'
import Dashboard from "@/views/Dashboard.vue"
import Login from "@/views/Login.vue"

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard,
    meta: { requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from) => {
  const token = getAccessToken()

  if (to.meta.requiresAuth && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  } else if (to.name === 'Login' && token) {
    return { name: 'Dashboard' }
  }
})

export default router
