# AI 女友微信小程序 实施矩阵 v1

## 目的
把页面、组件、状态、接口、阶段交付压成一张可执行矩阵，方便前端、后端、产品直接按模块分工。

---

## 1. 页面实施矩阵

| 页面 | 核心目标 | 关键组件 | 主要状态 | 依赖接口 | 所属阶段 |
|---|---|---|---|---|---|
| 聊天页 | 完成文本聊天主链路 | message-list、message-item-text、message-item-audio、chat-input-bar | 当前消息列表、发送中、播放中的 audioId、聊天背景、AI 回复状态 | `/chat/send` `/chat/history` `/settings` | Phase 1 |
| 回忆页 | 展示她记住了什么 | memory-card、memory-timeline、memory-filter-tabs | 当前分类、记忆列表、时间线、加载状态 | `/memory/list` `/memory/timeline` `/relationship/status` | Phase 2 |
| 我的页 | 展示基础信息与统计 | user-summary-card、interaction-stats-card | 用户信息、统计信息 | `/user/profile` | Phase 3 |
| 设置页 | 管理偏好与背景 | setting-item、voice-toggle、style-selector、background-selector | 回复风格、主动程度、称呼方式、TTS 开关、背景设置 | `GET /settings` `PUT /settings` | Phase 3 |
| 实时语音页 | 完成实时语音陪伴 | live2d-stage、realtime-status-bar、voice-session-panel、summary-card | sessionId、连接状态、会话状态、会话时长、摘要数据 | `/realtime/session/create` `/realtime/session/end` `/realtime/session/:id/summary` | Phase 4 |
| 陪伴页 | 提供情绪入口与快速跳转 | companion-status-card、quick-action-panel、memory-preview-card | 当前状态、主动文案、最近回忆摘要 | `/relationship/status` `/home/companion` | Phase 5 |

---

## 2. 前端状态矩阵

| Store / 状态域 | 负责内容 | 被哪些页面消费 | 何时初始化 |
|---|---|---|---|
| chat store | 当前会话消息、历史游标、发送状态、AI 回复状态、背景配置引用 | 聊天页 | 进入聊天页时 |
| memory store | 记忆列表、时间线、筛选条件 | 回忆页、首页摘要 | 进入回忆页/首页时 |
| user store | 用户基础信息、统计信息 | 我的页 | 进入我的页时 |
| settings store | 回复风格、主动程度、称呼方式、TTS 开关、聊天背景 | 设置页、聊天页 | 进入设置页或聊天页首次需要时 |
| realtime store | 实时会话状态、会话摘要、连接生命周期 | 实时语音页 | 进入实时语音页时 |
| relationship store | 亲密度、熟悉度、情绪状态、首页摘要 | 首页、回忆页（概览） | 进入首页时 |

---

## 3. 接口消费矩阵

| 接口 | 前端用途 | 直接消费页面/模块 | 说明 |
|---|---|---|---|
| `GET /user/profile` | 加载用户信息与统计 | 我的页 | 只服务基础信息，不承担关系态 |
| `POST /chat/send` | 发送文本并返回 AI 回复 | 聊天页 | 最好一次返回文本 + 可用音频信息 |
| `GET /chat/history` | 加载历史消息 | 聊天页 | 支持分页 |
| `POST /tts/generate` 或聊天内联 | 生成/获取 AI 音频 | 聊天页 | 建议内联，减少前端链路复杂度 |
| `GET /memory/list` | 获取记忆分类列表 | 回忆页 | 支持分类筛选 |
| `GET /memory/timeline` | 获取回忆时间线 | 回忆页 | 用于时间轴展示 |
| `GET /relationship/status` | 获取关系值与情绪 | 首页、回忆页概览 | 首页的基础状态来源 |
| `GET /home/companion` | 获取首页角色卡摘要 | 首页 | 减少前端聚合接口数量 |
| `GET /settings` | 获取偏好 | 设置页、聊天页 | 聊天页主要用来拿背景和 TTS 开关 |
| `PUT /settings` | 保存偏好 | 设置页 | 更新后聊天页应即时生效 |
| `POST /realtime/session/create` | 建立实时会话 | 实时语音页 | 返回 session 参数 |
| `POST /realtime/session/end` | 结束实时会话 | 实时语音页 | 结束时调用 |
| `GET /realtime/session/:id/summary` | 获取会话摘要 | 实时语音页 | 用于结束总结卡 |

---

## 4. 开发分工建议

### 前端先做
1. 聊天页结构与消息组件
2. 聊天历史 + 文本发送 + AI 回复渲染
3. AI 音频播放
4. 回忆页基础列表
5. 我的页基础信息
6. 设置页基础偏好

### 后端先做
1. `/chat/send`
2. `/chat/history`
3. TTS 输出能力
4. `/memory/list`
5. `/memory/timeline`
6. `/user/profile`
7. `/settings`

### 第二阶段再做
1. Realtime 接入
2. 实时语音页
3. Live2D 适配 / 降级方案
4. 首页陪伴卡片

---

## 5. 交付顺序建议

### 最小闭环 A
聊天页 → `/chat/send` → TTS → `/chat/history`

### 最小闭环 B
聊天结果 → 记忆提炼 → `/memory/list` → 回忆页

### 最小闭环 C
设置页 → `/settings` → 聊天页背景 / TTS 偏好生效

### 差异化闭环 D
实时语音页 → `/realtime/session/create` → 会话结束 → 摘要卡
