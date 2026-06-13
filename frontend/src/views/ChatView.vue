<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { streamChat } from '@/api/chat'

const chatStore = useChatStore()
const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)

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
    <MessageList
      ref="messageListRef"
      :messages="chatStore.messages"
    />
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
</style>
