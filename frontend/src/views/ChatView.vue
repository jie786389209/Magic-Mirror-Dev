<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { streamChat } from '@/api/chat'

const chatStore = useChatStore()
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)
const ragEnabled = ref(false)

let abortController: AbortController | null = null

async function handleSend(content: string) {
  // 添加用户消息
  chatStore.addMessage({ role: 'user', content })

  // 添加 AI 占位消息
  const aiMsg = chatStore.addMessage({ role: 'assistant', content: '' })
  chatStore.setStreaming(aiMsg.id, true)
  chatStore.isLoading = true

  // 构建历史消息（不含当前 AI 占位）
  const history: { role: string; content: string }[] = []
  const msgs = [...chatStore.messages]
  msgs.slice(0, -1).forEach((m) => {
    history.push({ role: m.role, content: m.content })
  })

  // 创建 AbortController 用于停止
  abortController = new AbortController()

  await streamChat(
    content,
    history,
    ragEnabled.value,
    chatStore.sessionId,
    // onChunk
    (chunk) => {
      chatStore.updateMessage(aiMsg.id, chatStore.messages.find((m) => m.id === aiMsg.id)!.content + chunk)
    },
    // onDone
    () => {
      chatStore.setStreaming(aiMsg.id, false)
      chatStore.isLoading = false
      abortController = null
    },
    // onError
    (err) => {
      chatStore.updateMessage(aiMsg.id, `[错误] ${err}`)
      chatStore.setStreaming(aiMsg.id, false)
      chatStore.isLoading = false
      abortController = null
    },
    abortController.signal
  )
}

function handleStop() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  if (chatStore.currentStreamingId) {
    chatStore.setStreaming(chatStore.currentStreamingId, false)
    chatStore.isLoading = false
  }
}

</script>

<template>
  <div class="chat-view">
    <div class="chat-top-bar">
      <span class="session-label">{{ chatStore.sessionId.slice(0, 12) }}...</span>
      <button class="new-chat-btn" @click="chatStore.newSession()">＋ 新建对话</button>
    </div>
    <MessageList
      ref="messageListRef"
      :messages="chatStore.messages"
    />
    <div class="rag-bar">
      <button class="rag-toggle" :class="{ active: ragEnabled }" @click="ragEnabled = !ragEnabled" :title="ragEnabled ? '已开启知识库检索' : '点击开启知识库检索'">
        📚 知识库 {{ ragEnabled ? 'ON' : 'OFF' }}
      </button>
    </div>
    <ChatInput
      :is-loading="chatStore.isLoading || !!chatStore.currentStreamingId"
      @send="handleSend"
      @stop="handleStop"
    />
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.chat-top-bar { display: flex; justify-content: space-between; align-items: center; padding: var(--space-2) var(--space-6); border-bottom: 1px solid var(--border-subtle); }
.session-label { font-size: 11px; color: var(--text-subtle); font-family: var(--font-mono); }
.new-chat-btn { font-size: var(--text-xs); padding: 2px 10px; border: 1px solid var(--border-default); border-radius: var(--radius-sm); background: transparent; color: var(--text-muted); cursor: pointer; }
.new-chat-btn:hover { border-color: var(--accent); color: var(--accent); }
.rag-bar { display: flex; justify-content: center; padding: 0 var(--space-6) var(--space-1); }
.rag-toggle { font-size: var(--text-xs); padding: 2px 12px; border-radius: 12px; border: 1px solid var(--border-default); background: transparent; color: var(--text-subtle); cursor: pointer; transition: all var(--transition-fast); }
.rag-toggle:hover { border-color: var(--accent); color: var(--text-muted); }
.rag-toggle.active { background: var(--accent-glow); border-color: var(--accent); color: var(--accent); }
</style>
