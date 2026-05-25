<p align="center">
  <img src="docs/screenshots/screenshot-01.png" alt="Sister Project" width="600"/>
</p>

<h1 align="center">🌸 Sister Project</h1>
<h3 align="center">一个有温度、有情绪、看得见你的 AI 妹妹</h3>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen" alt="Spring Boot 3"/>
  <img src="https://img.shields.io/badge/Vue-3-4FC08D" alt="Vue 3"/>
  <img src="https://img.shields.io/badge/LangChain4j-✓-orange" alt="LangChain4j"/>
  <img src="https://img.shields.io/badge/Live2D-✓-ff69b4" alt="Live2D"/>
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License"/>
</p>

---

## 📸 功能展示

> 以下截图展示了 Sister 的核心交互界面。图片来自实际运行效果。

<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/screenshots/screenshot-01.png" width="100%" alt="聊天界面"/>
      <br/>
      <strong>💬 语音聊天</strong>
      <br/>
      <sub>流式 LLM 回复 + 实时 TTS 语音合成，Live2D 模型同步唇形和表情</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/screenshots/screenshot-02.png" width="100%" alt="情感面板"/>
      <br/>
      <strong>🎭 情感引擎</strong>
      <br/>
      <sub>PAD 三维情绪可视化，实时显示愉悦度、唤醒度、支配感变化</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/screenshots/screenshot-03.png" width="100%" alt="记忆与人格"/>
      <br/>
      <strong>🧠 记忆与人格</strong>
      <br/>
      <sub>OCEAN 人格模型配置、对话历史回顾、摘要记忆</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/screenshots/screenshot-04.png" width="100%" alt="功能面板"/>
      <br/>
      <strong>🎯 功能中心</strong>
      <br/>
      <sub>情绪锚点、动作中心、邮件系统、个性化设置</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/screenshots/screenshot-05.png" width="100%" alt="情绪云"/>
      <br/>
      <strong>☁️ 情绪云</strong>
      <br/>
      <sub>情绪词云可视化、性格标签、历史情绪轨迹</sub>
    </td>
    <td width="50%" align="center">
      <!-- 留空或由用户自行补充 -->
    </td>
  </tr>
</table>

---

## 🌟 项目简介

**Sister** 不只是一个聊天机器人。她是一个有**情绪**、有**个性**、能**看见**你的世界、能**听见**你声音的 AI 伴侣。

她的核心设计围绕一个理念：**让 AI 有温度**。

- 和她聊天，她会根据语气调整情绪 —— 你开心她也开心，你低落她会温柔安慰
- 你发图片，她能理解图片内容并融入对话
- 你不说话的时候，她会偶尔"偷偷看你"（Peek），关心你在做什么
- 她有自己的人格设定（OCEAN 模型），决定了她的性格底色
- 她会记住你们的对话，形成长期记忆

---

## ✨ 核心特性

<details open>
<summary><strong>🎙️ 语音聊天 · Voice Chat</strong></summary>
<br/>
<ul>
  <li><strong>流式 LLM 回复</strong> — 基于 LangChain4j + 通义千问，低延迟逐 token 推送到前端</li>
  <li><strong>实时 TTS 合成</strong> — 阿里云 DashScope 语音合成，支持音量/语速/音高/情感指令调节</li>
  <li><strong>括号内容过滤</strong> — 自动过滤 LLM 回复中的动作描写（如"脸红"），保留纯净对话语音</li>
  <li><strong>音频流式播放</strong> — 200ms 缓冲后开始播放，体验流畅</li>
</ul>
</details>

<details>
<summary><strong>🎭 情感引擎 · Emotion Engine</strong></summary>
<br/>
<ul>
  <li><strong>PAD 三维模型</strong> — Pleasure（愉悦）、Arousal（唤醒）、Dominance（支配），[-1, 1] 连续数值空间</li>
  <li><strong>OCEAN 人格</strong> — 开放性、尽责性、外向性、宜人性、神经质，决定情绪基准和衰减曲线</li>
  <li><strong>增量情绪更新</strong> — 每次对话可引发 delta 变化，多次交互累积出情绪弧线</li>
  <li><strong>自然衰减 + 回归基准</strong> — 情绪随时间淡化和回归人格底色，模拟真实情感恢复</li>
  <li><strong>Caffeine 本地缓存 + Redis 持久化</strong> — 毫秒级读取，跨实例共享</li>
  <li><strong>Redisson 分布式锁</strong> — 保证情绪更新的原子性和一致性</li>
