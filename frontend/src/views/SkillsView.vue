<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface SkillDef {
  name: string
  description: string
  triggers: string[]
  enabled: boolean
  params: Record<string, string>
  steps: Array<{ type: string; tool?: string; prompt?: string }>
}

const skills = ref<SkillDef[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetch('/api/skills')
    skills.value = await res.json()
  } catch { /* noop */ }
  loading.value = false
})
</script>

<template>
  <div class="skills-view">
    <h1>Skill 管理</h1>
    <p class="subtitle">声明式工作流，组合工具调用与 AI 追问</p>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else class="skill-grid">
      <div v-for="s in skills" :key="s.name" class="skill-card">
        <div class="card-top">
          <span class="skill-name">🧩 {{ s.name }}</span>
          <span class="badge" :class="{ active: s.enabled }">{{ s.enabled ? '启用' : '停用' }}</span>
        </div>
        <p class="skill-desc">{{ s.description }}</p>

        <div class="skill-meta">
          <div class="meta-row">
            <span class="meta-label">触发词</span>
            <span class="meta-val">{{ s.triggers?.join(', ') }}</span>
          </div>
          <div v-if="s.params && Object.keys(s.params).length" class="meta-row">
            <span class="meta-label">参数</span>
            <span class="meta-val">
              <code v-for="(desc, key) in s.params" :key="key">\{{ key }}: {{ desc }} </code>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">步骤</span>
            <span class="meta-val">
              <span v-for="(step, i) in s.steps" :key="i" class="step-tag">
                {{ step.type === 'tool' ? '🔧 ' + step.tool : '💬 AI 追问' }}
                <span v-if="i < s.steps.length - 1"> → </span>
              </span>
            </span>
          </div>
        </div>
      </div>

      <div v-if="skills.length === 0" class="empty">暂无 Skill，在 resources/skills/ 下添加 JSON 配置</div>
    </div>
  </div>
</template>

<style scoped>
.skills-view { padding: var(--space-8); max-width: 800px; margin: 0 auto; height: 100%; overflow-y: auto; }
.subtitle { font-size: var(--text-base); color: var(--text-muted); margin-bottom: var(--space-6); }
.loading { color: var(--text-muted); }
.skill-grid { display: flex; flex-direction: column; gap: var(--space-4); }
.skill-card { background: var(--bg-raised); border: 1px solid var(--border-subtle); border-radius: var(--radius-lg); padding: var(--space-4); }
.card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
.skill-name { font-weight: 600; color: var(--text-primary); }
.badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--bg-card); color: var(--text-subtle); }
.badge.active { background: var(--accent-glow); color: var(--accent); }
.skill-desc { font-size: var(--text-sm); color: var(--text-muted); margin-bottom: var(--space-3); }
.skill-meta { display: flex; flex-direction: column; gap: var(--space-1); }
.meta-row { display: flex; gap: var(--space-2); font-size: var(--text-xs); }
.meta-label { color: var(--text-subtle); min-width: 48px; }
.meta-val { color: var(--text-muted); }
.meta-val code { font-size: 11px; }
.step-tag { color: var(--accent-link); }
.empty { text-align: center; padding: var(--space-10); color: var(--text-muted); }
</style>
