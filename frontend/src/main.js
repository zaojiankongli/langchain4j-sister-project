import './assets/main.css'
import {createPinia} from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'


const app = createApp(App)
app.use(createPinia())
app.use(router)

// 全局错误处理：捕获 Vue 渲染和异步错误，防止白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[Global Error]', err, info)
  // 不阻止默认行为，让 ErrorBoundary 或浏览器自行处理
}

// 全局未捕获 Promise 拒绝：避免静默失败
window.addEventListener('unhandledrejection', (event) => {
  const message = event.reason?.message || event.reason || '未知错误'
  console.error('[Unhandled Rejection]', message)
  // 生产环境阻止默认行为（避免用户看到浏览器原始弹窗），开发环境保留浏览器红色告警
  if (!import.meta.env.DEV) {
    event.preventDefault()
  }
})

app.mount('#app')
