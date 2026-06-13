<script setup lang="ts">
import { computed, ref } from 'vue'
import { marked } from 'marked'
import type { ChatMessage } from '@/stores/chat'

marked.setOptions({ breaks: true, gfm: true })

const props = defineProps<{
  message: ChatMessage
}>()

const isUser = computed(() => props.message.role === 'user')
const thinkExpanded = ref(true)

const parsedContent = computed(() => {
  const text = props.message.content

  // 分离 thinking
  const thinkRegex = /\[THINK\]([\s\S]*?)\[\/THINK\]/g
  const thinkParts: string[] = []
  let cleanText = text.replace(thinkRegex, (_, part) => {
    thinkParts.push(part)
    return ''
  })
  const thinking = thinkParts.join('')

  // 分离工具调用
  const toolRegex = /\[TOOL_START\]([\s\S]*?)\[TOOL_END\]/g
  const toolParts: string[] = []
  cleanText = cleanText.replace(toolRegex, (_, part) => {
    toolParts.push(part.trim())
    return ''
  })
  const toolCalls = toolParts.join('\n')

  // 基础规范化
  cleanText = cleanText.replace(/\n{3,}/g, '\n\n').trim()

  // marked 渲染
  let html = ''
  if (cleanText) {
    try {
      const result = marked.parse(cleanText) as string
      html = result || ''
    } catch (e) {
      console.error('Markdown error:', e)
      html = cleanText.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/\n/g, '<br>')
    }
  }

  // 工具调用 Markdown 渲染
  let toolHtml = ''
  if (toolCalls) {
    try {
      toolHtml = marked.parse(toolCalls) as string || ''
    } catch { /* ignore */ }
  }

  return { thinking, toolCalls, toolHtml, html }
})

function toggleThink() {
  thinkExpanded.value = !thinkExpanded.value
}
</script>

<template>
  <div class="message-bubble" :class="{ user: isUser, assistant: !isUser }">
    <div v-if="!isUser" class="avatar">
      <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
        <circle cx="14" cy="14" r="13" stroke="#22C55E" stroke-width="1.5" />
        <circle cx="14" cy="14" r="5" fill="#22C55E" opacity="0.4" />
      </svg>
    </div>

    <div class="bubble-body">
      <div v-if="!isUser" class="sender-name">Mirror</div>

      <!-- 思考过程 -->
      <div v-if="parsedContent.thinking" class="think-block" :class="{ collapsed: !thinkExpanded }">
        <div class="think-header" @click="toggleThink">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5" />
          </svg>
          <span>思考过程</span>
          <svg class="chevron" :class="{ rotated: !thinkExpanded }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
        <div v-show="thinkExpanded" class="think-body">
          <span class="think-text">{{ parsedContent.thinking }}</span>
        </div>
      </div>

      <!-- 工具调用 -->
      <div v-if="parsedContent.toolCalls" class="tool-block">
        <div class="tool-header-bar">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
          <span>工具调用</span>
        </div>
        <div class="tool-body" v-html="parsedContent.toolHtml" />
      </div>

      <!-- 正文 -->
      <div
        v-if="parsedContent.html"
        class="bubble-content"
        :class="{ streaming: message.isStreaming }"
        v-html="parsedContent.html + (message.isStreaming ? '<span class=\'stream-cursor\'>▊</span>' : '')"
      />
      <div v-else-if="message.isStreaming" class="bubble-content streaming">
        <span class="stream-cursor">▊</span>
      </div>

      <div v-if="!message.isStreaming && parsedContent.html" class="bubble-time">
        {{ new Date(message.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}
      </div>
      <div v-if="!message.isStreaming && !isUser && parsedContent.html" class="bubble-actions">
        <button class="action-btn" title="复制">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
        </button>
        <button class="action-btn" title="重新生成">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="23 4 23 10 17 10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
        </button>
      </div>
    </div>

    <div v-if="isUser" class="avatar user-avatar">
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" color="#94A3B8">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.message-bubble { display: flex; gap: var(--space-3); max-width: 85%; }
.message-bubble.user { align-self: flex-end; flex-direction: row-reverse; }
.message-bubble.assistant { align-self: flex-start; }

.avatar { flex-shrink: 0; width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; margin-top: 2px; }

.bubble-body { display: flex; flex-direction: column; gap: var(--space-1); flex: 1; min-width: 0; }
.user .bubble-body { align-items: flex-end; }

.sender-name { font-size: var(--text-xs); color: var(--accent); font-weight: 500; }

/* 思考过程 */
.think-block { margin-top: var(--space-1); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); overflow: hidden; background: rgba(255,255,255,0.02); }
.think-header { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); cursor: pointer; font-size: var(--text-xs); color: var(--text-muted); user-select: none; }
.think-header:hover { background: rgba(255,255,255,0.03); }
.chevron { margin-left: auto; transition: transform var(--transition-fast); }
.chevron.rotated { transform: rotate(-90deg); }
.think-body { padding: var(--space-2) var(--space-3); border-top: 1px solid var(--border-subtle); max-height: 200px; overflow-y: auto; }
.think-text { font-size: var(--text-xs); color: var(--text-subtle); line-height: 1.5; white-space: pre-wrap; word-break: break-word; }

