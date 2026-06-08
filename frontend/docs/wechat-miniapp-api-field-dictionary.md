# AI 女友微信小程序 接口字段字典 v1

## 目标
统一前后端命名，减少“页面有了、接口也有了，但字段对不上”的实现歧义。

---

## 1. 通用响应字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `code` | number | 业务状态码，200 表示成功 |
| `message` | string | 可直接展示的错误/提示文本 |
| `data` | object/array/null | 主体数据 |

---

## 2. 用户资料字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | string | 用户 ID |
| `nickname` | string | 用户昵称 |
| `avatarUrl` | string | 用户头像 |
| `createdAt` | string | 注册时间 |
| `companionDays` | number | 陪伴天数 |
| `chatCount` | number | 聊天次数 |
| `voiceMinutes` | number | 总语音分钟数 |

---

## 3. 聊天消息字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `messageId` | string | 消息唯一 ID |
| `sessionId` | string | 会话 ID |
| `role` | string | `user` / `assistant` / `system` |
| `type` | string | `text` / `audio` / `hint` |
| `content` | string | 文本内容 |
| `audioUrl` | string | AI 语音地址，可为空 |
| `createdAt` | string | 消息创建时间 |
| `hasAudio` | boolean | 是否存在可播放音频 |
| `memoryHints` | string[] | 轻量记忆提示 |

---

## 4. 设置字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `replyStyle` | string | 回复风格 |
| `initiativeLevel` | string | 主动程度 |
| `nicknameStyle` | string | 角色称呼方式 |
| `ttsEnabled` | boolean | 是否自动播报 TTS |
| `chatBackground` | string | 聊天背景标识 |

---

## 5. 关系状态字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `intimacy` | number | 亲密度 |
| `familiarity` | number | 熟悉度 |
| `emotionState` | string | 当前情绪态 |
| `stage` | string | 当前关系阶段 |
| `todayDelta` | number | 今日变化值 |
| `statusText` | string | 首页显示文案 |

---

## 6. 记忆字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `memoryId` | string | 记忆 ID |
| `type` | string | `profile` / `preference` / `shared_experience` / `emotion_event` |
| `level` | string | `short` / `long` |
| `summary` | string | 记忆摘要 |
| `source` | string | `chat` / `realtime` / `game` |
| `importance` | number | 重要度 |
| `emotionTag` | string | 情绪标签 |
| `createdAt` | string | 创建时间 |

---

## 7. 实时语音字段

### 创建会话返回
| 字段 | 类型 | 含义 |
|---|---|---|
| `sessionId` | string | 会话 ID |
| `provider` | string | Realtime 供应商 |
| `token` | string | 临时鉴权令牌 |
| `wsUrl` | string | 实时连接地址 |

### 会话摘要返回
| 字段 | 类型 | 含义 |
|---|---|---|
| `summary` | string | 会话总结 |
| `emotion` | string | 情绪标签 |
| `memoryCreated` | string[] | 新生成记忆摘要 |

---

## 8. 命名约束

1. 小程序新接口统一使用 camelCase。
2. 不再沿用前端历史 Web 中 snake_case / camelCase 混杂风格。
3. 若旧接口仍返回 snake_case，后端或 BFF 层应先统一后再给小程序。
