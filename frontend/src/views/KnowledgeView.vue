<script setup lang="ts">
</script>

<template>
  <div class="placeholder-view">
    <div class="placeholder-icon">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      </svg>
    </div>
    <h2>知识库</h2>
    <p>上传文档、构建知识图谱、向量化搜索</p>
    <span class="tag">开发中，将在 RAG 模块上线</span>
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
