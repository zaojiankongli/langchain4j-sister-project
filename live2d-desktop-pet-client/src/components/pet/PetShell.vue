<script setup lang="ts">
import { computed, defineAsyncComponent, shallowRef, useTemplateRef, watch } from 'vue'
import PetShellHeader from './PetShellHeader.vue'
import Live2DStageCard from './Live2DStageCard.vue'
import { live2dRendererName } from '../../live2d/rendererMetadata'
import { usePerformanceQuality } from '../../composables/usePerformanceQuality'
import { useChatState } from '../../composables/useChatState'
import { usePetConfigState } from '../../composables/usePetConfigState'
import { usePetConnectionController } from '../../composables/usePetConnectionController'
import { usePetDebugLog } from '../../composables/usePetDebugLog'
import { usePetEventReducer } from '../../composables/usePetEventReducer'
import { usePetInteractionGuards } from '../../composables/usePetInteractionGuards'
import { usePetRendererController } from '../../composables/usePetRendererController'
import { usePetRuntimeState } from '../../composables/usePetRuntimeState'
import { usePetShellLifecycle } from '../../composables/usePetShellLifecycle'
import { usePetShellDisplayState } from '../../composables/usePetShellDisplayState'
import { usePetStageState } from '../../composables/usePetStageState'
import { usePetStatusBadges } from '../../composables/usePetStatusBadges'
import { useSocketState } from '../../composables/useSocketState'
import { usePetWindowActions } from '../../composables/usePetWindowActions'
import { useAuthFlow } from '../../composables/useAuthFlow'
import { useChatMessages } from '../../composables/useChatMessages'
import { usePetAudioPlayer } from '../../composables/usePetAudioPlayer'
import { usePeekHandler } from '../../composables/usePeekHandler'
import { usePetSettings } from '../../composables/usePetSettings'
import { usePetToast } from '../../composables/usePetToast'
import { useMoodHistory } from '../../composables/useMoodHistory'
import { usePetInteraction } from '../../composables/usePetInteraction'
import { useUserProfile } from '../../composables/useUserProfile'
import { useRecommendations } from '../../composables/useRecommendations'
import { usePetTheme } from '../../composables/usePetTheme'
import { useClientSettings } from '../../composables/useClientSettings'
import { usePetPanels } from '../../composables/usePetPanels'
import { usePetNotifications } from '../../composables/usePetNotifications'
import { usePetScreenshot } from '../../composables/usePetScreenshot'
import { usePetShellHotkeys } from '../../composables/usePetShellHotkeys'
import { usePetShellBootstrap } from '../../composables/usePetShellBootstrap'
import { usePetSocketEventPipeline } from '../../composables/usePetSocketEventPipeline'
import { usePetShellActionHandlers } from '../../composables/usePetShellActionHandlers'
import { usePetShellUiGlue } from '../../composables/usePetShellUiGlue'
import { usePetShellTransientUi } from '../../composables/usePetShellTransientUi'
import { useLocalCompanionMode } from '../../composables/useLocalCompanionMode'
import { useLocalCompanionSettings } from '../../composables/useLocalCompanionSettings'
import { useSharedMusicPresence } from '../../composables/useSharedMusicPresence'
import { useChatDisplay } from '../../composables/useChatDisplay'
import { useBackendMemoryGallery } from '../../composables/useBackendMemoryGallery'
import { usePetChatModule } from '../../composables/usePetChatModule'
import { sendPetRealtimeAudio, startPetRealtime, stopPetRealtime } from '../../ws/petStompClient'
import PetEmotionIndicator from './PetEmotionIndicator.vue'
import PetChatInput from './PetChatInput.vue'
import PetRadialMenu from './PetRadialMenu.vue'
import { usePetMemory } from '../../composables/usePetMemory'
import { usePetMailbox } from '../../composables/usePetMailbox'

