<script setup lang="ts">
</script>

<template>
  <div class="placeholder-view">
    <div class="placeholder-icon">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
        <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
        <line x1="6" y1="6" x2="6.01" y2="6" />
        <line x1="6" y1="18" x2="6.01" y2="18" />
      </svg>
    </div>
    <h2>Skill</h2>
    <p>自定义 Skill 工作流，自动化复杂任务</p>
    <span class="tag">开发中，将在 Skill 模块上线</span>
  </div>
</template>

<style scoped>
.placeholder-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  gap: var(--space-3);
}

.placeholder-icon {
  color: var(--text-subtle);
  margin-bottom: var(--space-2);
}

.placeholder-view h2 {
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--text-primary);
}

.placeholder-view p {
  font-size: var(--text-base);
}

.tag {
  font-size: var(--text-xs);
  padding: 2px 10px;
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-subtle);
  margin-top: var(--space-2);
}
</style>
