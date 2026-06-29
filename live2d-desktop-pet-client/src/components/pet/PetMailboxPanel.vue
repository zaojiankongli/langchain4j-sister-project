<script setup lang="ts">
import { computed } from 'vue'
import GlassPanel from '../glass/GlassPanel.vue'
import type { MailEntry } from '../../composables/usePetMailbox'
import { getTagMeta } from '../../composables/usePetMailbox'

const props = defineProps<{
  mails: readonly MailEntry[]
  isLoading: boolean
  isOpen: boolean
  unreadCount: number
}>()

const emit = defineEmits<{
  close: []
  read: [mailId: string]
  readAll: []
}>()

interface DateGroup {
  date: string
  items: readonly MailEntry[]
}

const groupedMails = computed<DateGroup[]>(() => {
  const groups = new Map<string, MailEntry[]>()
  for (const mail of props.mails) {
    const key = mail.date || '未知日期'
    let arr = groups.get(key)
    if (!arr) {
      arr = []
      groups.set(key, arr)
    }
    arr.push(mail)
  }
  return Array.from(groups.entries()).map(([date, items]) => ({ date, items }))
})
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
      aria-label="信箱"
      tabindex="-1"
      @keydown.escape="emit('close')"
    >
      <div class="panel-header">
        <div class="panel-header-left">
          <h2 class="panel-title">
            信箱
            <span v-if="unreadCount > 0" class="unread-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </h2>
        </div>
        <div class="panel-header-actions">
          <button
            v-if="unreadCount > 0"
            class="mark-all-btn"
            type="button"
            title="全部已读"
            @click="emit('readAll')"
          >
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M1.5 8.5l4 4L14.5 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            全部已读
          </button>
          <button
            class="panel-close"
            type="button"
            aria-label="关闭信箱"
            @click="emit('close')"
          >
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
              <path d="M4 4L14 14M14 4L4 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>

      <div class="panel-content">
        <!-- Loading -->
        <div v-if="isLoading" class="panel-loading">
          <span class="spinner" aria-hidden="true" />
          <span>加载中...</span>
        </div>

        <!-- Empty state -->
        <div v-if="!isLoading && mails.length === 0" class="panel-empty">
          <div class="panel-empty-icon" aria-hidden="true">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
              <rect x="4" y="12" width="40" height="26" rx="4" stroke="currentColor" stroke-width="1.5" opacity="0.4" />
              <path d="M4 16l20 12 20-12" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" opacity="0.4" />
            </svg>
          </div>
          <p class="panel-empty-text">信箱空空如也~</p>
        </div>

        <!-- Mail list grouped by date -->
        <template v-for="group in groupedMails" :key="group.date">
          <div class="date-divider">
            <span class="date-divider-text">{{ group.date }}</span>
          </div>

          <button
            v-for="mail in group.items"
            :key="mail.id"
            class="mail-card"
            :class="{ 'mail-card--unread': !mail.isRead }"
            type="button"
            @click="!mail.isRead && emit('read', mail.id)"
          >
            <div class="mail-card-header">
              <span
                class="mail-tag"
                :style="{ color: getTagMeta(mail.tag).color, borderColor: getTagMeta(mail.tag).color }"
              >
                {{ getTagMeta(mail.tag).label }}
              </span>
              <span v-if="!mail.isRead" class="mail-unread-dot" aria-label="未读" />
            </div>
            <p class="mail-subject">{{ mail.subject }}</p>
            <p class="mail-excerpt">{{ mail.excerpt }}</p>
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

/* ── Header ── */

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.panel-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.panel-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-heading);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.35rem;
  font-size: var(--font-size-caption);
  font-weight: 700;
  font-family: var(--font-body);
  color: #fff;
  background: #ef4444;
  border-radius: var(--radius-pill);
  line-height: 1;
}

.mark-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: var(--space-1) var(--space-2);
  font-size: var(--font-size-caption);
  font-family: var(--font-body);
  color: var(--color-text-muted);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}

.mark-all-btn:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
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

/* ── Mail cards ── */

.mail-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  cursor: default;
  font-family: var(--font-body);
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.mail-card--unread {
  border-color: var(--color-accent);
  box-shadow: inset 3px 0 0 var(--color-accent);
  cursor: pointer;
}

.mail-card--unread:hover {
  box-shadow:
    inset 3px 0 0 var(--color-accent),
    0 2px 8px rgba(0, 0, 0, 0.06);
}

.mail-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mail-tag {
  display: inline-block;
  padding: 1px 6px;
  font-size: var(--font-size-caption);
  font-weight: 500;
  border: 1px solid;
  border-radius: var(--radius-sm);
  line-height: 1.4;
}

.mail-unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  flex-shrink: 0;
}

.mail-subject {
  margin: 0;
  font-size: var(--font-size-small);
  font-weight: 600;
  color: var(--color-heading);
  line-height: var(--line-height-tight);
}

.mail-excerpt {
  margin: 0;
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
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

  .panel-close,
  .mark-all-btn,
  .mail-card {
    transition: none;
  }
}
</style>
