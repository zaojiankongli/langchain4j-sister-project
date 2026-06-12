# Sister Project

Sister Project 是一个 AI 伴侣全栈系统。它用 Spring Boot 3、Java 21、LangChain4j、通义千问、Milvus、Redis、MySQL 和 Vue 3，把实时对话、多模态理解、长期记忆、情绪状态、主动唤醒和小程序接入放进同一个后端架构中。这个 README 重点说明系统设计、关键链路和工程取舍，方便技术评审或面试官快速判断项目深度。

## 项目亮点

这个项目的重点不是调用一个大模型接口，而是把多个慢服务和有状态模块编排成稳定的产品链路：

- **双路记忆检索**：同时维护摘要记忆和 Graph RAG，上下文由向量检索、全文检索、实体关系检索和大模型重排共同生成
- **实时交互链路**：后端通过 STOMP over WebSocket 推送文本、音频、情绪、动作、表情和系统消息
- **情绪状态建模**：用 Pleasure、Arousal、Dominance 三维情绪模型驱动 TTS、主动交互和情绪锚点
- **外部调用隔离**：大模型、语音合成、Milvus、图片处理分别使用有界线程池，避免慢调用拖垮主链路
- **多端接入**：同一套后端同时服务 Web 前端、桌宠实时通道和微信小程序通道
- **生产配置治理**：敏感配置通过环境变量注入，Docker Compose 只保留统一入口，公开仓库不提交真实密钥

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端运行时 | Java 21、Virtual Threads、Maven |
| Web 框架 | Spring Boot 3.5.11、Spring Web、Spring WebSocket、Spring Validation |
| AI 编排 | LangChain4j 1.13.0-beta23、LangChain4j Reactor、Easy RAG、MCP、Agentic |
| 模型服务 | 通义千问 DashScope Chat、Streaming Chat、Vision、Embedding、TTS |
| 数据存储 | MySQL 8、MyBatis、Redis、Milvus |
| 并发与缓存 | Virtual Threads、ThreadPoolTaskExecutor、Redisson、Caffeine、Redis Stream |
| 文件与对象存储 | 阿里云 OSS |
| 安全 | JWT 双 Token、请求拦截器、环境变量配置、CORS 和 WebSocket 白名单 |
| 可观测性 | Spring Boot Actuator、Micrometer、Prometheus、队列健康检查 |
| 前端 | Vue 3、Vite 7、Pinia、Vue Router、Axios、SockJS、STOMP、GSAP、Live2D |

## 系统架构

系统按“接入层、编排层、领域层、基础设施层”拆分。接入层负责 REST、STOMP 和小程序 WebSocket；编排层负责编排大模型、记忆、情绪、TTS 和推送；领域层封装用户、记忆、情绪、锚点、推荐和唤醒；基础设施层连接 MySQL、Redis、Milvus、OSS 和 DashScope。

```text
Web / Live2D / 小程序
        │
        ▼
REST API + STOMP WebSocket + 小程序 WebSocket
        │
        ▼
Chat orchestration
        ├── LangChain4j streaming chat
        ├── Vision understanding
        ├── TTS streaming
        ├── Emotion engine
        ├── Summary memory RAG
        └── Graph RAG
        │
        ▼
MySQL + Redis + Milvus + OSS + DashScope
```

这种拆分让实时链路保持可控：用户消息进入后端后，系统可以并行准备记忆、情绪和多模态上下文，再把 LLM 的流式输出拆成文本、音频和状态事件推送给前端。

## 核心链路设计

### 实时聊天链路

聊天链路围绕“低等待时间”和“可降级”设计。文本回复通过 LangChain4j Streaming Chat Model 流式输出，后端再通过 STOMP 推给前端；音频合成和情绪更新不阻塞文本首包。

```text
用户输入
  │
  ├── 用户身份校验
  ├── 活跃状态更新
  ├── 图片或截图理解
  ├── 摘要记忆检索
  ├── Graph RAG 检索
  └── 情绪状态读取
        │
        ▼
Prompt 组装 → LLM 流式响应 → 文本推送 / TTS / 情绪更新 / 持久化
```

这个链路的工程点在于隔离慢操作。LLM、TTS、Milvus 和图片任务都有独立执行器，避免一个外部服务抖动导致 WebSocket 推送线程被占满。

### 长期记忆链路

长期记忆分成“摘要记忆”和“结构化图谱记忆”。摘要记忆把多轮对话压缩成可检索文本，Graph RAG 把对话里的实体、关系和来源片段拆成图谱向量集合。

检索时系统同时使用三类信号：

- **Dense vector**：用 embedding 匹配语义相似内容
- **Sparse BM25**：用全文检索保留关键词命中能力
- **Graph relation**：用实体和关系补充跨轮对话中的事实联系

`SummaryMemoryService` 使用 Milvus Hybrid Search，把 dense vector 和 sparse BM25 结果用 Reciprocal Rank Fusion (RRF) 融合。`GraphQueryService` 先用 LLM 抽取查询实体，再批量 embedding，最后检索实体、关系和来源片段，并限制候选关系数量，控制 Prompt 长度。