/* 工具调用 */
.tool-block { margin-top: var(--space-2); border: 1px solid rgba(245,158,11,0.3); border-radius: var(--radius-md); overflow: hidden; background: rgba(245,158,11,0.05); }
.tool-header-bar { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); font-size: var(--text-xs); color: var(--accent-warn); font-weight: 500; }
.tool-body { padding: var(--space-2) var(--space-3); border-top: 1px solid rgba(245,158,11,0.15); font-size: var(--text-xs); color: var(--text-muted); line-height: 1.5; }
.tool-body :deep(p) { margin: 2px 0; }
.tool-body :deep(strong) { color: var(--text-primary); }

/* 气泡 */
.bubble-content { font-size: var(--text-base); line-height: 1.65; color: var(--text-primary); padding: var(--space-3) var(--space-4); border-radius: var(--radius-lg); word-break: break-word; }
.user .bubble-content { background: var(--user-msg-bg); border-right: 3px solid var(--user-msg-border); border-radius: var(--radius-lg) 4px var(--radius-lg) var(--radius-lg); }
.assistant .bubble-content { background: transparent; border-left: 3px solid var(--ai-msg-border); border-radius: 4px var(--radius-lg) var(--radius-lg) var(--radius-lg); padding: 0 var(--space-4); }

/* marked 渲染元素 */
.bubble-content :deep(p) { margin: var(--space-2) 0; }
.bubble-content :deep(p:first-child) { margin-top: 0; }
.bubble-content :deep(strong) { font-weight: 700; color: var(--text-primary); }
.bubble-content :deep(em) { font-style: italic; }
.bubble-content :deep(code) { font-family: var(--font-mono); font-size: var(--text-sm); background: var(--code-bg); padding: 1px 6px; border-radius: var(--radius-sm); color: var(--accent); }
.bubble-content :deep(pre) { background: var(--code-bg); border: 1px solid var(--code-border); border-radius: var(--radius-md); padding: var(--space-3) var(--space-4); margin: var(--space-2) 0; overflow-x: auto; }
.bubble-content :deep(pre code) { background: transparent; padding: 0; color: var(--text-primary); }
.bubble-content :deep(ul), .bubble-content :deep(ol) { margin: var(--space-2) 0; padding-left: var(--space-5); }
.bubble-content :deep(li) { margin: var(--space-1) 0; line-height: 1.6; }
.bubble-content :deep(h1), .bubble-content :deep(h2), .bubble-content :deep(h3) { font-weight: 600; color: var(--text-primary); margin: var(--space-4) 0 var(--space-2); }
.bubble-content :deep(h1) { font-size: var(--text-xl); }
.bubble-content :deep(h2) { font-size: var(--text-lg); }
.bubble-content :deep(h3) { font-size: var(--text-base); }
.bubble-content :deep(blockquote) { border-left: 3px solid var(--accent); margin: var(--space-2) 0; padding: var(--space-1) var(--space-3); color: var(--text-muted); }
.bubble-content :deep(table) { border-collapse: collapse; margin: var(--space-2) 0; width: 100%; }
.bubble-content :deep(th), .bubble-content :deep(td) { border: 1px solid var(--border-default); padding: var(--space-2) var(--space-3); text-align: left; font-size: var(--text-sm); }
.bubble-content :deep(th) { background: var(--bg-raised); font-weight: 600; }
.bubble-content :deep(a) { color: var(--accent-link); }
.bubble-content :deep(hr) { border: none; border-top: 1px solid var(--border-subtle); margin: var(--space-3) 0; }

.stream-cursor { display: inline-block; animation: blink 1s step-end infinite; color: var(--accent); font-weight: bold; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.bubble-time { font-size: 11px; color: var(--text-subtle); margin-top: 2px; }
.user .bubble-time { text-align: right; }
.bubble-actions { display: flex; gap: var(--space-1); margin-top: var(--space-1); opacity: 0; transition: opacity var(--transition-fast); }
.message-bubble:hover .bubble-actions { opacity: 1; }
.action-btn { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: none; border-radius: var(--radius-sm); background: transparent; color: var(--text-subtle); cursor: pointer; transition: all var(--transition-fast); }
.action-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
</style>
