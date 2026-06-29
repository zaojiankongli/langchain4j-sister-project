<script setup lang="ts">
import { computed } from 'vue'
import GlassPanel from '../glass/GlassPanel.vue'
import type { ChatMessage } from '../../types/message'
import { getMoodEmoji } from '../../utils/moodEmoji'
import { formatTime } from '../../utils/formatTime'

const props = defineProps<{
  messages: ChatMessage[]
  isLoading: boolean
  hasMore: boolean
  isOpen: boolean
}>()

const emit = defineEmits<{
  close: []
  loadMore: []
}>()

interface DateSeparatorItem {
  type: 'date-separator'
  label: string
}

interface MessageItem {
  type: 'message'
  message: ChatMessage
}

type DisplayItem = DateSeparatorItem | MessageItem

const displayItems = computed<DisplayItem[]>(() => {
  const items: DisplayItem[] = []
  let lastDate = ''

  for (const msg of props.messages) {
    const dateKey = msg.createdAt.slice(0, 10)
    if (dateKey !== lastDate) {
      lastDate = dateKey
      const dateObj = new Date(msg.createdAt)
      const label = `${dateObj.getFullYear()}年${dateObj.getMonth() + 1}月${dateObj.getDate()}日`
      items.push({ type: 'date-separator', label })
    }
    items.push({ type: 'message', message: msg })
  }

  return items
})

function moodIcon(label: string): string {
  return getMoodEmoji(label.toLowerCase())
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
      aria-label="聊天记录"
      tabindex="-1"
      @keydown.escape="emit('close')"
    >
      <div class="panel-header">
        <h2 class="panel-title">聊天记录</h2>
        <button
          class="panel-close"
          type="button"
          aria-label="关闭聊天记录"
          @click="emit('close')"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 18 18"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M4 4L14 14M14 4L4 14"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>

      <div class="panel-content">
        <!-- Loading indicator -->
        <div v-if="isLoading" class="panel-loading">
          <span class="spinner" aria-hidden="true" />
          <span>加载中...</span>
        </div>

        <!-- Load more button -->
        <button
          v-if="hasMore && !isLoading"
          class="load-more-btn"
          type="button"
          @click="emit('loadMore')"
        >
          加载更多
        </button>

        <!-- Empty state -->
        <div v-if="!isLoading && messages.length === 0" class="panel-empty">
          <div class="panel-empty-icon" aria-hidden="true">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" aria-hidden="true">
              <rect x="6" y="12" width="36" height="24" rx="6" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" opacity="0.4" />
              <path d="M18 36l6-6h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.4" />
              <circle cx="18" cy="24" r="1.5" fill="currentColor" opacity="0.3" />
              <circle cx="24" cy="24" r="1.5" fill="currentColor" opacity="0.3" />
              <circle cx="30" cy="24" r="1.5" fill="currentColor" opacity="0.3" />
            </svg>
          </div>
          <p class="panel-empty-text">No chat history yet</p>
        </div>

        <!-- Message list with date grouping -->
        <template v-for="item in displayItems" :key="item.type === 'message' ? `msg-${item.message.id}` : `date-${item.label}`">
          <div v-if="item.type === 'date-separator'" class="date-divider">
            <span class="date-divider-text">{{ item.label }}</span>
          </div>

          <div v-else class="message" :class="`message--${item.message.role}`">
            <div class="message-bubble">
              <img
                v-if="item.message.imageUrl"
                :src="item.message.imageUrl"
                alt="聊天图片"
                class="message-image"
                loading="lazy"
              />
              <p class="message-text">{{ item.message.content }}</p>
              <div class="message-meta">
                <span class="message-time">{{ formatTime(item.message.createdAt) }}</span>
                <span
                  v-if="item.message.role === 'user' && item.message.deliveryState === 'sending'"
                  class="message-status"
                >发送中…</span>
                <span
                  v-if="item.message.role === 'assistant' && item.message.moodLabel"
                  class="message-mood"
                  :title="item.message.moodLabel"
                >
                  {{ moodIcon(item.message.moodLabel) }}
                </span>
              </div>
            </div>
          </div>
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

/* ── Header ── */

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
  letter-spacing: var(--letter-spacing-tight);
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
  transition:
    color var(--duration-fast) ease,
    background var(--duration-fast) ease;
}

.panel-close:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

/* ── Scrollable content ── */

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3) var(--space-3) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.panel-content::-webkit-scrollbar {
  width: 4px;
}

.panel-content::-webkit-scrollbar-track {
  background: transparent;
}

.panel-content::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: var(--radius-pill);
}

/* ── Loading spinner ── */

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
  border-radius: var(--radius-pill);
  animation: panel-spin 0.6s linear infinite;
}

@keyframes panel-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Load More button ── */

.load-more-btn {
  display: block;
  margin: var(--space-2) auto;
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-small);
  font-family: var(--font-body);
  color: var(--color-accent);
  background: transparent;
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

.load-more-btn:hover {
  background: var(--color-accent-soft);
}

/* ── Empty state ── */

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  height: 100%;
  user-select: none;
}

.panel-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  opacity: 0.5;
}

.panel-empty-text {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  opacity: 0.75;
}

/* ── Date divider ── */

.date-divider {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-3) 0;
}

.date-divider::before,
.date-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}

.date-divider-text {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

/* ── Chat messages ── */

.message {
  display: flex;
  flex-direction: column;
}

.message--user {
  align-items: flex-end;
}

.message--assistant {
  align-items: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  word-break: break-word;
}

.message-image {
  display: block;
  width: 100%;
  max-height: 14rem;
  object-fit: cover;
  border-radius: var(--radius-sm);
  margin-bottom: var(--space-2);
  background: rgba(0, 0, 0, 0.08);
}

.message--user .message-bubble {
  background: var(--color-accent);
  color: var(--color-action-text);
  border-bottom-right-radius: var(--radius-sm);
}

.message--assistant .message-bubble {
  background: var(--color-surface);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--radius-sm);
}

.message-text {
  margin: 0;
  font-size: var(--font-size-small);
  line-height: var(--line-height-body);
  white-space: pre-wrap;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-top: var(--space-1);
}

.message-time {
  font-size: var(--font-size-caption);
  opacity: 0.7;
}

.message-status {
  font-size: var(--font-size-caption);
  color: var(--color-warning, #d97706);
}

.message--user .message-time {
  color: var(--color-action-text);
}

.message--assistant .message-time {
  color: var(--color-text-muted);
}

.message-mood {
  font-size: var(--font-size-small);
  line-height: 1;
}

/* ── Slide transition (Vue) ── */

.panel-slide-enter-active {
  transition:
    transform 250ms ease,
    opacity 250ms ease;
}

.panel-slide-leave-active {
  transition:
    transform 200ms ease,
    opacity 200ms ease;
}

.panel-slide-enter-from {
  transform: translateX(100%);
  opacity: 0;
}

.panel-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ── Reduced motion ── */

@media (prefers-reduced-motion: reduce) {
  .panel-slide-enter-active,
  .panel-slide-leave-active {
    transition: none;
  }

  .panel-slide-enter-from,
  .panel-slide-leave-to {
    transform: none;
    opacity: 1;
  }

  .spinner {
    animation: none;
    opacity: 0.6;
  }

  .panel-close {
    transition: none;
  }

  .load-more-btn {
    transition: none;
  }
}
</style>