### 情绪引擎链路

情绪模块使用 Pleasure、Arousal、Dominance 三维状态表示角色心情。每次对话、主动唤醒或情绪锚点事件都可以改变情绪状态。

情绪状态不是只给前端展示。它参与三件事：

- **Prompt 上下文**：把当前情绪和心情标签注入角色设定
- **TTS 表达**：影响语音合成参数和播放风格
- **主动行为**：触发 WakeUp、Peek、Anchor 等主动互动

这种设计让“情绪”成为业务状态，而不是 UI 标签。

### 主动交互链路

主动交互由 WakeUp、Peek、Anchor 和 Recommendation 组成。WakeUp 根据沉默时间和冷却时间决定是否发起对话；Peek 请求前端截图并交给视觉模型分析；Anchor 根据情绪区域触发事件；Recommendation 用 Agentic 工作流生成推荐内容。

主动链路和聊天链路共享推送层。`ChatPushServiceImpl` 把文本、音频、情绪、动作和系统消息统一转成 WebSocket 消息，同时转发给小程序实时通道。

## 工程设计

### 并发模型

项目启用 Java 21 虚拟线程处理通用异步任务，同时为慢外部调用保留有界线程池。这个组合解决两个问题：通用任务不需要手写复杂线程管理，外部服务调用又不会无限扩张。

| 执行器 | 处理任务 | 设计目的 |
| --- | --- | --- |
| `asyncTaskExecutor` | 通用异步任务 | 用虚拟线程降低阻塞任务成本 |
| `llmTaskExecutor` | LLM 调用、摘要生成 | 限制大模型并发，保护主链路 |
| `ttsTaskExecutor` | 语音合成 | 防止音频生成挤占聊天线程 |
| `milvusTaskExecutor` | 向量库访问 | 隔离向量检索延迟 |
| `imageTaskExecutor` | 图片理解和生成 | 控制多模态任务队列长度 |

线程池使用 `CallerRunsPolicy` 做背压。队列满时调用方承担执行成本，系统不会静默丢任务。

### 一致性与分布式状态

系统把 Redis 用在三类场景：短期状态、队列和分布式协调。Redisson 提供分布式锁，避免多实例同时更新情绪、冷却时间或主动任务状态。

配置类通过 `@ConfigurationProperties` 绑定 `app.*` 前缀，减少散落的字符串配置。这样能把认证、Redis、Milvus、WebSocket、CORS、TTS、MCP 和线程池配置统一管理。

### 失败隔离

AI 应用最大的风险是外部服务不稳定。项目把大模型、向量库、TTS、OSS 和邮件服务放在可降级链路里：记忆检索失败时跳过记忆上下文，Graph RAG 失败时跳过图谱块，摘要生成失败时等待下次压缩重试。

这个策略保证聊天主链路优先返回可用回复，而不是因为某个增强模块失败而整体失败。

### 安全边界

项目使用 JWT 双 Token 机制保护 API。Access Token 用于请求认证，Refresh Token 用于续期。公开接口白名单限制在登录、验证码、刷新和绑定邮箱相关端点。

配置层不提交真实密钥。`application.yml` 使用环境变量占位符，Docker Compose 通过 `${variable_name}` 注入 MySQL、Redis、DashScope、JWT、OSS、TTS 和邮件参数。`.gitignore` 阻止 `.env`、生产配置、备份配置、构建产物和本地截图进入仓库。

### 可观测性

Spring Boot Actuator 暴露健康检查、指标和 Prometheus endpoint。队列监控接口提供摘要队列统计和健康状态。日志按 `dev.langchain4j` 和 `com.zjkl` 分组，方便定位模型调用和业务链路问题。

## 模块结构

```text
src/main/java/com/zjkl/
├── ai/                    # 对话、图片、OSS、Peek、Prompt、摘要队列
├── anchor/                # 情绪锚点与语义锚点
├── auth/                  # 登录、验证码、微信登录、JWT 刷新
├── common/                # 通用配置、异常、上下文、拦截器、响应结构
├── emotion/               # PAD 情绪引擎、TTS、语音播放、情绪记录
├── mail/                  # 邮件服务与站内邮件
├── memory/                # 长期记忆、混合检索、记忆画廊、Graph 相关服务
├── miniprogram/           # 微信小程序接口与实时连接
├── recommendation/        # AI 推荐生成与反馈
├── settings/              # 用户设置与预设配置
├── user/                  # 用户画像、兴趣标签、头像和基础资料
└── wakeup/                # 主动唤醒工作流
```

```text
langchain4j_sister_backend/
├── VectorGraphRag/        # 独立 Graph RAG / Milvus 向量图谱模块
├── frontend/              # Vue 3 + Vite 前端
├── src/main/resources/    # 配置、Mapper、Prompt、Skill 资源
├── docker-compose.yml     # 统一基础设施编排
├── pom.xml                # Maven 构建配置
└── README.md
```

## 功能范围

