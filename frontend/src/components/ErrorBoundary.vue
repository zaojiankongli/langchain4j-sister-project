<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-boundary__content">
      <h2>😅 哎呀，出错了</h2>
      <p class="error-boundary__message">{{ errorMessage }}</p>
      <p class="error-boundary__hint">你可以刷新页面重试</p>
      <button class="error-boundary__btn" @click="reload">刷新页面</button>
    </div>
  </div>
  <slot v-else />
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'

const hasError = ref(false)
const errorMessage = ref('')

onErrorCaptured((err) => {
  hasError.value = true
  errorMessage.value = err.message || '发生了未知错误'
  console.error('[ErrorBoundary]', err)
  return false
})

function reload() {
  hasError.value = false
  errorMessage.value = ''
  window.location.reload()
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-primary, #f5f5f5);
  padding: 20px;
}

.error-boundary__content {
  text-align: center;
  max-width: 400px;
  padding: 40px;
  background: var(--bg-card, #fff);
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.error-boundary__content h2 {
  margin: 0 0 16px;
  font-size: 20px;
  color: var(--text-primary, #333);
}

.error-boundary__message {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--text-secondary, #666);
  word-break: break-word;
}

.error-boundary__hint {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--text-tertiary, #999);
}

.error-boundary__btn {
  padding: 10px 28px;
  font-size: 14px;
  color: #fff;
  background: var(--accent-color, #e5739b);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.error-boundary__btn:hover {
  opacity: 0.85;
}
</style>
