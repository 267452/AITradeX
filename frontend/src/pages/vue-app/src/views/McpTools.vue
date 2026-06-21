<template>
  <div class="panel panel-tier-1 module-console-layout module-shell-card">
    <div class="module-main-column">
      <article class="panel">
        <div class="panel-head">
          <div>
            <h3>MCP 工具</h3>
            <p>传输方式、端点、分类和测试状态</p>
          </div>
        </div>
        <div class="panel-body">
          <div v-if="loading" class="loading-state">加载中...</div>
          <div v-else-if="mcpTools.length === 0" class="empty-state">暂无 MCP 工具。</div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>工具</th>
                <th>传输</th>
                <th>Endpoint</th>
                <th>状态</th>
                <th>最近测试</th>
                <th>备注</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="tool in mcpTools" :key="tool.id">
                <td>
                  <span class="value-strong">{{ tool.name || '--' }}</span>
                  <br><span class="item-meta">{{ tool.category || '--' }}</span>
                </td>
                <td>{{ tool.transport_type || tool.transportType || '--' }}</td>
                <td class="endpoint-cell">{{ tool.endpoint || '--' }}</td>
                <td>
                  <span class="badge" :class="toneClass(normalizeStatus(tool.status))">
                    {{ (tool.status || '--').toUpperCase() }}
                  </span>
                </td>
                <td>{{ formatDateTime(tool.last_test_at || tool.lastTestAt) }}</td>
                <td>{{ tool.note || '--' }}</td>
                <td>
                  <button class="mini-btn" @click="openEditModal(tool)">编辑</button>
                  <button class="mini-btn danger" @click="handleDelete(tool.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </article>
    </div>

    <aside class="module-side-stack">
      <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-library">
        <summary>
          <div class="agent-collab-card-copy">
            <div class="agent-collab-card-kicker">MCP MARKETS</div>
            <div class="agent-collab-card-title-row">
              <h4>MCP 市场</h4>
              <span class="agent-collab-card-badge state-idle">LIST</span>
            </div>
            <p class="agent-collab-card-peek">默认收起，展开查看市场包数量、可见性和刷新结果。</p>
          </div>
        </summary>
        <div class="overview-side-card-body module-side-card-body">
          <div v-if="mcpMarkets.length === 0" class="empty-small">暂无 MCP 市场。</div>
          <table v-else class="data-table small">
            <thead>
              <tr>
                <th>市场</th>
                <th>包</th>
                <th>可见性</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="market in mcpMarkets" :key="market.id">
                <td>{{ market.name || '--' }}</td>
                <td>{{ formatNumber(market.package_count || market.packageCount || 0) }}</td>
                <td>{{ market.visibility || '--' }}</td>
                <td>
                  <span class="badge" :class="toneClass(normalizeStatus(market.status))">
                    {{ (market.status || '--').toUpperCase() }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-action">
        <summary>
          <div class="agent-collab-card-copy">
            <div class="agent-collab-card-kicker">MODULE ACTIONS</div>
            <div class="agent-collab-card-title-row">
              <h4>模块操作</h4>
              <span class="agent-collab-card-badge state-idle">ACTION</span>
            </div>
            <p class="agent-collab-card-peek">新增工具、新增市场和刷新入口集中到右侧，主区专注工具主表。</p>
          </div>
        </summary>
        <div class="overview-side-card-body module-side-card-body">
          <div class="module-action-stack">
            <button class="btn btn-primary small" @click="openAddModal">新增工具</button>
            <button class="btn btn-secondary small" @click="loadData">刷新</button>
          </div>
        </div>
      </details>
    </aside>
  </div>

  <!-- MCP 工具表单弹窗 -->
  <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
    <div class="modal-card">
      <div class="modal-head">
        <div>
          <h3>{{ isEdit ? '编辑 MCP 工具' : '新增 MCP 工具' }}</h3>
          <p>维护工具端点、传输方式和启用状态。</p>
        </div>
        <button class="modal-close" @click="closeModal">×</button>
      </div>
      <form class="modal-form" @submit.prevent="handleSubmit">
        <div class="modal-body">
          <div class="field">
            <label>工具名称 <span style="color:red">*</span></label>
            <input v-model="form.name" type="text" required placeholder="例如：文件系统工具">
          </div>
          <div class="field">
            <label>传输方式 <span style="color:red">*</span></label>
            <select v-model="form.transportType" required>
              <option value="REMOTE_HTTP">REMOTE_HTTP</option>
              <option value="REMOTE_SSE">REMOTE_SSE</option>
              <option value="LOCAL_STDIO">LOCAL_STDIO</option>
            </select>
          </div>
          <div class="field">
            <label>Endpoint <span style="color:red">*</span></label>
            <input v-model="form.endpoint" type="text" required placeholder="例如：http://localhost:3000">
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
            <label>备注</label>
            <textarea v-model="form.note" rows="3" placeholder="备注信息..."></textarea>
          </div>
        </div>
        <div class="modal-foot">
          <button type="button" class="btn btn-secondary" @click="closeModal">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="submitting">
            {{ submitting ? '保存中...' : (isEdit ? '保存修改' : '新增工具') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { apiRequest } from '../api/index.js'

const mcpTools = ref([])
const mcpMarkets = ref([])
const loading = ref(false)
const showModal = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const currentEditId = ref(null)

const form = reactive({
  name: '',
  transportType: 'REMOTE_HTTP',
  endpoint: '',
  category: 'general',
  status: 'enabled',
  note: ''
})

const transportOptions = ['REMOTE_HTTP', 'REMOTE_SSE', 'LOCAL_STDIO']
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

function pick(obj, ...keys) {
  for (const key of keys) {
    if (obj && key in obj) return obj[key]
  }
  return null
}

async function loadData() {
  loading.value = true
  try {
    const [toolsData, marketsData] = await Promise.all([
      apiRequest('/admin/mcp/tools').catch(() => []),
      apiRequest('/admin/mcp/markets').catch(() => [])
    ])
    mcpTools.value = toolsData || []
    mcpMarkets.value = marketsData || []
  } catch (err) {
    showToast(err.message || '加载失败', 'warning')
    mcpTools.value = []
    mcpMarkets.value = []
  } finally {
    loading.value = false
  }
}

function openAddModal() {
  isEdit.value = false
  currentEditId.value = null
  resetForm()
  showModal.value = true
}

function openEditModal(tool) {
  isEdit.value = true
  currentEditId.value = tool.id
  Object.assign(form, {
    name: pick(tool, 'name') || '',
    transportType: pick(tool, 'transport_type', 'transportType') || 'REMOTE_HTTP',
    endpoint: pick(tool, 'endpoint') || '',
    category: pick(tool, 'category') || 'general',
    status: pick(tool, 'status') || 'enabled',
    note: pick(tool, 'note') || ''
  })
  showModal.value = true
}

function resetForm() {
  Object.assign(form, {
    name: '',
    transportType: 'REMOTE_HTTP',
    endpoint: '',
    category: 'general',
    status: 'enabled',
    note: ''
  })
}

function closeModal() {
  showModal.value = false
  resetForm()
}

async function handleSubmit() {
  if (!form.name || !form.endpoint) {
    showToast('请填写必填项', 'warning')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await apiRequest(`/admin/mcp/tools/${currentEditId.value}`, {
        method: 'PUT',
        body: { ...form }
      })
    } else {
      await apiRequest('/admin/mcp/tools', {
        method: 'POST',
        body: { ...form }
      })
    }
    showToast(isEdit.value ? '工具已更新' : '工具已创建', 'success')
    closeModal()
    await loadData()
  } catch (err) {
    showToast(err.message || '保存失败', 'warning')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  if (!confirm('确认删除该 MCP 工具？')) return
  try {
    await apiRequest(`/admin/mcp/tools/${id}`, { method: 'DELETE' })
    showToast('删除成功', 'success')
    await loadData()
  } catch (err) {
    showToast(err.message || '删除失败', 'danger')
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

.empty-small {
  text-align: center;
  padding: 20px;
  color: var(--text-muted);
  font-size: 12px;
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

.data-table.small td {
  padding: 8px 10px;
  font-size: 12px;
}

.data-table tr:last-child td {
  border-bottom: none;
}

.endpoint-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  max-width: 520px;
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
  min-height: 60px;
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
