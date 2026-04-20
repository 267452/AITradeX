# AITradeX Java Edition

AITradeX 是一个基于 `Multi-Agent + Workflow + RAG` 的 AI 量化交易决策系统，提供从自然语言请求到风控校验、下单执行、通知回传、监控总览的闭环能力。

![AITradeX Dashboard](img.png)

## 项目定位

- 面向交易场景的 AI 助手与执行中台
- 支持模型管理、知识库、MCP 工具、Skill、工作流可视化编排
- 支持纸面交易与多券商模式统一接入
- 支持风控规则管理与执行前拦截

## 核心特性

- Agent-first 决策链：Intent Router / Market Analyst / Risk Guardian / Execution Agent / Summary Agent
- 对常见交易指令、账户查询、风控查询支持规则直达，AI 不可用时仍可走确定性链路
- 风控支持“预检不落状态”，分析模式不会污染真实频次和执行上下文
- 实盘审批闸门：实盘模式下禁止 `/api/ai/chat-and-execute` 直达执行，必须走 `/api/ai/confirm-execute`（复核人 + 审批口令）
- 工作流图可视化编排与拓扑持久化
- Agent 质量评分：内置成功率、风控拒绝率、平均/P95 延迟聚合与趋势序列接口
- 行情检索支持 A 股、可转债、港股、美股、期货、区块链
- 交易链路完整：信号 -> 风控 -> 订单 -> 成交 -> 持仓/账户快照
- 知识文档解析 + 向量化写入（Milvus）
- 通知渠道（飞书/企业微信 Webhook）

## 系统架构

### 技术架构图

```mermaid
flowchart TB
  UI["前端 Dashboard\nfrontend/src/pages"] --> API["Spring Boot API\naitradex-server"]
  API --> PG[(PostgreSQL)]
  API --> Redis[(Redis)]
  API --> Milvus[(Milvus 可选)]

  subgraph API["Spring Boot API"]
    C[Controller]
    S[Service]
    R[Repository JDBC]
    AI[AI Module\nProvider/Factory/Agent]
    C --> S --> R
    S --> AI
  end
```

### 模块总图

```mermaid
flowchart LR
U["用户请求"] --> C["对话管理 (conversation_id)"]
U --> W["工作流选择 (workflow_id)"]
C --> X["工作流执行器 (run_id)"]
W --> X

X --> M["模型管理 (LLM)"]
X --> K["知识管理 (RAG)"]
X --> P["MCP 管理 (工具)"]
X --> S["Skill 管理 (策略模板)"]

M --> D["决策草案"]
K --> D
P --> D
S --> D

D --> R["风控规则"]
R -->|通过| T["交易管理 (order_id)"]
R -->|拒绝| N["通知渠道"]

T --> N
X --> O["总览中心"]
T --> O
N --> O
C --> O
```

### 一次执行时序

```mermaid
sequenceDiagram
participant UI as 前端
participant AI as /api/ai/chat-and-execute
participant WF as WorkflowExecutor
participant CAP as 模型/知识/MCP/Skill
participant RISK as RiskService
participant TRADE as TradeService
participant NOTI as NotificationService
participant MON as 总览

UI->>AI: message + conversation_id + workflow_id
AI->>WF: 创建 run_id，写 workflow_run
WF->>CAP: Intent Router / Market Analyst / Risk Guardian / Execution Agent
CAP-->>WF: 决策卡片 / 角色 trace / 工具结果
WF->>RISK: 风控校验(run_id, conversation_id)
alt 风控通过
RISK-->>WF: pass
WF->>TRADE: 下单(run_id, conversation_id)
TRADE-->>WF: signal_id + order_id
WF->>NOTI: 发送成交/风控通知
else 风控拒绝
RISK-->>WF: reject(reason)
WF->>NOTI: 发送拒绝通知
end
WF-->>AI: result + run_id + order_id?
AI-->>UI: 可追踪响应
UI->>MON: 按 run_id/order_id 拉总览
```