</ul>
</details>

<details>
<summary><strong>🖼️ 多模态理解 · Multimodal</strong></summary>
<br/>
<ul>
  <li><strong>图片 VLM 理解</strong> — 用户发送图片后，通义千问 VLM 自动理解内容，融入对话上下文</li>
  <li><strong>屏幕感知 (Peek)</strong> — 定时请求前端截图，VLM 分析用户状态，自动发送关怀消息</li>
  <li><strong>异步并行处理</strong> — 图片理解与 LLM 流式回复并行执行，不阻塞聊天</li>
</ul>
</details>

<details>
<summary><strong>🔔 主动交互 · Proactive</strong></summary>
<br/>
<ul>
  <li><strong>WakeUp 定时唤醒</strong> — 在合适的时间主动发起对话（早晨问候、饭点提醒等）</li>
  <li><strong>Peek 屏幕感知</strong> — 定期请求截图，根据用户活动状态生成个性化关怀</li>
  <li><strong>情感锚点 (Anchor)</strong> — PAD 值进入特定区域时自动触发情绪反馈</li>
  <li><strong>内容推荐 (Recommendation)</strong> — Agentic Workflow 驱动的个性化推荐</li>
</ul>
</details>

<details>
<summary><strong>💾 记忆系统 · Memory</strong></summary>
<br/>
<ul>
  <li><strong>Redis 短期记忆</strong> — ChatMemoryProvider 管理对话上下文</li>
  <li><strong>MySQL 持久化</strong> — 消息记录长期存储</li>
  <li><strong>自动摘要</strong> — Redis Stream 消费者异步生成对话摘要</li>
  <li><strong>向量存储</strong> — Milvus 语义检索，支持长期记忆查询</li>
</ul>
</details>

<details>
<summary><strong>🔐 认证与安全</strong></summary>
<br/>
<ul>
  <li><strong>JWT 双 Token</strong> — Access Token (2h) + Refresh Token (7d)</li>
  <li><strong>邮箱验证码</strong> — 验证码登录/注册</li>
  <li><strong>Token 自动刷新</strong> — Access Token 过半有效期时自动续期</li>
</ul>
</details>

---

## 🧠 情感引擎工作原理

Sister 的情感系统是整个项目最核心的设计。它不是简单的标签匹配，而是一个完整的**动态数值模型**。

```
                        人格 (OCEAN)
                             │
                             ▼
                     ┌─────────────────┐
                     │  基准情绪 (Base) │
                     │  P₀, A₀, D₀     │
                     └────────┬────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
         ▼                    ▼                    ▼
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │ 对话刺激 ΔP  │   │ 对话刺激 ΔA  │   │ 对话刺激 ΔD  │
  │ × 敏感度系数  │   │ × 敏感度系数  │   │ × 敏感度系数  │
  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │  更新情绪 P₁    │
                     │  + 衰减 decay   │
                     │  + 回归 regression│
                     │                 │
                     │  P₁ = P₀×(1-d)  │
                     │     + (B-P₀)×r  │
                     └────────┬────────┘
                              │
               ┌──────────────┼──────────────┐
               │              │              │
               ▼              ▼              ▼
         ┌──────────┐  ┌──────────┐  ┌──────────┐
         │ 情绪描述  │  │ 情绪标签  │  │ 锚点触发  │
         │ 自然语言  │  │ 简短标签  │  │ 主动互动  │
         └──────────┘  └──────────┘  └──────────┘
```

**关键参数：**
| 参数 | 说明 | 默认值 |
|------|------|--------|
| `sensitivity` | 敏感度 — 情绪对外部刺激的反应幅度 | 可配置 |
| `decayRate` | 衰减率 — 情绪随时间衰减的速度 | 0.1 |
| `regressionRate` | 回归率 — 情绪回归人格基准的速率 | 0.05 |

---

## 🚀 快速开始

### 前置条件

| 依赖 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 17+ | 后端运行 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0+ | 数据持久化 |
| Redis | 7+ | 缓存 + 消息队列 |
| 阿里云 DashScope | API Key | LLM + TTS + VLM |

### 后端启动

```bash
# 1. 克隆
git clone https://github.com/zaojiankongli/langchain4j-sister-project.git
cd langchain4j-sister-project

# 2. 配置环境变量
# 复制下方配置表，设置对应的环境变量

# 3. 编译
./mvnw compile

# 4. 运行开发模式
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端启动

```bash
cd langchain4j_sister_ui/langchain4j_sister_ui/vue
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，Vite 自动代理 `/api` 和 `/ws` 到后端。

