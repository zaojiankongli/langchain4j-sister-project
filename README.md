# Sister Project

Sister Project 是一个“传统业务系统 + 多模态交互 + 双路 RAG 伴侣系统”的全栈项目。它用 Spring Boot 3、Java 21、LangChain4j、通义千问、Milvus、Redis、MySQL 和 Vue 3，把用户体系、实时通信、对象存储、可观测性、AI 妹妹对话、多模态理解、长期记忆和主动交互放进同一个工程。这个 README 重点说明系统设计、RAG 路由、重排链路和工程取舍，方便技术评审或面试官快速判断项目深度。

## 项目亮点

这个项目的重点不是调用一个大模型接口，而是把传统后端能力、AI 编排能力和 RAG 检索能力组合成一条稳定的产品链路：

- **传统后端底座**：包含认证、用户画像、设置、邮件、对象存储、管理接口、监控指标和 Docker 化部署
- **AI 伴侣对话中枢**：聊天层像一个有长期记忆、情绪状态和多模态感知的 AI 妹妹，把用户问题组织成可回复、可降级、可追踪的上下文
- **RAG 长期记忆层**：Milvus 承担长期记忆检索，路由器决定走 native hybrid RAG、Graph RAG，或双路并行
- **多阶段重排链路**：native RAG 使用 dense vector + sparse BM25 + Reciprocal Rank Fusion，Graph RAG 使用实体召回、关系扩展和 LLM rerank
- **Vector Graph RAG**：不引入传统图数据库，直接把实体、关系和片段向量化到 Milvus；用单次 LLM 重排代替多轮 agent 反射；既能做多跳关系检索，大大降低了延迟并有相当的能力
- **多模态统一编排**：文本、图片、截图、语音、情绪和主动唤醒共用同一条会话链路，不是分散的功能 Demo
- **实时交互链路**：后端通过 STOMP over WebSocket 推送文本、音频、情绪、动作、表情和系统消息
- **情绪状态建模**：用 Pleasure、Arousal、Dominance 三维情绪模型驱动 TTS、主动交互和情绪锚点
- **外部调用隔离**：大模型、语音合成、Milvus、图片处理分别使用有界线程池，避免慢调用拖垮主链路
- **工程化设计**：配置属性集中绑定、JWT 鉴权、Redisson 分布式锁、有界线程池背压、Actuator 指标、Prometheus 暴露和降级策略

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

这种拆分让实时链路保持可控：用户消息进入后端后，系统先由查询分析器判断是否需要长期记忆，再并行准备 native hybrid RAG、Graph RAG、情绪和多模态上下文，最后把 LLM 的流式输出拆成文本、音频和状态事件推送给前端。面试时可以把它定义成“有路由、有重排、有图谱、有多模态、有降级的 RAG 伴侣系统”。

## 核心链路设计

### 实时聊天链路

聊天链路本质上是一个 AI 伴侣对话中枢。它不直接把用户输入丢给模型，而是先读取用户身份、最近消息、长期记忆、图谱关系、情绪状态和多模态上下文，再组装成一次可解释、可降级的 LLM 请求。图片、截图、语音和主动事件都进入同一套会话编排，而不是各自独立响应。

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

RAG 总体架构如下：

```text
用户问题
  │
  ▼
QueryAnalyzer / Router
  │
  ├── query rewrite / topic hint / date hint / sentiment hint
  │
  ├── native hybrid RAG
  │     ├── Milvus dense_vector
  │     ├── Milvus sparse BM25
  │     ├── RRF 融合
  │     ├── score threshold
  │     ├── user_id 二次校验
  │     └── 去重 + 压缩
  │
  └── Graph RAG
        ├── LLM entity extraction
        ├── entity vector search
        ├── relation id expansion
        ├── relation vector search
        ├── LLM relation rerank
        └── passage small-to-big fetch
              │
              ▼
跨路融合排序 + 句子级去重
              │
              ▼
Prompt memory block
              │
              ▼
Streaming LLM response
```

