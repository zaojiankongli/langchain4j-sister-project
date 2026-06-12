# Sister Project

> 基于 Spring Boot 3、LangChain4j、通义千问、Milvus、Redis、MySQL 和 Vue 3 构建的 AI 伴侣系统。项目覆盖实时对话、多模态理解、长期记忆、情绪建模、主动唤醒、小程序接入和前端 Live2D 交互等完整链路。

## 项目定位

Sister Project 不是一个简单的聊天接口，而是一套面向“AI 伴侣”场景的全栈应用。后端负责身份认证、LLM 编排、实时消息推送、图片理解、语音合成、记忆检索、情绪状态更新和主动交互；前端基于 Vue 3 提供聊天、仪表盘、Live2D、设置和状态可视化能力。

系统目标是让 AI 具备更连续的上下文、更稳定的人格表现和更自然的主动互动能力。

## 核心功能

### 1. 实时 AI 对话

- 基于 LangChain4j 接入通义千问 DashScope Chat Model 和 Streaming Chat Model。
- 支持 STOMP over WebSocket / SockJS 实时推送消息。
- 提供文本对话、消息历史、会话查询和桌宠实时交互能力。
- 结合 Redis、MySQL 和 Milvus 管理短期上下文、长期记录和语义记忆。

### 2. 多模态理解与图像能力

- 接入 DashScope Vision Model，用于图片描述、Peek 截图分析和图像元素提取。
- 支持图片上传到阿里云 OSS，并提供预签名 URL、远程 URL 上传和对象删除能力。
- 提供文生图 / 图像生成相关服务封装。

### 3. 情绪引擎

- 使用 PAD 情绪模型管理 Pleasure、Arousal、Dominance 三维状态。
- 支持情绪状态查询、心情描述、情绪演化、历史记录和重置。
- 结合人格、情绪锚点和对话刺激，实现更连续的情绪变化。
- 语音合成服务可根据情绪状态调整 TTS 表达。

### 4. 长期记忆与 Graph RAG

- 使用 Milvus 作为向量数据库，支持记忆搜索、语义检索和画廊式记忆展示。
- 内置 `VectorGraphRag` 模块，提供实体、关系、片段三类向量集合建模能力。
- 支持 Graph Snapshot、Graph Entity、Summary Memory 等长期记忆服务。
- 通过摘要队列和后台任务降低实时对话链路负载。

### 5. 主动交互机制

- WakeUp 模块支持定时主动唤醒和状态驱动的主动关怀。
- Peek 模块支持前端截图回调，用于感知用户当前状态。
- Anchor 模块支持情绪锚点触发和语义锚点事件。
- Recommendation 模块提供 AI 驱动的个性化推荐生成与点击反馈。

### 6. 认证、用户和设置

- JWT 双 Token 认证：Access Token + Refresh Token。
- 支持邮箱验证码登录、微信小程序登录、绑定邮箱、同步邮箱账户和登出。
- 用户画像、兴趣标签、AI 类型、头像和基础信息管理。
- Settings 模块支持用户配置读取、更新和预设配置。

### 7. 小程序与外部接入

- `miniprogram` 模块提供首页摘要、设备绑定、设备状态、聊天发送、历史记录和资料同步接口。
- 支持小程序实时 WebSocket 处理器。
- MCP 配置预留 Firecrawl 和 Context7 集成能力。

### 8. 运维与可观测性

- Spring Boot Actuator 暴露 `health`、`info`、`metrics`、`prometheus`。
- Micrometer Prometheus 用于指标采集。
- Redis Stream / 队列监控接口用于查看后台摘要任务状态。
- Docker Compose 提供 MySQL、Redis、Milvus、etcd、MinIO 和应用服务编排。

## 技术栈

### 后端

