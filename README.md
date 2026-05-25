# Sister Project — AI 妹妹聊天机器人

> 一个具有情感引擎、语音交互、视觉理解能力的 AI 伴侣聊天机器人。
> 后端基于 Spring Boot + LangChain4j，前端基于 Vue 3 + Live2D。

![Java 17](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D)
![License](https://img.shields.io/badge/license-MIT-green)

---

## 目录

- [项目简介](#项目简介)
- [架构概览](#架构概览)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [快速开始](#快速开始)
  - [前置条件](#前置条件)
  - [后端启动](#后端启动)
  - [前端启动](#前端启动)
- [环境变量配置](#环境变量配置)
- [项目结构](#项目结构)
- [API 概览](#api-概览)
- [架构决策](#架构决策)
- [截图](#截图)

---

## 项目简介

**Sister Project** 是一个 AI 伴侣聊天机器人，她不仅是聊天助手，更是一个有情绪、有个性、能看见你、能听你说话的"妹妹"。

核心设计理念：

- **情感引擎** — 基于 PAD (Pleasure-Arousal-Dominance) 三维情绪模型 + OCEAN 人格模型，模拟真实情感变化
- **多模态交互** — 支持文字聊天、语音合成 (TTS)、图片理解 (VLM)、屏幕感知
- **异步架构** — Redis Stream 驱动的消息队列、虚拟线程、分布式锁，确保高并发下的稳定
- **实时通信** — STOMP over WebSocket 实现消息即时推送

---

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                   前端 (Vue 3)                        │
│  ┌─────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ Live2D  │  │ Chat     │  │ Side Panels       │  │
│  │ Avatar  │  │ Window   │  │ (Memory/Emotion…)  │  │
│  └─────────┘  └──────────┘  └───────────────────┘  │
│         │              │               │            │
│         └──────────────┼───────────────┘            │
│                        │ STOMP/WebSocket             │
└────────────────────────┼────────────────────────────┘
                         │
┌────────────────────────┼────────────────────────────┐
│            Spring Boot Backend                       │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Auth     │  │ Chat Service │  │ Emotion      │  │
│  │ (JWT)    │──│ (LangChain4j)│──│ Engine (PAD) │  │
│  └──────────┘  └──────┬───────┘  └──────────────┘  │
│                       │                              │
│  ┌──────────┐  ┌──────┴───────┐  ┌──────────────┐  │
│  │ TTS      │  │ Image       │  │ Memory       │  │
│  │ (DashScope)│  │ (VLM/Qwen)  │  │ (Redis+MySQL) │  │
│  └──────────┘  └──────────────┘  └──────────────┘  │
│                       │                              │
│  ┌──────────┐  ┌──────┴───────┐  ┌──────────────┐  │
│  │ Peek     │  │ WakeUp      │  │ Recommend    │  │
│  │ (Screen) │  │ (Scheduler) │  │ (Agent Flow) │  │
│  └──────────┘  └──────────────┘  └──────────────┘  │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Infrastructure: Redis · MySQL · OSS · Mail  │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 技术栈

### 后端

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.x |
| AI SDK | LangChain4j + DashScope (通义千问) |
| 数据库 | MySQL + MyBatis |
| 缓存 | Redis (数据缓存 + Stream 消息队列) |
| 分布式锁 | Redisson |
| 实时通信 | STOMP over WebSocket |
| 消息推送 | SimpMessagingTemplate + 虚拟线程队列 |
| 对象存储 | Aliyun OSS |
| 邮件 | Spring Mail (验证码) |
| 构建 | Maven |

### 前端

| 类别 | 技术 |
|------|------|
| 语言 | JavaScript (ES6+) |
| 框架 | Vue 3 (Composition API) |
| 构建 | Vite 7 |
| 状态管理 | Pinia |
| 路由 | Vue Router 5 |
| WebSocket | STOMP.js + SockJS |
| 2D 模型 | Live2D (oh-my-live2d) |
| 动画 | GSAP |

---

## 核心功能

### 🗣️ 语音聊天
- LLM 流式生成回复，实时推送到前端
- 基于情绪的 TTS 语音合成（阿里云 DashScope）
- 括号内容自动过滤，语音播报更自然

### 🎭 情感引擎
- **PAD 三维情绪模型**：愉悦度 (Pleasure)、唤醒度 (Arousal)、支配感 (Dominance)
- **OCEAN 人格模型**：开放性、尽责性、外向性、宜人性、神经质
- 情绪随时间自然衰减 + 回归人格基准
- 支持情绪锚点（Emotion Anchor）自动触发互动
- Redisson 分布式锁保证情绪更新的原子性

### 🖼️ 多模态理解
- 用户发送图片 → VLM 自动理解图片内容
- 视觉描述融入聊天上下文
- Peek（屏幕感知）定期截图分析，主动关怀

### 🔔 主动交互
- **WakeUp**：定时唤醒，主动发起对话
- **Peek**：定时请求截图，分析用户状态
- **Recommendation**：基于 Agentic Workflow 的内容推荐

### 💾 记忆系统
- Redis 短期对话记忆
- MySQL 持久化消息存储
- 定期摘要总结（Redis Stream 异步处理）
- 向量存储（Milvus）支持语义检索

### 🔐 认证
- JWT (Access + Refresh Token) 双 token 机制
- 邮箱验证码登录 / 注册
- Token 自动刷新

---

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 7+
- 阿里云 DashScope API Key（通义千问 + 语音合成）

### 后端启动

```bash
# 1. 克隆仓库
git clone https://github.com/zaojiankongli/langchain4j-sister-project.git
cd langchain4j-sister-project

# 2. 配置环境变量（参见下方配置表）
export REDIS_HOST=localhost
export MYSQL_HOST=localhost
export DASHSCOPE_API_KEY=your-key
# ... 完整列表见配置表

# 3. 编译
./mvnw compile

# 4. 运行
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端启动

```bash
cd langchain4j_sister_ui/langchain4j_sister_ui/vue
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，Vite 配置自动代理 `/api` 和 `/ws` 到后端。

---

## 环境变量配置

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | **必填** |
| `JWT_SECRET` | JWT 签名密钥 | **必填** |
| `MYSQL_HOST` | MySQL 主机 | `localhost` |
| `MYSQL_PORT` | MySQL 端口 | `3306` |
| `MYSQL_DATABASE` | 数据库名 | `zjkl_sister` |
| `MYSQL_USERNAME` | MySQL 用户名 | **必填** |
| `MYSQL_PASSWORD` | MySQL 密码 | **必填** |
| `REDIS_HOST` | Redis 主机 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_DATABASE` | Redis 数据库编号 | `0` |
| `REDIS_PASSWORD` | Redis 密码 | 可选 |
| `MAIL_HOST` | SMTP 服务器 | **必填** |
| `MAIL_PORT` | SMTP 端口 | `465` |
| `MAIL_USERNAME` | 邮箱账号 | **必填** |
| `MAIL_PASSWORD` | 邮箱密码/授权码 | **必填** |
| `OSS_ENDPOINT` | OSS Endpoint | 可选 |
| `OSS_ACCESS_KEY_ID` | OSS AccessKey | 可选 |
| `OSS_ACCESS_KEY_SECRET` | OSS Secret | 可选 |
| `OSS_BUCKET_NAME` | OSS Bucket | 可选 |
| `APP_DEFAULT_IMAGE_URL` | 默认用户头像 | 可选 |

---

## 项目结构

```
langchain4j_sister_backend/
├── src/main/java/com/zjkl/
│   ├── common/              # 公共模块
│   │   ├── config/          # 通用配置
│   │   ├── context/         # 上下文（UserContext 等）
│   │   ├── exception/       # 全局异常处理
│   │   ├── interceptor/     # JWT 认证拦截器
│   │   └── Result.java      # 统一响应体
│   ├── auth/                # 认证模块
│   │   ├── controller/      # 登录/注册 API
│   │   ├── service/         # AuthService
│   │   └── util/            # JWT 工具类
│   ├── user/                # 用户模块
│   │   ├── controller/      # 用户画像 API
│   │   ├── domain/          # 用户实体
│   │   ├── mapper/          # MyBatis Mapper
│   │   └── service/         # 用户服务
│   ├── ai/                  # AI 核心模块
│   │   ├── chat/            # 聊天 + STOMP 消息推送
│   │   │   ├── controller/  # 消息 API
│   │   │   ├── entity/      # 消息实体
│   │   │   ├── service/     # SisterChatService, ConverMessageService
│   │   │   ├── stomp/       # WebSocket 处理器 + 消息推送
│   │   │   └── config/      # STOMP 配置
│   │   ├── image/           # 图片 VLM 理解
│   │   ├── memory/          # 对话记忆
│   │   ├── summary/         # 摘要生成（Redis Stream 消费者）
│   │   ├── oss/             # 阿里云 OSS 服务
│   │   ├── prompt/          # 提示词模板管理
│   │   ├── component/       # AI 通用组件（UserActivityTracker）
│   │   └── peek/            # 屏幕感知模块
│   ├── emotion/             # 情感引擎
│   │   ├── config/          # 情感引擎配置
│   │   ├── controller/      # 情感 API
│   │   ├── model/           # EmotionalState, DeltaEmotion, VoiceParams 等
│   │   ├── service/         # EmotionService, ChatVoiceService 等
│   │   └── util/            # LlmResponseStreamParser, AudioBuffer
│   ├── wakeup/              # 定时唤醒模块
│   │   ├── service/         # WakeUpService
│   │   └── tool/            # TimeContextTool, UserStateTool
│   ├── push/                # 推送通知
│   ├── recommendation/      # 内容推荐（Agentic Workflow）
│   └── anchor/              # 锚点匹配
│
├── src/main/resources/
│   ├── application.yml       # 主配置
│   ├── application-dev.yml   # 开发环境
│   └── application-prod.yml  # 生产环境
│
├── pom.xml
└── README.md
```

前端结构（位于 `vue/` 目录）：

```
vue/
├── src/
│   ├── api/              # Axios 实例 + API 模块
│   ├── assets/           # Live2D 模型、图片、音频
│   ├── components/       # 通用组件
│   │   ├── chat/         # 聊天相关组件
│   │   ├── dashboard/    # 仪表盘子组件
│   │   └── Panel/        # 侧栏面板
│   ├── composables/      # Vue 组合式函数
│   ├── config/           # API 地址等配置
│   ├── router/           # 路由定义
│   ├── stores/           # Pinia 状态管理
│   ├── utils/            # 工具函数（WebSocket, auth, Live2D）
│   ├── views/            # 页面视图
│   ├── App.vue           # 根组件
│   └── main.js           # 入口
```

---

## API 概览

### 认证

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/send-code` | POST | 发送邮箱验证码 |
| `/api/auth/login` | POST | 登录 / 注册 |
| `/api/auth/refresh-token` | POST | 刷新 Token |
| `/api/auth/logout` | POST | 登出 |

### 聊天

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat/send` | POST | 发送文本消息 |
| `/api/chat/send-audio` | POST | 发送语音消息 |
| `/api/chat/list` | GET | 获取消息历史 |

### 情感

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/emotion/state` | GET | 获取用户情绪状态 |
| `/api/emotion/personality` | GET | 获取人格配置 |
| `/api/emotion/mood-label` | GET | 获取情绪标签 |
| `/api/emotion/mood-description` | GET | 获取情绪描述 |
| `/api/emotion/reset` | POST | 重置情绪到基准值 |

### Peek

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/peek/callback` | POST | 截图上传回调 |

### WebSocket

连接端点：`/ws` (STOMP over SockJS)

| 订阅 | 说明 |
|------|------|
| `/user/queue/chat` | 聊天消息推送 |
| `/user/queue/control` | 控制消息（心跳、认证结果等） |

---

## 架构决策

### 为什么使用 PAD 情绪模型而不是简单的标签？

PAD 模型提供连续的数值空间（[-1, 1] 三维），能够：

- 表达细腻的情绪渐变（不只是"开心/难过"二值）
- 支持 Delta 增量更新（每次对话对情绪影响可量化）
- 自然衰减 + 回归人格基准（模拟真实情绪恢复）

### 为什么使用 Redis Stream 而不是消息队列？

- 轻量无需额外中间件（项目已依赖 Redis）
- 消费者组支持多实例消费
- 适合异步非关键任务（图片生成、摘要总结）

### 为什么使用虚拟线程？

Java 21 虚拟线程非常适合 I/O 密集型的消息推送场景：

- 每个用户独立的 sender loop 无需池化
- 阻塞操作（queue.poll）不消耗 OS 线程
- 代码保持同步风格，无需 Reactor 或回调

### Future Work

- [ ] 服务接口全面抽取（当前进行中）
- [ ] ChatVoiceService 模块归位（emotion → ai.chat）
- [ ] ChatPushService 拆分（ConnectionStateManager / HeartbeatChecker）
- [ ] Dashboard.vue 拆分（当前 800+ 行）
- [ ] 前端路由懒加载
- [ ] 单元测试覆盖
- [ ] 情绪锚点系统完善

---

## 截图

<!-- 截图占位 —— 请替换为实际截图 -->

| 聊天界面 | 情感面板 | Live2D |
|---------|---------|--------|
| ![](docs/screenshots/chat.png) | ![](docs/screenshots/emotion.png) | ![](docs/screenshots/live2d.png) |

---

## License

[MIT](LICENSE)
