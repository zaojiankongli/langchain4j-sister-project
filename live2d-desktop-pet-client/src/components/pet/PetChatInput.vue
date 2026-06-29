<script setup lang="ts">
import { computed, nextTick, onUnmounted, shallowRef, useTemplateRef } from 'vue'
import type { ChatSendPayload } from '../../composables/useChatMessages'

interface Props {
  canSend: boolean
  isSending?: boolean
  sendError?: string
  realtimeActive?: boolean
  canToggleRealtime?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  send: [payload: ChatSendPayload]
  clearError: []
  inputFocus: [focused: boolean]
  firstInteraction: []
  toggleRealtime: []
}>()

const inputRef = useTemplateRef<HTMLInputElement>('chatInput')
const fileInputRef = useTemplateRef<HTMLInputElement>('fileInput')
const chatInput = shallowRef('')
const pendingImagePreviewUrl = shallowRef<string | null>(null)
const pendingImageFile = shallowRef<File | null>(null)
const imageError = shallowRef('')
const stickerTrayOpen = shallowRef(false)
let hasInteracted = false

const stickerPresets = ['(ฅ´ω`ฅ)', '♡', '贴贴', '摸摸头'] as const
const MAX_IMAGE_SIZE_BYTES = 4 * 1024 * 1024

const trimmedInput = computed(() => chatInput.value.trim())
const canSubmit = computed(() => !props.isSending && props.canSend && (trimmedInput.value.length > 0 || !!pendingImagePreviewUrl.value))
const realtimeLabel = computed(() => props.realtimeActive ? '停止实时语音' : '开启实时语音')
const stickerTrayLabel = computed(() => stickerTrayOpen.value ? '收起表情包' : '打开表情包')

function revokePendingImagePreviewUrl(): void {
  if (pendingImagePreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(pendingImagePreviewUrl.value)
  }
}

function onInputKeydown(e: KeyboardEvent): void {
  emit('clearError')
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleSend(): void {
  if (!canSubmit.value) return
  emit('send', {
    text: trimmedInput.value,
    imageUrl: pendingImagePreviewUrl.value ?? undefined,
    imageFile: pendingImageFile.value ?? undefined,
  })
  chatInput.value = ''
  pendingImagePreviewUrl.value = null
  pendingImageFile.value = null
  imageError.value = ''
  stickerTrayOpen.value = false
}

function toggleStickerTray(): void {
  stickerTrayOpen.value = !stickerTrayOpen.value
}

function appendSticker(sticker: string): void {
  const separator = chatInput.value.length > 0 && !chatInput.value.endsWith(' ') ? ' ' : ''
  chatInput.value = `${chatInput.value}${separator}${sticker}`
  focusInput()
}

function triggerFileSelect(): void {
  fileInputRef.value?.click()
}

function onFileSelected(event: Event): void {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !file.type.startsWith('image/')) {
    return
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    imageError.value = '图片请控制在 4MB 以内'
    target.value = ''
    return
  }

  imageError.value = ''
  revokePendingImagePreviewUrl()
  pendingImageFile.value = file
  pendingImagePreviewUrl.value = URL.createObjectURL(file)
  emit('clearError')
  target.value = ''
}

function clearPendingImage(): void {
  revokePendingImagePreviewUrl()
  pendingImagePreviewUrl.value = null
  pendingImageFile.value = null
  imageError.value = ''
  emit('clearError')
}

function onFocus(): void {
  emit('inputFocus', true)
  if (!hasInteracted) {
    hasInteracted = true
    emit('firstInteraction')
  }
}

function onBlur(): void {
  emit('inputFocus', false)
}

function focusInput(): void {
  nextTick(() => inputRef.value?.focus())
}

defineExpose({ focusInput })

onUnmounted(() => {
  revokePendingImagePreviewUrl()
})
</script>

