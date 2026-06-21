<template>
  <div class="agents-page">
    <div class="page-header">
      <div class="page-header-info">
        <h1 class="page-title">智能体管理</h1>
        <p class="page-desc">配置智能体绑定的技能、MCP 工具和知识库。</p>
      </div>
      <button class="btn btn-primary" @click="openAddModal">
        <span>+</span> 新增智能体
      </button>
    </div>

    <div class="panel">
      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="agents.length === 0" class="empty-state">
        暂无智能体，点击右上角「新增智能体」创建。
      </div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>Agent</th>
            <th>描述</th>
            <th>状态</th>
            <th>最近运行</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="agent in agents" :key="agent.id">
            <td>
              <div class="agent-name-cell">
                <span class="agent-icon">{{ agent.icon || '🤖' }}</span>
                <div class="agent-info">
                  <span class="value-strong">{{ agent.name }}</span>
                  <span class="item-meta">
                    {{ agent.model_name || agent.modelName || '未指定模型' }} · 运行 {{ formatNumber(agent.run_count || 0) }} 次
                  </span>
                </div>
              </div>
            </td>
            <td>{{ agent.description || '--' }}</td>
            <td>
              <span :class="['badge', toneClass(normalizeToneFromStatus(agent.status))]">
                {{ (agent.status || '--').toUpperCase() }}
              </span>
            </td>
            <td>{{ formatDateTime(agent.last_run_at || agent.lastRunAt) }}</td>
            <td>
              <button class="mini-btn" @click="openEditModal(agent)">编辑</button>
              <button class="mini-btn danger" @click="handleDelete(agent)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal panel">
        <div class="modal-head">
          <h3>{{ isEdit ? '编辑智能体' : '新增智能体' }}</h3>
          <p>配置智能体的基本信息、提示词以及绑定的技能、MCP 工具和知识库。</p>
        </div>
        <div class="modal-body">
          <div class="field">
            <label><span style="color:red;margin-right:4px;">*</span>名称</label>
            <input v-model="form.name" type="text" placeholder="例如：智能投顾助手" />
          </div>
          <div class="field">
            <label>描述</label>
            <input v-model="form.description" type="text" placeholder="简要说明 Agent 的用途" />
          </div>
          <div class="field-row">
            <div class="field">
              <label>图标</label>
              <input v-model="form.icon" type="text" placeholder="emoji 或简短标识" />
            </div>
            <div class="field">
              <label>状态</label>
              <select v-model="form.status">
                <option value="enabled">enabled</option>
                <option value="disabled">disabled</option>
              </select>
            </div>
          </div>
          <div class="field">
            <label>模型名称</label>
            <input v-model="form.modelName" type="text" placeholder="例如：gpt-4.1" />
          </div>
          <div class="field">
            <label>系统提示词 (system_prompt)</label>
            <textarea v-model="form.systemPrompt" rows="4" placeholder="描述 Agent 的身份和行为。"></textarea>
          </div>
          <div class="field-row">
            <div class="field">
              <label>Temperature</label>
              <input v-model.number="form.temperature" type="number" step="0.1" min="0" max="2" />
            </div>
            <div class="field">
              <label>Max Tokens</label>
              <input v-model.number="form.maxTokens" type="number" step="1" min="0" />
            </div>
            <div class="field">
              <label>Tool Call Mode</label>
              <select v-model="form.toolCallMode">
                <option value="auto">auto</option>
                <option value="required">required</option>
                <option value="none">none</option>
              </select>
            </div>
          </div>

          <div class="field">
            <label>绑定技能 (Skills)</label>
            <div class="multi-select-wrap">
              <label v-for="skill in skillsList" :key="skill.id" class="multi-select-item">
                <input type="checkbox" :value="skill.id" v-model="form.skillIds" />
                <span>{{ skill.name }}</span>
              </label>
            </div>
            <div class="field-hint">选中的 skill 将可被该 Agent 调用。</div>
          </div>

          <div class="field">
            <label>绑定 MCP 工具</label>
            <div class="multi-select-wrap">
              <label v-for="mcp in mcpList" :key="mcp.id" class="multi-select-item">
                <input type="checkbox" :value="mcp.id" v-model="form.mcpToolIds" />
                <span>{{ mcp.name }}</span>
              </label>
            </div>
            <div class="field-hint">选中的 MCP 工具将可被该 Agent 调用。</div>
          </div>

          <div class="field">
            <label>绑定知识库 (Knowledge Bases)</label>
            <div class="kb-select-wrap">
              <div v-for="kb in kbList" :key="kb.id" class="kb-item">
                <label class="kb-checkbox">
                  <input type="checkbox" :value="kb.id" v-model="form.kbConfig" />
                  <span>{{ kb.name }}</span>
                </label>
                <div v-if="form.kbConfig.includes(kb.id)" class="kb-config">
                  <div class="kb-config-field">
                    <label>Top K</label>
                    <input type="number" min="1" max="50" :value="getKbConfig(kb.id).topK" @input="updateKbConfig(kb.id, 'topK', $event)" />
                  </div>
                  <div class="kb-config-field">
                    <label>Score 阈值</label>
                    <input type="number" step="0.01" min="0" max="1" :value="getKbConfig(kb.id).scoreThreshold" @input="updateKbConfig(kb.id, 'scoreThreshold', $event)" />
                  </div>
                </div>
              </div>
            </div>
            <div class="field-hint">选中的知识库将用于 RAG 检索，可自定义 top_k 和 score 阈值。</div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="handleSubmit">{{ isEdit ? '保存修改' : '创建智能体' }}</button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
      <div class="modal panel confirm-modal">
        <div class="modal-head">
          <h3>确认删除</h3>
        </div>
        <div class="modal-body">
          <p>确认删除该智能体？</p>
        </div>
        <div class="modal-foot">
          <button class="btn btn-secondary" @click="showDeleteConfirm = false">取消</button>
          <button class="btn btn-danger" @click="confirmDelete">确认删除</button>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <div v-if="toast.show" :class="['toast', toast.type]">{{ toast.message }}</div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { apiRequest } from '@/api'

