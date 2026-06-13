<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { marked } from 'marked'

interface ChatMsg { role: string; content: string; toolName?: string; toolCalls?: Array<{ function?: { name?: string } }>; timestamp: string }
interface Memory { content: string; similarity?: number }

const activeTab = ref<'short' | 'long'>('short')
const sessions = ref<Array<{ id: string; msgs: ChatMsg[] }>>([])
const expandedTurns = ref(new Set<string>())

const searchQuery = ref('')
const longResults = ref<Memory[]>([])
const longCount = ref(0)
const loading = ref(false)

onMounted(() => { loadAll(); loadLongCount() })

async function loadAll() {
  try {
    const res = await fetch('/api/memory/short')
    const data = await res.json()
    sessions.value = Object.entries(data).map(([id, msgs]) => ({ id, msgs: msgs as ChatMsg[] }))
  } catch {}
}

async function loadLongCount() {
  try { const r = await fetch('/api/memory/long/count'); longCount.value = (await r.json()).count || 0 } catch {}
}

async function searchLong() {
  if (!searchQuery.value.trim()) return
  loading.value = true
  try {
    const r = await fetch('/api/memory/long/search', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ query: searchQuery.value }) })
    longResults.value = await r.json()
  } catch {}
  loading.value = false
}

async function clearAll() {
  await fetch('/api/memory/short', { method: 'DELETE' })
  sessions.value = []
}

async function clearSession(sid: string) {
  await fetch(`/api/memory/short/${sid}`, { method: 'DELETE' })
  sessions.value = sessions.value.filter(s => s.id !== sid)
}

async function clearLong() {
  await fetch('/api/memory/long', { method: 'DELETE' })
  longResults.value = []; longCount.value = 0
}

// 分组轮次
function getTurns(msgs: ChatMsg[]) {
  const reversed = [...msgs].reverse()
  const turns: Array<{ user: ChatMsg; details: ChatMsg[] }> = []
  let i = 0
  while (i < reversed.length) {
    if (reversed[i].role === 'user') {
      const turn: { user: ChatMsg; details: ChatMsg[] } = { user: reversed[i], details: [] }
      for (i++; i < reversed.length && reversed[i].role !== 'user'; i++) turn.details.push(reversed[i])
      turns.push(turn)
    } else { i++ }
  }
  return turns.reverse()
}

function toggleTurn(key: string) {
  const s = new Set(expandedTurns.value)
  if (s.has(key)) s.delete(key); else s.add(key)
  expandedTurns.value = s
}

function detailCount(ds: ChatMsg[]) {
  const t = ds.filter(d => d.role === 'tool').length
  const a = ds.filter(d => d.role === 'assistant').length
  return [t ? `${t} 工具` : '', a ? `${a} 回复` : ''].filter(Boolean).join(' · ')
}

function renderMd(t: string) { return t ? (marked.parse(t, { breaks: true }) as string) : '' }
function roleLabel(m: ChatMsg) { return m.role === 'tool' ? '🔧 ' + (m.toolName || '工具') : m.role === 'user' ? '👤' : '🤖' }
</script>

<template>
  <div class="memory-view">
    <h1>记忆管理</h1>

    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'short' }" @click="activeTab = 'short'">短期记忆 · 对话转录</button>
      <button class="tab" :class="{ active: activeTab === 'long' }" @click="activeTab = 'long'">长期记忆 · Chroma</button>
    </div>

    <!-- 短期 -->
    <div v-show="activeTab === 'short'">
      <div class="tab-head">
        <span class="count">{{ sessions.length }} 个会话 · Redis · 24h 过期</span>
        <button v-if="sessions.length > 0" class="clear-all-btn" @click="clearAll">一键清空全部</button>
      </div>
      <div v-if="sessions.length === 0" class="empty">暂无对话记录</div>
      <div v-for="sess in sessions" :key="sess.id" class="session-block">
        <div class="session-head">
          <span class="session-id">📋 {{ sess.id.slice(0, 16) }}...</span>
          <span class="session-stats">{{ sess.msgs.length }} 条消息</span>
          <button class="clear-btn" @click="clearSession(sess.id)">清空</button>
        </div>
        <div v-for="(turn, ti) in getTurns(sess.msgs)" :key="ti" class="turn-card">
          <div class="turn-user" @click="toggleTurn(sess.id + '_' + ti)">
            <span class="turn-arrow">{{ expandedTurns.has(sess.id + '_' + ti) ? '▼' : '▶' }}</span>
            <span class="turn-label">👤</span>
            <span class="turn-text">{{ turn.user.content?.slice(0, 60) }}{{ (turn.user.content?.length || 0) > 60 ? '...' : '' }}</span>
            <span class="turn-time">{{ turn.user.timestamp?.slice(11, 19) }}</span>
            <span v-if="turn.details.length" class="turn-badge">{{ detailCount(turn.details) }}</span>
          </div>
          <div v-if="expandedTurns.has(sess.id + '_' + ti)" class="turn-details">
            <div v-for="(m, mi) in turn.details" :key="mi" class="msg-item" :class="m.role">
              <div class="msg-head"><span class="msg-role">{{ roleLabel(m) }}</span></div>
              <div class="msg-content" v-if="m.content" v-html="renderMd(m.content)" />
              <div v-if="m.toolCalls?.length" class="msg-tools"><span v-for="(tc, tci) in m.toolCalls" :key="tci" class="tc-tag">🔧 {{ tc.function?.name }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 长期 -->
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
    </div>
  </div>
</template>

<style scoped>
.memory-view { padding: var(--space-8); max-width: 760px; margin: 0 auto; height: 100%; overflow-y: auto; }
.memory-view h1 { font-size: var(--text-2xl); font-weight: 700; margin-bottom: var(--space-5); }

.tabs { display: flex; border-bottom: 2px solid var(--border-subtle); margin-bottom: var(--space-4); }
.tab { padding: var(--space-2) var(--space-5); font-size: var(--text-base); font-weight: 500; color: var(--text-muted); background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -2px; cursor: pointer; }
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }

