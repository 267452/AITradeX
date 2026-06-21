<template>
  <div class="panel panel-tier-1 module-console-layout module-shell-card">
    <div class="module-main-column">
      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>Skills</h3>
            <p>技能名称、描述、状态与绑定的工具</p>
          </div>
          <div style="display:flex;gap:8px;align-items:center;">
            <button class="btn btn-primary small" @click="openUploadModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin-right:4px;">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="17 8 12 3 7 8"></polyline>
                <line x1="12" y1="3" x2="12" y2="15"></line>
              </svg>
              上传技能
            </button>
          </div>
        </div>
        <div class="panel-body">
          <div v-if="loading" class="loading-state">加载中...</div>
          <div v-else-if="skills.length === 0" class="empty-state">暂无 Skill。</div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Skill</th>
                <th>描述</th>
                <th>状态</th>
                <th>工具</th>
                <th>最近运行</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="skill in skills" :key="skill.id">
                <td>
                  <span class="value-strong">{{ skill.icon || '⚡' }} {{ skill.name || '--' }}</span>
                  <br><span class="item-meta">{{ skill.category || '--' }} · 运行 {{ formatNumber(skill.run_count || 0) }} 次</span>
                </td>
                <td>{{ skill.description || '--' }}</td>
                <td>
                  <span class="badge" :class="toneClass(normalizeStatus(skill.status))">
                    {{ (skill.status || '--').toUpperCase() }}
                  </span>
                </td>
                <td>
                  <template v-if="getTools(skill).length">
                    <span v-for="(tool, idx) in getTools(skill).slice(0, 3)" :key="idx" class="tag">{{ tool }}</span>
                    <span v-if="getTools(skill).length > 3">...</span>
                  </template>
                  <span v-else>-</span>
                </td>
                <td>{{ formatDateTime(skill.last_run_at || skill.lastRunAt) }}</td>
                <td>
                  <button class="mini-btn" @click="openEditModal(skill)">编辑</button>
                  <button class="mini-btn danger" @click="handleDelete(skill.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </div>

    <aside class="module-side-stack">
      <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-action">
        <summary>
          <div class="agent-collab-card-copy">
            <div class="agent-collab-card-kicker">MODULE ACTIONS</div>
            <div class="agent-collab-card-title-row">
              <h4>模块操作</h4>
              <span class="agent-collab-card-badge state-idle">ACTION</span>
            </div>
            <p class="agent-collab-card-peek">新增 Skill 和刷新入口统一收纳到右侧，主区保留技能清单。</p>
          </div>
        </summary>
        <div class="overview-side-card-body module-side-card-body">
          <div class="module-action-stack">
            <button class="btn btn-primary small" @click="openAddModal">新增 Skill</button>
            <button class="btn btn-secondary small" @click="loadData">刷新</button>
          </div>
        </div>
      </details>
    </aside>
  </div>

  <!-- Skill 表单弹窗 -->
  <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
    <div class="modal-card">
      <div class="modal-head">
        <div>
          <h3>{{ isEdit ? '编辑 Skill' : '新增 Skill' }}</h3>
          <p>配置技能名称、提示词模板与可用工具。</p>
        </div>
        <button class="modal-close" @click="closeModal">×</button>
      </div>
      <form class="modal-form" @submit.prevent="handleSubmit">
        <div class="modal-body">
          <div class="field">
            <label>技能名称 <span style="color:red">*</span></label>
            <input v-model="form.name" type="text" required placeholder="例如：技术分析">
          </div>
          <div class="field">
            <label>描述</label>
            <input v-model="form.description" type="text" placeholder="简要描述技能功能">
          </div>
          <div class="field">
            <label>图标</label>
            <input v-model="form.icon" type="text" placeholder="emoji 或简短标识，默认 ⚡">
          </div>
          <div class="field">
            <label>分类</label>
            <input v-model="form.category" type="text" placeholder="分类标签">
          </div>
          <div class="field">
            <label>状态</label>
            <select v-model="form.status">
              <option value="enabled">enabled</option>
              <option value="warning">warning</option>
              <option value="gray">gray</option>
              <option value="disabled">disabled</option>
            </select>
          </div>
          <div class="field">
            <label>提示词模板</label>
            <textarea v-model="form.promptTemplate" rows="4" placeholder="输入提示词模板..."></textarea>
          </div>
          <div class="field">
            <label>Prompt 文档 (prompt.md)</label>
            <textarea v-model="form.promptContent" rows="4" placeholder="输入 Prompt 文档内容..."></textarea>
          </div>
          <div class="field">
            <label>Python 脚本 (script.py)</label>
            <textarea v-model="form.scriptContent" rows="4" placeholder="输入 Python 脚本内容..."></textarea>
          </div>
          <div class="field">
            <label>可用工具 (逗号分隔)</label>
            <input v-model="form.enabledTools" type="text" placeholder="例如：tool1,tool2,tool3">
          </div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn btn-secondary" @click="closeModal">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            {{ submitting ? '保存中...' : (isEdit ? '保存修改' : '创建 Skill') }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- 上传技能弹窗 -->
  <div v-if="showUploadModal" class="modal-overlay" @click.self="closeUploadModal">
    <div class="modal-card">
      <div class="modal-head">
        <div>
          <h3>上传技能包</h3>
        </div>
        <button class="modal-close" @click="closeUploadModal">×</button>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>选择技能压缩包 (.zip)</label>
          <input type="file" ref="fileInput" accept=".zip">
          <div class="field-hint">上传包含完整技能的压缩包（必须包含 config.json，可选 prompt.md、script.py）</div>
          <div v-if="selectedFileName" class="file-selected">{{ selectedFileName }}</div>
        </div>
      </div>
      <div class="modal-foot">
        <button type="button" class="btn btn-secondary" @click="closeUploadModal">取消</button>
        <button type="button" class="btn btn-primary" @click="handleUpload" :disabled="uploading">
          {{ uploading ? '上传中...' : '上传并保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { apiRequest } from '../api/index.js'

const skills = ref([])
const loading = ref(false)
const showModal = ref(false)
const showUploadModal = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const fileInput = ref(null)
const selectedFileName = ref('')

const form = reactive({
  name: '',
  description: '',
  icon: '⚡',
  category: 'general',
  status: 'enabled',
  promptTemplate: '',
  promptContent: '',
  scriptContent: '',
  enabledTools: ''
})

const statusOptions = ['enabled', 'warning', 'gray', 'disabled']

function normalizeStatus(status) {
  if (!status) return 'secondary'
  const s = String(status).toLowerCase()
  if (s === 'enabled' || s === 'online' || s === 'active') return 'success'
  if (s === 'warning') return 'warning'
  if (s === 'disabled' || s === 'offline') return 'secondary'
  return 'secondary'
}

function toneClass(tone) {
  const map = {
    success: 'success',
    warning: 'warning',
    secondary: 'secondary',
    danger: 'danger'
  }
  return map[tone] || 'secondary'
}

function formatNumber(num) {
  if (!num) return '0'
  return Number(num).toLocaleString()
}

function formatDateTime(dt) {
  if (!dt) return '--'
  try {
    const d = new Date(dt)
    if (isNaN(d.getTime())) return '--'
    return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch {
    return '--'
  }
}

function getTools(skill) {
  const tools = skill.enabled_tools || skill.enabledTools || ''
  if (!tools) return []
  return tools.split(',').map(t => t.trim()).filter(Boolean)
}

async function loadData() {
  loading.value = true
  try {
    const data = await apiRequest('/admin/skills')
    skills.value = data || []
  } catch (err) {
    showToast(err.message || '加载失败', 'warning')
    skills.value = []
  } finally {
    loading.value = false
  }
}

function openAddModal() {
  isEdit.value = false
  resetForm()
  showModal.value = true
}

async function openEditModal(skill) {
  isEdit.value = true
  resetForm()
  try {
    const detail = await apiRequest(`/admin/skills/${skill.id}/detail`)
    if (detail) {
      Object.assign(form, {
        name: skill.name || '',
        description: skill.description || '',
        icon: skill.icon || '⚡',
        category: skill.category || 'general',
        status: skill.status || 'enabled',
        promptTemplate: detail.prompt_template || detail.promptTemplate || '',
        promptContent: detail.promptContent || '',
        scriptContent: detail.scriptContent || '',
        enabledTools: skill.enabled_tools || skill.enabledTools || ''
      })
    } else {
      Object.assign(form, {
        name: skill.name || '',
        description: skill.description || '',
        icon: skill.icon || '⚡',
        category: skill.category || 'general',
        status: skill.status || 'enabled',
        promptTemplate: '',
        promptContent: '',
        scriptContent: '',
        enabledTools: skill.enabled_tools || skill.enabledTools || ''
      })
    }
  } catch {
    Object.assign(form, {
      name: skill.name || '',
      description: skill.description || '',
      icon: skill.icon || '⚡',
      category: skill.category || 'general',
      status: skill.status || 'enabled',
      promptTemplate: '',
      promptContent: '',
      scriptContent: '',
      enabledTools: skill.enabled_tools || skill.enabledTools || ''
    })
  }
  showModal.value = true
}

function resetForm() {
  Object.assign(form, {
    name: '',
    description: '',
    icon: '⚡',
    category: 'general',
    status: 'enabled',
    promptTemplate: '',
    promptContent: '',
    scriptContent: '',
    enabledTools: ''
  })
}

function closeModal() {
  showModal.value = false
  resetForm()
}

async function handleSubmit() {
  if (!form.name) {
    showToast('请输入技能名称', 'warning')
    return
  }
  submitting.value = true
  try {
    const { promptContent, scriptContent, ...skillPayload } = form
    if (isEdit.value) {
      await apiRequest(`/admin/skills/${skills.value.find(s => s.name === form.name)?.id}`, {
        method: 'PUT',
        body: skillPayload
      })
    } else {
      await apiRequest('/admin/skills', {
        method: 'POST',
        body: skillPayload
      })
    }
    showToast(isEdit.value ? 'Skill 已更新' : 'Skill 已创建', 'success')
    closeModal()
    await loadData()
  } catch (err) {
    showToast(err.message || '保存失败', 'warning')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  if (!confirm('确认删除该 Skill？')) return
  try {
    await apiRequest(`/admin/skills/${id}`, { method: 'DELETE' })
    showToast('删除成功', 'success')
    await loadData()
  } catch (err) {
    showToast(err.message || '删除失败', 'danger')
  }
}

function openUploadModal() {
  selectedFileName.value = ''
  if (fileInput.value) fileInput.value.value = ''
  showUploadModal.value = true
}

function closeUploadModal() {
  showUploadModal.value = false
}

function handleFileChange(e) {
  const files = e.target.files
  if (files && files.length > 0) {
    selectedFileName.value = `已选择: ${files[0].name} (${(files[0].size / 1024).toFixed(1)} KB)`
  } else {
    selectedFileName.value = ''
  }
}

async function handleUpload() {
  const files = fileInput.value?.files
  if (!files || files.length === 0) {
    showToast('请选择技能压缩包', 'warning')
    return
  }

  uploading.value = true
  try {
    const token = localStorage.getItem('token') || ''
    const formData = new FormData()
    formData.append('skillPackage', files[0])

    const response = await fetch('/api/admin/skills/upload', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData
    })

    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.msg || '技能上传失败')
    }

    const result = await response.json()
    showToast(`技能创建成功: ${result.data?.name || '未知'}`, 'success')
    closeUploadModal()
    await loadData()
  } catch (err) {
    showToast(`创建失败: ${err.message}`, 'warning')
  } finally {
    uploading.value = false
  }
}

function showToast(message, type = 'info') {
  const toast = document.createElement('div')
  toast.className = `toast toast-${type}`
  toast.textContent = message
  document.body.appendChild(toast)
  setTimeout(() => toast.remove(), 3000)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.panel {
  background: var(--bg-panel);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-panel);
}

.panel-head {
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--line-soft);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-head h3 {
  color: var(--text-strong);
  font-size: 16px;
  margin: 0;
}

.panel-head p {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 2px;
}

.panel-body {
  padding: 16px 20px;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  padding: 10px 12px;
  color: var(--text-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--line-soft);
  font-size: 11px;
  letter-spacing: 0.5px;
}

.data-table td {
  padding: 12px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-main);
  vertical-align: top;
}

