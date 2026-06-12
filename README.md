# Sister Project

Sister Project 是一个“传统业务系统 + AI 伴侣对话中枢 + RAG 长期记忆层”的全栈项目。它用 Spring Boot 3、Java 21、LangChain4j、通义千问、Milvus、Redis、MySQL 和 Vue 3，把用户体系、实时通信、对象存储、可观测性、AI 妹妹对话、多模态理解、长期记忆和主动交互放进同一个工程。这个 README 重点说明系统设计、RAG 路由、重排链路和工程取舍，方便技术评审或面试官快速判断项目深度。

## 项目亮点

这个项目的重点不是调用一个大模型接口，而是把传统后端能力、AI 编排能力和 RAG 检索能力组合成一条稳定的产品链路：

- **传统后端底座**：包含认证、用户画像、设置、邮件、对象存储、管理接口、监控指标和 Docker 化部署
- **AI 伴侣对话中枢**：聊天层像一个有长期记忆、情绪状态和多模态感知的 AI 妹妹，把用户问题组织成可回复、可降级、可追踪的上下文
- **RAG 长期记忆层**：Milvus 承担长期记忆检索，路由器决定使用 native hybrid RAG、Graph RAG 或两者并行
- **多阶段重排链路**：native RAG 使用 dense vector + sparse BM25 + Reciprocal Rank Fusion，Graph RAG 使用实体召回、关系扩展和 LLM rerank
- **实时交互链路**：后端通过 STOMP over WebSocket 推送文本、音频、情绪、动作、表情和系统消息
- **情绪状态建模**：用 Pleasure、Arousal、Dominance 三维情绪模型驱动 TTS、主动交互和情绪锚点
- **外部调用隔离**：大模型、语音合成、Milvus、图片处理分别使用有界线程池，避免慢调用拖垮主链路
- **企业级工程设计**：配置属性集中绑定、JWT 鉴权、Redisson 分布式锁、有界线程池背压、Actuator 指标、Prometheus 暴露和灰度式降级策略

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
        ├── Query analyzer / RAG router
        ├── LangChain4j streaming chat
        ├── Vision understanding
        ├── TTS streaming
        ├── Emotion engine
        ├── Native hybrid RAG
        └── Graph RAG
        │
        ▼
MySQL + Redis + Milvus + OSS + DashScope
```

这种拆分让实时链路保持可控：用户消息进入后端后，系统先由查询分析器判断是否需要长期记忆，再并行准备 native hybrid RAG、Graph RAG、情绪和多模态上下文，最后把 LLM 的流式输出拆成文本、音频和状态事件推送给前端。

## 核心链路设计

### 实时聊天链路

聊天链路本质上是一个 AI 伴侣对话中枢。它不直接把用户输入丢给模型，而是先读取用户身份、最近消息、长期记忆、图谱关系、情绪状态和多模态上下文，再组装成一次可解释、可降级的 LLM 请求。

```text
用户输入
  │
  ├── 用户身份校验
  ├── 活跃状态更新
  ├── QueryAnalyzer 判断 RAG 路由
  ├── 图片或截图理解
  ├── native hybrid RAG 检索
  ├── Graph RAG 检索与重排
  └── 情绪状态读取
        │
        ▼
Prompt 组装 → LLM 流式响应 → 文本推送 / TTS / 情绪更新 / 持久化
```

这个链路的工程点在于隔离慢操作。LLM、TTS、Milvus 和图片任务都有独立执行器，避免一个外部服务抖动导致 WebSocket 推送线程被占满。

### RAG 长期记忆链路

长期记忆层不是单一向量检索，而是一套路由式 RAG。`QueryAnalyzer` 先判断当前问题是否需要搜索历史记忆、是否需要搜索实体关系图谱，并提取 topic、date、sentiment 等过滤线索。聊天链路再根据路由结果选择 native hybrid RAG、Graph RAG 或两者并行。

```text
用户问题
  │
  ▼
QueryAnalyzer
  ├── needMemorySearch → native hybrid RAG
  ├── needGraphSearch  → Graph RAG
  ├── primarySource    → memory / graph / both
  └── filters          → topic / date / sentiment
        │
        ▼