检索时系统同时使用三类信号：

- **Dense vector**：用 embedding 匹配语义相似内容
- **Sparse BM25**：用全文检索保留关键词命中能力
- **Graph relation**：用实体和关系补充跨轮对话中的事实联系

`SummaryMemoryService` 使用 Milvus Hybrid Search，把 dense vector 和 sparse BM25 结果用 Reciprocal Rank Fusion (RRF) 融合，再做阈值过滤、用户二次校验、去重和文本压缩。`GraphQueryService` 先用 LLM 抽取查询实体，再批量 embedding，随后检索实体、扩展候选关系、做关系向量召回，并用 LLM rerank 选出最有用的关系。最后系统按路由的 primary source 和检索分数融合 native RAG 与 Graph RAG 结果。它的优势不只是“能记住”，而是“知道该查哪里、怎么查、查完怎么排、最后怎么说”。

### Vector Graph RAG 设计

Vector Graph RAG 的 Java 实现参考了 Zilliz `vector-graph-rag` 的思路，但没有照搬 Python 生态。核心思想是：**不用传统图数据库，而是把实体、关系和片段都编码进 Milvus，用纯向量检索 + 单次重排来完成多跳问题召回**。这个项目把图谱记忆拆成实体、关系和来源片段三类集合，并用 Java 服务把抽取、召回、扩展、重排和格式化串成一条可控 workflow。

和官方思路一致，这条链路强调四个关键词：

- **No graph database**：不引入 Neo4j 一类独立图数据库
- **Pure vector search**：实体、关系、片段都通过 Milvus 检索
- **Single-pass reranking**：用一次 LLM 重排，而不是多轮 agent 反射
- **Multi-hop reasoning**：通过关系扩展和片段回捞完成多跳信息拼接

在这个项目里的落地方式是：

- **实体层**：从用户问题中抽取人物、地点、事件和偏好等实体，用向量检索找到历史中最接近的节点
- **关系层**：通过实体上挂载的 relation ids 扩展候选关系，再补充一次 relation vector search，避免只依赖实体命中
- **片段层**：通过 passage ids 找回来源片段，实现 small-to-big 检索：先用小粒度实体和关系定位，再取更完整的片段给 LLM
- **重排层**：用 LLM 对候选关系做 rerank，只保留和当前问题最相关的关系，减少无关图谱信息进入 Prompt
- **安全层**：Milvus 查询按 user_id 过滤，实体文本进入 filter 前做字符校验，避免跨用户记忆污染和 filter 注入

执行流程可以概括成：

```text
Question
  │
  ▼
Entity extraction
  │
  ▼
Vector search on Milvus
  │
  ├── entity retrieval
  ├── relation expansion
  └── relation vector retrieval
        │
        ▼
Subgraph expansion
        │
        ▼
Single-pass LLM reranking
        │
        ▼
Passage fetch and answer context assembly
```

这种设计比“把所有历史对话都塞进向量库”更适合 AI 伴侣场景。AI 妹妹需要记住“谁、什么事、为什么、后来怎样”，而不是只找几段语义相似文本。Vector Graph RAG 能把长期陪伴中形成的人物关系、偏好变化和事件因果保存成结构化线索，同时避免引入新的图库运维复杂度。

### 为什么不用传统图数据库

如果采用传统图数据库，系统通常要维护独立图存储、图 schema、图查询语言和额外的同步链路。对于 AI 伴侣这类以检索和重排为核心的场景，这会把工程复杂度拉得很高，但不一定显著提高用户体验。

这个项目选择 Vector Graph RAG 路线，原因有三点：

