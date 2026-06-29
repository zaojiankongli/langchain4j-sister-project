<script setup lang="ts">
import GlassPanel from '../glass/GlassPanel.vue'
import type { Recommendation } from '../../composables/useRecommendations'
import { getResourceEmoji } from '../../composables/useRecommendations'

const props = defineProps<{
  items: readonly Recommendation[]
  isLoading: boolean
  isOpen: boolean
}>()

const emit = defineEmits<{
  close: []
  click: [id: string]
}>()

function openLink(item: Recommendation): void {
  if (item.url) {
    window.open(item.url, '_blank', 'noopener')
  }
  if (!item.isClicked) {
    emit('click', item.id)
  }
}

function scorePercent(score: number): string {
  return `${Math.round(score * 100)}%`
}
</script>

<template>
  <Transition name="panel-slide">
    <GlassPanel
      v-if="isOpen"
      class="panel"
      tag="aside"
      @backdrop-click="emit('close')"
      role="dialog"
      aria-modal="true"
      aria-label="今日推荐"
      tabindex="-1"
      @keydown.escape="emit('close')"
    >
      <div class="panel-header">
        <h2 class="panel-title">今日推荐</h2>
        <button
          class="panel-close"
          type="button"
          aria-label="关闭"
          @click="emit('close')"
        >
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path d="M4 4L14 14M14 4L4 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="panel-content">
        <!-- Loading -->
        <div v-if="isLoading" class="panel-loading">
          <span class="spinner" aria-hidden="true" />
          <span>加载中...</span>
        </div>

        <!-- Empty -->
        <div v-else-if="items.length === 0" class="panel-empty">
          <p class="panel-empty-text">今天还没有推荐哦~</p>
          <p class="panel-empty-hint">推荐内容会在每日凌晨自动更新</p>
        </div>

        <!-- Recommendations list -->
        <template v-else>
          <button
            v-for="item in items"
            :key="item.id"
            class="recom-card"
            :class="{ 'recom-card--clicked': item.isClicked }"
            type="button"
            @click="openLink(item)"
          >
            <div class="recom-header">
              <span class="recom-type">{{ getResourceEmoji(item.resourceType) }} {{ item.resourceType }}</span>
              <span class="recom-score" :title="`相关度 ${scorePercent(item.relevanceScore)}`">
                {{ scorePercent(item.relevanceScore) }}
              </span>
            </div>
            <h3 class="recom-title">{{ item.title }}</h3>
            <p v-if="item.description" class="recom-desc">{{ item.description }}</p>
            <div class="recom-footer">
              <span class="recom-source">{{ item.source }}</span>
              <span v-if="item.isClicked" class="recom-read-mark">已读</span>
            </div>
          </button>
        </template>
      </div>
    </GlassPanel>
  </Transition>
</template>

<style scoped>
.panel {
  position: fixed;
  top: 0;
  right: 0;
  width: 360px;
  height: 100%;
  z-index: 101;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.panel-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-heading);
  line-height: var(--line-height-tight);
}

.panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color var(--duration-fast) ease, background var(--duration-fast) ease;
}

.panel-close:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.panel-content::-webkit-scrollbar {
  width: 4px;
}

.panel-content::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 2px;
}

.panel-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-5);
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: recom-spin 0.6s linear infinite;
}

@keyframes recom-spin {
  to { transform: rotate(360deg); }
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 200px;
  text-align: center;
}

.panel-empty-text {
  margin: 0;
  font-size: var(--font-size-body);
  color: var(--color-text-muted);
}

.panel-empty-hint {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  opacity: 0.7;
}

/* ── Recommendation cards ── */

.recom-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  font-family: var(--font-body);
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.recom-card:hover {
  border-color: var(--color-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.recom-card--clicked {
  opacity: 0.65;
}

.recom-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recom-type {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

.recom-score {
  font-size: var(--font-size-caption);
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.recom-title {
  margin: 0;
  font-size: var(--font-size-small);
  font-weight: 600;
  color: var(--color-heading);
  line-height: var(--line-height-tight);
}

.recom-desc {
  margin: 0;
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recom-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.recom-source {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  opacity: 0.7;
}

.recom-read-mark {
  font-size: var(--font-size-caption);
  color: var(--color-success);
  font-weight: 500;
}

/* ── Transition ── */

.panel-slide-enter-active {
  transition: transform 250ms ease, opacity 250ms ease;
}

.panel-slide-leave-active {
  transition: transform 200ms ease, opacity 200ms ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .panel-slide-enter-active,
  .panel-slide-leave-active {
    transition: none;
  }

  .spinner {
    animation: none;
    opacity: 0.6;
  }

  .recom-card,
  .panel-close {
    transition: none;
  }
}
</style>