<template>
  <div class="chat-dock">
    <div v-if="pendingImagePreviewUrl" class="image-preview" aria-label="图片预览">
      <img :src="pendingImagePreviewUrl" alt="待发送图片" class="image-preview-img" />
      <span v-if="props.isSending" class="image-preview-status">上传中…</span>
      <button type="button" class="image-preview-clear" aria-label="移除图片" @click="clearPendingImage">✕</button>
    </div>
    <p v-if="imageError" class="image-error">{{ imageError }}</p>
    <p v-else-if="props.sendError" class="image-error">{{ props.sendError }}</p>

    <div v-if="stickerTrayOpen" class="sticker-tray" aria-label="表情包快捷选择">
      <button
        v-for="sticker in stickerPresets"
        :key="sticker"
        class="sticker-chip"
        type="button"
        :disabled="!props.canSend"
        @click="appendSticker(sticker)"
      >
        {{ sticker }}
      </button>
      <span class="sticker-more">更多表情包后续可扩展</span>
    </div>

    <div class="chat-float-bar">
      <button
        class="voice-toggle-btn"
        :class="{ 'voice-toggle-btn--active': props.realtimeActive }"
        type="button"
        :aria-label="realtimeLabel"
        :aria-pressed="props.realtimeActive ? 'true' : 'false'"
        :disabled="!props.canToggleRealtime || props.isSending"
        @click="emit('toggleRealtime')"
      >
        <svg aria-hidden="true" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3Z" />
          <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
          <path d="M12 19v3" />
        </svg>
      </button>
      <button
        class="image-toggle-btn"
        type="button"
        :disabled="!props.canSend || props.isSending"
        aria-label="添加图片"
        title="发图片"
        @click="triggerFileSelect"
      >
        🖼
      </button>
      <input
        ref="fileInput"
        class="chat-file-input"
        type="file"
        accept="image/*"
        aria-label="选择图片"
        @change="onFileSelected"
      />
      <button
        class="sticker-toggle-btn"
        :class="{ 'sticker-toggle-btn--active': stickerTrayOpen }"
        type="button"
        :aria-label="stickerTrayLabel"
        :aria-expanded="stickerTrayOpen"
        :disabled="!props.canSend || props.isSending"
        title="表情"
        @click="toggleStickerTray"
      >
        ฅ♡
      </button>
      <input
        ref="chatInput"
        v-model="chatInput"
        class="chat-text-input"
        type="text"
        name="pet-chat-message"
        aria-label="发送聊天消息"
        placeholder="和小伙伴说点悄悄话…"
        :disabled="!props.canSend || props.isSending"
        autocomplete="off"
        @input="emit('clearError')"
        @keydown="onInputKeydown"
        @focus="onFocus"
        @blur="onBlur"
      />
      <button
        class="send-btn"
        type="button"
        aria-label="发送"
        :disabled="!canSubmit"
        @click="handleSend"
      >
        <template v-if="props.isSending">
          <span class="send-btn-spinner" aria-hidden="true"></span>
          <span class="send-btn-text">发送中</span>
        </template>
        <svg v-else aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13" />
          <polygon points="22 2 15 22 11 13 2 9 22 2" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-dock {
  display: grid;
  gap: 8px;
}

.image-preview {
  position: relative;
  padding: 6px;
  border: 1px solid rgba(255, 196, 214, 0.3);
  border-radius: 18px;
  background: rgba(44, 34, 48, 0.62);
  backdrop-filter: blur(10px);
}

.image-preview-status {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 11px;
}

.image-preview-img {
  display: block;
  width: 100%;
  max-height: 8rem;
  object-fit: cover;
  border-radius: 12px;
}

.image-preview-clear {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: rgba(17, 24, 39, 0.7);
  color: #fff;
  cursor: pointer;
}

.image-error {
  margin: 0;
  font-size: 11px;
  color: #b91c1c;
}

.sticker-tray {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  overflow-x: auto;
  padding: 6px 8px;
  border: 1px solid rgba(255, 196, 214, 0.26);
  border-radius: 18px;
  background:
    radial-gradient(circle at 16% 0%, rgba(255, 255, 255, 0.28), transparent 34%),
    rgba(44, 34, 48, 0.62);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  overscroll-behavior-x: contain;
  box-shadow: 0 10px 24px rgba(42, 20, 32, 0.18);
}

.sticker-chip {
  flex: 0 0 auto;
  border: 1px solid rgba(255, 206, 221, 0.34);
  border-radius: 999px;
  padding: 4px 9px;
  color: #7b5260;
  background: rgba(255, 247, 250, 0.9);
  font-size: 12px;
  line-height: 1.25;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
  transition:
    transform 120ms ease,
    border-color 150ms ease,
    background-color 150ms ease;
}

.sticker-chip:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(255, 143, 179, 0.62);
  background: #fff;
}

.sticker-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.sticker-chip:focus-visible {
  outline: 2px solid rgba(255, 143, 179, 0.72);
  outline-offset: 2px;
}

.sticker-more {
  flex: 0 0 auto;
  color: rgba(255, 245, 249, 0.54);
  font-size: 11px;
  white-space: nowrap;
}

