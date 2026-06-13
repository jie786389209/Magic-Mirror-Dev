<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { ChatMessage } from '@/stores/chat'
import MessageBubble from './MessageBubble.vue'

const props = defineProps<{
  messages: ChatMessage[]
}>()

const listRef = ref<HTMLDivElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

watch(
  () => props.messages.length,
  () => scrollToBottom()
)

watch(
  () => props.messages[props.messages.length - 1]?.content,
  () => scrollToBottom()
)

defineExpose({ scrollToBottom })
</script>

<template>
  <div ref="listRef" class="message-list">
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-logo">
        <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
          <circle cx="32" cy="32" r="30" stroke="#22C55E" stroke-width="2" />
          <circle cx="32" cy="32" r="8" fill="#22C55E" opacity="0.3" />
          <path d="M32 8 L32 24 M32 40 L32 56 M8 32 L24 32 M40 32 L56 32"
            stroke="#22C55E" stroke-width="2" stroke-linecap="round" opacity="0.5" />
        </svg>
      </div>
      <h2>Magic Mirror Dev</h2>
      <p>你的 AI 开发助手，随时待命。输入问题开始对话。</p>
    </div>

    <TransitionGroup name="message" tag="div" class="messages-container">
      <MessageBubble
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
      />
    </TransitionGroup>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6) var(--space-8);
}

.messages-container {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: var(--text-muted);
  gap: var(--space-3);
}

.empty-logo {
  margin-bottom: var(--space-4);
}

.empty-state h2 {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
}

.empty-state p {
  font-size: var(--text-base);
  max-width: 360px;
}
</style>
