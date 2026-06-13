<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useSettingsStore } from '@/stores/settings'

const settings = useSettingsStore()

const form = ref({
  model: settings.model,
  apiBaseUrl: settings.apiBaseUrl,
  temperature: settings.temperature,
  maxTokens: settings.maxTokens,
})

function handleSave() {
  settings.model = form.value.model
  settings.apiBaseUrl = form.value.apiBaseUrl
  settings.temperature = form.value.temperature
  settings.maxTokens = form.value.maxTokens
  ElMessage.success('设置已保存')
}
</script>

<template>
  <div class="settings-view">
    <div class="settings-container">
      <h1>设置</h1>
      <p class="subtitle">配置 AI 模型和 API 连接</p>

      <div class="settings-form">
        <div class="form-group">
          <label>API 地址</label>
          <input v-model="form.apiBaseUrl" type="text" class="form-input" placeholder="http://localhost:8080/api" />
        </div>

        <div class="form-group">
          <label>模型</label>
          <select v-model="form.model" class="form-input">
            <option value="deepseek-chat">DeepSeek Chat</option>
            <option value="deepseek-coder">DeepSeek Coder</option>
          </select>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Temperature: {{ form.temperature }}</label>
            <input v-model.number="form.temperature" type="range" min="0" max="2" step="0.1" class="form-range" />
          </div>
          <div class="form-group">
            <label>Max Tokens</label>
            <input v-model.number="form.maxTokens" type="number" class="form-input" min="256" max="32768" />
          </div>
        </div>

        <button class="save-btn" @click="handleSave">保存设置</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-view {
  padding: var(--space-10) var(--space-8);
  max-width: 640px;
  margin: 0 auto;
  overflow-y: auto;
  height: 100%;
}

.settings-container h1 {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-1);
}

.subtitle {
  font-size: var(--text-base);
  color: var(--text-muted);
  margin-bottom: var(--space-8);
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form-group label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-muted);
}

.form-input {
  background: var(--bg-raised);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-3);
  color: var(--text-primary);
  font-size: var(--text-base);
  font-family: var(--font-sans);
  outline: none;
  transition: border-color var(--transition-fast);
}

.form-input:focus {
  border-color: var(--accent);
}

select.form-input {
  cursor: pointer;
}

.form-range {
  width: 100%;
  accent-color: var(--accent);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.save-btn {
  align-self: flex-start;
  padding: var(--space-2) var(--space-6);
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 500;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.save-btn:hover {
  background: var(--accent-hover);
}
</style>