const agents = ref([])
const loading = ref(false)
const showModal = ref(false)
const showDeleteConfirm = ref(false)
const isEdit = ref(false)
const currentAgentId = ref(null)
const deleteTarget = ref(null)

const skillsList = ref([])
const mcpList = ref([])
const kbList = ref([])

const form = reactive({
  name: '',
  description: '',
  icon: '🤖',
  status: 'enabled',
  modelName: '',
  systemPrompt: '',
  temperature: 0.7,
  maxTokens: 2048,
  toolCallMode: 'auto',
  skillIds: [],
  mcpToolIds: [],
  kbConfig: []
})

const toast = reactive({
  show: false,
  message: '',
  type: 'info'
})

function showToast(message, type = 'info') {
  toast.message = message
  toast.type = type
  toast.show = true
  setTimeout(() => {
    toast.show = false
  }, 3000)
}

function formatNumber(num) {
  return Number(num || 0).toLocaleString()
}

function formatDateTime(dt) {
  if (!dt) return '--'
  const date = new Date(dt)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function normalizeToneFromStatus(status) {
  const s = String(status || '').toLowerCase()
  if (s === 'enabled' || s === 'active' || s === 'online' || s === 'published' || s === 'completed') return 'success'
  if (s === 'disabled' || s === 'offline' || s === 'blocked') return 'secondary'
  if (s === 'warning' || s === 'pending' || s === 'draft') return 'warning'
  if (s === 'danger' || s === 'error') return 'danger'
  return 'info'
}

function toneClass(tone) {
  const map = {
    success: 'success',
    warning: 'warning',
    danger: 'danger',
    secondary: 'secondary',
    info: 'info'
  }
  return map[tone] || 'info'
}

async function loadAgents() {
  loading.value = true
  try {
    const data = await apiRequest('/admin/agents')
    agents.value = Array.isArray(data) ? data : []
  } catch (e) {
    showToast(e.message || '加载失败', 'warning')
    agents.value = []
  } finally {
    loading.value = false
  }
}

async function loadDropdownData() {
  try {
    const [skills, mcp, kb] = await Promise.all([
      apiRequest('/admin/skills'),
      apiRequest('/admin/mcp/tools'),
      apiRequest('/admin/mcp/knowledge/bases').catch(() => [])
    ])
    skillsList.value = Array.isArray(skills) ? skills : []
    mcpList.value = Array.isArray(mcp) ? mcp : []
    if (Array.isArray(kb)) {
      kbList.value = kb
    } else if (kb && Array.isArray(kb.knowledgeBases)) {
      kbList.value = kb.knowledgeBases
    } else {
      kbList.value = []
    }
  } catch (e) {
    console.error('加载下拉选项失败:', e)
    skillsList.value = []
    mcpList.value = []
    kbList.value = []
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.icon = '🤖'
  form.status = 'enabled'
  form.modelName = ''
  form.systemPrompt = ''
  form.temperature = 0.7
  form.maxTokens = 2048
  form.toolCallMode = 'auto'
  form.skillIds = []
  form.mcpToolIds = []
  form.kbConfig = []
}

function openAddModal() {
  resetForm()
  isEdit.value = false
  showModal.value = true
}

async function openEditModal(agent) {
  try {
    const detail = await apiRequest(`/admin/agents/${agent.id}/detail`)
    const fullData = { ...agent, ...detail }
    
    resetForm()
    form.name = fullData.name || ''
    form.description = fullData.description || ''
    form.icon = fullData.icon || '🤖'
    form.status = fullData.status || 'enabled'
    form.modelName = fullData.model_name || fullData.modelName || ''
    form.systemPrompt = fullData.system_prompt || fullData.systemPrompt || ''
    form.temperature = fullData.temperature ?? 0.7
    form.maxTokens = fullData.max_tokens ?? fullData.maxTokens ?? 2048
    form.toolCallMode = fullData.tool_call_mode || fullData.toolCallMode || 'auto'
    form.skillIds = fullData.skillIds || fullData.skill_ids || []
    form.mcpToolIds = fullData.mcpToolIds || fullData.mcp_tool_ids || []
    
    const existingKbs = fullData.knowledgeBases || fullData.knowledge_bases || []
    form.kbConfig = existingKbs.map(kb => kb.knowledgeBaseId || kb.knowledge_base_id)
    
    currentAgentId.value = agent.id
    isEdit.value = true
    showModal.value = true
  } catch (e) {
    showToast('加载详情失败', 'warning')
  }
}

function closeModal() {
  showModal.value = false
}

function getKbConfig(kbId) {
  const defaultConfig = { topK: 5, scoreThreshold: 0.7 }
  const existing = form.kbConfig.find(kb => kb.knowledgeBaseId === kbId)
  return existing || defaultConfig
}

function updateKbConfig(kbId, field, event) {
  let kbEntry = form.kbConfig.find(kb => kb.knowledgeBaseId === kbId)
  if (!kbEntry) {
    kbEntry = { knowledgeBaseId: kbId, topK: 5, scoreThreshold: 0.7, name: '' }
    form.kbConfig.push(kbEntry)
  }
  kbEntry[field] = parseFloat(event.target.value) || 0
}

function collectKbConfig() {
  return form.kbConfig.map(kbId => {
    const kb = kbList.value.find(k => k.id === kbId)
    const existing = form.kbConfig.find(kb => kb.knowledgeBaseId === kbId)
    return {
      knowledgeBaseId: kbId,
      topK: existing?.topK || 5,
      scoreThreshold: existing?.scoreThreshold || 0.7,
      name: kb?.name || ''
    }
  })
}

async function handleSubmit() {
  if (!form.name.trim()) {
    showToast('请填写智能体名称', 'warning')
    return
  }

  const payload = {
    name: form.name.trim(),
    description: form.description.trim(),
    icon: form.icon.trim() || '🤖',
    status: form.status,
    modelName: form.modelName.trim(),
    systemPrompt: form.systemPrompt,
    temperature: form.temperature || 0.7,
    maxTokens: form.maxTokens || 2048,
    toolCallMode: form.toolCallMode,
    skillIds: form.skillIds,
    mcpToolIds: form.mcpToolIds,
    knowledgeBases: collectKbConfig()
  }

  try {
    if (isEdit.value) {
      await apiRequest(`/admin/agents/${currentAgentId.value}`, { method: 'PUT', body: payload })
      showToast('智能体 已更新', 'success')
    } else {
      await apiRequest('/admin/agents', { method: 'POST', body: payload })
      showToast('智能体 已创建', 'success')
    }
    closeModal()
    await loadAgents()
  } catch (e) {
    showToast(e.message || '保存失败', 'warning')
  }
}

function handleDelete(agent) {
  deleteTarget.value = agent
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await apiRequest(`/admin/agents/${deleteTarget.value.id}`, { method: 'DELETE' })
    showToast('智能体 已删除', 'success')
    showDeleteConfirm.value = false
    deleteTarget.value = null
    await loadAgents()
  } catch (e) {
    showToast(e.message || '删除失败', 'danger')
  }
}

onMounted(() => {
  loadAgents()
  loadDropdownData()
})
</script>

<style scoped>
.agents-page {
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
  padding: 20px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  text-align: left;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-dark);
}