const PetDebugPanel = defineAsyncComponent(() => import('./PetDebugPanel.vue'))
const PetAuthModal = defineAsyncComponent(() => import('./PetAuthModal.vue'))
const PetChatHistoryPanel = defineAsyncComponent(() => import('./PetChatHistoryPanel.vue'))
const PetMoodHistoryPanel = defineAsyncComponent(() => import('./PetMoodHistoryPanel.vue'))
const PetMemoryPanel = defineAsyncComponent(() => import('./PetMemoryPanel.vue'))
const PetMailboxPanel = defineAsyncComponent(() => import('./PetMailboxPanel.vue'))
const PetProfileCard = defineAsyncComponent(() => import('./PetProfileCard.vue'))
const PetRecommendPanel = defineAsyncComponent(() => import('./PetRecommendPanel.vue'))
const preloadDebugPanel = () => import('./PetDebugPanel.vue')
const debugPanelRef = useTemplateRef<{
  focusPreferredField: (preferToken: boolean) => Promise<void>
}>('debugPanelRef')

const { modelPath, invalidModelPath, canvasRef, status, activeModelPath, rendererHandle, lastLoadError } = usePetStageState()

const { authToken, debugPanelOpen, toggleDebugPanel, clearAuthTokens, userId } = usePetConfigState()
const { chatInput, streamPreviewLabel } = useChatState()
const { moodLabel, petRuntimeState, lastSemanticEvent, moodDescription, pleasure, arousal, dominance } = usePetRuntimeState()
const { socketStatus, lastSocketError, reconnectAttempt, reconnectMaxAttempts, reconnectDelayMs, reconnectHint, socketStatusLabel, socketStatusVisualState, isReconnecting } = useSocketState()
const { logEntries, recentEvents, appendLog } = usePetDebugLog(live2dRendererName)

const { handleSocketEvent } = usePetEventReducer({
  activeModelPath,
  moodLabel,
  pleasure,
  arousal,
  dominance,
  petRuntimeState,
  lastSemanticEvent,
  rendererHandle,
  appendLog,
})

// New composables for Wave 3/4 features
const chatMessages = useChatMessages()
const audioPlayer = usePetAudioPlayer()
const peekHandler = usePeekHandler()
const memoryGallery = useBackendMemoryGallery({ userId })

// Chat display layer (replaces legacy streamText dual-write)
const chatDisplay = useChatDisplay({ chatMessages, petRuntimeState })
const { localCompanionSettings } = useLocalCompanionSettings()
const { blurStrength, shouldPauseAmbientMotion, isPageVisible } = usePerformanceQuality()

const localCompanionMode = useLocalCompanionMode({
  socketStatus,
  isReconnecting,
  stageStatus: status,
  settings: localCompanionSettings,
  isActive: isPageVisible,
})

const companionBubbleContent = computed(() => {
  return chatDisplay.latestBubbleContent.value || localCompanionMode.localCompanionBubble.value
})

function handleLocalCompanionTap(): void {
  const reaction = localCompanionMode.handleLocalPetTap()
  if (!reaction) {
    return
  }

  lastSemanticEvent.value = `local:motion:${reaction.motion}`
  void rendererHandle.value?.playSemanticMotion(reaction.motion)
}

let socketEventPipeline: ReturnType<typeof usePetSocketEventPipeline> | null = null

function wrappedHandleSocketEvent(event: Parameters<NonNullable<typeof socketEventPipeline>['handleSocketEvent']>[0]): void {
  socketEventPipeline?.handleSocketEvent(event)
}

const { disposeRenderer, setCanvasRef, loadModel, refreshLayout } = usePetRendererController({
  canvasRef,
  status,
  activeModelPath,
  rendererHandle,
  petRuntimeState,
  lastLoadError,
  appendLog,
})
// Settings (declared early for enableAudio used in controller below)
const petSettings = usePetSettings()
const { clientSettings } = useClientSettings()
// Apply theme from settings
usePetTheme(petSettings.currentSettings, computed(() => clientSettings.value.petDisplay))
const enableAudio = shallowRef(false)
watch(petSettings.currentSettings, (settings) => {
  enableAudio.value = settings?.tts?.enabled ?? false
}, { immediate: true })

