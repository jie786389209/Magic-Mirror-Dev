<script setup lang="ts">
import { ref, computed } from 'vue'
import TopBar from './TopBar.vue'
import SideIconBar from './SideIconBar.vue'
import RightPanel from './RightPanel.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const rightPanelWidth = ref(320)
const isResizing = ref(false)

const panelStyle = computed(() => {
  if (!appStore.isRightPanelOpen) return { width: '0', minWidth: '0' }
  return {
    width: `${rightPanelWidth.value}px`,
    minWidth: `${rightPanelWidth.value}px`,
  }
})

function onResizeStart(e: MouseEvent) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = rightPanelWidth.value

  function onMove(ev: MouseEvent) {
    const delta = startX - ev.clientX
    rightPanelWidth.value = Math.min(480, Math.max(240, startWidth + delta))
  }

  function onUp() {
    isResizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}
</script>

<template>
  <div class="app-layout">
    <TopBar />
    <div class="app-body">
      <SideIconBar />
      <main class="main-content">
        <slot />
      </main>
      <div
        v-if="appStore.isRightPanelOpen"
        class="resize-handle"
        :class="{ active: isResizing }"
        @mousedown="onResizeStart"
      />
      <div class="right-panel-wrapper" :style="panelStyle">
        <RightPanel v-if="appStore.isRightPanelOpen" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.app-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-base);
}

.resize-handle {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background var(--transition-fast);
  flex-shrink: 0;
}
.resize-handle:hover,
.resize-handle.active {
  background: var(--accent);
}

.right-panel-wrapper {
  overflow: hidden;
  flex-shrink: 0;
  transition: width var(--transition-normal);
  border-left: 1px solid var(--border-subtle);
}
</style>
