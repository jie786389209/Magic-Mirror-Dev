<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { marked } from 'marked'

interface ChatMsg {
  role: string
  content: string
  toolName?: string
  toolCallId?: string
  toolCalls?: Array<{ function?: { name?: string } }>
  timestamp: string
}

interface Memory {
  content: string
  similarity?: number
}

const activeTab = ref<'short' | 'long'>('short')
const shortMem = ref<ChatMsg[]>([])
const searchQuery = ref('')
const longResults = ref<Memory[]>([])
const longCount = ref(0)
const loading = ref(false)
const expandedTurns = ref<Set<number>>(new Set())

onMounted(() => { loadShort(); loadLongCount() })

// 按对话轮次分组：每个 user 消息及其后续的 assistant/tool 消息为一组
interface Turn {
  user: ChatMsg
  details: ChatMsg[]
}

const turns = computed(() => {
  const result: Turn[] = []
  // shortMem 是倒序的（最新在前），需要反转处理
  const msgs = [...shortMem.value].reverse()
  let i = 0
  while (i < msgs.length) {
    if (msgs[i].role === 'user') {
      const turn: Turn = { user: msgs[i], details: [] }
      i++
      while (i < msgs.length && msgs[i].role !== 'user') {
        turn.details.push(msgs[i])
        i++
      }
      result.push(turn)
    } else {
      i++
    }
  }
  return result.reverse() // 最新对话在前
})

function toggleTurn(idx: number) {
  const s = new Set(expandedTurns.value)
  if (s.has(idx)) s.delete(idx); else s.add(idx)
  expandedTurns.value = s
}

async function loadShort() {
  try {
    const res = await fetch('/api/memory/short/default')
    shortMem.value = await res.json()
  } catch { /* noop */ }
}

async function searchLong() {
  if (!searchQuery.value.trim()) return
  loading.value = true
  try {
    const res = await fetch('/api/memory/long/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: searchQuery.value })
    })
    longResults.value = await res.json()
  } catch { /* noop */ }
  loading.value = false
}

async function clearShort() {
  const res = await fetch('/api/memory/short/default', { method: 'DELETE' })
  if (res.ok) shortMem.value = []
}

async function loadLongCount() {
  try {
    const res = await fetch('/api/memory/long/count')
    const data = await res.json()
    longCount.value = data.count || 0
  } catch { /* noop */ }
}

async function clearLong() {
  const res = await fetch('/api/memory/long', { method: 'DELETE' })
  if (res.ok) { longResults.value = []; longCount.value = 0 }
}

function roleLabel(msg: ChatMsg): string {
  if (msg.role === 'tool') return '🔧 ' + (msg.toolName || '工具')
  return msg.role === 'user' ? '👤' : '🤖'
}

function renderMd(text: string): string {
  if (!text) return ''
  return marked.parse(text, { breaks: true }) as string
}

function detailCount(details: ChatMsg[]): string {
  const tools = details.filter(d => d.role === 'tool').length
  const replies = details.filter(d => d.role === 'assistant').length
  const parts: string[] = []
  if (tools) parts.push(`${tools} 次工具调用`)
  if (replies) parts.push(`${replies} 条回复`)
  return parts.join(' · ')
}
</script>

<template>
  <div class="memory-view">
    <h1>记忆管理</h1>

    <!-- Tab 切换 -->
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'short' }" @click="activeTab = 'short'">
        短期记忆 · 对话转录
      </button>
      <button class="tab" :class="{ active: activeTab === 'long' }" @click="activeTab = 'long'">
        长期记忆 · Chroma
      </button>
    </div>

    <!-- 短期记忆 -->
    <div v-show="activeTab === 'short'">
      <div class="tab-head">
        <span class="count">{{ turns.length }} 轮对话 · Redis · 24h 过期</span>
        <button class="clear-btn" @click="clearShort">清空</button>
      </div>

      <div v-if="turns.length === 0" class="empty">暂无对话记录</div>

      <div v-for="(turn, ti) in turns" :key="turn.user.timestamp + ti" class="turn-card">
        <!-- 用户问题（始终显示） -->
        <div class="turn-user" @click="toggleTurn(ti)">
          <span class="turn-arrow">{{ expandedTurns.has(ti) ? '▼' : '▶' }}</span>
          <span class="turn-label">👤</span>
          <span class="turn-text">{{ turn.user.content?.slice(0, 80) }}{{ (turn.user.content?.length || 0) > 80 ? '...' : '' }}</span>
          <span class="turn-time">{{ turn.user.timestamp?.slice(11, 19) }}</span>
          <span v-if="turn.details.length" class="turn-badge">{{ detailCount(turn.details) }}</span>
        </div>

        <!-- 展开详情 -->
        <div v-if="expandedTurns.has(ti)" class="turn-details">
          <div v-for="(msg, mi) in turn.details" :key="mi" class="msg-item" :class="msg.role">
            <div class="msg-head">
              <span class="msg-role">{{ roleLabel(msg) }}</span>
            </div>
            <div class="msg-content" v-if="msg.content" v-html="renderMd(msg.content)" />
            <div v-if="msg.toolCalls?.length" class="msg-tools">
              <span v-for="(tc, tci) in msg.toolCalls" :key="tci" class="tc-tag">🔧 {{ tc.function?.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 长期记忆 -->
    <div v-show="activeTab === 'long'">
      <div class="tab-head">
        <span class="count">{{ longCount }} 条长期记忆 · 语义向量检索</span>
        <button class="clear-btn" @click="clearLong">清空</button>
      </div>
      <div class="search-row">
        <input v-model="searchQuery" class="search-input" placeholder="搜索记忆..." @keyup.enter="searchLong" />
        <button class="search-btn" :disabled="loading" @click="searchLong">搜索</button>
      </div>

      <div v-if="longResults.length" class="long-list">
        <div v-for="m in longResults" :key="m.content" class="mem-item">
          <span class="mem-content">{{ m.content }}</span>
          <span class="mem-score">{{ ((m.similarity || 0) * 100).toFixed(0) }}%</span>
        </div>
      </div>
      <div v-else class="empty">输入关键词搜索长期记忆</div>
    </div>
  </div>
</template>

<style scoped>
.memory-view { padding: var(--space-8); max-width: 760px; margin: 0 auto; height: 100%; overflow-y: auto; }
.memory-view h1 { font-size: var(--text-2xl); font-weight: 700; margin-bottom: var(--space-5); }

/* Tabs */
.tabs { display: flex; gap: 0; border-bottom: 2px solid var(--border-subtle); margin-bottom: var(--space-4); }
.tab { padding: var(--space-2) var(--space-5); font-size: var(--text-base); font-weight: 500; color: var(--text-muted); background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -2px; cursor: pointer; transition: all var(--transition-fast); }
.tab:hover { color: var(--text-primary); }
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }

.tab-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-3); }
.count { font-size: var(--text-xs); color: var(--text-subtle); }
.clear-btn { font-size: var(--text-xs); background: none; border: 1px solid var(--border-default); color: var(--text-muted); padding: 2px 10px; border-radius: var(--radius-sm); cursor: pointer; }
.clear-btn:hover { border-color: var(--accent-error); color: var(--accent-error); }