.chat-float-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 6px 6px 10px;
  background:
    linear-gradient(135deg, rgba(255, 244, 250, 0.8), rgba(255, 220, 236, 0.54)),
    rgba(34, 24, 38, 0.52);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 196, 214, 0.32);
  border-radius: 22px;
  box-shadow:
    0 12px 28px rgba(42, 20, 32, 0.2),
    0 2px 8px rgba(255, 143, 184, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.42);
  opacity: 0.86;
  touch-action: manipulation;
  transition:
    opacity 180ms ease,
    border-color 180ms ease,
    background-color 180ms ease,
    box-shadow 180ms ease;
}

.voice-toggle-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.42);
  color: rgba(126, 83, 98, 0.72);
  cursor: pointer;
  padding: 0;
  transition:
    transform 120ms ease,
    opacity 150ms ease,
    background-color 150ms ease,
    color 150ms ease;
}

.image-toggle-btn,
.sticker-toggle-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.44);
  color: rgba(126, 83, 98, 0.8);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
  padding: 0 0 2px;
  transition:
    transform 120ms ease,
    opacity 150ms ease,
    background-color 150ms ease,
    color 150ms ease;
}

.chat-file-input {
  display: none;
}

.voice-toggle-btn--active {
  background: rgba(59, 130, 246, 0.22);
  color: rgba(219, 234, 254, 0.92);
  box-shadow: 0 0 0 1px rgba(96, 165, 250, 0.18);
}

.voice-toggle-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.72);
  color: rgba(126, 83, 98, 0.94);
  transform: scale(1.04);
}

.voice-toggle-btn--active:hover:not(:disabled) {
  background: rgba(59, 130, 246, 0.3);
}

.voice-toggle-btn:disabled {
  opacity: 0.32;
  cursor: not-allowed;
}

.voice-toggle-btn:focus-visible {
  outline: 2px solid rgba(96, 165, 250, 0.72);
  outline-offset: 2px;
}

.sticker-toggle-btn--active,
.image-toggle-btn:hover:not(:disabled),
.sticker-toggle-btn:hover:not(:disabled) {
  background: rgba(255, 143, 179, 0.24);
  color: #9a4f67;
  box-shadow: 0 0 0 1px rgba(255, 143, 179, 0.22);
  transform: scale(1.04);
}

.image-toggle-btn:disabled,
.sticker-toggle-btn:disabled {
  opacity: 0.32;
  cursor: not-allowed;
}

.image-toggle-btn:focus-visible,
.sticker-toggle-btn:focus-visible {
  outline: 2px solid rgba(255, 143, 179, 0.72);
  outline-offset: 2px;
}

.chat-float-bar:focus-within {
  opacity: 0.96;
  border-color: rgba(255, 143, 179, 0.52);
  background:
    linear-gradient(135deg, rgba(255, 249, 252, 0.88), rgba(255, 227, 239, 0.68)),
    rgba(41, 29, 45, 0.6);
  box-shadow:
    0 14px 32px rgba(42, 20, 32, 0.24),
    0 0 0 1px rgba(255, 143, 179, 0.16);
}

.chat-text-input {
  flex: 1;
  min-width: 0;
  height: 30px;
  border: 0;
  background: transparent;
  color: #704b58;
  font-size: 12px;
  line-height: 30px;
  outline: none;
}

.chat-text-input::placeholder {
  color: rgba(112, 75, 88, 0.5);
}

.chat-text-input:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.send-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 28px;
  min-width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffabc8, #f7c36f);
  color: #fff;
  cursor: pointer;
  transition:
    transform 120ms ease,
    opacity 150ms ease,
    background-color 150ms ease,
    color 150ms ease;
  padding: 0;
}

.send-btn-text {
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.send-btn-spinner {
  width: 10px;
  height: 10px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: chat-send-spin 0.7s linear infinite;
}

.send-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #ff8fb3, #f5b65f);
  color: #fff;
  transform: scale(1.04);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.94);
}

.send-btn:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

.send-btn:focus-visible {
  outline: 2px solid rgba(244, 163, 182, 0.72);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .chat-float-bar,
  .voice-toggle-btn,
  .sticker-toggle-btn,
  .sticker-chip,
  .send-btn {
    transition: none;
  }
  .send-btn:hover:not(:disabled),
  .voice-toggle-btn:hover:not(:disabled),
  .sticker-toggle-btn:hover:not(:disabled),
  .sticker-chip:hover:not(:disabled),
  .send-btn:active:not(:disabled) {
    transform: none;
  }

  .send-btn-spinner {
    animation: none;
  }
}

@keyframes chat-send-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
