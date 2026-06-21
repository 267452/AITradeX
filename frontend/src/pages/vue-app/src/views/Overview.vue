<template>
  <div class="page-view" id="page-overview">
    <article class="panel panel-tier-1 overview-command-panel">
      <div class="panel-head">
        <div>
          <h3>交易指挥台</h3>
        </div>
        <span class="badge success">COCKPIT</span>
      </div>
      <div class="panel-body">
        <div class="overview-command-layout">
          <div class="overview-command-main-column">
            <div class="overview-command-stage-strip">
              <span class="overview-stage-chip active">1. AI 分析</span>
              <span class="overview-stage-chip">2. 人机确认</span>
              <span class="overview-stage-chip">3. 交易执行</span>
            </div>

            <div class="overview-command-main">
              <div class="overview-control-strip">
                <div class="field">
                  <label for="brokerModeSelect">交易模式</label>
                  <select id="brokerModeSelect" v-model="brokerMode">
                    <option value="paper">模拟交易</option>
                    <option value="gtja">A股</option>
                    <option value="okx">区块链</option>
                    <option value="usstock">美股交易</option>
                  </select>
                </div>
                <label class="field">
                  <span>执行策略</span>
                  <select id="executionPolicySelect" v-model="executionPolicy">
                    <option value="auto">自动执行</option>
                    <option value="approval">确认后执行</option>
                  </select>
                </label>
                <div class="field">
                  <label>&nbsp;</label>
                  <div class="overview-inline-actions">
                    <button class="btn btn-secondary" @click="applyBrokerMode">切换交易模式</button>
                    <button class="btn btn-secondary" @click="openRunHistory">运行回放</button>
                  </div>
                </div>
              </div>

              <div class="field">
                <label for="commandInput">交易指令</label>
                <textarea
                  id="commandInput"
                  v-model="commandInput"
                  placeholder="例如：买入 600519 100&#10;运行策略 BTC-USDT 50"
                ></textarea>
              </div>

              <div class="command-actions">
                <button class="btn btn-secondary" @click="analyzeCommand">AI 分析</button>
                <button class="btn btn-primary btn-emphasis" @click="analyzeAndExecute">AI 分析并执行</button>
                <button class="btn btn-secondary" @click="dryRun">Dry Run</button>
              </div>

              <div class="command-confirm-row">
                <button class="btn btn-secondary" id="confirmExecuteBtn" :disabled="!pendingCommand" @click="confirmExecute">
                  确认执行建议指令
                </button>
                <div class="approval-inline" :class="{ show: showApproval }">
                  <input class="approval-input" v-model="approvalCoApprover" type="text" placeholder="复核人（必填）">
                  <input class="approval-input" v-model="approvalPassphrase" type="password" placeholder="审批口令（必填）">
                  <input class="approval-input" v-model="approvalNote" type="text" placeholder="审批备注（可选）">
                </div>
                <span class="item-meta" id="pendingCommandHint">{{ pendingCommandHint }}</span>
              </div>

              <div class="result-box" :class="resultStatus">{{ commandResult }}</div>
            </div>
          </div>

          <aside class="overview-side-stack" id="overviewSideStack" aria-label="总览中心右侧卡片">
            <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-search" :open="searchOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">MARKET TOOL</div>
                  <div class="agent-collab-card-title-row">
                    <h4>行情检索</h4>
                    <span class="agent-collab-card-badge" :class="searchBadgeState">{{ searchBadgeText }}</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，展开后再按市场检索代码和标的。</p>
                </div>
              </summary>
              <div class="overview-side-card-body">
                <div class="search-row">
                  <select id="quoteMarket" v-model="quoteMarket">
                    <option value="cn_stock">A股</option>
                    <option value="cn_convertible">可转债</option>
                    <option value="crypto">区块链</option>
                    <option value="futures">期货</option>
                    <option value="hk_stock">港股</option>
                    <option value="us_stock">美股</option>
                  </select>
                  <input id="quoteQuery" v-model="quoteQuery" type="text" placeholder="输入代码、股票名或 BTC-USDT">
                  <button class="btn btn-secondary" @click="searchQuote">搜索</button>
                </div>
                <div class="list-box" id="searchResults">
                  <div v-for="item in searchResults" :key="item.symbol" class="list-item">
                    <span>{{ item.symbol }} - {{ item.name }}</span>
                    <span class="item-meta">{{ item.price }}</span>
                  </div>
                </div>
              </div>
            </details>

            <details class="agent-collab-card overview-side-nav-card side-nav-agent" :open="agentOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">AGENT PIPELINE</div>
                  <div class="agent-collab-card-title-row">
                    <h4>多 Agent 协作链</h4>
                    <span class="agent-collab-card-badge" :class="agentBadgeState">{{ agentBadgeText }}</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，发起分析后再展开查看完整协作流程。</p>
                </div>
              </summary>
              <div class="agent-collab-card-body">
                <div class="agent-collab-summary" id="agentCollabSummary">
                  当前协作链已接入 {{ agentCount }} 个角色，发起分析后会展示每一步状态、摘要与运行上下文。
                </div>
                <div class="agent-collab-grid" id="agentCollabGrid">
                  <div v-for="agent in agentPipeline" :key="agent.id" class="agent-stage-card">
                    <div class="agent-stage-name">{{ agent.name }}</div>
                    <div class="agent-stage-status" :class="agent.status">{{ agent.statusText }}</div>
                  </div>
                </div>
              </div>
            </details>

            <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-debug" :open="debugOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">DEBUG TRACE</div>
                  <div class="agent-collab-card-title-row">
                    <h4>Trace 与调试细节</h4>
                    <span class="agent-collab-card-badge state-idle">DEBUG</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，展开查看决策卡片、执行上下文和 Agent Trace。</p>
                </div>
              </summary>
              <div class="overview-side-card-body">
                <div class="overview-command-rail">
                  <section class="decision-card">
                    <h4 class="decision-title">Agent 决策卡片</h4>
                    <p class="decision-subtitle">意图、风险、执行状态与建议指令</p>
                    <div id="decisionCardPanel" class="decision-placeholder">
                      {{ decisionCard || '等待执行 AI 分析后展示。' }}
                    </div>
                  </section>
                  <section class="decision-card">
                    <h4 class="decision-title">执行上下文</h4>
                    <p class="decision-subtitle">run_id、workflow_run_id 与执行模式</p>
                    <div id="executionContextPanel" class="decision-placeholder">
                      {{ executionContext || '尚未生成执行上下文。' }}
                    </div>
                  </section>
                  <div class="trace-panel">
                    <h4 class="decision-title">Agent Trace</h4>
                    <p class="decision-subtitle">intent_router → market_analyst → risk_guardian → execution_agent → summary_agent</p>
                    <div id="agentTracePanel" class="decision-placeholder">
                      {{ agentTrace || '尚未产生 trace 记录。' }}
                    </div>
                  </div>
                </div>
              </div>
            </details>

            <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-status" :open="statsOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">SYSTEM STATUS</div>
                  <div class="agent-collab-card-title-row">
                    <h4>系统摘要</h4>
                    <span class="agent-collab-card-badge" :class="statsBadgeState">{{ statsBadgeText }}</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，展开查看执行通道、资产、负载和决策引擎摘要。</p>
                </div>
              </summary>
              <div class="overview-side-card-body">
                <div class="stats-grid" id="overviewStats">
                  <div v-for="stat in systemStats" :key="stat.label" class="stat-card">
                    <div class="stat-top">
                      <span class="stat-label">{{ stat.label }}</span>
                    </div>
                    <div class="stat-value">{{ stat.value }}</div>
                  </div>
                </div>
              </div>
            </details>

            <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-focus" :open="focusOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">TODAY FOCUS</div>
                  <div class="agent-collab-card-title-row">
                    <h4>今日优先事项</h4>
                    <span class="agent-collab-card-badge state-idle" id="overviewFocusBadge">{{ focusBadge }}</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，展开查看当前最值得优先处理的事项。</p>
                </div>
              </summary>
              <div class="overview-side-card-body">
                <div class="overview-priority-tip">优先补齐主链路缺口，确认可执行后再发起分析与下单。</div>
                <div class="overview-focus-list" id="overviewFocusList">
                  <div v-for="item in focusList" :key="item.id" class="overview-focus-card">
                    {{ item.content }}
                  </div>
                </div>
              </div>
            </details>

            <details class="agent-collab-card overview-side-fold-card overview-side-nav-card side-nav-aux" :open="auxOpen">
              <summary>
                <div class="agent-collab-card-copy">
                  <div class="agent-collab-card-kicker">LOW FREQ</div>
                  <div class="agent-collab-card-title-row">
                    <h4>辅助信息</h4>
                    <span class="agent-collab-card-badge state-idle" id="recentOrdersBadge">{{ recentOrdersCount }}</span>
                  </div>
                  <p class="agent-collab-card-peek">默认收起，最近运行和更多操作都放在这里按需查看。</p>
                </div>
              </summary>
              <div class="overview-side-card-body">
                <div class="overview-side-section">
                  <div class="overview-side-section-head">
                    <h5>最近运行</h5>
                    <span class="badge info">RECENT</span>
                  </div>
                  <div class="list-box" id="recentOrdersList">
                    <div v-for="order in recentOrders" :key="order.id" class="list-item">
                      <span>{{ order.symbol }} {{ order.type }} {{ order.amount }}</span>
                      <span class="item-meta">{{ order.time }}</span>
                    </div>
                  </div>
                </div>
                <div class="overview-side-section">
                  <div class="overview-side-section-head">
                    <h5>更多操作</h5>
                    <span class="badge info">MORE</span>
                  </div>
                  <div class="quick-grid" id="quickActionList">
                    <button v-for="action in quickActions" :key="action.id" class="quick-action" @click="action.handler">
                      {{ action.label }}
                    </button>
                  </div>
                </div>
              </div>
            </details>
          </aside>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { apiRequest } from '@/api'