.desc { font-size: var(--text-xs); color: var(--text-subtle); margin-bottom: var(--space-3); }
.empty { color: var(--text-subtle); font-size: var(--text-sm); padding: var(--space-8) 0; text-align: center; }

/* 对话轮次 */
.turn-card { margin-bottom: var(--space-3); border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border-subtle); }
.turn-user { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); background: var(--bg-raised); cursor: pointer; user-select: none; transition: background var(--transition-fast); }
.turn-user:hover { background: var(--bg-hover); }
.turn-arrow { font-size: 10px; color: var(--text-subtle); width: 12px; flex-shrink: 0; }
.turn-label { flex-shrink: 0; }
.turn-text { flex: 1; font-size: var(--text-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.turn-time { font-size: 10px; color: var(--text-subtle); flex-shrink: 0; }
.turn-badge { font-size: 10px; padding: 1px 6px; border-radius: 8px; background: var(--bg-card); color: var(--text-subtle); flex-shrink: 0; }
.turn-details { border-top: 1px solid var(--border-subtle); padding: var(--space-2); background: var(--bg-base); }

/* 消息卡片 */
.msg-item { padding: var(--space-2) var(--space-3); background: var(--bg-raised); border-radius: var(--radius-md); margin-bottom: var(--space-2); }
.msg-item.user { border-left: 3px solid var(--user-msg-border); }
.msg-item.assistant { border-left: 3px solid var(--accent); }
.msg-item.tool { border-left: 3px solid var(--accent-warn); }
.msg-head { display: flex; justify-content: space-between; margin-bottom: 2px; }
.msg-role { font-size: 11px; font-weight: 600; color: var(--text-muted); }
.msg-time { font-size: 10px; color: var(--text-subtle); }
.msg-content { font-size: var(--text-sm); color: var(--text-primary); white-space: pre-wrap; word-break: break-word; }
.msg-content :deep(p) { margin: 4px 0; }
.msg-content :deep(strong) { font-weight: 600; }
.msg-content :deep(code) { font-family: var(--font-mono); font-size: 12px; background: var(--code-bg); padding: 1px 4px; border-radius: 3px; }
.msg-tools { margin-top: var(--space-1); display: flex; gap: var(--space-1); flex-wrap: wrap; }
.tc-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: rgba(245,158,11,0.15); color: var(--accent-warn); }

/* 搜索 */
.search-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.search-input { flex: 1; background: var(--bg-raised); border: 1px solid var(--border-default); border-radius: var(--radius-md); padding: var(--space-2) var(--space-3); color: var(--text-primary); font-size: var(--text-sm); outline: none; }
.search-input:focus { border-color: var(--accent); }
.search-btn { padding: var(--space-2) var(--space-4); background: var(--accent); color: #fff; border: none; border-radius: var(--radius-md); cursor: pointer; font-size: var(--text-sm); }
.search-btn:disabled { opacity: 0.5; }

/* 长期记忆 */
.long-list { display: flex; flex-direction: column; gap: var(--space-2); }
.mem-item { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-3); padding: var(--space-2) var(--space-3); background: var(--bg-raised); border-radius: var(--radius-md); font-size: var(--text-sm); border-left: 3px solid var(--accent-link); }
.mem-content { color: var(--text-primary); flex: 1; }
.mem-score { color: var(--accent-link); font-size: var(--text-xs); font-weight: 600; white-space: nowrap; }
</style>
