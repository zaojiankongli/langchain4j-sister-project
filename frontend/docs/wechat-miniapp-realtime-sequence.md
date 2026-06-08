# AI 女友微信小程序 实时语音时序流 v1

## 目标
把实时语音陪伴从“进入页面”到“挂断拿摘要”的全过程写成统一时序，减少前端、后端和 Realtime 供应层联调歧义。

---

## 1. 参与方

- 用户
- 小程序实时语音页
- Realtime Session API
- Realtime provider
- 摘要服务
- Memory 服务
- Relationship 服务

---

## 2. 标准时序

### Step 1：用户进入实时语音页
前端进入页面，状态为 `idle`。

### Step 2：前端创建会话
调用：
`POST /realtime/session/create`

返回：
- `sessionId`
- `provider`
- `token`
- `wsUrl`

前端状态切为 `connecting`。

### Step 3：建立 realtime 连接
前端使用 `wsUrl` + `token` 建连。

连接就绪后：
- 状态切为 `connected`

### Step 4：用户开始说话
前端开始推送音频流：
- 页面状态切为 `listening`

### Step 5：用户一轮说完
前端结束本轮输入：
- 状态切为 `thinking`

### Step 6：AI 回包
provider 开始返回内容：
- 前端状态切为 `speaking`
- 若 Live2D 可用，则同步 speaking 态

### Step 7：本轮回复结束
本轮回复结束后：
- 前端状态回到 `connected`
- 用户可继续下一轮语音

### Step 8：用户挂断
前端调用：
`POST /realtime/session/end`

请求体最小内容：
- `sessionId`
- `duration`

### Step 9：后端生成摘要与记忆候选
后端：
1. 结束会话
2. 生成摘要
3. 提炼记忆候选
4. 更新关系状态

### Step 10：前端拉取摘要
调用：
`GET /realtime/session/:sessionId/summary`

前端展示：
- 会话摘要
- 情绪结果
- 新增记忆提示

状态切为 `ended`。

---

## 3. 成功条件

一次完整实时语音最小成功定义：
1. 会话创建成功
2. 用户能完成一轮语音输入
3. AI 有一轮实时回复
4. 挂断后有摘要卡

---

## 4. 失败分支

### F1：创建会话失败
- 前端进入 `error_recoverable`
- 提供重试和回聊天页按钮

### F2：会话中途断开
- 前端进入 `error_recoverable`
- 不自动续旧会话，只允许重建新会话

### F3：摘要失败
- 不阻断挂断流程
- 提示“稍后在回忆页查看”

### F4：Live2D 失败
- 降级为静态角色图
- 不影响语音主链路

---

## 5. V1 原则

1. Realtime 是差异化能力，但不能拖死聊天主链路。
2. Live2D 是增强层，不是语音会话生命线。
3. 摘要失败不能让整次语音体验看起来像失败。
