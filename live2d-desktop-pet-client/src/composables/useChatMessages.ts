import { useChatMessageState, groupMessagesByDate } from './useChatMessageState'
import { useChatMessageTransport } from './useChatMessageTransport'

export { groupMessagesByDate }
export type { MessageGroup } from './useChatMessageState'
export type { ChatSendPayload } from './useChatMessageTransport'

export function useChatMessages() {
  const state = useChatMessageState()
  const transport = useChatMessageTransport({
    clearStream: state.clearStream,
    addOptimisticUserMessage: state.addOptimisticUserMessage,
    updateMessageImageUrl: state.updateMessageImageUrl,
    updateMessageDeliveryState: state.updateMessageDeliveryState,
    removeMessageById: state.removeMessageById,
    notifyUserMessageCommitted: state.notifyUserMessageCommitted,
  })

  return {
    messages: state.messages,
    streamingMessage: state.streamingMessage,
    isStreaming: state.isStreaming,
    isSending: transport.isSending,
    sendError: transport.sendError,
    clearSendError: transport.clearSendError,
    isLoadingHistory: state.isLoadingHistory,
    hasMoreHistory: state.hasMoreHistory,
    unreadCount: state.unreadCount,
    appendStreamContent: state.appendStreamContent,
    markStreamComplete: state.markStreamComplete,
    sendMessage: transport.sendMessage,
    sendVoiceMessage: transport.sendVoiceMessage,
    loadHistory: state.loadHistory,
    loadMore: state.loadMore,
    clearMessages: state.clearMessages,
    clearStream: state.clearStream,
    resetUnreadCount: state.resetUnreadCount,
    groupMessagesByDate,
    setStreamCallbacks: state.setStreamCallbacks,
    setMessageEventHooks: state.setMessageEventHooks,
    setSendChatFunction: transport.setSendChatFunction,
    setSendVoiceFunction: transport.setSendVoiceFunction,
  }
}
