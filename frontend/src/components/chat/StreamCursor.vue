<template>
  <span class="stream-cursor">▊</span>
</template>

<style scoped>
.stream-cursor {
  color: var(--accent);
  font-weight: bold;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
