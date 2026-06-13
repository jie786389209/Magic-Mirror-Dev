<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface ToolDef {
  type: string
  function: {
    name: string
    description: string
    parameters: Record<string, unknown>
  }
}

const tools = ref<ToolDef[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetch('/api/tools')
    tools.value = await res.json()
  } catch (e) {
    console.error('Failed to fetch tools', e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="tools-view">
    <div class="tools-header">
      <h1>工具管理</h1>
      <p>已注册的工具列表，AI 可在对话中自动调用</p>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="tools-grid">
      <div v-for="tool in tools" :key="tool.function.name" class="tool-card">
        <div class="tool-header">
          <span class="tool-name">🔧 {{ tool.function.name }}</span>
          <span class="tool-status active">已启用</span>
        </div>
        <p class="tool-desc">{{ tool.function.description }}</p>
        <details class="tool-params">
          <summary>参数 Schema</summary>
          <pre>{{ JSON.stringify(tool.function.parameters, null, 2) }}</pre>
        </details>
      </div>

      <div v-if="tools.length === 0" class="empty">
        <p>暂无注册工具</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tools-view {
  padding: var(--space-8);
  max-width: 800px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.tools-header {
  margin-bottom: var(--space-6);
}
.tools-header h1 {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-1);
}
.tools-header p {
  font-size: var(--text-base);
  color: var(--text-muted);
}

.loading {
  color: var(--text-muted);
  font-size: var(--text-base);
}

.tools-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.tool-card {
  background: var(--bg-raised);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.tool-name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono);
}

.tool-status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}
.tool-status.active {
  background: var(--accent-glow);
  color: var(--accent);
}

.tool-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
  margin-bottom: var(--space-2);
}

.tool-params {
  font-size: var(--text-xs);
  color: var(--text-subtle);
}
.tool-params summary {
  cursor: pointer;
  color: var(--accent-link);
}
.tool-params pre {
  background: var(--code-bg);
  border-radius: var(--radius-md);
  padding: var(--space-2);
  margin-top: var(--space-1);
  overflow-x: auto;
  font-size: 11px;
  color: var(--text-muted);
}

.empty {
  text-align: center;
  padding: var(--space-10);
  color: var(--text-muted);
}
</style>