## 当前实现说明

- 工作流模块当前已实现定义管理、节点拓扑编辑与存储。
- AI 执行入口已改造为多 Agent 编排主链，`FinancialAgentService` 作为薄入口转发给 `TradingDecisionOrchestrator`。
- 当前 agent 角色为：
  - `intent_router`：意图识别与任务路由
  - `market_analyst`：行情、账户、上下文事实收集
  - `risk_guardian`：生成候选信号并做无副作用风控预检
  - `execution_agent`：分析模式输出建议，执行模式接入真实交易链
  - `summary_agent`：把结构化结果汇总成用户可读回复
- 每个 agent 阶段都会落到 `workflow_run_step`，可用于回放、评估和后续 A/B。
- 知识文档 `trigger_parse=true` 时会尝试写入 Milvus；未启用解析时不依赖 Milvus。
- 风控存在两套视角：
  - `/api/admin/risk/*`：规则配置管理（`risk_rule` 表）
  - `/api/monitor/risk/rules`：运行时阈值快照（来自 `app.*` 配置）

## Agent-First 改造方向

当前版本已经把系统主链从“单一大 prompt”切到了“多角色协作 + 结构化决策卡片”。如果继续往真正的 agent 决策交易系统演进，最值得优先补的点是：

- 组合级 agent memory：把账户持仓、近 N 次决策、失败原因、滑点表现沉淀成长期记忆，而不是只看单次请求。
- Human approval gate：已落地基础审批闸门（复核人 + 审批口令）；下一步可升级为“审批人权限分级 + 超时/撤销 + 双签审计”。
- Research agent 扩展：把新闻、公告、财报、宏观事件接入统一 research agent，而不只依赖价格和 K 线。
- Portfolio construction agent：从“单笔信号是否可下”升级为“当前组合下最合适的仓位与调仓路径”。
- Post-trade review agent：对每次执行做复盘，评估触发理由、风控质量、成交偏差、PnL 归因。
- Model routing / ensemble：不同 agent 使用不同模型与温度，例如路由 agent 偏确定性，summary agent 偏表达，review agent 偏严格约束。

## 功能模块（与前端侧边栏对应）

- 总览中心：系统状态、订单与资产摘要
- 业务流程：工作流、交易管理
- AI 核心：模型管理、对话管理
- 知识与工具：知识库、MCP、Skill
- 系统管理：通知渠道、风控规则

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.8+
- Docker + Docker Compose
- 可选：Milvus（仅在文档解析入库时需要）

### 方式一：Docker Compose 一键启动

```bash
cp .env.example .env
docker compose up --build -d
```

访问地址：`http://localhost:8000/`

若构建时遇到 Docker Hub 拉取超时（`failed to fetch anonymous token`），可先使用“方式二”本地运行 API，再重试 Compose。

### 方式二：本地运行 API（依赖 Docker 数据库）

```bash
cp .env.example .env

docker compose up -d postgres redis

cd aitradex-server
mvn clean package -DskipTests
java -jar target/aitradex-java-1.0.0.jar
```

健康检查：

```bash
curl http://localhost:8000/api/system/health
```

### 执行链路校验脚本

新增脚本：`scripts/verify_execution_chain.sh`

用途：
- 自动登录（默认 `admin/admin123`，也可传 `JWT_TOKEN`）
- 调用 `/api/ai/chat-and-execute`
- 按 `run_id` 校验 `workflow_run`、`workflow_run_step`、`strategy_signal`、`trade_order`、`risk_check_log`

注意：
- 当前版本在实盘模式下会拦截 `/api/ai/chat-and-execute`，返回 `approval_required=true`（预期行为）。
- 若要继续使用脚本做“自动执行链路校验”，请先切到 `paper` 模式；或改用 `/api/ai/chat` + `/api/ai/confirm-execute` 两阶段校验。