.data-table tr:last-child td {
  border-bottom: none;
}

.value-strong {
  font-weight: 600;
  color: var(--text-strong);
}

.item-meta {
  font-size: 11px;
  color: var(--text-muted);
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.badge.success {
  background: rgba(35, 178, 111, 0.15);
  color: var(--good);
}

.badge.warning {
  background: rgba(235, 159, 49, 0.15);
  color: var(--warn);
}

.badge.secondary {
  background: rgba(95, 119, 147, 0.12);
  color: var(--text-muted);
}

.badge.danger {
  background: rgba(223, 90, 106, 0.12);
  color: var(--bad);
}

.tag {
  display: inline-block;
  padding: 2px 6px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  border-radius: 4px;
  font-size: 11px;
  margin-right: 4px;
}

.mini-btn {
  padding: 4px 10px;
  border: 1px solid var(--line-soft);
  background: white;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  color: var(--text-main);
}

.mini-btn:hover {
  background: var(--bg-card-soft);
}

.mini-btn.danger {
  color: var(--bad);
  border-color: rgba(223, 90, 106, 0.3);
}

.mini-btn.danger:hover {
  background: rgba(223, 90, 106, 0.08);
}

.btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  display: inline-flex;
  align-items: center;
}

.btn-primary {
  background: var(--brand);
  color: white;
}

