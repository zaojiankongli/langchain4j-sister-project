# AI 女友微信小程序 Realtime 错误恢复策略 v1

## 目标
定义实时语音陪伴在小程序环境中的最小错误恢复策略，保证会话失败时可恢复、可回退、可解释，而不是直接把用户卡死在异常状态里。

---

## 1. 适用范围

本策略只覆盖 V1 的以下场景：
- 建立实时会话失败
- 会话中途断开
- AI 长时间无回复
- 摘要拉取失败
- Live2D 渲染失败

不覆盖：
- 后台保活
- 多设备切换
- 通话中断后自动恢复到原音频流

---

## 2. 前端状态扩展

在现有 realtime 状态机基础上，补充可恢复视角：

- `idle`
- `connecting`
- `connected`
- `listening`
- `thinking`
- `speaking`
- `ended`
- `error_recoverable`
- `error_terminal`

说明：
- `error_recoverable`：可以重试或重新建立会话
- `error_terminal`：当前会话已不可恢复，只能退出并重新进入

---

## 3. 场景与恢复策略

## 场景 A：创建会话失败

### 触发条件
- `/realtime/session/create` 返回失败
- provider token 缺失
- wsUrl 缺失

### 前端处理
1. 页面状态进入 `error_recoverable`
2. 展示文案：`连接失败，请重试`
3. 按钮：`重试` / `返回聊天`

### 后端要求
- 创建失败必须返回可读 `message`
- 不返回半有效会话数据

---

## 场景 B：会话建立成功后中途断开

### 触发条件
- WebSocket 断开
- provider 主动 close
- 网络抖动

### 前端处理
1. 若当前处于 `connecting` / `connected` / `listening` / `thinking` / `speaking`，统一切到 `error_recoverable`
2. 展示文案：`语音连接已中断`
3. 提供按钮：`重新连接` / `结束会话`
4. 不自动续连旧流，只重新创建新 session

### 原则
V1 不做“无缝续会话”，避免状态复杂度失控。

---

## 场景 C：AI 长时间无回复

### 触发条件
- 用户停止说话后，超过阈值（如 8~12 秒）仍停留在 `thinking`

### 前端处理
1. 显示轻提示：`她有点卡住了，正在努力回应…`
2. 超过第二阈值（如 20 秒）进入 `error_recoverable`
3. 给按钮：`重新开始这次语音` / `回到聊天`

### 后端要求
- provider 超时必须能被前端识别为明确错误，而不是静默悬空

---

## 场景 D：摘要拉取失败

### 触发条件
- `GET /realtime/session/:id/summary` 失败

### 前端处理
1. 不阻断挂断后的退出流程
2. 展示简化文案：`本次通话已结束，摘要稍后可在回忆页查看`
3. 保留按钮：`回聊天` / `去回忆页`

### 原则
摘要失败不能让整次语音会话看起来像失败。

---

## 场景 E：Live2D 渲染失败

### 触发条件
- Live2D 初始化失败
- 模型资源加载失败
- 小程序渲染兼容失败

### 前端处理
1. 保持实时语音页可用
2. 降级为静态头像 / 静态插画 / 占位图
3. 不影响会话状态逻辑与音频流

### 原则
Live2D 是增强层，不是会话生死依赖。

---

## 4. 用户可见恢复按钮规范

### 可恢复类错误
- 主按钮：`重试`
- 次按钮：`返回聊天`

### 终止类错误
- 主按钮：`重新进入语音`
- 次按钮：`返回聊天`

不建议出现超过 2 个操作按钮，避免用户在故障时做选择题。

---

## 5. 日志与事件要求

前端至少上报：
- `realtime_session_create_failed`
- `realtime_session_disconnected`
- `realtime_session_timeout`
- `realtime_summary_failed`
- `realtime_live2d_fallback_used`

这些事件应带：
- `sessionId`（如果有）
- `userId`
- `provider`
- `stage`
- `errorMessage`
- `timestamp`

---

## 6. V1 成功标准

1. 任何实时语音异常都不会让页面卡死。
2. 用户总能回到聊天页。
3. Live2D 失败不影响语音能力。
4. 摘要失败不影响会话结束。