const { connectSocket, disconnectSocket, sendChatMessage } = usePetConnectionController({
  authToken,
  activeModelPath,
  socketStatus,
  lastSocketError,
  reconnectAttempt,
  reconnectMaxAttempts,
  reconnectDelayMs,
  petRuntimeState,
  lastSemanticEvent,
  enableAudio,
  appendLog,
  handleSocketEvent: wrappedHandleSocketEvent,
  onClearStream: () => chatMessages.clearStream(),
})
const { requestClickThrough, startWindowDrag, setWindowMode, openMusicWindow, openSettingsWindow } = usePetWindowActions({
  activeModelPath,
  lastSocketError,
  appendLog,
})
// Auto-apply clickThrough when setting changes
watch(() => clientSettings.value.windowBehavior.clickThrough, (enabled) => {
  void requestClickThrough(enabled)
}, { immediate: true })
const { canSendChat, canConnectSocket, canDisconnectSocket } = usePetInteractionGuards({
  authToken,
  chatInput,
  socketStatus,
  isSocketReconnecting: isReconnecting,
  petRuntimeState,
})

const { statusLabel } = usePetShellDisplayState({ status })
const musicPresence = useSharedMusicPresence()
const { stateBadges } = usePetStatusBadges({
  debugPanelOpen,
  petRuntimeState,
  moodLabel,
  musicListeningLabel: musicPresence.listeningLabel,
  socketStatusLabel,
  socketStatusVisualState,
  status,
  statusLabel,
})
usePetShellLifecycle({
  loadModel,
  modelPath,
  disconnectSocket,
  disposeRenderer,
  appendLog,
})

// Connection quality based on reconnect attempts
const connectionQuality = computed<'good' | 'fair' | 'poor'>(() => {
  const attempts = reconnectAttempt.value
  if (attempts === 0) return 'good'
  if (attempts <= 2) return 'fair'
  return 'poor'
})

// Auth flow
const authFlow = useAuthFlow()

// Mood history
const moodHistory = useMoodHistory()

// Memory/diary
const petMemory = usePetMemory()

// Mailbox
const petMailbox = usePetMailbox()

// Pet interaction (tap/double-tap on Live2D model)
const petInteraction = usePetInteraction(rendererHandle)

// User profile
const userProfile = useUserProfile()

// Recommendations
const petRecommendations = useRecommendations()

// Toast notification
const { toastMessage, toastType, showToast } = usePetToast()

// Panel visibility state (extracted to usePetPanels composable)
const {
  showAuthModal, showChatHistory, showMoodHistory, showSettings,
  showMemory, showMusic, showMailbox, showProfile, showRecommend,
  handleRadialMenuClick: panelMenuClick,
  closeAllPanels,
  handleEscape: panelEscape,
} = usePetPanels({
  chatMessages, moodHistory, petMemory, petMailbox, userProfile, petRecommendations,
})

// Desktop notifications
const { notify } = usePetNotifications()

socketEventPipeline = usePetSocketEventPipeline({
  baseHandleSocketEvent: handleSocketEvent,
  chatMessages,
  audioPlayer,
  peekHandler,
  showToast,
  notify,
})

// Screenshot & GIF recording
const { captureStatus, takeScreenshot, startGifRecording, stopGifRecording } = usePetScreenshot({ canvasRef })

// Chat input ref for programmatic focus
const chatInputRef = useTemplateRef<InstanceType<typeof PetChatInput>>('chatInputRef')
const {
  realtimeAudioStream,
  canToggleRealtime,
  chatLayoutState,
  toggleRealtimeAudio,
  sendUiChat,
  sendPetInput,
} = usePetChatModule({
  authToken,
  socketStatus,
  petRuntimeState,
  chatMessages,
  sendChatMessage,
  showToast,
  startRealtimeSession: () => startPetRealtime(),
  sendAudioChunk: sendPetRealtimeAudio,
  stopRealtimeSession: stopPetRealtime,
})

// Chat can-send: reuse existing interaction guard
const canSendChatBubble = computed(() => {
  return authToken.value.trim().length > 0 && socketStatus.value === 'connected' && petRuntimeState.value === 'idle'
})