// 交易模式
const brokerMode = ref('paper')
const executionPolicy = ref('auto')
const commandInput = ref('')
const commandResult = ref('还没有执行交易命令。')
const resultStatus = ref('')

// 审批相关
const showApproval = ref(false)
const approvalCoApprover = ref('')
const approvalPassphrase = ref('')
const approvalNote = ref('')
const pendingCommand = ref(null)
const pendingCommandHint = computed(() => pendingCommand.value ? `待确认: ${pendingCommand.value}` : '暂无待确认指令')

// 行情检索
const searchOpen = ref(false)
const quoteMarket = ref('cn_stock')
const quoteQuery = ref('')
const searchResults = ref([])
const searchBadgeState = ref('state-idle')
const searchBadgeText = ref('检索')

// Agent 协作
const agentOpen = ref(false)
const agentCount = ref(5)
const agentPipeline = ref([
  { id: 1, name: 'Intent Router', status: 'state-idle', statusText: '待命' },
  { id: 2, name: 'Market Analyst', status: 'state-idle', statusText: '待命' },
  { id: 3, name: 'Risk Guardian', status: 'state-idle', statusText: '待命' },
  { id: 4, name: 'Execution Agent', status: 'state-idle', statusText: '待命' },
  { id: 5, name: 'Summary Agent', status: 'state-idle', statusText: '待命' }
])
const agentBadgeState = ref('state-idle')
const agentBadgeText = ref('待命')

