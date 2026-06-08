# AI 女友微信小程序 数据实体映射 v1

## 目的
把 MVP 功能和后端实体一一对应，防止页面先画完、接口先写完，但最终数据模型对不上。

---

## 1. 用户实体（User)

### 承载功能
- 我的页用户卡
- 基础统计展示
- 首页基础身份显示（如昵称）

### 建议字段
- `id`
- `nickname`
- `avatarUrl`
- `createdAt`
- `lastActiveAt`
- `companionDays`
- `chatCount`
- `voiceMinutes`

### 直接服务页面
- 我的页

---

## 2. 聊天消息实体（ChatMessage)

### 承载功能
- 聊天页消息流
- 历史消息加载
- AI 文字 / AI 语音展示

### 建议字段
- `messageId`
- `userId`
- `sessionId`
- `role`（user / assistant / system）
- `type`（text / audio / hint）
- `content`
- `audioUrl`
- `createdAt`

### 直接服务页面
- 聊天页

---

## 3. 记忆实体（Memory)

### 承载功能
- 回忆页列表
- 回忆时间线
- 聊天中的记忆召回
- 首页最近回忆摘要

### 建议字段
- `memoryId`
- `userId`
- `type`（profile / preference / shared_experience / emotion_event）
- `level`（short / long）
- `summary`
- `content`
- `source`（chat / realtime / game）
- `importance`
- `emotionTag`
- `createdAt`
- `updatedAt`
- `lastHitAt`
- `hitCount`
- `status`

### 直接服务页面
- 回忆页
- 聊天页（召回提示）
- 首页

---

## 4. 关系状态实体（RelationshipStatus)

### 承载功能
- 首页状态卡
- 回忆页概览
- 我的页辅助统计（可选）

### 建议字段
- `userId`
- `intimacy`
- `familiarity`
- `emotionState`
- `stage`
- `todayDelta`
- `statusText`
- `updatedAt`

### 直接服务页面
- 首页
- 回忆页

---

## 5. 设置实体（UserSettings)

### 承载功能
- 偏好设置页
- 聊天页背景
- TTS 自动播放开关
- 回复风格与称呼方式

### 建议字段
- `userId`
- `replyStyle`
- `initiativeLevel`
- `nicknameStyle`
- `ttsEnabled`
- `chatBackground`
- `updatedAt`

### 直接服务页面
- 设置页
- 聊天页

---

## 6. 实时语音会话实体（RealtimeSession)

### 承载功能
- 实时语音页状态
- 会话结束总结
- 语音统计

### 建议字段
- `sessionId`
- `userId`
- `provider`
- `status`
- `startAt`
- `endAt`
- `duration`
- `summaryId`

### 直接服务页面
- 实时语音页
- 我的页统计（总时长）

---

## 7. 会话摘要实体（SessionSummary)

### 承载功能
- 实时语音结束总结卡
- 回忆时间线补充
- 记忆提炼来源

### 建议字段
- `summaryId`
- `sessionId`
- `userId`
- `summary`
- `emotion`
- `memoryCreatedCount`
- `createdAt`

### 直接服务页面
- 实时语音页总结卡
- 回忆页时间线

---

## 8. 小游戏记录实体（GameSession）【后置】

### 承载功能
- 小游戏结算
- 共同经历写入

### 建议字段
- `gameSessionId`
- `userId`
- `gameType`
- `result`
- `score`
- `memoryCreated`
- `createdAt`

### 直接服务页面
- 小游戏页（后置）
- 回忆页（共同经历来源）

---

## 9. MVP 功能到实体映射总表

| 功能 | 核心实体 |
|---|---|
| 文本聊天 | ChatMessage |
| AI 语音回复（TTS） | ChatMessage |
| 聊天历史 | ChatMessage |
| 记忆提炼 | Memory |
| 回忆页 | Memory + RelationshipStatus |
| 首页陪伴状态 | RelationshipStatus + Memory |
| 我的页基础信息 | User |
| 偏好设置 | UserSettings |
| 实时语音会话 | RealtimeSession + SessionSummary |