const { clearPreview, handleRadialMenuClick, handleLogout } = usePetShellActionHandlers({
  petRuntimeState,
  captureStatus,
  takeScreenshot,
  startGifRecording,
  stopGifRecording,
  panelMenuClick,
  showToast,
  disconnectSocket,
  clearAuthTokens,
  authFlow,
  chatMessages,
  closeAllPanels,
})

const shellUiGlue = usePetShellUiGlue({
  showAuthModal,
  showChatHistory,
  showMoodHistory,
  showSettings,
  showMemory,
  showMailbox,
  showProfile,
  authFlow,
  chatInput,
  sendChatMessage: sendUiChat,
  debugPanelOpen,
  toggleDebugPanel,
  debugPanelRef,
  authToken,
  connectSocket,
  disconnectSocket,
  clearPreview,
})

const {
  menuOpen,
  activePetMenuItem,
  closeTransientUi,
  handlePetCanvasTap,
  handlePetDragIntent,
  handlePetMenuItemClick,
} = usePetShellTransientUi({
  debugPanelOpen,
  showMusic,
  showChatHistory,
  showSettings,
  petInteraction,
  startWindowDrag,
  openMusicWindow,
  openSettingsWindow,
  openDebugPanel: async () => {
    await shellUiGlue.openDebugPanel()
  },
  focusChatInput: () => chatInputRef.value?.focusInput(),
  handleRadialMenuClick,
  onPetTap: handleLocalCompanionTap,
})

usePetShellHotkeys({
  debugPanelOpen,
  toggleDebugPanel,
  takeScreenshot,
  focusChatInput: () => chatInputRef.value?.focusInput(),
  openSettingsWindow,
  showChatHistory,
  showMoodHistory,
  showMailbox,
  panelEscape: () => {
    if (menuOpen.value) {
      closeTransientUi()
      return
    }
    // First, let the panels handle Esc (close open panels/drawers)
    const panelHandled = panelEscape()
    if (!panelHandled) {
      // No panel consumed Esc — interrupt the pet if it's actively responding
      const state = petRuntimeState.value
      if (state === 'thinking' || state === 'speaking' || state === 'settling') {
        petRuntimeState.value = 'idle'
      }
    }
  },
  showToast,
})

usePetShellBootstrap({
  userId,
  moodLabel,
  moodDescription,
  pleasure,
  arousal,
  dominance,
  showAuthModal,
  socketStatus,
  authFlow,
  chatMessages,
  moodHistory,
  petSettings,
  petMailbox,
  userProfile,
  petRecommendations,
  reconnectAttempt,
  reconnectDelayMs,
  connectSocket,
  showToast,
  notify,
})

watch(debugPanelOpen, (isOpen) => {
  void setWindowMode(isOpen ? 'debug' : 'pet')
  requestAnimationFrame(() => {
    refreshLayout()
  })
}, { immediate: true })
</script>