跨路结果融合 → Prompt memory block → LLM 回复
```

Milvus 长期记忆分成两条检索路径。native hybrid RAG 负责“这段经历、这类历史对话、某天发生过什么”的召回；Graph RAG 负责“人物、事件、关系、事实链路”的召回。

检索时系统同时使用三类信号：

- **Dense vector**：用 embedding 匹配语义相似内容
- **Sparse BM25**：用全文检索保留关键词命中能力
- **Graph relation**：用实体和关系补充跨轮对话中的事实联系

`SummaryMemoryService` 使用 Milvus Hybrid Search，把 dense vector 和 sparse BM25 结果用 Reciprocal Rank Fusion (RRF) 融合，再做阈值过滤、用户二次校验、去重和文本压缩。`GraphQueryService` 先用 LLM 抽取查询实体，再批量 embedding，随后检索实体、扩展候选关系、做关系向量召回，并用 LLM rerank 选出最有用的关系。最后系统按路由的 primary source 和检索分数融合 native RAG 与 Graph RAG 结果。

### RAG 路由与重排

RAG 路由解决的问题是“不是每个问题都需要查长期记忆，也不是每个问题都适合查图谱”。例如闲聊可以直接回答，问“上次我说的那件事”更适合 native hybrid RAG，问“某个人和某件事之间的关系”更适合 Graph RAG。

```text
QueryAnalyzer
  │
  ├── native hybrid RAG
  │     ├── dense_vector search
  │     ├── sparse BM25 search
  │     ├── RRF(k=60)
  │     ├── score threshold
  │     ├── user_id defense check
  │     └── dedupe + compress
  │
  └── Graph RAG
        ├── LLM entity extraction
        ├── batch embedding
        ├── entity search
        ├── relation id expansion
        ├── relation vector search
        ├── LLM relation rerank
        └── passage fetch
```

这条链路能体现两个技术点：第一，Milvus 不只是存向量，而是同时承担向量检索、BM25、元数据过滤和多集合图谱检索；第二，LLM 不只负责最终回复，还参与查询理解、实体抽取和候选关系重排。

### 传统业务系统能力

AI 项目也需要传统后端底座。这个项目保留了完整业务系统常见的模块：JWT 认证、邮箱验证码、微信登录、用户画像、设置中心、OSS 上传、邮件通知、管理接口、监控指标和 Docker Compose 编排。

这些模块让 AI 对话不是一个孤立 demo，而是可以接入真实用户、真实文件、真实消息和真实部署环境的应用后端。

### 企业级设计亮点

项目在 AI 能力之外保留了后端系统的工程边界。配置通过 `@ConfigurationProperties` 集中绑定，敏感信息只从环境变量读取；慢外部调用通过有界线程池隔离；Redis 和 Redisson 处理短期状态、冷却时间和分布式一致性；Actuator、Micrometer 和 Prometheus 暴露运行状态；RAG、图谱、TTS、OSS 和邮件服务失败时可以局部降级，不让增强能力拖垮主对话链路。

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
| Chat | AI 伴侣对话中枢、流式文本回复、消息持久化、历史查询、桌宠实时通道 |
| Image | 图片描述、Peek 截图理解、元素提取、图像生成 |
| Memory | native hybrid RAG、Graph RAG、RAG 路由、重排、记忆画廊 |
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
- **传统系统能力不能省**：认证、用户、设置、OSS、邮件、监控和部署让项目不是纯 demo
- **聊天层是 AI 伴侣中枢**：它负责整合用户问题、长期记忆、情绪状态、多模态输入和实时推送
- **RAG 先路由再检索**：QueryAnalyzer 判断是否走 native hybrid RAG、Graph RAG 或双路并行
- **Milvus 不只做向量相似度**：native RAG 使用 dense + sparse + RRF，Graph RAG 使用实体、关系和来源片段
- **重排分两层**：native RAG 用 RRF 融合，Graph RAG 用 LLM 对候选关系 rerank
- **实时系统需要背压**：慢外部调用走有界线程池，文本回复优先返回，音频和摘要异步处理
- **状态一致性需要边界**：Redis 保存短期状态，Redisson 锁保护跨实例更新，MySQL 保存长期业务数据
- **配置治理体现工程成熟度**：敏感信息只走环境变量，公开仓库只保留脱敏配置和统一 Docker Compose 入口
- **可观测性不是附加项**：Actuator、Prometheus 和队列健康接口用于定位线上链路问题
- **企业级设计能讲清楚**：鉴权、配置治理、分布式锁、背压、降级、指标和日志都能对应到代码模块

## 当前限制

这个仓库仍有一些测试用例和当前源码不一致，`mvn test` 可能失败。`mvn compile` 可以作为 README 变更后的基础验证。后续需要同步测试构造器、Lombok 生成方法和 Mockito inline mock 配置。

## License

如果要正式开源，请补充 `LICENSE` 文件，并让 README 中的许可证声明和实际许可证保持一致。