示例：

```bash
# 基础校验（run/step 必须存在）
scripts/verify_execution_chain.sh

# 严格校验（要求 risk/signal/order 都有落库）
STRICT_TRADE=1 scripts/verify_execution_chain.sh

# 指定消息与环境
CHAT_MESSAGE="请执行交易命令：买入 000001 100" \
API_BASE_URL="http://localhost:8000" \
scripts/verify_execution_chain.sh
```

### 实盘审批链路快速验证

```bash
# 1) 登录拿 token
TOKEN=$(curl -sS -X POST "http://localhost:8000/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.access_token')

# 2) 直接执行会被拦截（预期：approval_required=true）
curl -sS -X POST "http://localhost:8000/api/ai/chat-and-execute" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"买入 600519 100 股","conversation_id":1,"workflow_id":1}'

# 3) 走确认执行接口（注意字段是 command，不是 message）
curl -sS -X POST "http://localhost:8000/api/ai/confirm-execute" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"command":"买入 600519 100 股","conversation_id":1,"workflow_id":1,"co_approver":"ops-user","approval_passphrase":"your-passphrase","approval_note":"manual approval"}'
```

说明：
- 若未配置 `APP_EXECUTION_APPROVAL_PASSPHRASE`，第 3 步会返回“审批口令未配置”（预期行为）。
- 在生产环境建议强制配置审批口令，并结合审计日志保留复核轨迹。

## 配置说明

主要环境变量（见 `.env.example` 与 `application.yml`）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `APP_HOST` | `0.0.0.0` | 服务监听地址 |
| `APP_PORT` | `8000` | 服务端口 |
| `JDBC_DATABASE_URL` | `jdbc:postgresql://localhost:5432/aibuy` | PostgreSQL JDBC 地址 |
| `POSTGRES_USER` | `aibuy` | 数据库用户 |
| `POSTGRES_PASSWORD` | `aibuy` | 数据库密码 |
| `REDIS_URL` | `redis://localhost:6379/0` | Redis 连接地址 |
| `BROKER_MODE` | `paper` | 默认券商模式 |
| `RISK_MAX_QTY` | `100000` | 风控：最大数量 |
| `RISK_MAX_NOTIONAL` | `2000000` | 风控：最大金额 |
| `RISK_ALLOW_SHORT` | `false` | 风控：是否允许做空 |
| `APP_EXECUTION_APPROVAL_PASSPHRASE` | 空 | 实盘确认执行审批口令（建议在生产环境强制配置） |
| `OPENAI_API_KEY` | 空 | OpenAI API Key |
| `OPENAI_BASE_URL` | 空 | OpenAI Base URL |
| `MINIMAX_API_KEY` | 空 | MiniMax API Key |
| `MINIMAX_BASE_URL` | `https://api.minimaxi.com/v1` | MiniMax Base URL |
| `KNOWLEDGE_MILVUS_HOST` | `localhost` | Milvus 主机 |
| `KNOWLEDGE_MILVUS_PORT` | `19530` | Milvus 端口 |

## 目录结构

```text
AITradeX/
├── aitradex-server/                # Spring Boot 后端
│   ├── src/main/java/com/
│   │   ├── controller/             # API 路由层
│   │   ├── service/                # 业务逻辑层
│   │   ├── repository/             # JDBC 数据访问层
│   │   ├── ai/                     # AI Provider/Factory/Agent
│   │   ├── domain/                 # DTO / Entity
│   │   ├── config/                 # 配置与拦截器
│   │   └── common/                 # 统一响应、异常处理
│   └── src/main/resources/
│       └── application.yml
├── frontend/src/pages/             # login/register/dashboard 页面
├── infra/postgres/init/            # PostgreSQL 初始化脚本
├── docker-compose.yml
├── .env.example
└── README.md
```

## API 总览（按模块）

统一前缀：`/api`