- **部署更轻**：Milvus 已经承担了 native RAG、Vector Graph RAG 和部分 metadata 过滤，不需要再引入 Neo4j 一类独立图数据库
- **检索更统一**：实体、关系、片段都能向量化，查询层可以复用 embedding、rerank 和过滤逻辑
- **体验更稳定**：图谱能力被包装成 workflow，不需要让模型在图数据库、向量库和工具调用之间自由跳转

这不是“为了少用一个数据库”而做的选择，而是为了让用户问题更快进入答案路径。对 AI 伴侣来说，用户更关心“你记不记得我说过什么、你能不能把关系讲顺、你能不能自然地回答”，而不是后端是否使用了传统图数据库。

### Query rewrite 和 small-to-big 方向

当前路由器已经会提取 topic、date、sentiment 等 hint，并把 topic hint 拼入 native RAG query 来增强召回。这个设计可以自然扩展成 query rewrite workflow：先把用户口语化问题改写成检索友好的查询，再分别派发给 native RAG 和 Vector Graph RAG。

如果从策略角度拆解，query rewrite 可以承担三类任务：

- **召回增强**：把“上次那个事情”这种口语表达改写成带主题词、时间词和情绪词的检索问题
- **视角扩展**：把同一个问题扩成多种检索表述，例如实体视角、事件视角、关系视角，形成 multi-query recall
- **假设驱动召回**：引入 HyDE 风格的思路，先生成一个可能答案或假设性片段，再拿这个片段反向做检索，提升稀疏问题或隐式关系问题的召回率

在这个项目里，最现实的落地方式不是无条件开启所有 rewrite，而是按路由策略逐步启用：

- **topic/date/sentiment hint**：低成本，适合默认开启
- **multi-query expansion**：适合复杂回忆问题，但要控制 Milvus 查询次数
- **HyDE-style rewrite**：适合事实稀疏、关系隐含的问题，但要评估额外 LLM 调用带来的时延和成本

small-to-big 的思路也已经体现在图谱链路里：先用实体和关系这类小粒度结构定位，再取 relation 关联的 passage 作为更完整的上下文。这样能兼顾准确召回和回答完整性，避免直接把长文本块塞给模型导致噪声过多。

### topK 之后的上下文控制

RAG 的关键不只是“能不能检索到”，而是“检索到之后怎样把上下文控制在模型可消化的预算内”。这个项目在设计上已经考虑了 topK 之后的上下文治理。

上下文预算控制分成四层：

- **阈值过滤**：先按 RRF score 或 relation score 去掉弱命中，避免把低质量候选塞进 Prompt
- **元数据过滤**：按 user_id、date hint、sentiment hint 等条件二次筛选，避免把相关性不高的历史带进来
- **去重压缩**：native RAG 对文本做去重和压缩，跨路融合时做句子级去重，避免 memory 与 graph 重复讲同一件事
- **LLM 压缩降级**：当 topK 后的上下文仍然过长时，先按 small-to-big 保留核心片段，再触发一次压缩；如果压缩失败，再降级到硬截断或有限条数返回

从代码实现上看，`SummaryMemoryService` 已经在 `buildMemoryBlockWithScore()` 中把召回结果限制到固定条数，并在超出阈值时走 `compressMemoriesWithLLM()`。这使得“检索质量”和“上下文长度”不是两个互相冲突的问题，而是同一个 workflow 里的两个优化目标。

### 为什么用 workflow，而不是完全 agentic RAG

这个项目没有把 RAG 做成完全自由的 agentic RAG，而是选择可控 workflow。原因不是能力不足，而是用户体验要求不同。

AI 伴侣对话需要稳定、低延迟、可解释和可降级。完全 agentic RAG 会让模型自己决定查什么、查几轮、什么时候停止，灵活性更高，但延迟、成本和结果稳定性更难控制。这个项目把“是否检索、查哪一路、怎么重排、怎么融合”固化成 workflow，让每一步都能记录、超时、降级和打点。

这种取舍更接近生产系统：