<template>
  <main
    class="renderer-shell"
    :class="{
      'renderer-shell--pet-first': !debugPanelOpen,
      'renderer-shell--motion-paused': shouldPauseAmbientMotion,
    }"
    :style="{ '--glass-blur': `${blurStrength}px` }"
  >
    <PetShellHeader
      v-if="debugPanelOpen"
      :debug-panel-open="debugPanelOpen"
      :state-badges="stateBadges"
      :reconnect-hint="reconnectHint"
      :is-authenticated="authFlow.isAuthenticated.value"
      :connection-quality="connectionQuality"
      :is-audio-playing="audioPlayer.isPlaying.value || musicPresence.presence.value.playing"
      :mail-unread-count="petMailbox.unreadCount.value"
      @prewarm-debug-panel="preloadDebugPanel"
      @toggle-debug-panel="toggleDebugPanel"
      @toggle-chat-history="shellUiGlue.toggleChatHistory()"
      @toggle-mood-history="shellUiGlue.toggleMoodHistory()"
      @toggle-settings="openSettingsWindow()"
      @toggle-memory="shellUiGlue.toggleMemory()"
      @toggle-mailbox="shellUiGlue.toggleMailbox()"
      @toggle-profile="shellUiGlue.toggleProfile()"
      @open-auth="shellUiGlue.openAuthModal()"
      @logout="handleLogout"
    />

    <section
      class="renderer-panel"
      :class="{ 'renderer-panel--pet-first': !debugPanelOpen }"
      :aria-label="debugPanelOpen ? 'Live2D debug workspace' : 'Live2D pet workspace'"
    >
      <div class="stage-wrapper">
        <Live2DStageCard
          :pet-first="!debugPanelOpen"
          :status="status"
          :status-label="statusLabel"
          :renderer-name="live2dRendererName"
          :model-path="activeModelPath"
          :runtime-state="petRuntimeState"
          :mood-label="moodLabel"
          :chat-layout-state="chatLayoutState"
          :reconnect-hint="reconnectHint"
          :load-error="lastLoadError"
          :bubble-content="companionBubbleContent"
          :show-cursor="chatDisplay.showCursor.value"
          :stream-preview-label="streamPreviewLabel"
          :particles="petInteraction.particles.value"
          @canvas-ready="setCanvasRef"
          @load-sample="loadModel(modelPath)"
          @test-failure="loadModel(invalidModelPath)"
          @check-click-through="requestClickThrough(true)"
          @canvas-tap="handlePetCanvasTap"
          @start-drag="handlePetDragIntent"
        />

        <button
          v-if="menuOpen"
          class="menu-dismiss-layer"
          type="button"
          aria-label="Close menu"
          @click="closeTransientUi"
        />

        <PetEmotionIndicator
          v-if="!debugPanelOpen && menuOpen && moodLabel"
          :mood-label="moodLabel"
          :mood-description="moodDescription"
          :pleasure="pleasure"
          :arousal="arousal"
          :dominance="dominance"
          :compact="true"
          :is-peeking="peekHandler.isPeeking.value"
          :peek-status="peekHandler.peekStatus.value"
          class="stage-emotion-indicator"
        />

        <div v-if="captureStatus === 'recording'" class="recording-indicator" aria-live="polite">
          <span class="recording-dot" />
          <span class="recording-label">REC</span>
        </div>

        <!-- Minimal floating chat input (pet-first mode only) -->
        <PetChatInput
          v-if="!debugPanelOpen"
          ref="chatInputRef"
          :can-send="canSendChatBubble"
          :is-sending="chatMessages.isSending.value"
          :send-error="chatMessages.sendError.value"
          :realtime-active="realtimeAudioStream.isStreaming.value"
          :can-toggle-realtime="canToggleRealtime"
          class="chat-input-overlay"
          :class="`chat-input-overlay--${chatLayoutState}`"
          @send="sendPetInput"
          @clear-error="chatMessages.clearSendError()"
          @toggle-realtime="toggleRealtimeAudio"
        />

        <PetRadialMenu
          :visible="!debugPanelOpen && menuOpen"
          :active-item="activePetMenuItem"
          @menu-item-click="handlePetMenuItemClick"
        />
      </div>

      <Transition name="debug-panel">
        <PetDebugPanel
          ref="debugPanelRef"
          v-if="debugPanelOpen"
          v-model:auth-token="authToken"
          v-model:chat-input="chatInput"
          :can-send="canSendChat"
          :can-connect="canConnectSocket"
          :can-disconnect="canDisconnectSocket"
          :socket-status="socketStatus"
          :socket-status-label="socketStatusLabel"
          :socket-status-visual-state="socketStatusVisualState"
          :last-socket-error="lastSocketError"
          :reconnect-hint="reconnectHint"
          :stream-preview-label="streamPreviewLabel"
          :pet-runtime-state="petRuntimeState"
          :mood-label="moodLabel"
          :last-semantic-event="lastSemanticEvent"
          :log-entries="logEntries"
          :recent-events="recentEvents"
          @connect-socket="shellUiGlue.connectSocket()"
          @disconnect-socket="shellUiGlue.disconnectSocket()"
          @send-chat="shellUiGlue.sendDebugChat()"
          @clear-preview="shellUiGlue.clearPreview()"
          @reload-model="loadModel(activeModelPath)"
        />
      </Transition>
    </section>

    <Transition name="modal-fade">
      <PetAuthModal
        v-if="showAuthModal"
        :email="authFlow.email.value"
        :code="authFlow.code.value"
        :step="authFlow.step.value"
        :is-sending-code="authFlow.isSendingCode.value"
        :is-logging-in="authFlow.isLoggingIn.value"
        :auth-error="authFlow.authError.value"
        :countdown="authFlow.countdown.value"
        @update:email="shellUiGlue.updateAuthEmail($event)"
        @update:code="shellUiGlue.updateAuthCode($event)"
        @send-code="shellUiGlue.sendCode()"
        @login="shellUiGlue.login()"
        @go-back="shellUiGlue.resetAuth()"
        @clear-error="shellUiGlue.clearAuthError()"
        @close="shellUiGlue.closeAuthModal()"
      />
    </Transition>

    <PetChatHistoryPanel
      :messages="chatMessages.messages.value"
      :is-loading="chatMessages.isLoadingHistory.value"
      :has-more="chatMessages.hasMoreHistory.value"
      :is-open="showChatHistory"
      @close="showChatHistory = false"
      @load-more="chatMessages.loadMore()"
    />

    <PetMoodHistoryPanel
      :entries="moodHistory.historyEntries.value"
      :is-loading="moodHistory.isLoadingHistory.value"
      :is-open="showMoodHistory"
      :history-error="moodHistory.historyError.value"
      @close="showMoodHistory = false"
      @retry="moodHistory.loadHistory()"
    />

    <PetMemoryPanel
      :entries="petMemory.memoryEntries.value"
      :is-loading="petMemory.isLoadingMemory.value"
      :is-loading-more="petMemory.isLoadingMore.value"
      :has-more="petMemory.hasMoreMemory.value"
      :is-open="showMemory"
      :search-results="petMemory.searchResults.value"
      :is-searching="petMemory.isSearching.value"
      :search-query="petMemory.searchQuery.value"
      :active-filter="petMemory.activeFilter.value"
      :gallery-items="memoryGallery.galleryItems.value"
      :unlocked-gallery-count="memoryGallery.unlockedCount.value"
      :total-gallery-count="memoryGallery.totalCount.value"
      @close="showMemory = false"
      @load-more="petMemory.loadMoreMemory()"
      @search="petMemory.searchMemories($event)"
      @clear-search="petMemory.clearSearch()"
      @filter-change="petMemory.loadMemory($event)"
      @gallery-select="memoryGallery.loadDetail($event)"
    />

    <PetMailboxPanel
      :mails="petMailbox.mails.value"
      :is-loading="petMailbox.isLoading.value"
      :is-open="showMailbox"
      :unread-count="petMailbox.unreadCount.value"
      @close="showMailbox = false"
      @read="async (mailId) => { await petMailbox.markAsRead(mailId) }"
      @read-all="async () => { await petMailbox.markAllAsRead() }"
    />

    <!-- Settings dialog moved to separate Tauri window -->
    <!--
    <Transition name="modal-fade">
      <PetSettingsDialog
        v-if="showSettings"
        :settings="petSettings.currentSettings.value"
        :is-saving="petSettings.isSaving.value"
        :settings-error="petSettings.settingsError.value"
        :presets="petSettings.presets.value"
        @save="petSettings.saveSettings"
        @close="showSettings = false"
      />
    </Transition>
    -->

    <Transition name="toast-fade">
      <div v-if="toastMessage" class="app-toast" :class="'app-toast--' + toastType" role="status" aria-atomic="true">{{ toastMessage }}</div>
    </Transition>

    <PetProfileCard
      :profile="userProfile.profile.value"
      :is-loading="userProfile.isLoading.value"
      :is-open="showProfile"
      @close="showProfile = false"
    />

    <PetRecommendPanel
      :items="petRecommendations.recommendations.value"
      :is-loading="petRecommendations.isLoading.value"
      :is-open="showRecommend"
      @close="showRecommend = false"
      @click="petRecommendations.markClicked($event)"
    />
  </main>
