<script setup lang="ts">
import { onMounted, onUnmounted, nextTick, watch, useTemplateRef } from 'vue'
import { handleTabTrap } from '../../utils/focusTrap'

const props = defineProps<{
  email: string
  code: string
  step: 'email' | 'code'
  isSendingCode: boolean
  isLoggingIn: boolean
  authError: string
  countdown: number
}>()

const emit = defineEmits<{
  'update:email': [value: string]
  'update:code': [value: string]
  sendCode: []
  login: []
  goBack: []
  close: []
  clearError: []
}>()

const emailInputRef = useTemplateRef<HTMLInputElement>('emailInput')
const codeInputRef = useTemplateRef<HTMLInputElement>('codeInput')

onMounted(() => {
  document.body.style.overflow = 'hidden'
  nextTick(() => {
    if (props.step === 'email') emailInputRef.value?.focus()
    else codeInputRef.value?.focus()
  })
})

onUnmounted(() => {
  document.body.style.overflow = ''
})

watch(() => props.step, (newStep) => {
  if (newStep === 'code') {
    nextTick(() => codeInputRef.value?.focus())
  }
})
</script>

<template>
  <div
    class="auth-overlay"
    tabindex="-1"
    @click.self="emit('close')"
    @keydown.escape="emit('close')"
  >
    <div class="auth-card" role="dialog" aria-modal="true" aria-label="登录" @keydown.tab="handleTabTrap">
      <button
        class="auth-close"
        type="button"
        aria-label="关闭"
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

      <h2 class="auth-title">登录</h2>
      <p class="auth-subtitle">输入邮箱验证码快速登录</p>

      <p v-if="authError" class="auth-error" role="alert">{{ authError }}</p>

      <template v-if="step === 'email'">
        <input
          ref="emailInput"
          class="auth-input"
          type="email"
          :value="email"
          placeholder="请输入邮箱"
          autocomplete="email"
          @keydown.enter="emit('sendCode')"
          @input="emit('update:email', ($event.target as HTMLInputElement).value); emit('clearError')"
        />
        <button
          class="auth-btn auth-btn--accent"
          :disabled="isSendingCode || countdown > 0"
          type="button"
          @click="emit('sendCode')"
        >
          <template v-if="isSendingCode">
            <span class="auth-btn-spinner" aria-hidden="true" />
            发送中...
          </template>
          <template v-else-if="countdown > 0">
            重新发送 ({{ countdown }}s)
          </template>
          <template v-else>发送验证码</template>
        </button>
      </template>

      <template v-if="step === 'code'">
        <input
          ref="codeInput"
          class="auth-input"
          type="text"
          inputmode="numeric"
          maxlength="6"
          :value="code"
          placeholder="请输入6位验证码"
          autocomplete="one-time-code"
          @keydown.enter="emit('login')"
          @input="
            emit(
              'update:code',
              ($event.target as HTMLInputElement).value.replace(/\D/g, ''),
            );
            emit('clearError')
          "
        />
        <button
          class="auth-btn auth-btn--accent"
          :disabled="isLoggingIn"
          type="button"
          @click="emit('login')"
        >
          <template v-if="isLoggingIn">
            <span class="auth-btn-spinner" aria-hidden="true" />
            登录中...
          </template>
          <template v-else>登录</template>
        </button>
        <button
          class="auth-btn auth-btn--ghost"
          type="button"
          @click="emit('goBack')"
        >
          返回
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.auth-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.auth-card {
  position: relative;
  width: min(90vw, 24rem);
  padding: var(--space-7) var(--space-6) var(--space-6);
  background: rgba(25, 23, 31, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.auth-close {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
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

.auth-close:hover {
  color: var(--color-text);
  background: var(--color-surface-subtle);
}

.auth-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-heading);
  text-align: center;
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
}

.auth-subtitle {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  text-align: center;
}

.auth-error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-small);
  color: var(--color-danger);
  background: rgba(255, 143, 124, 0.1);
  border-radius: var(--radius-sm);
  text-align: center;
}

.auth-input {
  width: 100%;
  box-sizing: border-box;
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-body);
  font-family: var(--font-body);
  color: var(--color-text);
  background: var(--color-field-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  transition:
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.auth-input::placeholder {
  color: var(--color-text-muted);
  opacity: 0.6;
}

.auth-input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 1px var(--color-accent);
}

.auth-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-body);
  font-family: var(--font-body);
  font-weight: 500;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    opacity var(--duration-fast) ease,
    transform var(--duration-fast) ease;
  user-select: none;
}

.auth-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.auth-btn--accent {
  color: var(--color-action-text);
  background: var(--color-accent);
}

.auth-btn--accent:hover:not(:disabled) {
  opacity: 0.9;
}

.auth-btn--accent:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.auth-btn--ghost {
  color: var(--color-text-muted);
  background: transparent;
}

.auth-btn--ghost:hover {
  color: var(--color-text);
  background: var(--color-surface-subtle);
}

.auth-btn-spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-action-text);
  border-top-color: transparent;
  border-radius: var(--radius-pill);
  animation: auth-spin 0.6s linear infinite;
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-overlay {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .auth-card {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .auth-btn-spinner {
    animation: none;
    opacity: 0.6;
  }

  .auth-btn:active:not(:disabled) {
    transform: none;
  }

  .auth-close {
    transition: none;
  }

  .auth-input {
    transition: none;
  }

  .auth-btn {
    transition: none;
  }
}
</style>
