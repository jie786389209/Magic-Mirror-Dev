<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface ToolState {
  name: string
  description: string
  enabled: boolean
}

const tools = ref<ToolState[]>([])
const loading = ref(true)

onMounted(loadTools)

async function loadTools() {
  try { const r = await fetch('/api/tools'); tools.value = await r.json() } catch {}
  loading.value = false
}

async function toggleTool(name: string, enabled: boolean) {
  const url = `/api/tools/${name}/${enabled ? 'enable' : 'disable'}`
  await fetch(url, { method: 'POST' })
  loadTools()
}
</script>

<template>
  <div class="tools-view">
    <div class="tools-header">
      <h1>工具管理</h1>
      <p>已注册的工具列表，AI 可在对话中自动调用</p>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="tools-grid">
      <div v-for="tool in tools" :key="tool.name" class="tool-card">
        <div class="tool-header">
          <span class="tool-name">🔧 {{ tool.name }}</span>
          <label class="toggle-switch">
            <input type="checkbox" :checked="tool.enabled" @change="toggleTool(tool.name, !tool.enabled)" />
            <span class="slider" />
          </label>
        </div>
        <p class="tool-desc">{{ tool.description }}</p>
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

.tool-desc {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

/* Toggle Switch */
.toggle-switch { position: relative; display: inline-block; width: 40px; height: 22px; flex-shrink: 0; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: 22px; transition: 0.2s; }
.slider::before { content: ""; position: absolute; height: 16px; width: 16px; left: 3px; bottom: 3px; background: var(--text-subtle); border-radius: 50%; transition: 0.2s; }
.toggle-switch input:checked + .slider { background: var(--accent); }
.toggle-switch input:checked + .slider::before { transform: translateX(18px); background: #fff; }

.empty {
  text-align: center;
  padding: var(--space-10);
  color: var(--text-muted);
}
</style>