.data-table th {
  font-weight: 600;
  color: var(--text-muted);
  font-size: 12px;
  text-transform: uppercase;
}

.data-table tbody tr:hover {
  background: var(--bg-panel-soft);
}

.agent-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-icon {
  font-size: 24px;
}

.agent-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.value-strong {
  font-weight: 600;
  color: var(--text-light);
}

.item-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.badge.success { background: var(--good); color: white; }
.badge.warning { background: var(--warn); color: white; }
.badge.danger { background: var(--bad); color: white; }
.badge.info { background: var(--info); color: white; }
.badge.secondary { background: var(--text-muted); color: white; }

.mini-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  background: var(--brand);
  color: white;
  margin-right: 8px;
}

.mini-btn.danger {
  background: var(--bad);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  max-width: 780px;
  width: 90%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-head {
  padding: 20px 24px;
  border-bottom: 1px solid var(--line-dark);
}

.modal-head h3 {
  font-size: 18px;
  color: var(--text-light);
  margin-bottom: 4px;
}

.modal-head p {
  font-size: 13px;
  color: var(--text-muted);
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.modal-foot {
  padding: 16px 24px;
  border-top: 1px solid var(--line-dark);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-light);
  margin-bottom: 6px;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--line-dark);
  border-radius: var(--radius-sm);
  background: var(--bg-panel-soft);
  color: var(--text-light);
  font-size: 14px;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand);
}

