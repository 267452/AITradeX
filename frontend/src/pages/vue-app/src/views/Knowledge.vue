<template>
  <div class="page-knowledge">
    <div class="panel panel-tier-1 module-console-layout module-shell-card">
      <div class="module-main-column">
        <article class="panel">
          <div class="panel-head">
            <div>
              <h3>文档列表</h3>
              <p>文档解析状态、切片数量和同步记录</p>
            </div>
            <div style="display:flex;gap:8px;align-items:center;">
              <button class="btn btn-primary small" @click="openUploadDocumentModal">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align:middle;margin-right:4px;">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17 8 12 3 7 8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                上传文档
              </button>
            </div>
          </div>
          <div class="panel-body">
            <div v-if="loading" class="loading-state">加载中...</div>
            <div v-else-if="documents.length === 0" class="empty-state">暂无文档记录。</div>
            <table v-else class="data-table">
              <thead>
                <tr>
                  <th>文件名</th>
                  <th>所属知识库</th>
                  <th>解析状态</th>
                  <th>Chunk</th>
                  <th>Page</th>
                  <th>备注</th>
                  <th>最近同步</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="doc in documents" :key="doc.id">
                  <td><span class="value-strong">{{ doc.fileName || '--' }}</span></td>
                  <td>{{ doc.knowledgeBaseName || '--' }}</td>
                  <td>
                    <span :class="['badge', toneClass(normalizeToneFromStatus(doc.parseStatus))]">
                      {{ (doc.parseStatus || '--').toUpperCase() }}
                    </span>
                  </td>
                  <td>{{ formatNumber(doc.chunkCount || 0) }}</td>
                  <td>{{ formatNumber(doc.pageCount || 0) }}</td>
                  <td>{{ doc.syncNote || '--' }}</td>
                  <td>{{ formatDateTime(doc.lastSyncAt) }}</td>
                  <td>
                    <button class="mini-btn" @click="viewDocument(doc.id)">查看</button>
                    <button class="mini-btn danger" @click="deleteDocument(doc.id)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </div>

      <aside class="module-side-stack">
        <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-summary" open>
          <summary>
            <div class="agent-collab-card-copy">
              <div class="agent-collab-card-kicker">KNOWLEDGE SUMMARY</div>
              <div class="agent-collab-card-title-row">
                <h4>知识概览</h4>
                <span class="agent-collab-card-badge state-idle">摘要</span>
              </div>
              <p class="agent-collab-card-peek">默认收起，展开查看知识库规模、文档量和切片状态摘要。</p>
            </div>
          </summary>
          <div class="overview-side-card-body module-side-card-body">
            <div class="meta-grid">
              <div class="meta-card">
                <div class="meta-label">知识库数量</div>
                <div class="meta-value">{{ formatNumber(stats.baseCount || stats.base_count || 0) }}</div>
              </div>
              <div class="meta-card">
                <div class="meta-label">文档总量</div>
                <div class="meta-value">{{ formatNumber(stats.documentCount || stats.document_count || 0) }}</div>
              </div>
              <div class="meta-card">
                <div class="meta-label">切片总量</div>
                <div class="meta-value">{{ formatNumber(stats.sliceCount || stats.slice_count || 0) }}</div>
              </div>
              <div class="meta-card">
                <div class="meta-label">Embedding 模型</div>
                <div class="meta-value" style="font-size: 14px;">{{ stats.embeddingModel || stats.embedding_model || '--' }}</div>
              </div>
            </div>
          </div>
        </details>

        <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-action" open>
          <summary>
            <div class="agent-collab-card-copy">
              <div class="agent-collab-card-kicker">DOCUMENT SYNC</div>
              <div class="agent-collab-card-title-row">
                <h4>文档同步记录</h4>
                <span class="agent-collab-card-badge state-idle">LIST</span>
              </div>
              <p class="agent-collab-card-peek">默认收起，展开查看最新文档解析与写入状态。</p>
            </div>
          </summary>
          <div class="overview-side-card-body module-side-card-body">
            <div v-if="documents.length === 0" class="empty" style="padding:8px 0;">暂无文档。</div>
            <div v-else class="list-box">
              <div v-for="doc in documents.slice(0, 5)" :key="doc.id" class="list-item">
                <div class="list-item-title">{{ doc.fileName || '--' }}</div>
                <div class="list-item-meta">
                  <span :class="['badge', 'mini', toneClass(normalizeToneFromStatus(doc.parseStatus))]">
                    {{ (doc.parseStatus || '--').toUpperCase() }}
                  </span>
                  {{ formatDateTime(doc.lastSyncAt) }}
                </div>
              </div>
            </div>
          </div>
        </details>

        <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-action" open>
          <summary>
            <div class="agent-collab-card-copy">
              <div class="agent-collab-card-kicker">KNOWLEDGE CONFIG</div>
              <div class="agent-collab-card-title-row">
                <h4>知识库配置</h4>
                <span class="agent-collab-card-badge state-idle">MANAGE</span>
              </div>
              <p class="agent-collab-card-peek">新建、编辑和删除知识库，配置向量库参数。</p>
            </div>
          </summary>
          <div class="overview-side-card-body module-side-card-body">
            <div class="module-action-stack">
              <button class="btn btn-primary small" @click="openKnowledgeModal()" style="width:100%;justify-content:center;">新建知识库</button>
            </div>
            <div class="module-side-card">
              <div style="font-size:13px;color:var(--text-muted);margin-bottom:8px;">已创建的知识库：</div>
              <div v-if="bases.length === 0" class="empty" style="padding:8px 0;">暂无知识库。</div>
              <div v-else>
                <div v-for="base in bases" :key="base.id" class="side-card-item" style="display:flex;align-items:center;padding:8px;border-bottom:1px solid var(--border-color);">
                  <div style="flex:1;min-width:0;">
                    <div style="font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">{{ base.name || '--' }}</div>
                    <div style="font-size:11px;color:var(--text-muted);">{{ base.vectorStore || base.vector_store || '--' }}</div>
                  </div>
                  <div style="display:flex;gap:4px;margin-left:8px;">
                    <button class="mini-btn" @click="openKnowledgeModal(base)">编辑</button>
                    <button class="mini-btn danger" @click="deleteKnowledge(base.id)">删除</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </details>
      </aside>
    </div>

    <!-- 知识库弹窗 -->
    <div v-if="showKnowledgeModal" class="modal-overlay" @click.self="closeKnowledgeModal">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <h3>{{ editingKnowledge ? '编辑知识库' : '新增知识库' }}</h3>
            <p>维护知识库描述、向量库与切片规模，支持上传文档文件。</p>
          </div>
          <button class="modal-close" @click="closeKnowledgeModal" aria-label="关闭">×</button>
        </div>
        <form class="modal-form" @submit.prevent="submitKnowledge">
          <div class="modal-body">
            <div class="field">
              <label for="kb-name">知识库名称 *</label>
              <input id="kb-name" v-model="knowledgeForm.name" type="text" required placeholder="请输入知识库名称" />
            </div>
            <div class="field">
              <label for="kb-status">状态 *</label>
              <select id="kb-status" v-model="knowledgeForm.status" required>
                <option value="online">online</option>
                <option value="syncing">syncing</option>
                <option value="draft">draft</option>
              </select>
            </div>
            <div class="field">
              <label for="kb-desc">描述</label>
              <textarea id="kb-desc" v-model="knowledgeForm.description" rows="3" placeholder="请输入描述（可选）"></textarea>
            </div>
            <div class="field">
              <label for="kb-files">上传文档</label>
              <div style="display:flex;align-items:center;gap:12px;">
                <input id="kb-files" type="file" ref="fileInput" multiple accept=".pdf,.md,.txt,.doc,.docx,.xlsx,.xls,.ppt,.pptx" @change="onFileChange" />
              </div>
              <div style="font-size:12px;color:var(--text-muted);margin-top:4px;">支持 PDF、Markdown、Word、Excel、PPT 等格式，可多选</div>
              <div v-if="selectedFiles.length > 0" style="font-size:12px;color:var(--text-primary);margin-top:8px;">
                已选择 {{ selectedFiles.length }} 个文件：{{ selectedFiles.map(f => f.name).join(', ') }}
              </div>
            </div>
            <div class="field">
              <label for="kb-vector-store">向量库类型 *</label>
              <select id="kb-vector-store" v-model="knowledgeForm.vectorStore" required>
                <option value="Milvus">Milvus</option>
                <option value="Weaviate">Weaviate</option>
                <option value="Elasticsearch">Elasticsearch</option>
                <option value="Pgvector">Pgvector (PostgreSQL)</option>
                <option value="Chroma">Chroma</option>
                <option value="Qdrant">Qdrant</option>
                <option value="Local">内置文件存储</option>
              </select>
            </div>
            <div class="field">
              <label for="kb-embedding">Embedding 模型 *</label>
              <select id="kb-embedding" v-model="knowledgeForm.embeddingModel" required>
                <option value="bge-m3">bge-m3（多语言，建议）</option>
                <option value="bge-large-zh-v1.5">bge-large-zh-v1.5（中文大模型）</option>
                <option value="text-embedding-3-small">text-embedding-3-small（OpenAI）</option>
                <option value="text-embedding-3-large">text-embedding-3-large（OpenAI）</option>
                <option value="text-embedding-ada-002">text-embedding-ada-002（OpenAI）</option>
              </select>
            </div>
            <div class="field">
              <label for="kb-host">Host / 地址</label>
              <input id="kb-host" v-model="knowledgeForm.vectorHost" type="text" placeholder="例如 localhost 或 192.168.1.100" />
            </div>
            <div class="field">
              <label for="kb-port">Port / 端口</label>
              <input id="kb-port" v-model.number="knowledgeForm.vectorPort" type="number" placeholder="例如 19530" />
            </div>
            <div class="field">
              <label for="kb-collection">Collection / 索引名</label>
              <input id="kb-collection" v-model="knowledgeForm.vectorCollection" type="text" placeholder="例如 knowledge_document_chunks" />
            </div>
            <div class="field">
              <label for="kb-api-key">API Key（如有）</label>
              <input id="kb-api-key" v-model="knowledgeForm.vectorApiKey" type="password" placeholder="API Key，本地部署可留空" />
            </div>
            <div class="field">
              <label for="kb-dim">向量维度</label>
              <input id="kb-dim" v-model.number="knowledgeForm.embeddingDim" type="number" placeholder="bge-m3 默认 1024" min="0" />
            </div>
          </div>
          <div class="modal-foot">
            <button type="button" class="btn btn-secondary small" @click="closeKnowledgeModal">取消</button>
            <button type="submit" class="btn btn-primary small" :disabled="submitting">
              {{ submitting ? '提交中...' : (editingKnowledge ? '保存修改' : '新增知识库') }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <!-- 上传文档弹窗 -->
    <div v-if="showUploadModal" class="modal-overlay" @click.self="closeUploadModal">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <h3>上传文档</h3>
          </div>
          <button class="modal-close" @click="closeUploadModal" aria-label="关闭">×</button>
        </div>
        <div class="modal-body" style="max-height:70vh;overflow-y:auto;">
          <div class="field">
            <label for="up-doc-base">选择知识库（向量库）</label>
            <select id="up-doc-base" v-model="uploadForm.knowledgeBaseId" required>
              <option value="">请选择知识库</option>
              <option v-for="base in bases" :key="base.id" :value="base.id">{{ base.name || '--' }}</option>
            </select>
          </div>
          <div class="field" style="margin-top:12px;">
            <label for="up-doc-files">选择文档文件</label>
            <div style="display:flex;align-items:center;gap:12px;">
              <input id="up-doc-files" type="file" multiple accept=".pdf,.md,.txt,.doc,.docx,.xlsx,.xls,.ppt,.pptx,.csv,.json,.log,.html,.xml" @change="onUploadFileChange" />
            </div>
            <div style="font-size:12px;color:var(--text-muted);margin-top:4px;">支持 PDF、Markdown、Word、Excel、PPT、TXT、CSV、JSON 等格式，可多选</div>
            <div v-if="uploadFiles.length > 0" style="font-size:12px;color:var(--text-primary);margin-top:8px;">
              已选择 {{ uploadFiles.length }} 个文件：{{ uploadFiles.map(f => f.name).join(', ') }}
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-secondary small" @click="closeUploadModal">取消</button>
          <button class="btn btn-primary small" @click="submitUploadDocument" :disabled="uploading">
            {{ uploading ? '上传中...' : '上传并保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 文档查看弹窗 -->
    <div v-if="showDocumentViewer" class="modal-overlay" @click.self="closeDocumentViewer">
      <div class="modal-card" style="max-width:880px;width:90%;">
        <div class="modal-head">
          <div>
            <h3>{{ documentViewerData.fileName || '文档内容' }}</h3>
          </div>
          <button class="modal-close" @click="closeDocumentViewer" aria-label="关闭">×</button>
        </div>
        <div class="modal-body" style="max-height:70vh;overflow-y:auto;">
          <div v-if="documentLoading" style="padding:12px 0;color:var(--text-muted);font-size:13px;">正在读取文档内容...</div>
          <template v-else-if="documentViewerData.content !== undefined">
            <div style="margin-bottom:16px;">
              <div class="item-row" style="display:flex;gap:16px;flex-wrap:wrap;font-size:12px;color:var(--text-muted);">
                <span>格式: <span class="badge" style="padding:1px 8px;">{{ documentViewerData.fileFormat || '--' }}</span></span>
                <span>大小: {{ documentViewerData.fileSize ? (Number(documentViewerData.fileSize) / 1024).toFixed(1) + ' KB' : '—' }}</span>
                <span>解析状态: <span class="badge" style="padding:1px 8px;">{{ documentViewerData.parseStatus || '—' }}</span></span>
                <span>文本格式: {{ documentViewerData.isText ? '是' : '否（二进制/非文本）' }}</span>
              </div>
            </div>
            <div style="border:1px solid var(--border-color);border-radius:8px;padding:16px;background:var(--panel-bg);">
              <pre style="white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace;font-size:12.5px;line-height:1.6;margin:0;color:var(--text-primary);">{{ documentViewerData.content }}</pre>
            </div>
            <div v-if="documentViewerData.contentTruncated" style="margin-top:12px;color:var(--text-muted);font-size:12px;text-align:center;">内容过长，仅展示前 1MB，完整内容请从原文件查看。</div>
          </template>
          <div v-else style="color:var(--error);font-size:13px;padding:12px 0;">读取失败: {{ documentError }}</div>
        </div>
        <div class="modal-foot" style="justify-content:flex-end;">
          <button class="btn btn-secondary small" @click="closeDocumentViewer">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { apiRequest } from '@/api/index.js'

const API_BASE = '/api'

// 状态
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
const documentLoading = ref(false)
const stats = reactive({})
const bases = ref([])
const documents = ref([])

// 知识库弹窗
const showKnowledgeModal = ref(false)
const editingKnowledge = ref(null)
const fileInput = ref(null)
const selectedFiles = ref([])

const knowledgeForm = reactive({
  name: '',
  status: 'draft',
  description: '',
  vectorStore: 'Milvus',
  embeddingModel: 'bge-m3',
  vectorHost: '',
  vectorPort: null,
  vectorCollection: 'knowledge_document_chunks',
  vectorApiKey: '',
  embeddingDim: 1024
})

// 上传文档弹窗
const showUploadModal = ref(false)
const uploadFiles = ref([])
const uploadForm = reactive({
  knowledgeBaseId: ''
})

// 文档查看弹窗
const showDocumentViewer = ref(false)
const documentViewerData = reactive({
  fileName: '',
  content: '',
  contentTruncated: false,
  fileFormat: '',
  fileSize: 0,
  parseStatus: '',
  isText: false
})
const documentError = ref('')

// 工具函数
function pick(obj, ...keys) {
  for (const key of keys) {
    if (obj && key in obj) return obj[key]
  }
  return undefined
}

function escapeHtml(str) {
  if (!str) return ''
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

function formatNumber(num) {
  if (num === null || num === undefined) return '0'
  return Number(num).toLocaleString()
}

function formatDateTime(dateStr) {
  if (!dateStr) return '--'
  try {
    const date = new Date(dateStr)
    if (isNaN(date.getTime())) return '--'
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return '--'
  }
}

function normalizeToneFromStatus(status) {
  if (!status) return 'secondary'
  const s = String(status).toLowerCase()
  if (['online', 'active', 'ready', 'success', 'completed', 'synced'].includes(s)) return 'success'
  if (['syncing', 'processing', 'pending', 'running'].includes(s)) return 'warning'
  if (['error', 'failed', 'danger', 'critical'].includes(s)) return 'danger'
  return 'secondary'
}

function toneClass(tone) {
  return `tone-${tone}`
}

// 加载数据
async function loadKnowledgeData() {
  loading.value = true
  try {
    const [statsData, basesData, docsData] = await Promise.all([
      apiRequest('/admin/knowledge/stats').catch(() => ({})),
      apiRequest('/admin/knowledge/bases').catch(() => []),
      apiRequest('/admin/knowledge/documents').catch(() => [])
    ])

    Object.assign(stats, statsData || {})
    bases.value = basesData || []
    documents.value = docsData || []
  } catch (error) {
    console.error('加载知识库数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 知识库弹窗
function openKnowledgeModal(record = null) {
  editingKnowledge.value = record

  if (record) {
    knowledgeForm.name = record.name || ''
    knowledgeForm.status = record.status || 'draft'
    knowledgeForm.description = record.description || ''

    const rawVectorConfig = record.vectorConfig || record.vector_config || {}
    let vectorConfig = {}
    if (typeof rawVectorConfig === 'string') {
      try { vectorConfig = JSON.parse(rawVectorConfig) } catch { vectorConfig = {} }
    } else if (rawVectorConfig && typeof rawVectorConfig === 'object') {
      vectorConfig = rawVectorConfig
    }

    knowledgeForm.vectorStore = record.vectorStore || record.vector_store || 'Milvus'
    knowledgeForm.embeddingModel = record.embeddingModel || record.embedding_model || 'bge-m3'
    knowledgeForm.vectorHost = vectorConfig.host || ''
    knowledgeForm.vectorPort = vectorConfig.port ?? null
    knowledgeForm.vectorCollection = vectorConfig.collection || 'knowledge_document_chunks'
    knowledgeForm.vectorApiKey = vectorConfig.api_key || ''
    knowledgeForm.embeddingDim = vectorConfig.dim ?? 1024
  } else {
    knowledgeForm.name = ''
    knowledgeForm.status = 'draft'
    knowledgeForm.description = ''
    knowledgeForm.vectorStore = 'Milvus'
    knowledgeForm.embeddingModel = 'bge-m3'
    knowledgeForm.vectorHost = ''
    knowledgeForm.vectorPort = null
    knowledgeForm.vectorCollection = 'knowledge_document_chunks'
    knowledgeForm.vectorApiKey = ''
    knowledgeForm.embeddingDim = 1024
  }

  selectedFiles.value = []
  showKnowledgeModal.value = true
}

function closeKnowledgeModal() {
  showKnowledgeModal.value = false
  editingKnowledge.value = null
  selectedFiles.value = []
}

function onFileChange(e) {
  selectedFiles.value = e.target.files ? Array.from(e.target.files) : []
}

async function submitKnowledge() {
  submitting.value = true

  try {
    // 组装 vector_config
    const vectorConfigPayload = {}
    if (knowledgeForm.vectorHost) vectorConfigPayload.host = knowledgeForm.vectorHost
    if (knowledgeForm.vectorPort) vectorConfigPayload.port = Number(knowledgeForm.vectorPort)
    if (knowledgeForm.vectorCollection) vectorConfigPayload.collection = knowledgeForm.vectorCollection
    if (knowledgeForm.vectorApiKey) vectorConfigPayload.api_key = knowledgeForm.vectorApiKey
    if (knowledgeForm.embeddingDim) vectorConfigPayload.dim = Number(knowledgeForm.embeddingDim)

    const submitPayload = {
      name: knowledgeForm.name,
      description: knowledgeForm.description || '',
      vectorStore: knowledgeForm.vectorStore || 'Milvus',
      embeddingModel: knowledgeForm.embeddingModel || 'bge-m3',
      status: knowledgeForm.status || 'draft',
      vectorConfig: vectorConfigPayload
    }

    // 处理文件上传
    if (selectedFiles.value.length > 0) {
      const formData = new FormData()
      for (let i = 0; i < selectedFiles.value.length; i++) {
        formData.append('files', selectedFiles.value[i])
      }

      if (editingKnowledge.value) {
        formData.append('knowledge_base_id', editingKnowledge.value.id)
      } else {
        formData.append('knowledge_base_name', submitPayload.name)
        formData.append('knowledge_base_description', submitPayload.description || '')
      }

      try {
        const token = localStorage.getItem('token')
        const uploadResponse = await fetch(`${API_BASE}/admin/knowledge/documents/upload`, {
          method: 'POST',
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          body: formData
        })

        if (!uploadResponse.ok) {
          const errorData = await uploadResponse.json()
          throw new Error(errorData.msg || '文档上传失败')
        }

        const uploadResult = await uploadResponse.json()
        showToast(`文档上传成功，共 ${uploadResult.data?.success_count || 0} 个文件`, 'success')
      } catch (uploadError) {
        showToast(`文档上传失败: ${uploadError.message}`, 'warning')
      }
    }

    // 提交知识库
    if (editingKnowledge.value) {
      await apiRequest(`/admin/knowledge/bases/${editingKnowledge.value.id}`, {
        method: 'PUT',
        body: submitPayload
      })
      showToast('知识库已更新', 'success')
    } else {
      await apiRequest('/admin/knowledge/bases', {
        method: 'POST',
        body: submitPayload
      })
      showToast('知识库已创建', 'success')
    }

    closeKnowledgeModal()
    await loadKnowledgeData()
  } catch (error) {
    showToast(error.message || '提交失败', 'error')
  } finally {
    submitting.value = false
  }
}

async function deleteKnowledge(id) {
  if (!confirm('确定要删除这个知识库吗？')) return

  try {
    await apiRequest(`/admin/knowledge/bases/${id}`, { method: 'DELETE' })
    showToast('知识库已删除', 'success')
    await loadKnowledgeData()
  } catch (error) {
    showToast(error.message || '删除失败', 'error')
  }
}

// 上传文档弹窗
function openUploadDocumentModal() {
  if (bases.value.length === 0) {
    showToast('请先创建知识库，再上传文档', 'warning')
    return
  }
  uploadForm.knowledgeBaseId = ''
  uploadFiles.value = []
  showUploadModal.value = true
}

function closeUploadModal() {
  showUploadModal.value = false
  uploadFiles.value = []
}

function onUploadFileChange(e) {
  uploadFiles.value = e.target.files ? Array.from(e.target.files) : []
}

async function submitUploadDocument() {
  if (!uploadForm.knowledgeBaseId) {
    showToast('请选择知识库', 'warning')
    return
  }
  if (uploadFiles.value.length === 0) {
    showToast('请选择至少一个文件', 'warning')
    return
  }

  uploading.value = true

  try {
    const formData = new FormData()
    for (let i = 0; i < uploadFiles.value.length; i++) {
      formData.append('files', uploadFiles.value[i])
    }
    formData.append('knowledge_base_id', uploadForm.knowledgeBaseId)

    const token = localStorage.getItem('token')
    const uploadResponse = await fetch(`${API_BASE}/admin/knowledge/documents/upload`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData
    })

    if (!uploadResponse.ok) {
      const errorData = await uploadResponse.json()
      throw new Error(errorData.msg || '文档上传失败')
    }

    const uploadResult = await uploadResponse.json()
    showToast(`上传成功，共 ${pick(uploadResult, 'data.success_count') || 0} 个文件`, 'success')

    closeUploadModal()
    await loadKnowledgeData()
  } catch (error) {
    showToast(`上传失败: ${error.message}`, 'warning')
  } finally {
    uploading.value = false
  }
}

// 文档查看
async function viewDocument(id) {
  showDocumentViewer.value = true
  documentLoading.value = true
  documentError.value = ''

  try {
    const data = await apiRequest(`/admin/knowledge/documents/${id}/content`)
    documentViewerData.fileName = data.file_name || ''
    documentViewerData.content = data.content || ''
    documentViewerData.contentTruncated = data.content_truncated || false
    documentViewerData.fileFormat = data.file_format || ''
    documentViewerData.fileSize = data.file_size || 0
    documentViewerData.parseStatus = data.parse_status || ''
    documentViewerData.isText = data.is_text || false
  } catch (error) {
    documentError.value = error.message || String(error)
  } finally {
    documentLoading.value = false
  }
}

function closeDocumentViewer() {
  showDocumentViewer.value = false
}

async function deleteDocument(id) {
  if (!confirm('确定要删除这个文档吗？')) return

  try {
    await apiRequest(`/admin/knowledge/documents/${id}`, { method: 'DELETE' })
    showToast('文档已删除', 'success')
    await loadKnowledgeData()
  } catch (error) {
    showToast(error.message || '删除失败', 'error')
  }
}

// Toast 提示
function showToast(message, type = 'info') {
  const existing = document.querySelector('.toast-container')
  if (existing) existing.remove()

  const toast = document.createElement('div')
  toast.className = `toast-container toast-${type}`
  toast.innerHTML = `<div class="toast-message">${escapeHtml(message)}</div>`

  const colors = {
    success: '#22c55e',
    error: '#ef4444',
    warning: '#f59e0b',
    info: '#3b82f6'
  }

  toast.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 9999;
    background: ${colors[type] || colors.info};
    color: white;
    padding: 12px 20px;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
    animation: toast-in 0.3s ease;
  `

  document.body.appendChild(toast)

  setTimeout(() => {
    toast.style.animation = 'toast-out 0.3s ease forwards'
    setTimeout(() => toast.remove(), 300)
  }, 3000)
}

// 生命周期
onMounted(() => {
  loadKnowledgeData()
})
</script>

<style scoped>
.page-knowledge {
  height: 100%;
}

.panel {
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-color);
}

.panel-head h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.panel-head p {
  font-size: 13px;
  color: var(--text-muted);
  margin: 4px 0 0;
}

.panel-body {
  padding: 16px 24px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  border-radius: 8px;
  border: none;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary {
  background: linear-gradient(135deg, #22c8ae, #1889b7);
  color: white;
}

.btn-primary:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.btn-secondary {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-secondary:hover {
  background: var(--bg-hover);
}

.mini-btn {
  padding: 4px 8px;
  font-size: 11px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  background: var(--bg-secondary);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.mini-btn:hover {
  background: var(--bg-hover);
}

.mini-btn.danger {
  color: #ef4444;
}

.mini-btn.danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.data-table th {
  font-weight: 600;
  color: var(--text-muted);
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.data-table td {
  color: var(--text-primary);
}

.value-strong {
  font-weight: 600;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.badge.tone-success {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.badge.tone-warning {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
}

.badge.tone-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
}

.badge.tone-secondary {
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
}

.loading-state,
.empty-state {
  padding: 40px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.module-console-layout {
  display: flex;
  gap: 20px;
  padding: 20px;
  height: 100%;
}

.module-main-column {
  flex: 1;
  min-width: 0;
}

.module-side-stack {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-collab-card {
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.agent-collab-card summary {
  padding: 16px;
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
  color: rgba(183, 210, 236, 0.54);
}

.agent-collab-card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.agent-collab-card-title-row h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.agent-collab-card-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
}

.agent-collab-card-peek {
  font-size: 12px;
  color: var(--text-muted);
  margin: 0;
}

.overview-side-card-body {
  padding: 0 16px 16px;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.meta-card {
  background: var(--bg-secondary);
  border-radius: 8px;
  padding: 12px;
}

.meta-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.meta-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.module-action-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.module-side-card {
  background: var(--bg-secondary);
  border-radius: 8px;
  padding: 12px;
}

.list-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-item {
  padding: 8px;
  background: var(--bg-secondary);
  border-radius: 6px;
}

.list-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.list-item-meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.badge.mini {
  padding: 1px 6px;
  font-size: 10px;
}

.side-card-item {
  transition: background 0.2s ease;
}

.side-card-item:hover {
  background: var(--bg-hover);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fade-in 0.2s ease;
}

.modal-card {
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  max-width: 560px;
  width: 90%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  animation: slide-up 0.3s ease;
}

.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-color);
}

.modal-head h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.modal-head p {
  font-size: 13px;
  color: var(--text-muted);
  margin: 4px 0 0;
}

.modal-close {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.modal-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.modal-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--border-color);
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.field input[type="text"],
.field input[type="number"],
.field input[type="password"],
.field select,
.field textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 13px;
  transition: border-color 0.2s ease;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  outline: none;
  border-color: #22c8ae;
}

.field textarea {
  resize: vertical;
  min-height: 80px;
}

.field input[type="file"] {
  font-size: 13px;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slide-up {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

@keyframes toast-in {
  from { transform: translateX(100%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

@keyframes toast-out {
  from { transform: translateX(0); opacity: 1; }
  to { transform: translateX(100%); opacity: 0; }
}
</style>
