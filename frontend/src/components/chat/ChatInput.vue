<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  isLoading: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  stop: []
}>()

const inputText = ref('')
const showCommands = ref(false)

const canSend = computed(() => inputText.value.trim().length > 0 && !props.isLoading)

function handleSubmit() {
  if (!canSend.value) return
  emit('send', inputText.value.trim())
  inputText.value = ''
  showCommands.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  } else if (e.key === 'Enter' && (e.ctrlKey || e.shiftKey)) {
    // Ctrl+Enter 换行
    inputText.value += '\n'
  }
}

function handleInput(e: Event) {
  const target = e.target as HTMLTextAreaElement
  const val = target.value
  inputText.value = val
  // 检测 / 触发命令面板
  if (val.endsWith('/') && val.length === 1) {
    showCommands.value = true
  }
}

function insertCommand(cmd: string) {
  inputText.value = cmd + ' '
  showCommands.value = false
}

const commands = [
  { cmd: '/file', desc: '引用文件' },
  { cmd: '/search', desc: '搜索知识库' },
  { cmd: '/skill', desc: '执行 Skill' },
  { cmd: '/clear', desc: '清空对话' },
]
</script>

<template>
  <div class="chat-input-area">
    <!-- 命令面板 -->
    <Transition name="slide-up">
      <div v-if="showCommands" class="command-panel">
        <div v-for="cmd in commands" :key="cmd.cmd" class="command-item" @click="insertCommand(cmd.cmd)">
          <span class="cmd-name">{{ cmd.cmd }}</span>
          <span class="cmd-desc">{{ cmd.desc }}</span>
        </div>
      </div>
    </Transition>

    <!-- 输入区域 -->
    <div class="input-wrapper">
      <div class="input-actions-left">
        <button class="icon-btn" title="上传文件">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
          </svg>
        </button>
        <button class="icon-btn" title="命令面板" @click="showCommands = !showCommands">
          <span class="slash-icon">/</span>
        </button>
      </div>

      <textarea
        v-model="inputText"
        class="input-field"
        placeholder="输入消息... (Enter 发送, Ctrl+Enter 换行, / 命令面板)"
        rows="1"
        @keydown="handleKeydown"
        @input="handleInput"
        @focus="showCommands = false"
      />

      <div class="input-actions-right">
        <button
          v-if="!isLoading"
          class="send-btn"
          :class="{ active: canSend }"
          :disabled="!canSend"
          @click="handleSubmit"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>
        </button>
        <button v-else class="stop-btn" @click="emit('stop')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
            <rect x="4" y="4" width="16" height="16" rx="2" />
          </svg>
        </button>
      </div>
    </div>

    <div class="input-hints">
      <span>Ctrl+Enter 换行</span>
      <span>/ 命令面板</span>
    </div>
  </div>
</template>

<style scoped>
.chat-input-area {
  position: relative;
  padding: var(--space-3) var(--space-6) var(--space-4);
  background: var(--bg-base);
  border-top: 1px solid var(--border-subtle);
}

.command-panel {
  position: absolute;
  bottom: 100%;
  left: var(--space-6);
  right: var(--space-6);
  background: var(--bg-raised);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-2);
  margin-bottom: var(--space-2);
  box-shadow: var(--shadow-lg);
  z-index: 50;
}

.command-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}
.command-item:hover {
  background: var(--bg-hover);
}

.cmd-name {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--accent);
  font-weight: 500;
}

.cmd-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  background: var(--bg-raised);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  padding: var(--space-2);
  transition: border-color var(--transition-fast);
}

.input-wrapper:focus-within {
  border-color: var(--accent);
  box-shadow: var(--shadow-glow);
}

.input-actions-left,
.input-actions-right {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.icon-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.slash-icon {
  font-family: var(--font-mono);
  font-size: var(--text-lg);
  font-weight: 600;
}

.input-field {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--text-base);
  font-family: var(--font-sans);
  line-height: 1.5;
  resize: none;
  outline: none;
  max-height: 200px;
  min-height: 24px;
  padding: var(--space-1) 0;
}
.input-field::placeholder {
  color: var(--text-subtle);
}

.send-btn,
.stop-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.send-btn {
  background: transparent;
  color: var(--text-subtle);
}
.send-btn.active {
  background: var(--accent);
  color: #fff;
}
.send-btn.active:hover {
  background: var(--accent-hover);
}
.send-btn:disabled {
  cursor: not-allowed;
}

.stop-btn {
  background: var(--accent-error);
  color: #fff;
}
.stop-btn:hover {
  opacity: 0.9;
}

.input-hints {
  display: flex;
  justify-content: space-between;
  margin-top: var(--space-2);
  padding: 0 var(--space-1);
  font-size: 11px;
  color: var(--text-subtle);
}
</style>