| 分类 | 技术 |
| --- | --- |
| 语言与运行时 | Java 21、Virtual Threads |
| Web 框架 | Spring Boot 3.5.11、Spring Web、Spring WebSocket |
| AI 编排 | LangChain4j 1.13.0-beta23、LangChain4j Reactor、Easy RAG、MCP、Agentic |
| 大模型服务 | Alibaba DashScope：Qwen Chat、Streaming Chat、Vision、Embedding、TTS |
| 数据库 | MySQL 8、MyBatis |
| 缓存与队列 | Redis、Redisson、Redis Stream、Caffeine |
| 向量数据库 | Milvus 2.x、Milvus Java SDK |
| 对象存储 | 阿里云 OSS |
| 安全认证 | JWT、邮箱验证码、请求拦截器 |
| API 文档 | Springdoc OpenAPI / Swagger UI |
| 监控 | Spring Boot Actuator、Micrometer、Prometheus |
| 构建 | Maven Wrapper、JaCoCo、Maven Enforcer |

### 前端

| 分类 | 技术 |
| --- | --- |
| 框架 | Vue 3、Vite 7 |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| 网络 | Axios、SockJS、STOMP |
| 动画与表现 | GSAP、oh-my-live2d |
| 工程化 | ESLint、Prettier、vite-plugin-compression |

## 项目结构

```text
langchain4j_sister_backend/
├── src/main/java/com/zjkl/
│   ├── ai/                    # AI 对话、图片、OSS、Peek、Prompt、摘要队列
│   ├── anchor/                # 情绪锚点与语义锚点
│   ├── auth/                  # 登录、验证码、微信登录、JWT 刷新
│   ├── common/                # 通用配置、异常、上下文、拦截器、响应结构
│   ├── emotion/               # PAD 情绪引擎、TTS、语音播放、情绪记录
│   ├── mail/                  # 邮件服务与站内邮件
│   ├── memory/                # 长期记忆、语义检索、记忆画廊、Graph 相关服务
│   ├── miniprogram/           # 微信小程序接口与实时连接
│   ├── recommendation/        # AI 推荐生成与反馈
│   ├── settings/              # 用户设置与预设配置
│   ├── user/                  # 用户画像、兴趣标签、头像和基础资料
│   └── wakeup/                # 主动唤醒工作流
├── src/main/resources/
│   ├── application.yml        # 主配置，使用环境变量注入敏感参数
│   ├── application-dev.yml    # 开发环境配置
│   ├── mapper/                # MyBatis XML Mapper
│   ├── prompts/               # Prompt 模板
│   └── skills/                # AI Skill 相关资源
├── VectorGraphRag/            # 独立 Graph RAG / Milvus 向量图谱模块
├── frontend/                  # Vue 3 + Vite 前端
├── docker-compose.yml         # 本地/生产基础设施编排
├── docker-compose.test.yml    # 测试环境编排
├── docker-compose.prod.yml    # 生产环境编排
├── pom.xml                    # 后端 Maven 配置
└── README.md
```

## API 概览

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| Auth | `/api/auth` | 发送验证码、登录、微信登录、绑定邮箱、刷新 Token、登出 |
| Messages | `/api/messages` | 消息列表、最新消息、按日期查询、会话列表 |
| Image | `/api/image` | 图片描述、Peek 图片描述、元素提取、图像生成 |
| OSS | `/api/oss` | 图片上传、URL 上传、对象删除、预签名 URL |
| Emotion | `/api/emotion` | 情绪状态、心情、演化、历史、重置 |
| Memory | `/api/ai/memory` | 长期记忆列表、详情、按日期查询 |
| Memory Search | `/api/memory/search` | 按日期和语义条件检索记忆 |
| Memory Gallery | `/api/ai/gallery` | 记忆画廊、调试、回填 |
| Anchor | `/api/ai/anchor` | 情绪锚点列表 |
| Recommendation | `/api/ai/recom` | 推荐生成、点击反馈 |
| Peek | `/api/peek` | 前端截图 / 状态回调 |
| Settings | `/api/settings` | 用户设置读取、更新、预设 |
| User | `/api/user` | 用户画像、基础信息、兴趣、AI 类型、头像 |
| Interest Tag | `/api/interest-tag` | 用户兴趣标签生成 |
| Mail | `/api/mails` | 邮件已读、全部已读 |
| Miniprogram | `/api/miniprogram` | 小程序首页、设备、聊天、历史、资料同步 |
| Admin Queue | `/api/admin/queue` | 后台队列统计与健康检查 |
| Admin Prompt | `/api/admin/prompts` | Prompt 模板刷新和查询 |

