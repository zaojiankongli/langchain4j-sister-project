import './assets/main.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { recordBootstrapMetric } from '@/utils/metrics'

const app = createApp(App)
app.use(createPinia())
app.use(router)

app.config.errorHandler = (err, _instance, info) => {
  console.error('[Global Error]', err, info)
  recordBootstrapMetric('vue_error', {
    info,
    message: err?.message || String(err),
  })
}

window.addEventListener('unhandledrejection', (event) => {
  const message = event.reason?.message || event.reason || '未知错误'
  console.error('[Unhandled Rejection]', message)
  recordBootstrapMetric('unhandled_rejection', { message: String(message) })
  if (!import.meta.env.DEV) {
    event.preventDefault()
  }
})

recordBootstrapMetric('app_bootstrap', { stage: 'before_mount' })
app.mount('#app')
recordBootstrapMetric('app_bootstrap', { stage: 'mounted' })