.btn-primary:hover {
  background: var(--brand-strong);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: white;
  color: var(--text-main);
  border: 1px solid var(--line-soft);
}

.btn-secondary:hover {
  background: var(--bg-card-soft);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-card {
  background: white;
  border-radius: var(--radius-md);
  max-width: 560px;
  width: 90%;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-head {
  padding: 16px 20px;
  border-bottom: 1px solid var(--line-soft);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.modal-head h3 {
  color: var(--text-strong);
  font-size: 16px;
  margin: 0;
}

.modal-head p {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 4px;
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-muted);
  padding: 0;
  line-height: 1;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 6px;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  font-size: 13px;
  background: white;
  color: var(--text-strong);
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand);
}

.field textarea {
  resize: vertical;
  min-height: 80px;
}

.field-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 6px;
}

.file-selected {
  font-size: 12px;
  color: var(--text-primary);
  margin-top: 8px;
}

.modal-foot {
  padding: 16px 20px;
  border-top: 1px solid var(--line-soft);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.module-side-stack {
  width: 280px;
  flex-shrink: 0;
}

.agent-collab-card {
  background: var(--bg-card-soft);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}

.agent-collab-card summary {
  padding: 14px 16px;
  cursor: pointer;
  list-style: none;
}

.agent-collab-card summary::-webkit-details-marker {
  display: none;
}

.agent-collab-card-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-collab-card-kicker {
  font-size: 10px;
  letter-spacing: 0.7px;
  color: var(--text-muted);
  font-weight: 600;
}

.agent-collab-card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.agent-collab-card-title-row h4 {
  font-size: 13px;
  color: var(--text-strong);
  margin: 0;
}

.agent-collab-card-badge {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(95, 119, 147, 0.12);
  color: var(--text-muted);
  font-weight: 600;
}

.agent-collab-card-peek {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.overview-side-card-body {
  padding: 0 16px 14px;
}

.module-action-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.module-console-layout {
  display: flex;
  gap: 20px;
  padding: 20px;
}

.module-main-column {
  flex: 1;
  min-width: 0;
}

.toast {
  position: fixed;
  bottom: 20px;
  right: 20px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 2000;
  animation: slideIn 0.3s ease;
}

.toast-success {
  background: var(--good);
  color: white;
}

.toast-warning {
  background: var(--warn);
  color: white;
}

.toast-danger {
  background: var(--bad);
  color: white;
}

.toast-info {
  background: var(--info);
  color: white;
}

@keyframes slideIn {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
</style>