// 决策卡片
const debugOpen = ref(false)
const decisionCard = ref('')
const executionContext = ref('')
const agentTrace = ref('')

// 系统摘要
const statsOpen = ref(false)
const systemStats = ref([
  { label: '执行通道', value: '--' },
  { label: '总资产', value: '--' },
  { label: '负载', value: '--' },
  { label: '决策引擎', value: '--' }
])
const statsBadgeState = ref('state-idle')
const statsBadgeText = ref('待命')

// 今日优先
const focusOpen = ref(false)
const focusBadge = ref('待命')
const focusList = ref([])

// 辅助信息
const auxOpen = ref(false)
const recentOrdersCount = ref('空')
const recentOrders = ref([])
const quickActions = ref([
  { id: 1, label: '刷新持仓', handler: loadPositions },
  { id: 2, label: '刷新订单', handler: loadOrders },
  { id: 3, label: '账户明细', handler: loadAccountSummary },
  { id: 4, label: '风险监控', handler: () => {} }
])

// 快捷命令
const quickCommands = [
  { label: '市价买入', command: '买入 {symbol} 100' },
  { label: '市价卖出', command: '卖出 {symbol} 100' },
  { label: '查看持仓', command: '持仓' },
  { label: '撤销订单', command: '撤销 {order_id}' }
]

