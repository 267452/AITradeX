# AITradeX Java Edition

**AITradeX** - 基于 Agent + Workflow + RAG 的 AI量化交易决策系统。

![AITradeX 系统界面](https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=AITradeX%20dashboard%20interface%20with%20dark%20theme%2C%20showing%20overview%20center%2C%20trade%20command%20platform%2C%20and%20module%20status%20panels&image_size=landscape_16_9)

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

## 项目亮点

- 基于 FinancialAgent 构建 Agent 决策引擎，实现自然语言交易请求到执行反馈的闭环
- 通过 Workflow Graph 实现 AI 决策路径编排，增强执行可控性与扩展性
- 基于 LangChain4j + MCP Tool 实现模型能力与外部工具的动态集成
- 结合 Milvus 向量数据库实现 RAG 检索增强，提升知识问答与策略解释能力

## 核心功能

AITradeX 以 FinancialAgent 为核心，构建"模型 + 工作流 + 工具"的三层AI能力体系，实现从用户指令到交易执行的闭环。

### 功能模块

- **总览中心**：数据大屏、AI 驱动的监控
- **业务流程**：工作流引擎、交易管理、指令交易
- **AI核心**：模型管理、对话管理
- **知识与工具**：知识管理、MCP 工具、Skill 管理
- **系统管理**：通知渠道、风控规则

### AI 能力

- **自然语言理解**：解析交易指令，理解用户意图
- **智能决策**：基于市场数据和历史表现，提供交易建议
- **风险评估**：AI 驱动的实时风险分析和拦截
- **知识检索**：向量数据库支持的智能文档问答
- **工具调用**：AI 自主选择和使用工具完成复杂任务

## 决策交易流程

```
用户输入
   ↓
意图识别 / 参数抽取
   ↓
FinancialAgent 调度
   ↓
Workflow 节点选择
   ↓
Tool / MCP 调用
   ↓
风控校验
   ↓
交易执行 / 结果反馈
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
│   │       ├── controller/    # 控制器层
│   │       │   ├── admin/     # 管理接口
│   │       │   ├── ai/        # AI 相关接口
│   │       │   ├── broker/    # 交易管理接口
│   │       │   ├── market/    # 行情接口
│   │       │   ├── monitor/   # 监控接口
│   │       │   ├── risk/      # 风控接口
│   │       │   ├── trade/     # 交易接口
│   │       │   └── user/      # 用户接口
│   │       ├── service/       # 服务层
│   │       │   ├── ai/        # AI 核心服务
│   │       │   ├── broker/    # 交易管理服务
│   │       │   ├── knowledge/ # 知识管理服务
│   │       │   ├── market/    # 行情服务
│   │       │   ├── risk/      # 风控服务
│   │       │   ├── skill/     # Skill 管理服务
│   │       │   └── trade/     # 交易服务
│   │       ├── repository/     # 数据访问层
│   │       ├── ai/            # AI 模块（Provider/Factory/Service）
│   │       ├── config/        # 配置类
│   │       ├── common/        # 异常处理、API 响应
│   │       ├── system/        # 系统模块（用户/权限）
│   │       └── domain/        # 实体、请求、响应 DTO
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
│   ├── 010_notification_channel.sql
│   └── 011_risk_rule.sql     # 风控规则表
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

## 工程化能力

| 能力 | 实现 |
|------|------|
| 分层架构 | ✅ Controller-Service-Repository |
| 异常处理 | ✅ 全局异常处理器 + 统一响应 |
| 日志系统 | ✅ Logback 多级别日志分类 |
| 密码加密 | ✅ BCrypt |
| JWT 认证 | ✅ Token 验证 |
| AI 模块 | ✅ 策略模式 + 工厂模式 |
| 向量数据库 | ✅ Milvus SDK 集成 |
| 配置管理 | ✅ 环境变量优先 |

## 项目特点

- 支持 Docker Compose 一键启动，降低本地部署成本
- 支持多券商模式切换与统一交易入口封装
- 支持知识库、Skill、MCP Tool、通知渠道等模块化扩展
- 后端采用分层架构，具备较好的可维护性与扩展性

## 设计与技术挑战

- **如何将大模型能力与交易系统解耦**  
  → 通过 FinancialAgent + Workflow 实现调度与执行分离

- **如何保证 AI 决策可控**  
  → 引入 Workflow 节点限制执行路径，结合风控规则进行拦截

- **如何扩展 AI 能力**  
  → 基于 MCP Tool 机制，实现工具注册与动态调用

- **如何提升策略解释性**  
  → 结合知识库（Milvus）进行 RAG 检索增强

## License

MIT License
