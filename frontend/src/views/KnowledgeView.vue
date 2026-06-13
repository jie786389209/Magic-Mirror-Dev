<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface DocResult { content: string; score: number; filename: string }

const activeTab = ref<'upload' | 'search'>('upload')
const docCount = ref(0)
const uploading = ref(false)
const uploadMsg = ref('')

const searchQuery = ref('')
const searchResults = ref<DocResult[]>([])
const searching = ref(false)

onMounted(loadCount)

async function loadCount() {
  try { const r = await fetch('/api/documents/count'); docCount.value = (await r.json()).count || 0 } catch {}
}

async function onUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  uploadMsg.value = ''
  try {
    const form = new FormData()
    form.append('file', file)
    const r = await fetch('/api/documents/upload', { method: 'POST', body: form })
    const d = await r.json()
    uploadMsg.value = d.message || '上传成功'
    loadCount()
  } catch { uploadMsg.value = '上传失败' }
  uploading.value = false
}

async function onSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  try {
    const r = await fetch('/api/documents/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: searchQuery.value, topK: 5 })
    })
    searchResults.value = await r.json()
  } catch {}
  searching.value = false
}

async function onClear() {
  await fetch('/api/documents', { method: 'DELETE' })
  docCount.value = 0
  searchResults.value = []
}
</script>

<template>
  <div class="knowledge-view">
    <h1>知识库</h1>

    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'upload' }" @click="activeTab = 'upload'">上传文档</button>
      <button class="tab" :class="{ active: activeTab === 'search' }" @click="activeTab = 'search'">搜索文档</button>
    </div>

    <!-- 上传 -->
    <div v-show="activeTab === 'upload'">
      <div class="tab-head">
        <span class="count">{{ docCount }} 个文档片段</span>
        <button v-if="docCount > 0" class="clear-btn" @click="onClear">清空</button>
      </div>
      <div class="upload-area">
        <label class="upload-btn" :class="{ loading: uploading }">
          {{ uploading ? '上传中...' : '选择文件上传' }}
          <input type="file" accept=".txt,.md,.pdf,.java,.py,.js,.ts,.json,.yaml,.xml,.html,.css" @change="onUpload" />
        </label>
        <p class="hint">支持 PDF、Markdown、TXT、代码文件</p>
        <p v-if="uploadMsg" class="upload-msg">{{ uploadMsg }}</p>
      </div>
    </div>

    <!-- 搜索 -->
    <div v-show="activeTab === 'search'">
      <div class="search-row">
        <input v-model="searchQuery" class="search-input" placeholder="输入问题检索文档..." @keyup.enter="onSearch" />
        <button class="search-btn" :disabled="searching" @click="onSearch">搜索</button>
      </div>
      <div v-if="searchResults.length" class="result-list">
        <div v-for="(r, i) in searchResults" :key="i" class="result-card">
          <div class="result-head">
            <span class="result-file">📄 {{ r.filename || '未知文件' }}</span>
            <span class="result-score">{{ ((r.score || 0) * 100).toFixed(0) }}%</span>
          </div>
          <p class="result-content">{{ r.content }}</p>
        </div>
      </div>
      <div v-else class="empty">搜索知识库中的文档内容</div>
    </div>
  </div>
</template>

<style scoped>
.knowledge-view { padding: var(--space-8); max-width: 760px; margin: 0 auto; height: 100%; overflow-y: auto; }
.knowledge-view h1 { font-size: var(--text-2xl); font-weight: 700; margin-bottom: var(--space-5); }

.tabs { display: flex; gap: 0; border-bottom: 2px solid var(--border-subtle); margin-bottom: var(--space-4); }
.tab { padding: var(--space-2) var(--space-5); font-size: var(--text-base); font-weight: 500; color: var(--text-muted); background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -2px; cursor: pointer; }
.tab.active { color: var(--accent); border-bottom-color: var(--accent); }

.tab-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-3); }
.count { font-size: var(--text-xs); color: var(--text-subtle); }
.clear-btn { font-size: var(--text-xs); background: none; border: 1px solid var(--border-default); color: var(--text-muted); padding: 2px 10px; border-radius: var(--radius-sm); cursor: pointer; }
.clear-btn:hover { border-color: var(--accent-error); color: var(--accent-error); }

.upload-area { text-align: center; padding: var(--space-10) 0; }
.upload-btn { display: inline-block; padding: var(--space-3) var(--space-6); background: var(--accent); color: #fff; border-radius: var(--radius-md); cursor: pointer; font-size: var(--text-base); position: relative; }
.upload-btn.loading { opacity: 0.6; }
.upload-btn input { position: absolute; opacity: 0; width: 100%; height: 100%; left: 0; top: 0; cursor: pointer; }
.hint { font-size: var(--text-xs); color: var(--text-subtle); margin-top: var(--space-3); }
.upload-msg { font-size: var(--text-sm); color: var(--accent); margin-top: var(--space-2); }

.search-row { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.search-input { flex: 1; background: var(--bg-raised); border: 1px solid var(--border-default); border-radius: var(--radius-md); padding: var(--space-2) var(--space-3); color: var(--text-primary); font-size: var(--text-sm); outline: none; }
.search-input:focus { border-color: var(--accent); }
.search-btn { padding: var(--space-2) var(--space-4); background: var(--accent); color: #fff; border: none; border-radius: var(--radius-md); cursor: pointer; }
.search-btn:disabled { opacity: 0.5; }
.empty { text-align: center; padding: var(--space-8); color: var(--text-subtle); font-size: var(--text-sm); }

.result-list { display: flex; flex-direction: column; gap: var(--space-3); }
.result-card { background: var(--bg-raised); border: 1px solid var(--border-subtle); border-radius: var(--radius-lg); padding: var(--space-3); }
.result-head { display: flex; justify-content: space-between; margin-bottom: var(--space-1); }
.result-file { font-size: var(--text-xs); color: var(--accent-link); }
.result-score { font-size: var(--text-xs); font-weight: 600; color: var(--accent); }
.result-content { font-size: var(--text-sm); color: var(--text-muted); line-height: 1.5; white-space: pre-wrap; }
</style>
