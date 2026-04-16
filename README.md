# AITradeX Java Edition

**AITradeX** - 智能量化交易终端，让 AI 驱动的量化交易更简单。

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (frontend/)                      │
│    login.html / register.html / index.html (dashboard)      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端服务                       │
│                    aitradex-server/                          │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Controller  │  │  Service    │  │    AI Module       │  │
│  │   Layer     │  │   Layer     │  │ (LangChain4j)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│         │               │                    │              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Repository Layer (JDBC)                 │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐                ┌─────────────────────────┐
│   PostgreSQL    │                │        Redis           │
│  (业务数据)      │                │      (缓存)            │
└─────────────────┘                └─────────────────────────┘
         │
         ▼
┌─────────────────┐
│     Milvus      │
│  (向量数据库)     │
└─────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    决策交易引擎 (FinancialAgent)              │
│                                                              │
│   用户请求 → AI 理解 → 工作流节点选择 → 工具执行 → 结果反馈     │
│                                                              │
│   工作流定义存储在 PostgreSQL，运行时由 FinancialAgent 调度     │
└─────────────────────────────────────────────────────────────┘
```

## 核心功能

| 功能模块 | 说明 |
|---------|------|
| **决策交易引擎** | 基于工作流的 AI 决策交易，FinancialAgent 智能调度 |
| **工作流引擎** | 可视化工作流设计，节点图结构管理 |
| **Skill 管理** | AI 助手技能配置，提示词模板与工具绑定 |
| **通知渠道** | 飞书、企业微信 Webhook 消息通知，支持交易信号推送 |
| **券商管理** | paper / gtja / okx / usstock / real 多通道切换 |
| **指令交易** | 中英文自然语言指令，买入/卖出/策略运行 |
| **AI 智能助手** | 多服务商支持（OpenAI、MiniMax、Custom） |
| **K 线图展示** | 多种时间周期，实时行情联动 |
| **风控配置** | 最大持仓数量、最大交易金额、做空权限 |
| **知识库** | PDF/文本文档向量化存储，Milvus 向量检索 |
| **MCP 工具** | AI 工具扩展，支持自定义 MCP 工具注册 |
| **数据大屏** | 专业可视化大屏，实时交易动态 |

## 决策交易流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 用户请求  │ ──▶ │ AI 理解   │ ──▶ │ 工作流节点 │ ──▶ │ 工具执行  │ ──▶ │ 结果反馈  │
└──────────┘     └──────────┘     └──────────┘     └──────────┘     └──────────┘
                      │                                                 ▲
                      │                                                 │
                      └─────────────────────────────────────────────────┘
                                     (循环迭代最多 4 轮)
```

**FinancialAgent 工具集：**
- `get_broker_mode` - 查看当前券商模式
- `get_risk_rules` - 查看风险规则
- `get_monitor_summary` - 查看账户与订单总览
- `get_active_account` - 查看当前激活账户
- `search_market_quote` - 搜索标的
- `get_market_quote` - 查询单个标的行情
- `get_market_kline` - 查询 K 线
- `get_okx_portfolio` - 查看 OKX 持仓
- `run_trade_command` - 执行或解析交易指令
- `run_strategy` - 执行策略

## 快速启动

### 方式一：Docker Compose 一键启动

```bash
cp .env.example .env
docker compose up --build -d
```

### 方式二：本地运行（依赖 Docker 中的数据库）

```bash
# 先启动数据库
docker compose up -d postgres redis

# 编译后端
cd aitradex-server
mvn clean package -DskipTests

# 启动服务
java -jar target/aitradex-java-1.0.0.jar
```

访问控制台：**http://localhost:8000/**

## 目录结构

```
AITradeX/
├── aitradex-server/          # Spring Boot 后端服务
│   ├── src/main/java/
│   │   └── com/
│   │       ├── controller/    # 8 个控制器
│   │       ├── service/       # 10 个服务类
│   │       ├── repository/     # 8 个数据访问类
│   │       ├── ai/            # AI 模块（Provider/Factory/Service）
│   │       ├── config/        # 配置类
│   │       ├── common/        # 异常处理、API 响应
│   │       ├── system/        # 系统模块（用户/权限）
│   │       └── domain/        │ 实体、请求、响应 DTO
│   │                           │  (WorkflowDefinition/WorkflowGraph/McpTool)
│   └── src/main/resources/
│       ├── application.yml    # 应用配置
│       └── logback-spring.xml # 日志配置
│
├── frontend/src/              # 前端源码
│   └── pages/
│       ├── index.html        # 主控台/Dashboard
│       ├── login.html        # 登录页面
│       └── register.html     # 注册页面
│
├── infra/postgres/init/      # 数据库初始化脚本
│   ├── 001_init.sql
│   ├── 002_market_data.sql
│   ├── 003_backtest_report.sql
│   ├── 004_broker_account.sql
│   ├── 005_system_setting.sql
│   ├── 006_sys_user.sql
│   ├── 007_ai_admin_modules.sql
│   ├── 008_ai_config.sql
│   ├── 009_skill.sql
│   └── 010_notification_channel.sql
│
├── docker-compose.yml         # 容器编排
├── .env                      # 环境变量配置
└── README.md
```

## 核心接口

### 认证接口
| 接口 | 方法 | 说明 |
|------|------|------|
| `/login` | GET | 登录页面 |
| `/register` | GET | 注册页面 |
| `POST /api/auth/login` | POST | 用户登录 |
| `POST /api/auth/register` | POST | 用户注册 |

### 券商管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/broker/mode` | GET | 获取当前券商模式 |
| `POST /api/broker/switch` | POST | 切换券商通道 |
| `POST /api/broker/accounts` | POST | 创建券商账户 |
| `GET /api/broker/accounts` | GET | 获取券商账户列表 |
| `GET /api/broker/okx/portfolio` | GET | OKX 真实持仓查询 |

