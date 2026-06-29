<script setup lang="ts">
import { computed, type DeepReadonly } from 'vue'
import type { UserProfile } from '../../composables/useUserProfile'
import { getMoodEmoji } from '../../utils/moodEmoji'

const props = defineProps<{
  profile: DeepReadonly<UserProfile> | null
  isLoading: boolean
  isOpen: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const level = computed(() => props.profile?.levelInfo?.currentLevel ?? props.profile?.current_level ?? 0)
const expCurrent = computed(() => props.profile?.levelInfo?.currentExp ?? props.profile?.current_exp ?? 0)
const expMax = computed(() => props.profile?.levelInfo?.levelUpExp ?? props.profile?.level_up_exp ?? 1)
const expPercent = computed(() => {
  const max = expMax.value || 1
  return Math.min(100, Math.round((expCurrent.value / max) * 100))
})

const meetDays = computed(() => props.profile?.meet_days ?? 0)
const messageCount = computed(() => props.profile?.message_count ?? 0)
const moodDesc = computed(() =>
  props.profile?.latestEmotion?.moodDescription ?? props.profile?.mood_description ?? '平静'
)
const moodEmoji = computed(() => getMoodEmoji(moodDesc.value.toLowerCase()))
const tags = computed(() => props.profile?.interest_tags ?? [])
const username = computed(() => props.profile?.username ?? '旅行者')

function padNum(n: number, len = 3): string {
  return String(n).padStart(len, '0')
}
</script>

<template>
  <Transition name="modal-fade">
    <div v-if="isOpen" class="profile-backdrop" @click.self="emit('close')">
      <div
        class="profile-card"
        role="dialog"
        aria-modal="true"
        aria-label="个人资料"
        @keydown.escape="emit('close')"
      >
        <!-- Close button -->
        <button class="profile-close" type="button" aria-label="关闭" @click="emit('close')">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </button>

        <!-- Loading -->
        <div v-if="isLoading" class="profile-loading">
          <span class="spinner" aria-hidden="true" />
          <span>加载中...</span>
        </div>

        <template v-else-if="profile">
          <!-- Avatar + Name -->
          <div class="profile-header">
            <div class="profile-avatar">
              <img v-if="profile.avatarUrl" :src="profile.avatarUrl" :alt="username" class="profile-avatar-img" />
              <span v-else class="profile-avatar-fallback">{{ username.charAt(0) }}</span>
            </div>
            <div class="profile-identity">
              <h2 class="profile-name">{{ username }}</h2>
              <p class="profile-mood">
                <span class="profile-mood-emoji">{{ moodEmoji }}</span>
                {{ moodDesc }}
              </p>
            </div>
          </div>

          <!-- Level / EXP -->
          <div class="profile-section">
            <div class="profile-level-row">
              <span class="profile-level-badge">Lv.{{ level }}</span>
              <div class="profile-exp-bar">
                <div class="profile-exp-fill" :style="{ width: `${expPercent}%` }" />
              </div>
              <span class="profile-exp-text">{{ expCurrent }} / {{ expMax }}</span>
            </div>
          </div>

          <!-- Stats grid -->
          <div class="profile-stats">
            <div class="profile-stat">
              <span class="profile-stat-value">{{ padNum(meetDays) }}</span>
              <span class="profile-stat-label">陪伴天数</span>
            </div>
            <div class="profile-stat">
              <span class="profile-stat-value">{{ padNum(messageCount) }}</span>
              <span class="profile-stat-label">对话次数</span>
            </div>
            <div class="profile-stat">
              <span class="profile-stat-value">{{ padNum(profile.total_exp ?? 0, 4) }}</span>
              <span class="profile-stat-label">总经验值</span>
            </div>
          </div>

          <!-- Interest tags -->
          <div v-if="tags.length > 0" class="profile-section">
            <h3 class="profile-section-title">兴趣标签</h3>
            <div class="profile-tags">
              <span v-for="tag in tags" :key="tag" class="profile-tag">{{ tag }}</span>
            </div>
          </div>

          <!-- PAD emotion -->
          <div class="profile-section">
            <h3 class="profile-section-title">情绪状态</h3>
            <div class="profile-pad">
              <div class="profile-pad-item">
                <span class="profile-pad-label">愉悦</span>
                <div class="profile-pad-bar">
                  <div
                    class="profile-pad-fill profile-pad-fill--pleasure"
                    :style="{ width: `${Math.round(((profile.latestEmotion?.pleasure ?? profile.pleasure ?? 0) + 1) * 50)}%` }"
                  />
                </div>
              </div>
              <div class="profile-pad-item">
                <span class="profile-pad-label">激活</span>
                <div class="profile-pad-bar">
                  <div
                    class="profile-pad-fill profile-pad-fill--arousal"
                    :style="{ width: `${Math.round(((profile.latestEmotion?.arousal ?? profile.arousal ?? 0.5)) * 100)}%` }"
                  />
                </div>
              </div>
              <div class="profile-pad-item">
                <span class="profile-pad-label">支配</span>
                <div class="profile-pad-bar">
                  <div
                    class="profile-pad-fill profile-pad-fill--dominance"
                    :style="{ width: `${Math.round(((profile.latestEmotion?.dominance ?? profile.dominance ?? 0) + 1) * 50)}%` }"
                  />
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- No profile -->
        <div v-else class="profile-empty">
          <p>暂无资料，请先登录</p>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.profile-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.profile-card {
  position: relative;
  width: 340px;
  max-height: 80vh;
  overflow-y: auto;
  padding: var(--space-5);
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: 0 1rem 3rem rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.profile-card::-webkit-scrollbar {
  width: 4px;
}

.profile-card::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 2px;
}

.profile-close {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color var(--duration-fast) ease, background var(--duration-fast) ease;
}

.profile-close:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

/* ── Loading / Empty ── */

.profile-loading,
.profile-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 160px;
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: profile-spin 0.6s linear infinite;
}

