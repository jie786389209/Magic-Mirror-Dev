import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSettingsStore = defineStore('settings', () => {
  const model = ref('deepseek-chat')
  const apiBaseUrl = ref('http://localhost:8080/api')
  const temperature = ref(0.7)
  const maxTokens = ref(4096)

  return {
    model,
    apiBaseUrl,
    temperature,
    maxTokens,
  }
})
