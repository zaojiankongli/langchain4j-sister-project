# AI 女友微信小程序 Java 后端模块蓝图 v1

## 目标
把后端按职责边界拆成可实现的模块，避免聊天、记忆、Realtime、设置、关系状态逻辑互相缠绕。

---

## 1. 模块总览

建议按以下领域拆分：

1. `auth`：登录态、用户上下文
2. `chat`：文本聊天主链路
3. `tts`：AI 语音输出
4. `realtime`：实时语音会话
5. `memory`：记忆提炼、写入、召回、展示
6. `relationship`：亲密度/熟悉度/情绪状态
7. `settings`：偏好配置
8. `home`：首页聚合摘要

---

## 2. 建议服务边界

## `AuthService`
- 读取当前用户
- 校验用户会话
- 向下游暴露稳定的 `userId`

## `ChatService`
- 接收用户文本
- 协调设置、关系状态、记忆召回
- 调用 AI 编排层
- 写入聊天消息
- 返回 AI 文本与可选 TTS 结果

## `TtsService`
- 文本转语音
- 返回 `audioUrl` 或音频资源定位
- 对接音色配置

## `RealtimeSessionService`
- 创建会话
- 结束会话
- 会话状态持久化
- 生成会话摘要

## `MemoryService`
- 提炼记忆候选
- 去重合并
- 写入短期/长期记忆
- 提供回忆页读取与聊天召回

## `RelationshipService`
- 计算亲密度/熟悉度/情绪状态
- 提供首页和回忆页的概览数据

## `SettingsService`
- 读取设置
- 保存设置
- 为 Chat / Realtime 编排提供配置输入

## `HomeSummaryService`
- 聚合首页所需状态
- 返回角色状态文案、最近回忆、快速动作摘要

---

## 3. 请求编排建议

### 文本聊天
`Controller -> ChatService -> (SettingsService + RelationshipService + MemoryService + AI Orchestrator + TtsService)`

### 实时语音
`Controller -> RealtimeSessionService -> AI Orchestrator -> MemoryService -> RelationshipService`

### 回忆页
`Controller -> MemoryService + RelationshipService`

### 首页
`Controller -> HomeSummaryService`

---

## 4. 后端必须坚持的约束

1. 记忆所有权始终在后端。
2. 前端不能上传“已整理好的记忆”来反向污染 Memory。
3. Realtime 状态要由后端归一化，不直接透传 provider 生事件给小程序。
4. 设置读取必须早于聊天 prompt 拼装。

---

## 5. V1 落地优先级

### 第一批后端模块
- AuthService
- ChatService
- TtsService
- MemoryService（最小版）
- SettingsService

### 第二批
- RealtimeSessionService
- RelationshipService
- HomeSummaryService

---

## 6. 适合作为包结构的示意

```text
com.xxx.miniapp
  auth/
  chat/
  tts/
  realtime/
  memory/
  relationship/
  settings/
  home/
  common/
```
