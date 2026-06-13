<script setup lang="ts">
import { ref } from 'vue'

type PanelTab = 'files' | 'memory' | 'tools' | 'context'

const activeTab = ref<PanelTab>('context')

const tabs: { id: PanelTab; label: string; icon: string }[] = [
  { id: 'context', label: '上下文', icon: 'context' },
  { id: 'files', label: '文件', icon: 'files' },
  { id: 'memory', label: '记忆', icon: 'memory' },
  { id: 'tools', label: '工具', icon: 'tools' },
]
</script>

<template>
  <aside class="right-panel">
    <div class="panel-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        <svg v-if="tab.icon === 'context'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="16" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12.01" y2="8" />
        </svg>
        <svg v-else-if="tab.icon === 'files'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
        <svg v-else-if="tab.icon === 'memory'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5" />
        </svg>
        <svg v-else-if="tab.icon === 'tools'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
        </svg>
        <span>{{ tab.label }}</span>
      </button>
    </div>

    <div class="panel-content">
      <!-- 上下文 Tab -->
      <div v-if="activeTab === 'context'" class="tab-content">
        <div class="info-block">
          <div class="info-label">🏷 当前上下文</div>
          <div class="info-item">
            <span class="info-key">数据库</span>
            <span class="info-val">MySQL 8.0</span>
          </div>
          <div class="info-item">
            <span class="info-key">项目</span>
            <span class="info-val">magic-mirror-dev</span>
          </div>
          <div class="info-item">
            <span class="info-key">状态</span>
            <span class="info-val status-ready">就绪</span>
          </div>
        </div>
      </div>

      <!-- 文件 Tab -->
      <div v-else-if="activeTab === 'files'" class="tab-content">
        <div class="empty-hint">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.4">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
          </svg>
          <p>暂无关联文件</p>
          <p class="sub">上传或 @ 引用文件后显示</p>
        </div>
      </div>

      <!-- 记忆 Tab -->
      <div v-else-if="activeTab === 'memory'" class="tab-content">
        <div class="empty-hint">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.4">
            <path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5" />
          </svg>
          <p>暂无相关记忆</p>
          <p class="sub">记忆模块将在后续版本上线</p>
        </div>
      </div>

      <!-- 工具 Tab -->
      <div v-else-if="activeTab === 'tools'" class="tab-content">
        <div class="empty-hint">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.4">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
          </svg>
          <p>暂无可用工具</p>
          <p class="sub">工具模块将在后续版本上线</p>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.right-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-base);
  user-select: none;
}

.panel-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--border-subtle);
  padding: 0 var(--space-2);
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-3);
  border: none;
  background: transparent;
  color: var(--text-subtle);
  font-size: var(--text-xs);
  font-family: var(--font-sans);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all var(--transition-fast);
  white-space: nowrap;
}
.tab-btn:hover {
  color: var(--text-muted);
}
.tab-btn.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
}

.info-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.info-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: var(--space-1);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-2) var(--space-3);
  background: var(--bg-raised);
  border-radius: var(--radius-md);
}

.info-key {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.info-val {
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.status-ready {
  color: var(--accent);
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-10) var(--space-4);
  text-align: center;
  color: var(--text-muted);
}
.empty-hint p {
  font-size: var(--text-sm);
  margin-top: var(--space-2);
}
.empty-hint .sub {
  font-size: var(--text-xs);
  color: var(--text-subtle);
  margin-top: var(--space-1);
}
</style>
