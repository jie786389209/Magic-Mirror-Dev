<script setup lang="ts">
</script>

<template>
  <div class="placeholder-view">
    <div class="placeholder-icon">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5" />
        <path d="M8.5 8.5v.01" />
        <path d="M15.5 15.5v.01" />
        <path d="M12 12v.01" />
      </svg>
    </div>
    <h2>记忆</h2>
    <p>持久化上下文记忆，让 AI 更懂你的偏好</p>
    <span class="tag">开发中，将在记忆模块上线</span>
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
