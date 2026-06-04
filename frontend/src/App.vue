<template>
  <div class="app-root">
    <!-- 主应用内容 -->
    <ErrorBoundary>
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </ErrorBoundary>
  </div>
</template>

<script setup>
import ErrorBoundary from '@/components/ErrorBoundary.vue'
</script>

<style scoped>
.app-root {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: var(--color-bg-dark, #1a1a2e);
}

/* ── 页面过渡 ── */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.5s ease, transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.page-enter-from {
  opacity: 0;
  transform: scale(0.96) translateY(10px);
}
.page-leave-to {
  opacity: 0;
  transform: scale(1.02) translateY(-10px);
}
</style>