| 模块 | 能力 |
| --- | --- |
| Auth | 邮箱验证码、微信登录、绑定邮箱、刷新 Token、登出 |
| Chat | 流式文本回复、消息持久化、历史查询、桌宠实时通道 |
| Image | 图片描述、Peek 截图理解、元素提取、图像生成 |
| Memory | 摘要记忆、混合检索、记忆画廊、Graph RAG |
| Emotion | PAD 状态、心情标签、情绪演化、情绪历史、TTS 参数联动 |
| WakeUp | 沉默检测、冷却控制、主动对话 |
| Peek | 前端截图回调、视觉模型分析、主动关怀 |
| Anchor | 情绪锚点、语义锚点、锚点事件 |
| Recommendation | 个性化推荐生成、点击反馈 |
| Miniprogram | 首页摘要、设备绑定、聊天、历史、资料同步、实时推送 |
| Admin | 队列健康检查、Prompt 模板刷新 |

## API 概览

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| Auth | `/api/auth` | 认证、刷新、微信登录、邮箱绑定 |
| Messages | `/api/messages` | 消息列表、最新消息、按日期查询、会话列表 |
| Image | `/api/image` | 图片理解、Peek 描述、元素提取、图像生成 |
| OSS | `/api/oss` | 上传、远程 URL 上传、对象删除、预签名 URL |
| Emotion | `/api/emotion` | 情绪状态、心情、演化、历史、重置 |
| Memory | `/api/ai/memory` | 长期记忆列表、详情、按日期查询 |
| Memory search | `/api/memory/search` | 记忆搜索 |
| Memory gallery | `/api/ai/gallery` | 记忆画廊和回填 |
| Anchor | `/api/ai/anchor` | 情绪锚点 |
| Recommendation | `/api/ai/recom` | 推荐生成和点击反馈 |
| Peek | `/api/peek` | 截图回调 |
| Settings | `/api/settings` | 用户设置和预设 |
| User | `/api/user` | 用户画像、兴趣、头像和基础资料 |
| Miniprogram | `/api/miniprogram` | 小程序首页、设备、聊天、历史、资料同步 |
| Admin queue | `/api/admin/queue` | 队列统计和健康检查 |
| Admin prompt | `/api/admin/prompts` | Prompt 模板刷新和查询 |

## 环境变量

生产配置通过环境变量注入。公开仓库不要提交真实密钥。

| 变量 | 说明 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 连接配置 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` / `REDIS_PASSWORD` | Redis 连接配置 |
| `MILVUS_HOST` / `MILVUS_PORT` | Milvus 连接配置 |
| `DASHSCOPE_API_KEY` | 通义千问 DashScope API Key |
| `JWT_SECRET` | JWT 签名密钥 |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序登录配置 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 邮件配置 |
| `OSS_ENDPOINT` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET_NAME` / `OSS_REGION` | 阿里云 OSS 配置 |
| `CORS_ALLOWED_ORIGINS` / `WEBSOCKET_ALLOWED_ORIGINS` | 跨域和 WebSocket 白名单 |
| `TTS_MODEL` / `TTS_VOICE` | DashScope TTS 模型和音色 |
| `FIRECRAWL_API_KEY` / `CONTEXT7_API_KEY` | MCP 扩展服务配置 |
| `APP_DEFAULT_IMAGE_URL` / `WANX_REFERENCE_IMAGE_URL` | 默认图片和参考图配置 |

## 本地运行

先启动基础设施：

```bash
docker compose up -d mysql redis etcd minio milvus
```

再启动后端：

```bash
./mvnw clean compile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

前端在 `frontend/` 目录运行：

```bash
cd frontend
npm install
npm run dev
```

## 构建与验证

后端构建：

```bash
./mvnw clean package
```

前端构建：

```bash
cd frontend
npm run lint
npm run build
```

## 面试可讲的设计点

如果把这个项目作为后端或 AI 应用项目讲，可以围绕这些问题展开：

- **AI 应用不是单次调用**：系统把 LLM、Vision、TTS、RAG、情绪和推送拆成多个可降级阶段
- **RAG 不只做向量相似度**：摘要记忆使用 dense + sparse + RRF，图谱记忆使用实体、关系和来源片段
- **实时系统需要背压**：慢外部调用走有界线程池，文本回复优先返回，音频和摘要异步处理
- **状态一致性需要边界**：Redis 保存短期状态，Redisson 锁保护跨实例更新，MySQL 保存长期业务数据
- **配置治理体现工程成熟度**：敏感信息只走环境变量，公开仓库只保留脱敏配置和统一 Docker Compose 入口
- **可观测性不是附加项**：Actuator、Prometheus 和队列健康接口用于定位线上链路问题

## 当前限制

这个仓库仍有一些测试用例和当前源码不一致，`mvn test` 可能失败。`mvn compile` 可以作为 README 变更后的基础验证。后续需要同步测试构造器、Lombok 生成方法和 Mockito inline mock 配置。

## License

如果要正式开源，请补充 `LICENSE` 文件，并让 README 中的许可证声明和实际许可证保持一致。