---

## 🔧 环境变量

| 变量 | 说明 | 必填 | 默认值 |
|------|------|------|--------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key | ✅ | — |
| `JWT_SECRET` | JWT 签名密钥 | ✅ | — |
| `MYSQL_HOST` | MySQL 地址 | ✅ | `localhost` |
| `MYSQL_PORT` | MySQL 端口 | | `3306` |
| `MYSQL_DATABASE` | 数据库名 | | `zjkl_sister` |
| `MYSQL_USERNAME` | MySQL 用户名 | ✅ | — |
| `MYSQL_PASSWORD` | MySQL 密码 | ✅ | — |
| `REDIS_HOST` | Redis 地址 | ✅ | `localhost` |
| `REDIS_PORT` | Redis 端口 | | `6379` |
| `REDIS_DATABASE` | Redis 数据库 | | `0` |
| `REDIS_PASSWORD` | Redis 密码 | | — |
| `MAIL_HOST` | SMTP 服务器 | ✅ | — |
| `MAIL_PORT` | SMTP 端口 | | `465` |
| `MAIL_USERNAME` | 邮箱账号 | ✅ | — |
| `MAIL_PASSWORD` | 邮箱密码/授权码 | ✅ | — |
| `OSS_ENDPOINT` | OSS Endpoint | | — |
| `OSS_ACCESS_KEY_ID` | OSS AccessKey | | — |
| `OSS_ACCESS_KEY_SECRET` | OSS Secret | | — |
| `OSS_BUCKET_NAME` | OSS Bucket | | — |
| `APP_DEFAULT_IMAGE_URL` | 默认头像 URL | | — |

---

## 📁 项目结构

```
langchain4j_sister_backend/
├── src/main/java/com/zjkl/
│   ├── common/                  # 公共模块
│   │   ├── config/              # 通用配置
│   │   ├── context/             # UserContext (请求级线程上下文)
│   │   ├── exception/           # 全局异常处理器
│   │   ├── interceptor/         # JWT 认证拦截器
│   │   └── Result.java          # 统一响应体
│   │
│   ├── auth/                    # 认证 ✅
│   │   ├── controller/          # 登录/注册/刷新 Token
│   │   ├── service/             # AuthService
│   │   └── util/                # JWT 工具、UserContext
│   │
│   ├── user/                    # 用户 ✅
│   │   ├── controller/          # 用户画像、兴趣标签
│   │   ├── domain/              # 用户实体、VO、DTO
│   │   ├── mapper/              # MyBatis DAO
│   │   └── service/             # 用户服务
│   │
│   ├── ai/                      # AI 核心
│   │   ├── chat/                # 💬 聊天 + STOMP
│   │   │   ├── config/          # STOMP/WebSocket 配置
│   │   │   ├── controller/      # 消息 Rest API
│   │   │   ├── entity/          # 消息体
│   │   │   ├── service/         # SisterChatService
│   │   │   └── stomp/           # WebSocket 处理器 + ChatPushService
│   │   ├── image/               # 🖼️ VLM 图片理解
│   │   ├── memory/              # 💾 对话记忆
│   │   ├── summary/             # 📝 摘要生成 (Redis Stream)
│   │   ├── oss/                 # ☁️ 阿里云 OSS
│   │   ├── prompt/              # 📋 提示词模板
│   │   ├── component/           # 🔧 通用组件 (活动追踪器)
│   │   └── peek/                # 👀 屏幕感知
│   │
│   ├── emotion/                 # 🎭 情感引擎
│   │   ├── config/              # 情感模型参数
│   │   ├── controller/          # 情感状态 API
│   │   ├── model/               # EmotionalState, DeltaEmotion, VoiceParams
│   │   ├── service/             # EmotionService, ChatVoiceService
│   │   └── util/                # 流解析器, 音频缓冲区
│   │
│   ├── wakeup/                  # ⏰ 定时唤醒
│   │   ├── service/             # WakeUpService
│   │   └── tool/                # 时间/状态工具
│   │
│   ├── recommendation/          # 🎯 内容推荐
│   ├── anchor/                  # 📍 情绪锚点
│   └── push/                    # 🔔 推送通知
│
├── src/main/resources/
│   ├── application.yml          # 主配置
│   ├── application-dev.yml      # 开发环境
│   └── application-prod.yml     # 生产环境
│
├── docs/
│   └── screenshots/             # 📸 功能截图
│
├── pom.xml
└── README.md
```