// 方法
function applyBrokerMode() {
  commandResult.value = `已切换交易模式: ${brokerMode.value}`
  resultStatus.value = 'success'
}

function openRunHistory() {
  commandResult.value = '打开运行回放...'
  resultStatus.value = ''
}

async function analyzeCommand() {
  if (!commandInput.value.trim()) {
    commandResult.value = '请输入交易指令'
    resultStatus.value = 'warning'
    return
  }
  
  commandResult.value = 'AI 分析中...'
  resultStatus.value = ''
  
  try {
    const response = await apiRequest('/api/ai/chat', {
      method: 'POST',
      body: { message: commandInput.value, mode: 'analyze' }
    })
    
    decisionCard.value = response.decision || JSON.stringify(response, null, 2)
    agentTrace.value = response.trace || '分析完成'
    
    if (response.suggested_command) {
      pendingCommand.value = response.suggested_command
      showApproval.value = executionPolicy.value === 'approval'
    }
    
    commandResult.value = response.message || '分析完成'
    resultStatus.value = response.success ? 'success' : 'warning'
    updateAgentPipeline(response.pipeline)
  } catch (error) {
    commandResult.value = `分析失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

async function analyzeAndExecute() {
  if (!commandInput.value.trim()) {
    commandResult.value = '请输入交易指令'
    resultStatus.value = 'warning'
    return
  }
  
  commandResult.value = 'AI 分析并执行中...'
  resultStatus.value = ''
  
  try {
    const response = await apiRequest('/api/ai/chat', {
      method: 'POST',
      body: { 
        message: commandInput.value, 
        mode: 'execute',
        broker_mode: brokerMode.value,
        execution_policy: executionPolicy.value
      }
    })
    
    decisionCard.value = response.decision || ''
    executionContext.value = response.run_id ? `run_id: ${response.run_id}` : ''
    agentTrace.value = response.trace || ''
    
    commandResult.value = response.message || '执行完成'
    resultStatus.value = response.success ? 'success' : 'warning'
    updateAgentPipeline(response.pipeline)
    
    if (response.execution_needed_approval) {
      pendingCommand.value = response.suggested_command
      showApproval.value = true
      commandResult.value += ' - 待确认执行'
    }
  } catch (error) {
    commandResult.value = `执行失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

function dryRun() {
  if (!commandInput.value.trim()) {
    commandResult.value = '请输入交易指令'
    resultStatus.value = 'warning'
    return
  }
  
  commandResult.value = `Dry Run: ${commandInput.value}\n模拟执行，仅做验证不会实际下单`
  resultStatus.value = 'info'
}

async function confirmExecute() {
  if (!pendingCommand.value) return
  
  try {
    const response = await apiRequest('/api/trade/execute', {
      method: 'POST',
      body: {
        command: pendingCommand.value,
        co_approver: approvalCoApprover.value,
        passphrase: approvalPassphrase.value,
        note: approvalNote.value
      }
    })
    
    commandResult.value = `确认执行成功: ${pendingCommand.value}`
    resultStatus.value = 'success'
    pendingCommand.value = null
    showApproval.value = false
    
    approvalCoApprover.value = ''
    approvalPassphrase.value = ''
    approvalNote.value = ''
  } catch (error) {
    commandResult.value = `确认执行失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

async function searchQuote() {
  if (!quoteQuery.value.trim()) {
    searchResults.value = []
    return
  }
  
  searchBadgeState.value = 'state-active'
  searchBadgeText.value = '搜索中'
  
  try {
    const response = await apiRequest('/api/market/search', {
      method: 'POST',
      body: {
        market: quoteMarket.value,
        query: quoteQuery.value
      }
    })
    
    searchResults.value = response.results || []
    searchBadgeState.value = 'state-completed'
    searchBadgeText.value = '完成'
  } catch (error) {
    searchBadgeState.value = 'state-blocked'
    searchBadgeText.value = '失败'
    searchResults.value = []
  }
}

function updateAgentPipeline(pipeline) {
  if (!pipeline) return
  
  agentPipeline.value = agentPipeline.value.map(agent => {
    const state = pipeline[agent.name.toLowerCase().replace(' ', '_')]
    if (state) {
      return {
        ...agent,
        status: state.status || 'state-idle',
        statusText: state.status_text || agent.statusText
      }
    }
    return agent
  })
  
  const hasActive = agentPipeline.value.some(a => a.status === 'state-active')
  const allCompleted = agentPipeline.value.every(a => a.status === 'state-completed')
  
  if (allCompleted) {
    agentBadgeState.value = 'state-completed'
    agentBadgeText.value = '完成'
  } else if (hasActive) {
    agentBadgeState.value = 'state-active'
    agentBadgeText.value = '运行中'
  }
}

async function loadPositions() {
  try {
    const positions = await apiRequest('/api/trade/positions')
    commandResult.value = `持仓: ${JSON.stringify(positions, null, 2)}`
    resultStatus.value = 'success'
  } catch (error) {
    commandResult.value = `加载持仓失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

async function loadOrders() {
  try {
    const orders = await apiRequest('/api/trade/orders')
    recentOrders.value = orders.slice(0, 5) || []
    recentOrdersCount.value = `${recentOrders.value.length}`
    commandResult.value = `订单已刷新`
    resultStatus.value = 'success'
  } catch (error) {
    commandResult.value = `加载订单失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

async function loadAccountSummary() {
  try {
    const summary = await apiRequest('/api/monitor/summary')
    systemStats.value = [
      { label: '执行通道', value: summary.channels || '--' },
      { label: '总资产', value: summary.total_assets || '--' },
      { label: '负载', value: summary.load || '--' },
      { label: '决策引擎', value: summary.decision_engine || '--' }
    ]
    statsBadgeState.value = 'state-completed'
    statsBadgeText.value = '正常'
    commandResult.value = '账户概览已刷新'
    resultStatus.value = 'success'
  } catch (error) {
    statsBadgeState.value = 'state-blocked'
    statsBadgeText.value = '异常'
    commandResult.value = `加载账户概览失败: ${error.message}`
    resultStatus.value = 'danger'
  }
}

onMounted(() => {
  loadAccountSummary()
  loadOrders()
})
</script>

<style scoped>
.page-view {
  min-height: 100%;
}

.overview-command-panel {
  border-color: rgba(57, 172, 201, 0.42);
  box-shadow: 0 24px 46px rgba(0, 0, 0, 0.42), 0 0 0 1px rgba(57, 172, 201, 0.16) inset;
}

.overview-command-panel .panel-head {
  background:
    radial-gradient(circle at 94% 20%, rgba(46, 167, 194, 0.24), transparent 50%),
    linear-gradient(180deg, rgba(19, 34, 55, 0.95), rgba(14, 27, 45, 0.95));
}

.overview-command-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
  gap: 16px;
  align-items: flex-start;
}

.overview-command-main-column {
  min-width: 0;
}

.overview-command-stage-strip {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 0;
}

.overview-stage-chip {
  height: 28px;
  border-radius: 999px;
  border: 1px solid rgba(110, 139, 168, 0.36);
  background: rgba(14, 24, 40, 0.68);
  color: #849ab6;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
}

.overview-stage-chip.active {
  border-color: rgba(46, 167, 194, 0.58);
  background: rgba(46, 167, 194, 0.18);
  color: #5dd9e8;
}

.overview-command-main > .field,
.overview-command-main > .command-actions,
.overview-command-main > .command-confirm-row,
.overview-command-main > .result-box {
  margin-top: 12px;
}

.overview-command-main > .command-actions {
  margin-top: 14px;
}

.overview-control-strip {
  display: grid;
  grid-template-columns: 170px 170px minmax(0, 1fr);
  gap: 12px;
  align-items: end;
}

.overview-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  color: #8da4bf;
  font-size: 12px;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  border: 1px solid rgba(114, 143, 173, 0.4);
  border-radius: 10px;
  background: rgba(13, 25, 42, 0.92);
  color: #e3effd;
  padding: 9px 10px;
  font-size: 13px;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  border-color: rgba(88, 174, 220, 0.82);
  box-shadow: 0 0 0 3px rgba(54, 140, 205, 0.2);
  outline: none;
}

.field textarea {
  resize: vertical;
  min-height: 112px;
}

.command-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.btn {
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 0 14px;
  height: 36px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
}

.btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #2ac9a9, #277dc1);
  color: #eafffb;
  box-shadow: 0 12px 24px rgba(23, 116, 178, 0.35);
}

.btn-primary:hover:not(:disabled) {
  box-shadow: 0 14px 30px rgba(19, 95, 156, 0.42);
  transform: translateY(-1px);
}

.btn-secondary {
  background: rgba(19, 34, 56, 0.92);
  color: #a4c5e2;
  border-color: rgba(114, 143, 173, 0.42);
}

.btn-secondary:hover:not(:disabled) {
  border-color: rgba(82, 182, 225, 0.58);
  color: #c6ecff;
}

.btn-emphasis {
  min-width: 150px;
}

.command-confirm-row {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.approval-inline {
  display: none;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.approval-inline.show {
  display: inline-flex;
}

.approval-input {
  border: 1px solid rgba(114, 143, 173, 0.4);
  border-radius: 10px;
  background: rgba(13, 25, 42, 0.92);
  color: #e3effd;
  height: 34px;
  padding: 0 10px;
  font-size: 12px;
  max-width: 140px;
}

.approval-input:focus {
  border-color: rgba(88, 174, 220, 0.82);
  box-shadow: 0 0 0 3px rgba(54, 140, 205, 0.2);
  outline: none;
}

.item-meta {
  margin-top: 2px;
  color: #849cb6;
  font-size: 12px;
  line-height: 1.3;
}

.result-box {
  margin-top: 12px;
  border-radius: 10px;
  border: 1px solid rgba(111, 141, 172, 0.3);
  background: rgba(12, 22, 37, 0.68);
  color: #a4bdd7;
  padding: 11px 12px;
  font-size: 13px;
  line-height: 1.6;
  min-height: 58px;
  white-space: pre-wrap;
}

.result-box.success {
  border-color: rgba(85, 204, 142, 0.54);
  background: rgba(65, 187, 126, 0.12);
  color: #a2f1c8;
}

.result-box.warning {
  border-color: rgba(245, 173, 75, 0.58);
  background: rgba(245, 173, 75, 0.12);
  color: #ffc06a;
}

.result-box.danger {
  border-color: rgba(240, 109, 122, 0.58);
  background: rgba(240, 109, 122, 0.12);
  color: #ffc2cb;
}

.result-box.info {
  border-color: rgba(82, 182, 225, 0.56);
  background: rgba(82, 182, 225, 0.12);
  color: #c6ecff;
}

.overview-side-stack {
  display: grid;
  gap: 10px;
  align-content: start;
}

.agent-collab-card {
  border: 1px solid rgba(110, 139, 167, 0.34);
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(16, 28, 47, 0.96), rgba(12, 22, 38, 0.96)),
    radial-gradient(circle at top right, rgba(45, 164, 154, 0.08), transparent 38%);
  overflow: hidden;
}

.agent-collab-card > summary {
  list-style: none;
  cursor: pointer;
  position: relative;
  padding: 14px;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.agent-collab-card > summary::-webkit-details-marker {
  display: none;
}

.agent-collab-card > summary::after {
  content: "展开";
  position: absolute;
  top: 14px;
  right: 14px;
  color: #6a849d;
  font-size: 11px;
  font-weight: 700;
}

.agent-collab-card[open] > summary::after {
  content: "收起";
}

.agent-collab-card-copy {
  padding-right: 52px;
}

.agent-collab-card-kicker {
  color: #6c88a3;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.32px;
}

.agent-collab-card-title-row {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.agent-collab-card-title-row h4 {
  margin: 0;
  color: #dceaf9;
  font-size: 14px;
  line-height: 1.35;
}

.agent-collab-card-badge {
  height: 22px;
  border-radius: 999px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(110, 139, 167, 0.38);
  background: rgba(16, 30, 50, 0.78);
  color: #96aec8;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.agent-collab-card-badge.state-idle {
  border-color: rgba(110, 139, 167, 0.38);
  background: rgba(16, 30, 50, 0.78);
  color: #96aec8;
}

.agent-collab-card-badge.state-active {
  border-color: rgba(82, 182, 225, 0.56);
  background: rgba(82, 182, 225, 0.18);
  color: #c6ecff;
}

.agent-collab-card-badge.state-blocked {
  border-color: rgba(240, 109, 122, 0.58);
  background: rgba(240, 109, 122, 0.18);
  color: #ffc2cb;
}

.agent-collab-card-badge.state-completed {
  border-color: rgba(85, 204, 142, 0.54);
  background: rgba(65, 187, 126, 0.18);
  color: #a2f1c8;
}

.agent-collab-card-peek {
  margin: 6px 0 0;
  color: #6c849d;
  font-size: 11px;
  line-height: 1.4;
}

.overview-side-fold-card .overview-side-card-body,
.overview-side-nav-card .overview-side-card-body {
  padding: 0 14px 14px;
  border-top: 1px solid rgba(107, 136, 164, 0.18);
  background: linear-gradient(180deg, rgba(12, 22, 37, 0.24), rgba(10, 19, 33, 0));
}

.agent-collab-card-body {
  padding: 0 14px 14px;
  border-top: 1px solid rgba(110, 139, 167, 0.22);
}

.agent-collab-summary {
  margin-top: 10px;
  border: 1px solid rgba(107, 136, 164, 0.18);
  background: rgba(13, 24, 39, 0.68);
  color: #a4bdd7;
  border-radius: 11px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
}

.agent-collab-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.agent-stage-card {
  border: 1px solid rgba(107, 136, 164, 0.18);
  border-radius: 10px;
  background: rgba(13, 24, 39, 0.68);
  padding: 10px 8px;
  text-align: center;
}

.agent-stage-name {
  color: #849ab6;
  font-size: 10px;
  font-weight: 600;
  margin-bottom: 4px;
}

.agent-stage-status {
  color: #6c849d;
  font-size: 10px;
}

.agent-stage-status.state-active {
  color: #5dd9e8;
}

.agent-stage-status.state-completed {
  color: #82e8b0;
}

.agent-stage-status.state-blocked {
  color: #ffc2cb;
}

.overview-command-rail {
  display: grid;
  gap: 12px;
}

.overview-command-rail .trace-panel {
  margin-top: 0;
}

.decision-card {
  border: 1px solid rgba(107, 136, 164, 0.24);
  border-radius: 12px;
  background: rgba(13, 24, 39, 0.68);
  padding: 12px;
}

.decision-title {
  margin: 0;
  color: #dceaf9;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
}

.decision-subtitle {
  margin: 4px 0 0;
  color: #6c849d;
  font-size: 11px;
  line-height: 1.4;
}

.decision-placeholder {
  border: 1px dashed rgba(107, 136, 164, 0.3);
  border-radius: 10px;
  background: rgba(12, 22, 37, 0.68);
  color: #6c849d;
  padding: 12px;
  margin-top: 8px;
  font-size: 12px;
  min-height: 48px;
  white-space: pre-wrap;
}

.trace-panel {
  margin-top: 12px;
  border: 1px solid rgba(107, 141, 172, 0.3);
  border-radius: 12px;
  background: rgba(13, 24, 39, 0.68);
  padding: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  border: 1px solid rgba(107, 136, 164, 0.18);
  border-radius: 12px;
  background: rgba(13, 24, 39, 0.68);
  padding: 12px;
  min-height: 80px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  color: #6c849d;
  font-size: 11px;
  font-weight: 600;
}

.stat-value {
  color: #dceaf9;
  font-size: 18px;
  font-weight: 700;
  margin-top: 4px;
}

.overview-priority-tip {
  border: 1px solid rgba(245, 173, 75, 0.28);
  background: rgba(245, 173, 75, 0.08);
  color: #dfb06d;
  border-radius: 11px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  margin: 12px 0 10px;
}

.overview-focus-list {
  display: grid;
  gap: 10px;
}

.overview-focus-card {
  width: 100%;
  border: 1px solid rgba(107, 136, 164, 0.18);
  border-left: 4px solid rgba(245, 173, 75, 0.58);
  border-radius: 12px;
  background: rgba(13, 24, 39, 0.68);
  padding: 11px;
  text-align: left;
  color: #a4bdd7;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.overview-focus-card:hover {
  border-color: rgba(245, 173, 75, 0.48);
  background: rgba(245, 173, 75, 0.08);
}

.overview-side-section {
  border: 1px solid rgba(107, 136, 164, 0.18);
  border-radius: 12px;
  background: rgba(13, 24, 39, 0.42);
  padding: 12px;
}

.overview-side-section + .overview-side-section {
  margin-top: 12px;
}

.overview-side-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.overview-side-section-head h5 {
  margin: 0;
  color: #dceaf9;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.overview-side-section .list-box {
  background: transparent;
  border: 0;
  padding: 0;
  gap: 8px;
}

.list-box {
  display: grid;
  gap: 8px;
}

.list-item {
  border: 1px solid rgba(107, 136, 164, 0.18);
  border-radius: 11px;
  padding: 10px 12px;
  background: rgba(13, 24, 39, 0.42);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  color: #a4bdd7;
  font-size: 12px;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.search-row select,
.search-row input {
  flex: 1;
  border: 1px solid rgba(114, 143, 173, 0.4);
  border-radius: 10px;
  padding: 9px 10px;
  font-size: 13px;
  background: rgba(13, 25, 42, 0.92);
  color: #e3effd;
}

.search-row select {
  flex: 0 0 156px;
  background: rgba(13, 25, 42, 0.92);
}

.search-row select:focus,
.search-row input:focus {
  border-color: rgba(88, 174, 220, 0.82);
  box-shadow: 0 0 0 3px rgba(54, 140, 205, 0.2);
  outline: none;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-action {
  border: 1px solid rgba(107, 136, 164, 0.24);
  background: rgba(13, 24, 39, 0.68);
  border-radius: 12px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #a4c5e2;
  font-size: 12px;
  font-weight: 600;
}

.quick-action:hover {
  border-color: rgba(82, 182, 225, 0.48);
  background: rgba(82, 182, 225, 0.12);
  color: #c6ecff;
}

.badge {
  min-width: 52px;
  height: 24px;
  border-radius: 999px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid;
}

.badge.success {
  border-color: rgba(94, 211, 145, 0.44);
  background: rgba(63, 193, 130, 0.16);
  color: #82e8b0;
}

.badge.info {
  border-color: rgba(125, 157, 211, 0.45);
  background: rgba(125, 157, 211, 0.14);
  color: #a9c2ea;
}

@media (max-width: 1024px) {
  .overview-command-layout {
    grid-template-columns: 1fr;
  }

  .overview-control-strip {
    grid-template-columns: 1fr;
  }

  .agent-collab-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .overview-command-stage-strip {
    flex-direction: column;
    gap: 4px;
  }

  .agent-collab-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .approval-inline {
    width: 100%;
  }

  .approval-input {
    max-width: none;
    width: 100%;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
