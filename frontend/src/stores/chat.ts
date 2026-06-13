import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  isStreaming?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref(generateSessionId())
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const currentStreamingId = ref<string | null>(null)

  function generateSessionId(): string {
    return 'sess_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8)
  }

  function newSession() {
    sessionId.value = generateSessionId()
    messages.value = []
    currentStreamingId.value = null
    isLoading.value = false
  }

  function addMessage(msg: Omit<ChatMessage, 'id' | 'timestamp'>) {
    const message: ChatMessage = {
      ...msg,
      id: `msg_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      timestamp: Date.now(),
    }
    messages.value.push(message)
    return message
  }

  function updateMessage(id: string, content: string) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) {
      msg.content = content
    }
  }

  function setStreaming(id: string, streaming: boolean) {
    const msg = messages.value.find((m) => m.id === id)
    if (msg) {
      msg.isStreaming = streaming
      currentStreamingId.value = streaming ? id : null
    }
  }

  function clearMessages() {
    messages.value = []
    currentStreamingId.value = null
    isLoading.value = false
  }

  function removeMessage(id: string) {
    messages.value = messages.value.filter((m) => m.id !== id)
  }

  return {
    messages,
    isLoading,
    currentStreamingId,
    addMessage,
    updateMessage,
    setStreaming,
    sessionId,
    newSession,
    clearMessages,
    removeMessage,
  }
})