### 认证与系统

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/auth/login` | 登录 |
| `POST` | `/auth/register` | 注册 |
| `POST` | `/auth/logout` | 退出 |
| `GET` | `/auth/userinfo` | 当前用户信息 |
| `GET` | `/system/health` | 系统健康检查 |

### 券商与账户

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/broker/mode` | 当前券商模式 |
| `POST` | `/broker/switch` | 切换券商模式 |
| `POST` | `/broker/accounts` | 创建账户 |
| `GET` | `/broker/accounts` | 账户列表 |
| `POST` | `/broker/accounts/{accountId}/activate` | 激活账户 |
| `GET` | `/broker/accounts/active` | 当前激活账户 |
| `GET` | `/broker/okx/real-data` | OKX 实盘数据 |
| `GET` | `/broker/okx/portfolio` | OKX 持仓快照 |

### 行情

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/market/quote/search` | 标的检索（需 `q`、`market`） |
| `GET` | `/market/quote/{symbol}` | 单标的行情 |
| `GET` | `/market/kline/{symbol}` | K 线数据 |
| `POST` | `/market/bars/import-csv` | 导入 CSV 行情 |
| `POST` | `/market/bars/simulate` | 生成模拟行情 |

`market` 支持值：`cn_stock`、`cn_convertible`、`crypto`、`futures`、`hk_stock`、`us_stock`

### 交易与回测

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/trade/signals` | 直接提交交易信号 |
| `GET` | `/trade/orders/{orderId}` | 查询订单详情 |
| `POST` | `/trade/trade/command` | 自然语言交易指令解析/执行 |
| `POST` | `/trade/strategy/run` | 执行策略并尝试下单 |
| `POST` | `/trade/backtest/sma` | SMA 回测 |
| `GET` | `/trade/backtest/reports` | 回测报告列表 |

### 监控

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/monitor/summary` | 监控摘要 |
| `GET` | `/monitor/orders` | 订单分页 |
| `GET` | `/monitor/risk/rules` | 运行时风控阈值快照 |
| `GET` | `/monitor/workflow-runs` | 工作流运行记录（支持 runId/workflowRunId/status/startedFrom/startedTo 过滤） |
| `GET` | `/monitor/workflow-runs/{runId}` | 指定 run_id 的回放详情（含步骤输入输出） |
| `GET` | `/monitor/workflow-quality` | Agent 质量评分聚合与趋势（成功率/拒绝率/延迟） |

### AI

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/ai/models` | 供应商/模型目录 |
| `GET` | `/ai/config` | 当前配置 |
| `GET` | `/ai/saved-configs` | 已保存配置 |
| `POST` | `/ai/config` | 保存模型配置 |
| `DELETE` | `/ai/config` | 清空当前配置 |
| `POST` | `/ai/test` | 连通性测试 |
| `POST` | `/ai/switch-model` | 切换模型 |
| `POST` | `/ai/chat` | AI 分析（不执行） |
| `POST` | `/ai/simple-chat` | 简单聊天 |
| `POST` | `/ai/chat-and-execute` | AI 分析并执行（实盘模式下会被审批闸门拦截） |
| `POST` | `/ai/confirm-execute` | 执行确认（支持 `co_approver`、`approval_passphrase`、`approval_note`） |

### 实盘审批执行流程

1. 调用 `/api/ai/chat` 生成建议指令与决策卡片（不落真实执行）。
2. 运营/交易员填写复核人和审批口令，调用 `/api/ai/confirm-execute`。
3. 服务端验证通过后才进入执行链，并把审批上下文（复核人、时间、签名）回写到执行结果与回放记录。

