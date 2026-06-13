import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const isSidebarCollapsed = ref(false)
  const isRightPanelOpen = ref(true)

  function toggleSidebar() {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
  }

  function toggleRightPanel() {
    isRightPanelOpen.value = !isRightPanelOpen.value
  }

  function closeRightPanel() {
    isRightPanelOpen.value = false
  }

  function openRightPanel() {
    isRightPanelOpen.value = true
  }

  return {
    isSidebarCollapsed,
    isRightPanelOpen,
    toggleSidebar,
    toggleRightPanel,
    closeRightPanel,
    openRightPanel,
  }
})