@keyframes profile-spin {
  to { transform: rotate(360deg); }
}

/* ── Header (avatar + name) ── */

.profile-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.profile-avatar {
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-soft);
  border: 2px solid var(--color-accent);
}

.profile-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar-fallback {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--color-accent);
}

.profile-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-size: var(--font-size-subtitle);
  font-weight: 600;
  color: var(--color-heading);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-mood {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}

.profile-mood-emoji {
  font-size: var(--font-size-body);
  line-height: 1;
}

/* ── Level / EXP ── */

.profile-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.profile-section-title {
  margin: 0;
  font-size: var(--font-size-caption);
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.profile-level-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.profile-level-badge {
  padding: 2px 8px;
  font-size: var(--font-size-caption);
  font-weight: 700;
  color: var(--color-action-text);
  background: var(--color-accent);
  border-radius: var(--radius-pill);
  white-space: nowrap;
}

.profile-exp-bar {
  flex: 1;
  height: 6px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
}

.profile-exp-fill {
  height: 100%;
  background: var(--color-accent);
  border-radius: 3px;
  transition: width 400ms ease;
}

.profile-exp-text {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* ── Stats grid ── */

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-2);
}

.profile-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-2);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.profile-stat-value {
  font-size: var(--font-size-subtitle);
  font-weight: 700;
  color: var(--color-heading);
  font-variant-numeric: tabular-nums;
}

.profile-stat-label {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  margin-top: 2px;
}

/* ── Interest tags ── */

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.profile-tag {
  padding: 2px 8px;
  font-size: var(--font-size-caption);
  color: var(--color-accent);
  background: var(--color-accent-soft);
  border-radius: var(--radius-pill);
  white-space: nowrap;
}

/* ── PAD emotion bars ── */

.profile-pad {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.profile-pad-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.profile-pad-label {
  width: 2rem;
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  text-align: right;
  flex-shrink: 0;
}

.profile-pad-bar {
  flex: 1;
  height: 5px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
}

.profile-pad-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 400ms ease;
}

.profile-pad-fill--pleasure {
  background: #ec4899;
}

.profile-pad-fill--arousal {
  background: #f59e0b;
}

.profile-pad-fill--dominance {
  background: #8b5cf6;
}

/* ── Modal transition ── */

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 200ms ease;
}

.modal-fade-enter-active .profile-card,
.modal-fade-leave-active .profile-card {
  transition: transform 200ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-from .profile-card {
  transform: scale(0.95);
}

.modal-fade-leave-to .profile-card {
  transform: scale(0.95);
}

@media (prefers-reduced-motion: reduce) {
  .modal-fade-enter-active,
  .modal-fade-leave-active,
  .modal-fade-enter-active .profile-card,
  .modal-fade-leave-active .profile-card {
    transition: none;
  }

  .spinner {
    animation: none;
    opacity: 0.6;
  }

  .profile-exp-fill,
  .profile-pad-fill {
    transition: none;
  }
}
</style>