前端结构 (`langchain4j_sister_ui/vue/`)：

```
vue/src/
├── api/              # 🌐 Axios 实例 + API 模块
├── assets/           # 🎨 Live2D 模型、图片、音频
├── components/       # 🧩 通用组件
│   ├── chat/         # 聊天窗口组件
│   ├── dashboard/    # 仪表盘子组件
│   └── Panel/        # 侧栏面板
├── composables/      # 🪝 Vue 组合式函数
├── config/           # ⚙️ 环境配置
├── router/           # 🧭 路由定义
├── stores/           # 📦 Pinia 状态管理
├── utils/            # 🛠️ 工具函数
├── views/            # 📄 页面视图
├── App.vue           # 根组件
└── main.js           # 入口
```

---

## 📡 API 概览

### 认证接口

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/auth/send-code` | 发送邮箱验证码 |
| POST | `/api/auth/login` | 登录/注册 |
| POST | `/api/auth/refresh-token` | 刷新 Token |
| POST | `/api/auth/logout` | 登出 |

### 聊天接口

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送文本/图片消息 |
| POST | `/api/chat/send-audio` | 发送语音消息 |
| GET | `/api/chat/list` | 获取历史消息 |

### 情感接口

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/emotion/state` | 获取情绪状态 |
| GET | `/api/emotion/personality` | 获取人格配置 |
| GET | `/api/emotion/mood-label` | 获取情绪标签 |
| GET | `/api/emotion/mood-description` | 获取情绪描述 |
| POST | `/api/emotion/reset` | 重置情绪 |

### WebSocket (STOMP over SockJS)

| 事件 | 方向 | 说明 |
|------|------|------|
| 连接 `/ws` | — | STOMP 入口 |
| 订阅 `/user/queue/chat` | 服务端 → 客户端 | 聊天消息推送 |
| 订阅 `/user/queue/control` | 服务端 → 客户端 | 控制消息 (心跳/认证) |
| 发送 `/app/chat.send` | 客户端 → 服务端 | 发送聊天消息 |
| 发送 `/app/auth` | 客户端 → 服务端 | WebSocket 认证 |

---

## 🏗️ 架构决策

### 为什么 PAD 模型而不是标签分类？

传统标签式情绪（开心/难过/愤怒）只能表达离散状态，而 Sister 需要**连续渐变**的情绪体验：

- PAD 三维空间可以表达 1000+ 种细腻情绪
- Delta 增量更新使每次对话都能产生可量化的影响
- 衰减 + 回归机制模拟了真实情感的"心情恢复"过程

### 为什么 Redis Stream 而不是 RabbitMQ/Kafka？

- 项目已经强依赖 Redis（缓存 + 分布式锁 + 会话管理）
- Stream 的消费者组足以满足摘要生成、图片生成等异步场景
- 减少中间件数量，降低运维成本

### 为什么虚拟线程而不是线程池？

Java 21 虚拟线程天然适合 ChatPushService 中每个用户一个 sender loop 的模式：

- 无需池化，每个连接独立线程
- `BlockingQueue.poll()` 不再浪费 OS 线程
- 代码维持同步风格，可读性强

### 为什么 Caffeine + Redis 双层缓存？

- 本地缓存（Caffeine）保证情绪读取 < 1ms
- Redis 层实现跨实例共享
- 写入时双写，保障一致性

---

## 🛣️ 开发路线

- [x] Phase 1: 模块边界清理 (Result/UserContext/Peek 归位)
- [x] Phase 1: ErrorBoundary 组件
- [ ] Phase 2: 服务接口抽取 (进行中)
- [ ] Phase 2: ChatVoiceService 模块归位
- [ ] Phase 2: ChatPushService 拆分
- [ ] Phase 3: Dashboard.vue 组件拆分 (800+ 行)
- [ ] Phase 3: 前端路由懒加载
- [ ] Phase 4: 单元测试 + 集成测试
- [ ] Phase 4: 情绪锚点系统完善

---

## 📄 License

[MIT](LICENSE)

---

<p align="center">
  <sub>Built with ❤️ using LangChain4j · Spring Boot · Vue 3 · Live2D</sub>
</p>

<p align="center">
  <a href="https://github.com/zaojiankongli/langchain4j-sister-project">GitHub</a>
</p>