- **用户体验优先**：文本回复先回来，RAG、TTS、多模态能力不能无限阻塞主链路
- **可观测性优先**：每次 RAG 都能记录 routeMemory、routeGraph、primarySource、hit、score、timeout 和 injectedChars
- **成本可控**：减少无意义多轮 agent 调用，把 LLM 用在查询理解、关系重排和最终回复这些高价值节点
- **可降级**：native RAG、Vector Graph RAG、snapshot、TTS 或图片理解失败时，聊天仍能继续

RAG 可以继续向 workflow 化增强，例如加入 query rewrite、multi-query expansion、small-to-big chunk expansion、cross-encoder rerank 或 answer verification，但核心仍保持“可控流程”，而不是让 agent 自由游走。

### 传统业务系统能力

AI 项目也需要传统后端底座。这个项目保留了完整业务系统常见的模块：JWT 认证、邮箱验证码、微信登录、用户画像、设置中心、OSS 上传、邮件通知、管理接口、监控指标和 Docker Compose 编排。

这些模块让 AI 对话不是一个孤立 demo，而是可以接入真实用户、真实文件、真实消息和真实部署环境的应用后端。

### 工程化设计亮点

项目在 AI 能力之外保留了后端系统的工程边界。配置通过 `@ConfigurationProperties` 集中绑定，敏感信息只从环境变量读取；慢外部调用通过有界线程池隔离；Redis 和 Redisson 处理短期状态、冷却时间和分布式一致性；Actuator、Micrometer 和 Prometheus 暴露运行状态；RAG、图谱、TTS、OSS 和邮件服务失败时可以局部降级，不让增强能力拖垮主对话链路。多模态输入也不是单点拼接，而是和记忆、情绪、推送、身份状态一起进入统一编排层，这就是它更像一个可落地的生产系统，而不是玩具 Demo 的原因。

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

### 多模态编排链路

多模态不是独立功能，而是进入同一套 AI 伴侣编排层。文本、图片、截图、语音、情绪和主动事件都会被转换成对话上下文或推送事件。目标不是“把多模态都接上”，而是让系统具备统一感知和统一回复能力。

```text
用户输入 / 主动事件
  │
  ├── text message
  ├── image upload / image URL
  ├── Peek screenshot callback
  ├── realtime transcript
  ├── emotion state
  └── wakeup / anchor / recommendation event
        │
        ▼
Conversation orchestration
  ├── identity + profile
  ├── RAG memory block
  ├── graph relation block
  ├── vision description
  ├── PAD emotion state
  └── prompt template
        │
        ▼
LLM / VLM / TTS
        │
        ▼
STOMP push
  ├── text tokens
  ├── audio chunks
  ├── emotion update
  ├── pet expression
  ├── pet motion
  └── miniprogram realtime message
```

这个设计的好处是用户体验统一。用户发图片、沉默太久触发唤醒、前端回传截图或语音转文本时，后端都能复用同一套身份、记忆、情绪和推送能力。对用户来说，这是一位能看、能听、能记、能响应的 AI 妹妹，而不是几个功能拼起来的页面。

## 工程设计与稳定性

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
- **多模态不是外挂**：图片、截图、语音和情绪都进入同一套会话编排
- **实时系统需要背压**：慢外部调用走有界线程池，文本回复优先返回，音频和摘要异步处理
- **状态一致性需要边界**：Redis 保存短期状态，Redisson 锁保护跨实例更新，MySQL 保存长期业务数据
- **配置治理体现工程成熟度**：敏感信息只走环境变量，公开仓库只保留脱敏配置和统一 Docker Compose 入口
- **可观测性不是附加项**：Actuator、Prometheus 和队列健康接口用于定位线上链路问题
- **工程化设计能讲清楚**：鉴权、配置治理、分布式锁、背压、降级、指标和日志都能对应到代码模块

## License

如果要正式开源，请补充 `LICENSE` 文件，并让 README 中的许可证声明和实际许可证保持一致。