### 管理后台（知识、对话、MCP、工作流、Skill、通知）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/admin/knowledge/stats` | 知识统计 |
| `GET/POST` | `/admin/knowledge/bases` | 知识库列表/创建 |
| `PUT/DELETE` | `/admin/knowledge/bases/{id}` | 知识库更新/删除 |
| `GET/POST` | `/admin/knowledge/documents` | 文档列表/创建 |
| `GET/POST` | `/admin/conversations` | 对话列表/创建 |
| `PUT/DELETE` | `/admin/conversations/{id}` | 对话更新/删除 |
| `GET` | `/admin/conversations/insights` | 对话洞察 |
| `GET/POST` | `/admin/mcp/tools` | MCP 工具列表/创建 |
| `PUT/DELETE` | `/admin/mcp/tools/{id}` | MCP 工具更新/删除 |
| `GET/POST` | `/admin/mcp/markets` | MCP 市场列表/创建 |
| `PUT/DELETE` | `/admin/mcp/markets/{id}` | MCP 市场更新/删除 |
| `GET/POST` | `/admin/workflows` | 工作流列表/创建 |
| `PUT/DELETE` | `/admin/workflows/{id}` | 工作流更新/删除 |
| `GET` | `/admin/workflows/nodes` | 工作流节点列表 |
| `GET/PUT` | `/admin/workflows/{id}/graph` | 工作流拓扑读取/保存 |
| `GET/POST` | `/admin/skills` | Skill 列表/创建 |
| `GET/PUT/DELETE` | `/admin/skills/{id}` | Skill 详情/更新/删除 |
| `GET` | `/admin/skills/{id}/detail` | Skill 聚合详情 |
| `GET/PUT` | `/admin/skills/{id}/prompt` | Skill Prompt 读写 |
| `GET/PUT` | `/admin/skills/{id}/script` | Skill Script 读写 |
| `GET/POST` | `/admin/notification-channels` | 通知渠道列表/创建 |
| `GET/PUT/DELETE` | `/admin/notification-channels/{id}` | 通知渠道详情/更新/删除 |

### 风控规则管理

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/admin/risk/rules` | 风控规则列表 |
| `GET` | `/admin/risk/rules/{id}` | 风控规则详情 |
| `POST` | `/admin/risk/rules` | 创建风控规则 |
| `PUT` | `/admin/risk/rules/{id}` | 更新风控规则 |
| `PUT` | `/admin/risk/rules/{id}/toggle?enabled=true|false` | 启停规则 |
| `DELETE` | `/admin/risk/rules/{id}` | 删除规则 |

## 数据模型（核心表）

- 交易域：`strategy_signal`、`trade_order`、`trade_fill`、`position_snapshot`、`account_snapshot`
- 风控域：`risk_rule`、`risk_check_log`
- AI 配置：`ai_config`
- 工作流域：`workflow_definition`、`workflow_node_definition`
- 知识域：`knowledge_base`、`knowledge_document`
- 对话域：`conversation_session`
- 工具域：`mcp_tool`、`mcp_market`、`skill`
- 系统域：`notification_channel`、`broker_account`、`system_setting`、`sys_user`

## 开发与排障

### 常用命令

```bash
# 编译
cd aitradex-server
mvn clean package -DskipTests

# 本地运行
java -jar target/aitradex-java-1.0.0.jar

# 查看日志
tail -f aitradex-server/logs/aitradex.log
tail -f aitradex-server/logs/aitradex-error.log
```

### 典型检查点

- 无法登录：检查 `JWT_SECRET`、数据库用户表初始化与浏览器 token
- 行情检索失败：确认 `market` 参数与标的代码格式
- AI 无响应：确认模型配置与 API Key
- 文档解析失败：`trigger_parse=true` 时确认 Milvus 可用
- 通知未发送：确认渠道 `enabled=true` 且 webhook 配置有效

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Spring Boot 3.3.5 + Java 17 |
| 构建 | Maven |
| 数据库 | PostgreSQL 16 |
| 缓存 | Redis 7 |
| 向量库 | Milvus 2.x（可选） |
| AI | LangChain4j |
| 前端 | HTML/CSS/Vanilla JS |
| 部署 | Docker Compose |

## License

MIT License