</template>

<style scoped>
.renderer-shell {
  min-height: 100svh;
  padding: var(--space-7);
  color: var(--color-text);
  background:
    radial-gradient(circle at 12% 18%, var(--color-glow-warm), transparent 32%),
    radial-gradient(circle at 86% 14%, var(--color-glow-cool), transparent 34%),
    var(--color-bg);
  box-sizing: border-box;
}

.renderer-shell--pet-first {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4) var(--space-4) var(--space-6);
  background: transparent;
  overflow: hidden;
}

.renderer-panel {
  display: grid;
  grid-template-columns: minmax(var(--size-canvas-min), 1.18fr) minmax(0, 0.82fr);
  gap: var(--space-6);
  max-width: var(--size-page-max);
  margin: 0 auto;
  align-items: stretch;
}

.renderer-panel--pet-first {
  grid-template-columns: minmax(0, 1fr);
  width: 100%;
  max-width: none;
  margin: 0;
}

.stage-wrapper {
  position: relative;
}

.renderer-panel--pet-first .stage-wrapper {
  width: min(30rem, 100%);
  margin: 0 auto;
  padding-bottom: 5.25rem;
  --size-canvas-height: min(74svh, 48rem);
}

/* ── Chat input (floating at bottom of stage) ── */

.chat-input-overlay {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: min(26rem, calc(100% - 1.25rem));
  transform: translateX(-50%);
  z-index: 6;
}

