<template>
  <div class="models-page">
    <div class="page-header">
      <div class="page-header-info">
        <h1 class="page-title">模型管理</h1>
        <p class="page-desc">配置 AI 模型供应商、API Key 与连接参数</p>
      </div>
    </div>

    <!-- 当前模型配置 -->
    <div class="panel">
      <div class="panel-head">
        <div>
          <h3>当前模型配置</h3>
          <p>生效中的供应商、模型与密钥状态</p>
        </div>
        <div class="panel-head-actions">
          <button class="btn btn-primary small" @click="openSwitchModelModal">切换模型</button>
          <button class="btn btn-secondary small" @click="openConfigModal('save')">编辑配置</button>
          <button class="btn btn-secondary small" @click="openConfigModal('test')">测试连接</button>
          <button class="btn btn-danger-soft small" @click="handleClearConfig">清空配置</button>
        </div>
      </div>
      <div class="panel-body">
        <div class="meta-grid">
          <div class="meta-card">
            <div class="meta-label">供应商</div>
            <div class="meta-value">{{ currentConfig.provider ? currentConfig.provider.toUpperCase() : '' }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-label">模型</div>
            <div class="meta-value">{{ currentConfig.model || '' }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-label">API Key</div>
            <div class="meta-value">{{ currentConfig.hasApiKey ? '已配置' : '' }}</div>
          </div>
          <div class="meta-card">
            <div class="meta-label">Base URL</div>
            <div class="meta-value" style="font-size: 13px;">{{ currentConfig.baseUrl || currentConfig.base_url || '' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 已保存的配置 -->
    <div class="panel" style="margin-top: 20px;">
      <div class="panel-head">
        <div>
          <h3>已保存的配置</h3>
          <p>查看已保存的配置并进行快速切换</p>
        </div>
      </div>
      <div class="panel-body">
        <div v-if="loading" class="loading-state">加载中...</div>
        <div v-else-if="savedConfigs.length === 0" class="empty">暂无已保存的配置。</div>
        <div v-else class="saved-configs-list">
          <div
            v-for="cfg in savedConfigs"
            :key="cfg.provider"
            :class="['list-item', { active: cfg.active }]"
          >
            <div>
              <div class="item-title">{{ cfg.provider }} / {{ cfg.model }}</div>
              <div class="item-meta">{{ cfg.baseUrl || '--' }}</div>
            </div>
            <span v-if="cfg.active" class="badge success">使用中</span>
            <button v-else class="btn btn-secondary small" @click="handleSwitchToConfig(cfg.provider)">切换</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 型号列表 -->
    <div class="panel" style="margin-top: 20px;">
      <div class="panel-head">
        <div>
          <h3>供应商目录</h3>
          <p>可用的模型列表与基本信息</p>
        </div>
      </div>
      <div class="panel-body">
        <div v-if="loading" class="loading-state">加载中...</div>
        <div v-else-if="!providers.length" class="empty">当前没有可展示的模型目录。</div>
        <div v-else class="catalog-list">
          <div v-for="provider in providers" :key="provider.id || provider.name" class="catalog-item">
            <div class="catalog-provider">{{ provider.name || provider.id }}</div>
            <div class="catalog-models">
              <span v-for="model in (provider.models || []).slice(0, 8)" :key="model.id || model.name" class="badge info">
                {{ model.name || model.id || model.modelId || '--' }}
              </span>
              <span v-if="(provider.models || []).length > 8" class="badge info">...</span>
            </div>
            <div class="catalog-baseurl">{{ provider.baseUrl || '--' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 配置弹窗 -->
    <div v-if="showConfigModal" class="modal-overlay" @click.self="closeConfigModal">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <h3>{{ modalMode === 'save' ? '编辑模型配置' : '测试模型连接' }}</h3>
            <p>{{ modalMode === 'save' ? '保存后将覆盖当前 AI 配置。' : '使用填写的配置进行即时连通性测试。' }}</p>
          </div>
          <button class="modal-close" @click="closeConfigModal">×</button>
        </div>
        <form @submit.prevent="handleConfigSubmit">
          <div class="modal-body">
            <div class="field">
              <label>供应商</label>
              <select v-model="configForm.provider" required>
                <option v-for="p in providerOptions" :key="p.value" :value="p.value">{{ p.label }}</option>
              </select>
            </div>
            <div class="field">
              <label>模型名称</label>
              <input v-model="configForm.model" type="text" placeholder="例如 MiniMax M2.7 高速版（可选）" />
            </div>
            <div class="field">
              <label>模型ID</label>
              <input v-model="configForm.modelId" type="text" placeholder="例如 MiniMax-M2.7-highspeed（必填）" required />
            </div>
            <div class="field">
              <label>Base URL</label>
              <input v-model="configForm.baseUrl" type="text" placeholder="例如 https://api.minimax.chat/v1（必填）" required />
            </div>
            <div class="field">
              <label>API Key</label>
              <input v-model="configForm.apiKey" type="password" :placeholder="modalMode === 'save' ? '输入 API Key' : '（可选）'" />
            </div>
          </div>
          <div class="modal-foot">
            <button type="button" class="btn btn-secondary" @click="closeConfigModal">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              {{ submitting ? '提交中...' : (modalMode === 'save' ? '保存配置' : '开始测试') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 切换模型弹窗 -->
    <div v-if="showSwitchModal" class="modal-overlay" @click.self="closeSwitchModal">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <h3>切换模型</h3>
            <p>从已保存的配置中选择</p>
          </div>
          <button class="modal-close" @click="closeSwitchModal">×</button>
        </div>
        <form @submit.prevent="handleSwitchSubmit">
          <div class="modal-body">
            <div class="field">
              <label>已保存的配置</label>
              <select v-model="switchForm.provider" required @change="handleSwitchProviderChange">
                <option v-for="cfg in savedConfigs" :key="cfg.provider" :value="cfg.provider">
                  {{ cfg.provider }} - {{ cfg.model }}
                </option>
              </select>
            </div>
            <div class="field">
              <label>模型名称</label>
              <input v-model="switchForm.model" type="text" placeholder="例如 MiniMax M2.7 高速版（可选）" required />
            </div>
            <div class="field">
              <label>模型ID</label>
              <input v-model="switchForm.modelId" type="text" placeholder="例如 MiniMax-M2.7-highspeed（必填）" required />
            </div>
          </div>
          <div class="modal-foot">
            <button type="button" class="btn btn-secondary" @click="closeSwitchModal">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              {{ submitting ? '提交中...' : '确认切换' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 清空确认弹窗 -->
    <div v-if="showClearConfirm" class="modal-overlay" @click.self="showClearConfirm = false">
      <div class="modal-card confirm-modal">
        <div class="modal-head">
          <h3>确认清空</h3>
        </div>
        <div class="modal-body">
          <p>确认清空当前 AI 配置吗？</p>
        </div>
        <div class="modal-foot">
          <button class="btn btn-secondary" @click="showClearConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmClearConfig">确认删除</button>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <div v-if="toast.show" :class="['toast', toast.type]">{{ toast.message }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { apiRequest } from '@/api'

const loading = ref(false)
const submitting = ref(false)

const currentConfig = ref({
  provider: '',
  model: '',
  modelId: '',
  baseUrl: '',
  base_url: '',
  hasApiKey: false
})

const aiModels = ref(null)
const savedConfigs = ref([])

const showConfigModal = ref(false)
const showSwitchModal = ref(false)
const showClearConfirm = ref(false)
const modalMode = ref('save')

const configForm = reactive({
  provider: '',
  model: '',
  modelId: '',
  baseUrl: '',
  apiKey: ''
})

const switchForm = reactive({
  provider: '',
  model: '',
  modelId: ''
})

const toast = reactive({
  show: false,
  message: '',
  type: 'info'
})

const defaultProviders = [
  { label: 'MiniMax', value: 'minimax' },
  { label: 'OpenAI', value: 'openai' },
  { label: '自定义 OpenAI 兼容接口', value: 'custom' }
]

const providerOptions = computed(() => {
  if (aiModels.value && aiModels.value.providers && aiModels.value.providers.length > 0) {
    return aiModels.value.providers.map(item => ({
      label: item.name || item.id,
      value: item.id || item.name
    }))
  }
  return defaultProviders
})

const providers = computed(() => {
  if (aiModels.value && aiModels.value.providers) {
    return sortProvidersByOrder(aiModels.value.providers)
  }
  return []
})

function sortProvidersByOrder(providersList) {
  const order = ['minimax', 'openai', 'deepseek', 'zhipu', ' moonshot', 'custom']
  return [...providersList].sort((a, b) => {
    const aIdx = order.indexOf(a.id?.toLowerCase() || a.name?.toLowerCase())
    const bIdx = order.indexOf(b.id?.toLowerCase() || b.name?.toLowerCase())
    if (aIdx === -1 && bIdx === -1) return 0
    if (aIdx === -1) return 1
    if (bIdx === -1) return -1
    return aIdx - bIdx
  })
}

function pick(obj, ...keys) {
  for (const key of keys) {
    if (obj && key in obj) return obj[key]
  }
  return null
}

function showToastMessage(message, type = 'info') {
  toast.message = message
  toast.type = type
  toast.show = true
  setTimeout(() => {
    toast.show = false
  }, 2600)
}

function escapeHtml(str) {
  if (!str) return ''
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

async function loadModelData() {
  loading.value = true
  try {
    const [config, models, configs] = await Promise.all([
      apiRequest('/ai/config').catch(() => null),
      apiRequest('/ai/models').catch(() => null),
      apiRequest('/ai/saved-configs').catch(() => null)
    ])

    if (config) {
      currentConfig.value = {
        provider: pick(config, 'provider') || '',
        model: pick(config, 'model') || '',
        modelId: pick(config, 'modelId') || '',
        baseUrl: pick(config, 'baseUrl', 'base_url') || '',
        base_url: pick(config, 'base_url') || '',
        hasApiKey: Boolean(pick(config, 'hasApiKey', 'has_api_key'))
      }
    }

    if (models) {
      aiModels.value = models
    }

    if (configs) {
      savedConfigs.value = Array.isArray(configs) ? configs : []
    }
  } catch (e) {
    showToastMessage(e.message || '模型数据加载失败', 'warning')
  } finally {
    loading.value = false
  }
}

function openConfigModal(mode = 'save') {
  modalMode.value = mode
  configForm.provider = currentConfig.value.provider || providerOptions.value[0]?.value || 'minimax'
  configForm.model = currentConfig.value.model || ''
  configForm.modelId = currentConfig.value.modelId || ''
  configForm.baseUrl = currentConfig.value.baseUrl || currentConfig.value.base_url || ''
  configForm.apiKey = ''
  showConfigModal.value = true
}

function closeConfigModal() {
  showConfigModal.value = false
}

function openSwitchModelModal() {
  if (savedConfigs.value.length === 0) {
    showToastMessage('暂无已保存的配置', 'warning')
    return
  }

  const firstConfig = savedConfigs.value[0]
  switchForm.provider = currentConfig.value.provider || firstConfig.provider
  switchForm.model = currentConfig.value.model || firstConfig.model || ''
  switchForm.modelId = currentConfig.value.modelId || firstConfig.modelId || ''

  const selectedConfig = savedConfigs.value.find(c => c.provider === switchForm.provider)
  if (selectedConfig) {
    switchForm.model = selectedConfig.model || ''
    switchForm.modelId = selectedConfig.modelId || ''
  }

  showSwitchModal.value = true
}

function closeSwitchModal() {
  showSwitchModal.value = false
}

function handleSwitchProviderChange() {
  const cfg = savedConfigs.value.find(c => c.provider === switchForm.provider)
  if (cfg) {
    switchForm.model = cfg.model || ''
    switchForm.modelId = cfg.modelId || ''
  }
}

async function handleConfigSubmit() {
  submitting.value = true
  try {
    const payload = {
      provider: configForm.provider,
      model: configForm.model,
      modelId: configForm.modelId,
      baseUrl: configForm.baseUrl
    }

    if (configForm.apiKey && configForm.apiKey !== '••••••••') {
      payload.apiKey = configForm.apiKey
    }

    if (modalMode.value === 'save') {
      await apiRequest('/ai/config', { method: 'POST', body: payload })
      showToastMessage('模型配置已更新', 'success')
    } else {
      const result = await apiRequest('/ai/test', { method: 'POST', body: payload })
      const message = pick(result, 'message') || '连接测试已执行'
      showToastMessage(message, pick(result, 'success') ? 'success' : 'warning')
    }

    closeConfigModal()
    await loadModelData()
  } catch (e) {
    showToastMessage(e.message || '操作失败', 'warning')
  } finally {
    submitting.value = false
  }
}

async function handleSwitchSubmit() {
  submitting.value = true
  try {
    await apiRequest('/ai/switch-model', { method: 'POST', body: switchForm })
    showToastMessage('模型已切换', 'success')
    closeSwitchModal()
    await loadModelData()
  } catch (e) {
    showToastMessage(e.message || '切换失败', 'warning')
  } finally {
    submitting.value = false
  }
}

async function handleSwitchToConfig(provider) {
  try {
    const cfg = savedConfigs.value.find(c => c.provider === provider)
    if (!cfg) {
      showToastMessage('未找到配置', 'warning')
      return
    }

    await apiRequest('/ai/switch-model', {
      method: 'POST',
      body: { provider: cfg.provider, model: cfg.model, modelId: cfg.modelId }
    })

    showToastMessage(`已切换到: ${cfg.provider} / ${cfg.model} (${cfg.modelId})`, 'success')
    await loadModelData()
  } catch (e) {
    showToastMessage(e.message || '切换失败', 'warning')
  }
}

function handleClearConfig() {
  showClearConfirm.value = true
}

async function confirmClearConfig() {
  try {
    await apiRequest('/ai/config', { method: 'DELETE' })
    showToastMessage('模型配置已清空', 'success')
    showClearConfirm.value = false
    await loadModelData()
  } catch (e) {
    showToastMessage(e.message || '清空失败', 'warning')
  }
}

onMounted(() => {
  loadModelData()
})
</script>

<style scoped>
.models-page {
  max-width: 1200px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-light);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 14px;
  color: var(--text-muted);
}

.panel {
  background: var(--bg-panel);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.panel-head {
  padding: 15px 18px;
  border-bottom: 1px solid var(--line-dark);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.panel-head h3 {
  color: var(--text-light);
  font-size: 16px;
  margin-bottom: 4px;
}

.panel-head p {
  color: var(--text-muted);
  font-size: 12px;
}

.panel-head-actions {
  display: flex;
  gap: 8px;
}

.panel-body {
  padding: 18px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.meta-card {
  border: 1px solid var(--line-dark);
  border-radius: 11px;
  background: var(--bg-panel-soft);
  padding: 10px 11px;
}

.meta-label {
  font-size: 12px;
  color: var(--text-muted);
}

.meta-value {
  margin-top: 4px;
  font-size: 18px;
  color: var(--text-light);
  font-weight: 700;
  line-height: 1.25;
}

.list-item {
  border: 1px solid var(--line-dark);
  border-radius: 11px;
  padding: 10px 12px;
  background: var(--bg-panel-soft);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.list-item:last-child {
  margin-bottom: 0;
}

.list-item.active {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.item-title {
  color: var(--text-light);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.3;
}

.item-meta {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.3;
}

.catalog-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.catalog-item {
  padding: 12px;
  border: 1px solid var(--line-dark);
  border-radius: 11px;
  background: var(--bg-panel-soft);
}

.catalog-provider {
  font-weight: 600;
  color: var(--text-light);
  font-size: 14px;
  margin-bottom: 8px;
}

.catalog-models {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.catalog-baseurl {
  font-size: 12px;
  color: var(--text-muted);
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 46px;
  height: 22px;
  border-radius: 999px;
  padding: 0 8px;
  font-size: 10px;
  letter-spacing: 0.15px;
  font-weight: 600;
  border: 1px solid transparent;
}

.badge.success {
  background: rgba(63, 193, 130, 0.16);
  color: #82e8b0;
  border-color: rgba(94, 211, 145, 0.44);
}

.badge.info {
  background: rgba(125, 157, 211, 0.14);
  color: #a9c2ea;
  border-color: rgba(125, 157, 211, 0.45);
}

.loading-state,
.empty {
  text-align: center;
  padding: 20px;
  color: var(--text-muted);
}

.empty {
  border: 1px dashed var(--line-dark);
  border-radius: 11px;
}

.btn {
  border-radius: 11px;
  border: none;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn.small {
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
}

.btn-primary {
  background: linear-gradient(135deg, #2ac9a9, #277dc1);
  color: #eafffb;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 30px rgba(19, 95, 156, 0.42);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--bg-panel-soft);
  border: 1px solid var(--line-dark);
  color: var(--text-light);
}

.btn-secondary:hover {
  background: var(--bg-card-soft);
}

.btn-danger {
  background: var(--bad);
  color: white;
}

.btn-danger-soft {
  background: rgba(240, 109, 122, 0.16);
  border: 1px solid rgba(240, 109, 122, 0.46);
  color: #ff9ca7;
}

.btn-danger-soft:hover {
  background: rgba(240, 109, 122, 0.24);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(6, 15, 28, 0.56);
  backdrop-filter: blur(3px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-card {
  width: min(740px, 100%);
  max-height: 90vh;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid var(--line-dark);
  background: var(--bg-panel);
  box-shadow: 0 28px 52px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}

.modal-head {
  padding: 16px 18px;
  border-bottom: 1px solid var(--line-dark);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  background: var(--bg-panel-soft);
}

.modal-head h3 {
  margin: 0;
  color: var(--text-light);
  font-size: 18px;
}

.modal-head p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.modal-close {
  width: 30px;
  height: 30px;
  border: 1px solid var(--line-dark);
  border-radius: 9px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.modal-close:hover {
  color: var(--text-light);
  border-color: var(--text-muted);
}

.modal-body {
  padding: 16px 18px;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.modal-body .field.full {
  grid-column: span 2;
}

.modal-foot {
  border-top: 1px solid var(--line-dark);
  padding: 12px 18px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: var(--bg-panel-soft);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 600;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid var(--line-dark);
  border-radius: 10px;
  background: var(--bg-panel-soft);
  color: var(--text-light);
  font-size: 13px;
  padding: 9px 10px;
  outline: none;
  transition: border-color 0.2s ease;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: var(--brand);
}

.field input::placeholder,
.field textarea::placeholder {
  color: var(--text-muted);
}

.confirm-modal {
  max-width: 400px;
}

.confirm-modal .modal-body {
  grid-template-columns: 1fr;
}

.confirm-modal .modal-body p {
  color: var(--text-light);
  font-size: 14px;
}

.toast {
  position: fixed;
  right: 20px;
  bottom: 20px;
  padding: 10px 12px;
  border-radius: 11px;
  font-size: 12px;
  z-index: 200;
  animation: toastIn 0.22s ease;
}

.toast.success {
  background: rgba(63, 193, 130, 0.16);
  border: 1px solid rgba(94, 211, 145, 0.44);
  color: #82e8b0;
}

.toast.warning {
  background: rgba(245, 173, 75, 0.16);
  border: 1px solid rgba(245, 173, 75, 0.46);
  color: #ffc06a;
}

.toast.danger {
  background: rgba(240, 109, 122, 0.16);
  border: 1px solid rgba(240, 109, 122, 0.46);
  color: #ff9ca7;
}

.toast.info {
  background: rgba(125, 157, 211, 0.14);
  border: 1px solid rgba(125, 157, 211, 0.45);
  color: #a9c2ea;
}

@keyframes toastIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
