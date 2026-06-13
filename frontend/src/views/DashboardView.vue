<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const systemStatus = [
  { label: 'API 连接', status: 'pending', text: '待配置' },
  { label: '知识库', status: 'pending', text: '未启用' },
  { label: '工具服务', status: 'pending', text: '未启用' },
  { label: '记忆服务', status: 'pending', text: '未启用' },
]

const quickActions = [
  { title: '开始新对话', desc: '打开对话面板，与 AI 开始交流', path: '/chat', icon: '💬' },
  { title: '配置 API', desc: '设置 DeepSeek API 连接参数', path: '/settings', icon: '⚙️' },
]
</script>

<template>
  <div class="dashboard">
    <!-- 欢迎区 -->
    <div class="welcome-section">
      <div class="welcome-icon">
        <svg width="56" height="56" viewBox="0 0 64 64" fill="none">
          <circle cx="32" cy="32" r="30" stroke="#22C55E" stroke-width="2" />
          <circle cx="32" cy="32" r="8" fill="#22C55E" opacity="0.3" />
          <path d="M32 8 L32 24 M32 40 L32 56 M8 32 L24 32 M40 32 L56 32"
            stroke="#22C55E" stroke-width="2" stroke-linecap="round" opacity="0.5" />
        </svg>
      </div>
      <h1>欢迎使用 Magic Mirror Dev</h1>
      <p class="welcome-desc">轻量级 AI 开发助手，支持多轮对话、知识库检索、工具调用与 Skill 编排</p>
    </div>

    <!-- 快速开始 -->
    <div class="section">
      <h2 class="section-title">快速开始</h2>
      <div class="quick-actions">
        <div
          v-for="action in quickActions"
          :key="action.path"
          class="action-card"
          @click="router.push(action.path)"
        >
          <span class="action-icon">{{ action.icon }}</span>
          <div class="action-info">
            <h3>{{ action.title }}</h3>
            <p>{{ action.desc }}</p>
          </div>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </div>
      </div>
    </div>

    <!-- 系统状态 -->
    <div class="section">
      <h2 class="section-title">系统状态</h2>
      <div class="status-grid">
        <div v-for="item in systemStatus" :key="item.label" class="status-item">
          <span class="status-dot" :class="item.status" />
          <span class="status-label">{{ item.label }}</span>
          <span class="status-text">{{ item.text }}</span>
        </div>
      </div>
    </div>

    <!-- 版本信息 -->
    <div class="footer-info">
      <span>Magic Mirror Dev v0.1.0</span>
      <span class="separator">·</span>
      <span>Spring Boot 3.2.5 + Vue 3.4</span>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding: var(--space-10) var(--space-8);
  max-width: 700px;
  margin: 0 auto;
  flex: 1;
  overflow-y: auto;
}

/* ── 欢迎区 ── */
.welcome-section {
  text-align: center;
  padding: var(--space-8) 0;
  margin-bottom: var(--space-8);
}

.welcome-icon {
  margin-bottom: var(--space-5);
}

.welcome-section h1 {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-2);
}

.welcome-desc {
  font-size: var(--text-base);
  color: var(--text-muted);
  max-width: 420px;
  margin: 0 auto;
  line-height: 1.6;
}

/* ── 通用区块 ── */
.section {
  margin-bottom: var(--space-8);
}

.section-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--space-3);
  padding-left: var(--space-1);
}

/* ── 快速操作 ── */
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.action-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--bg-raised);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-normal);
}
.action-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-glow);
}

.action-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.action-info {
  flex: 1;
}

.action-info h3 {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.action-info p {
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.arrow {
  color: var(--text-subtle);
  flex-shrink: 0;
}

/* ── 系统状态 ── */
.status-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.status-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--bg-raised);
  border-radius: var(--radius-md);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.online {
  background: var(--accent);
  box-shadow: 0 0 6px var(--accent-glow);
}
.status-dot.pending {
  background: var(--text-subtle);
}

.status-label {
  font-size: var(--text-sm);
  color: var(--text-primary);
  flex: 1;
}

.status-text {
  font-size: var(--text-xs);
  color: var(--text-subtle);
}

/* ── 底部 ── */
.footer-info {
  text-align: center;
  padding: var(--space-6) 0;
  font-size: var(--text-xs);
  color: var(--text-subtle);
}

.separator {
  margin: 0 var(--space-2);
}
</style>