.chat-input-overlay--connected {
  width: min(26rem, calc(100% - 1.25rem));
}

.chat-input-overlay--disconnected {
  width: min(22rem, calc(100% - 1.5rem));
  bottom: 0.35rem;
}

.menu-dismiss-layer {
  position: absolute;
  inset: 0;
  z-index: 7;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: default;
}

.renderer-panel--pet-first .stage-wrapper :deep(.stage-card) {
  max-width: 28rem;
  margin: 0 auto;
}

.stage-emotion-indicator {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  z-index: 10;
}

/* Recording indicator */
.recording-indicator {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border-radius: var(--radius-md);
  pointer-events: none;
}

.renderer-shell--motion-paused .recording-dot {
  animation: none;
}

.recording-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: recording-pulse 1s ease-in-out infinite;
}

.recording-label {
  font-size: 0.7rem;
  font-weight: 600;
  color: #ef4444;
  letter-spacing: 0.05em;
}

@keyframes recording-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

@media (prefers-reduced-motion: reduce) {
  .recording-dot {
    animation: none;
  }
}

.renderer-panel--pet-first :deep(.stage-card) {
  max-width: 28rem;
  margin: 0 auto;
  border: 0;
  box-shadow: none;
}

.debug-panel-enter-active,
.debug-panel-leave-active {
  transition:
    opacity var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}

.debug-panel-enter-from,
.debug-panel-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition:
    opacity 200ms ease,
    transform 200ms ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

@media (prefers-reduced-motion: reduce) {
  .debug-panel-enter-active,
  .debug-panel-leave-active {
    transition: none;
  }

  .debug-panel-enter-from,
  .debug-panel-leave-to {
    opacity: 1;
    transform: none;
  }

  .modal-fade-enter-active,
  .modal-fade-leave-active {
    transition: none;
  }

  .modal-fade-enter-from,
  .modal-fade-leave-to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 900px) {
  .renderer-shell {
    padding: var(--space-4);
  }

  .renderer-shell--pet-first {
    padding: var(--space-3) var(--space-3) var(--space-4);
  }

  .renderer-panel {
    grid-template-columns: 1fr;
  }
}

/* Toast notification */
.app-toast {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  z-index: 2000;
  background: var(--color-surface);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  color: var(--color-text);
  font-size: var(--font-size-small);
  line-height: var(--line-height-body);
  pointer-events: none;
  box-shadow: 0 0.5rem 1.5rem rgba(0, 0, 0, 0.34);
}

.app-toast--success {
  border-color: var(--color-success);
}

.app-toast--error {
  border-color: var(--color-danger);
}

.app-toast--info {
  border-color: var(--color-accent);
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition:
    opacity var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (prefers-reduced-motion: reduce) {
  .toast-fade-enter-active,
  .toast-fade-leave-active {
    transition: none;
  }

  .toast-fade-enter-from,
  .toast-fade-leave-to {
    opacity: 1;
    transform: none;
  }
}
</style>