.field textarea {
  resize: vertical;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.field-row:has(> .field:nth-child(3)) {
  grid-template-columns: 1fr 1fr 1fr;
}

.field-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.multi-select-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-sm);
}

.multi-select-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px;
}

.multi-select-item:hover {
  background: var(--bg-card-soft);
}

.multi-select-item input[type="checkbox"] {
  width: auto;
}

.kb-select-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-sm);
}

.kb-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.kb-checkbox input[type="checkbox"] {
  width: auto;
}

.kb-config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding-left: 24px;
  margin-top: 8px;
}

.kb-config-field label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.kb-config-field input {
  width: 100%;
  padding: 6px 8px;
  font-size: 13px;
}

.btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary {
  background: var(--brand);
  color: white;
}

.btn-primary:hover {
  background: var(--brand-strong);
}

.btn-secondary {
  background: var(--bg-panel-soft);
  color: var(--text-light);
  border: 1px solid var(--line-dark);
}

.btn-secondary:hover {
  background: var(--bg-card-soft);
}

.btn-danger {
  background: var(--bad);
  color: white;
}

.confirm-modal {
  max-width: 400px;
}

.confirm-modal .modal-body {
  padding: 24px;
}

.confirm-modal .modal-body p {
  color: var(--text-light);
  font-size: 14px;
}

.toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 12px 20px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  z-index: 2000;
  animation: slideIn 0.3s ease;
}

.toast.success { background: var(--good); color: white; }
.toast.warning { background: var(--warn); color: white; }
.toast.danger { background: var(--bad); color: white; }
.toast.info { background: var(--info); color: white; }

@keyframes slideIn {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
</style>