### 行情与交易
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/market/quote/{symbol}` | GET | 实时行情 |
| `GET /api/market/kline/{symbol}` | GET | K 线数据 |
| `POST /api/trade/command` | POST | 交易指令执行 |
| `GET /api/trade/orders` | GET | 历史订单查询 |

### 监控与风控
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/monitor/summary` | GET | 监控总览 |
| `GET /api/monitor/orders` | GET | 订单监控 |
| `GET /api/risk/rules` | GET | 风控规则 |

### AI 功能
| 接口 | 方法 | 说明 |
|------|------|------|
| `POST /api/ai/chat` | POST | AI 对话 |
| `POST /api/ai/config` | POST | AI 配置 |
| `GET /api/ai/models` | GET | 可用模型列表 |
| `POST /api/ai/test` | POST | AI 连接测试 |

### 知识库（向量数据库）
| 接口 | 方法 | 说明 |
|------|------|------|
| `POST /api/admin/knowledge/document` | POST | 创建知识文档 |
| `GET /api/admin/knowledge/document/{id}` | GET | 获取文档详情 |
| `POST /api/admin/knowledge/base` | POST | 创建知识库 |
| `GET /api/admin/knowledge/base` | GET | 获取知识库列表 |

### 工作流引擎
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/admin/workflows` | GET | 获取工作流列表 |
| `POST /api/admin/workflows` | POST | 创建工作流 |
| `PUT /api/admin/workflows/{id}` | PUT | 更新工作流 |
| `DELETE /api/admin/workflows/{id}` | DELETE | 删除工作流 |
| `GET /api/admin/workflows/nodes` | GET | 获取工作流节点列表 |
| `GET /api/admin/workflows/{id}/graph` | GET | 获取工作流图结构 |
| `PUT /api/admin/workflows/{id}/graph` | PUT | 更新工作流图结构 |

### MCP 工具管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/admin/mcp/tools` | GET | 获取 MCP 工具列表 |
| `POST /api/admin/mcp/tools` | POST | 创建 MCP 工具 |
| `PUT /api/admin/mcp/tools/{id}` | PUT | 更新 MCP 工具 |
| `DELETE /api/admin/mcp/tools/{id}` | DELETE | 删除 MCP 工具 |

### Skill 管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/admin/skills` | GET | 获取 Skill 列表 |
| `GET /api/admin/skills/{id}` | GET | 获取 Skill 详情 |
| `POST /api/admin/skills` | POST | 创建 Skill |
| `PUT /api/admin/skills/{id}` | PUT | 更新 Skill |
| `DELETE /api/admin/skills/{id}` | DELETE | 删除 Skill |

### 通知渠道管理
| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /api/admin/notification-channels` | GET | 获取通知渠道列表 |
| `GET /api/admin/notification-channels/{id}` | GET | 获取通知渠道详情 |
| `POST /api/admin/notification-channels` | POST | 创建通知渠道 |
| `PUT /api/admin/notification-channels/{id}` | PUT | 更新通知渠道 |
| `DELETE /api/admin/notification-channels/{id}` | DELETE | 删除通知渠道 |

## 环境变量配置

```bash
# 数据库
POSTGRES_DB=aitradex
POSTGRES_USER=aitradex
POSTGRES_PASSWORD=aitradex
JDBC_DATABASE_URL=jdbc:postgresql://localhost:5432/aitradex

# Redis
REDIS_URL=redis://localhost:6379/0

# 服务
APP_HOST=0.0.0.0
APP_PORT=8000

# 券商模式 (paper/real)
BROKER_MODE=paper

# AI 配置
AI_DEFAULT_PROVIDER=openai
AI_DEFAULT_MODEL=gpt-3.5-turbo
OPENAI_API_KEY=your_openai_key
OPENAI_BASE_URL=https://api.openai.com/v1
MINIMAX_API_KEY=your_minimax_key

# JWT
JWT_SECRET=your-256-bit-secret-key
JWT_EXPIRE_IN=604800

# 向量数据库 (Milvus)
KNOWLEDGE_MILVUS_HOST=localhost
KNOWLEDGE_MILVUS_PORT=19530
KNOWLEDGE_EMBEDDING_DIM=384
```

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.3.5 |
| 编程语言 | Java | 17 |
| 构建工具 | Maven | 3.8+ |
| 数据库 | PostgreSQL | 16 |
| 缓存 | Redis | 7 |
| 向量数据库 | Milvus | 2.4 |
| AI 框架 | LangChain4j | 0.35.0 |
| 前端 | HTML/CSS/JS | - |
| 容器 | Docker Compose | - |

## 开发说明

### 环境要求
- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 16
- Redis 7

### 编译打包
```bash
cd aitradex-server
mvn clean package -DskipTests
```

### 启动服务
```bash
java -jar target/aitradex-java-1.0.0.jar
```

### 查看日志
```bash
tail -f aitradex-server/logs/aitradex.log
tail -f aitradex-server/logs/aitradex-error.log
```

## 项目质量指标

| 指标 | 状态 |
|------|------|
| 分层架构 | ✅ Controller-Service-Repository |
| 异常处理 | ✅ 全局异常处理器 + 统一响应 |
| 日志系统 | ✅ Logback 多级别日志分类 |
| 密码加密 | ✅ BCrypt |
| JWT 认证 | ✅ Token 验证 |
| AI 模块 | ✅ 策略模式 + 工厂模式 |
| 向量数据库 | ✅ Milvus SDK 集成 |
| 配置管理 | ✅ 环境变量优先 |

## License

MIT License