.count { font-size: var(--text-xs); color: var(--text-subtle); margin-bottom: var(--space-3); }
.tab-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-3); }
.clear-btn { font-size: var(--text-xs); background: none; border: 1px solid var(--border-default); color: var(--text-muted); padding: 1px 8px; border-radius: var(--radius-sm); cursor: pointer; }
.clear-btn:hover { border-color: var(--accent-error); color: var(--accent-error); }
.clear-all-btn { font-size: var(--text-xs); background: none; border: 1px solid var(--accent-error); color: var(--accent-error); padding: 2px 10px; border-radius: var(--radius-sm); cursor: pointer; }
.clear-all-btn:hover { background: var(--accent-error); color: #fff; }
.empty { color: var(--text-subtle); font-size: var(--text-sm); padding: var(--space-8) 0; text-align: center; }

/* session */
.session-block { margin-bottom: var(--space-6); border: 1px solid var(--border-subtle); border-radius: var(--radius-lg); overflow: hidden; }
.session-head { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); background: var(--bg-raised); }
.session-id { font-family: var(--font-mono); font-size: 11px; color: var(--accent-link); flex: 1; }
.session-stats { font-size: 10px; color: var(--text-subtle); }

/* turns */
.turn-card { border-top: 1px solid var(--border-subtle); }
.turn-user { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); cursor: pointer; user-select: none; }
.turn-user:hover { background: var(--bg-hover); }
.turn-arrow { font-size: 10px; color: var(--text-subtle); width: 12px; flex-shrink: 0; }
.turn-label { flex-shrink: 0; }
.turn-text { flex: 1; font-size: var(--text-sm); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.turn-time { font-size: 10px; color: var(--text-subtle); flex-shrink: 0; }
.turn-badge { font-size: 10px; padding: 1px 6px; border-radius: 8px; background: var(--bg-card); color: var(--text-subtle); flex-shrink: 0; }
.turn-details { padding: var(--space-2); background: var(--bg-base); }

.msg-item { padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); margin-bottom: var(--space-1); }
.msg-item.user { border-left: 3px solid var(--user-msg-border); }
.msg-item.assistant { border-left: 3px solid var(--accent); }
.msg-item.tool { border-left: 3px solid var(--accent-warn); }
.msg-head { display: flex; justify-content: space-between; margin-bottom: 2px; }
.msg-role { font-size: 11px; font-weight: 600; color: var(--text-muted); }
.msg-content { font-size: var(--text-sm); color: var(--text-primary); white-space: pre-wrap; word-break: break-word; }
.msg-content :deep(p) { margin: 4px 0; }
.msg-tools { margin-top: var(--space-1); display: flex; gap: 4px; flex-wrap: wrap; }
.tc-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; background: rgba(245,158,11,0.15); color: var(--accent-warn); }

/* search */
.search-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.search-input { flex: 1; background: var(--bg-raised); border: 1px solid var(--border-default); border-radius: var(--radius-md); padding: var(--space-2) var(--space-3); color: var(--text-primary); font-size: var(--text-sm); outline: none; }
.search-input:focus { border-color: var(--accent); }
.search-btn { padding: var(--space-2) var(--space-4); background: var(--accent); color: #fff; border: none; border-radius: var(--radius-md); cursor: pointer; font-size: var(--text-sm); }
.search-btn:disabled { opacity: 0.5; }
.long-list { display: flex; flex-direction: column; gap: var(--space-2); }
.mem-item { display: flex; justify-content: space-between; padding: var(--space-2) var(--space-3); background: var(--bg-raised); border-radius: var(--radius-md); font-size: var(--text-sm); border-left: 3px solid var(--accent-link); }
.mem-content { color: var(--text-primary); flex: 1; }
.mem-score { color: var(--accent-link); font-size: var(--text-xs); font-weight: 600; white-space: nowrap; }
</style>