WebSocket 相关入口包括聊天 STOMP、桌宠实时交互和小程序实时连接，具体 destination 以对应 controller 和 config 为准。

## 环境变量

生产配置通过环境变量注入，不应把真实密钥写入仓库。

| 变量 | 说明 |
| --- | --- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 连接配置 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` / `REDIS_PASSWORD` | Redis 连接配置 |
| `MILVUS_HOST` / `MILVUS_PORT` | Milvus 连接配置 |
| `DASHSCOPE_API_KEY` | 通义千问 / DashScope API Key |
| `JWT_SECRET` | JWT 签名密钥 |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信小程序登录配置 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 邮件配置 |
| `OSS_ENDPOINT` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` / `OSS_BUCKET_NAME` / `OSS_REGION` | 阿里云 OSS 配置 |
| `CORS_ALLOWED_ORIGINS` / `WEBSOCKET_ALLOWED_ORIGINS` | 跨域与 WebSocket 白名单 |
| `TTS_MODEL` / `TTS_VOICE` | DashScope TTS 模型与音色 |
| `FIRECRAWL_API_KEY` / `CONTEXT7_API_KEY` | MCP 扩展服务配置 |
| `APP_DEFAULT_IMAGE_URL` / `WANX_REFERENCE_IMAGE_URL` | 默认图片与参考图配置 |

## 本地启动

### 1. 启动基础设施

```bash
docker compose up -d mysql redis etcd minio milvus
```

如果使用 `docker-compose.yml` 中的应用服务，需要先准备 `.env` 或在 shell 中导出必需变量。

### 2. 启动后端

```bash
./mvnw clean compile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

后端默认端口为 `8080`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务由 Vite 启动，默认端口通常为 `5173`。

## 构建与测试

### 后端

```bash
./mvnw clean test
./mvnw clean package
```

### VectorGraphRag 模块

```bash
cd VectorGraphRag
../mvnw clean test
```

### 前端

```bash
cd frontend
npm run lint
npm run build
```

## 部署说明

项目提供多个 Docker Compose 文件：

- `docker-compose.yml`：主编排文件，包含 MySQL、Redis、Milvus、etcd、MinIO 和应用服务。
- `docker-compose.test.yml`：测试环境编排。
- `docker-compose.prod.yml`：生产环境编排。
- `docker-compose.vm.yml`：虚拟机环境编排。

生产部署建议：

1. 使用强随机值配置 `JWT_SECRET`、数据库密码、Redis 密码和 OSS 密钥。
2. 不要提交 `.env`、`application-local.yml`、真实 `application-prod.yml` 或任何备份密钥文件。
3. 将 `CORS_ALLOWED_ORIGINS` 和 `WEBSOCKET_ALLOWED_ORIGINS` 限制为真实域名。
4. 使用 Prometheus 抓取 `/actuator/prometheus`，并对 `/actuator` 与 admin API 做访问控制。

## 安全注意事项

- `application.yml` 使用环境变量占位符，适合提交。
- `application-dev.yml` 包含开发环境默认地址和占位密钥，只应作为本地开发模板使用。
- `.env`、`.env.*`、`target/`、`frontend/dist/`、压缩包和 IDE 配置已在 `.gitignore` 中排除。
- 推送公开仓库前，请确认没有真实 API Key、数据库密码、OSS 密钥或生产配置备份进入暂存区。

## 代码质量与工程约束

- Java 版本由 Maven Enforcer 限制为 21+。
- Maven 版本要求 3.9+。
- JaCoCo 已配置覆盖率报告。
- Spring Boot graceful shutdown 已启用。
- 后端通过 `@ConfigurationProperties` 组织应用配置，减少散落的 `@Value`。
- Redis、Milvus、LLM、TTS 等耗时能力通过线程池和异步队列隔离。

## License

如需开源发布，请在仓库中补充明确的 `LICENSE` 文件；README 中的许可证声明应与实际许可证文件保持一致。
