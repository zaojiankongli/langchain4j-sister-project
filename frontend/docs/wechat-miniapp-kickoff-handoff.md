# AI 女友微信小程序 开发启动交接单 v1

## 目标
让前端、后端、AI 编排三方在 Day 1 不需要再开一轮“先干什么”的会，直接按统一顺序启动。

---

## 1. 前端 Day 1

### 先读
1. `wechat-miniapp-delivery-index.md`
2. `wechat-miniapp-week1-sprint-board.md`
3. `wechat-miniapp-implementation-matrix.md`
4. `wechat-miniapp-mock-payloads.md`

### 先锁定
1. 页面目录结构
2. 消息流组件结构
3. chat store / settings store 基础形态

### 当天必须开始做
1. 聊天页骨架
2. 消息列表与气泡组件
3. mock 数据驱动的静态渲染

---

## 2. 后端 Day 1

### 先读
1. `wechat-miniapp-issue-backlog.md`
2. `wechat-miniapp-entity-mapping.md`
3. `wechat-miniapp-api-field-dictionary.md`
4. `backend-owned-memory-controls.md`

### 先锁定
1. `/chat/send` 返回结构
2. `/chat/history` 分页结构
3. TTS 输出方式（内联或独立接口）

### 当天必须开始做
1. `/chat/send`
2. `/chat/history`
3. TTS 输出能力骨架

---

## 3. AI / 编排 Day 1

### 先读
1. `wechat-miniapp-ai-orchestration-contract.md`
2. `wechat-miniapp-prompt-contract.md`
3. `wechat-miniapp-relationship-rules.md`
4. `wechat-miniapp-memory-lifecycle.md`

### 先锁定
1. 文本聊天 prompt 基本模板
2. 记忆注入上限（文本 1~3 条）
3. Realtime 最小上下文注入内容

### 当天必须开始做
1. 文本聊天 prompt 模板初版
2. 记忆提炼候选结构
3. Realtime 会话摘要结构

---

## 4. 联调前置条件

前后端开始联调前，必须先统一三件事：
1. `POST /chat/send` 成功响应字段
2. 历史消息分页字段
3. TTS 音频返回方式

如果这三件没统一，前端联调会反复返工。

---

## 5. 第一个联合里程碑

团队第一个共同里程碑不是“所有页面都搭出来”，而是：

**用户打开聊天页 → 看到历史 → 发一条文本 → AI 回一条文本 → AI 语音可播放**

只要这一条通了，项目就进入正向阶段。
