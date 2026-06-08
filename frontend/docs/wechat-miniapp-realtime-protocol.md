# AI 女友微信小程序 Realtime 会话协议草案 v1

## 目标
定义前端实时语音页与后端 / Realtime 供应层之间的最小握手协议，保证“能连、能说、能停、能总结”。

---

## 1. 建立阶段

### 1.1 前端请求
`POST /realtime/session/create`

### 1.2 后端返回
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "sessionId": "rt_001",
    "provider": "qwen-omni-realtime",
    "token": "temporary_token",
    "wsUrl": "wss://..."
  }
}
```

### 1.3 前端动作
- 保存 `sessionId`
- 建立实时连接
- 页面状态从 `idle` → `connecting`

---

## 2. 前端状态机

状态建议固定为：
- `idle`
- `connecting`
- `connected`
- `listening`
- `thinking`
- `speaking`
- `ended`
- `error`

这些状态只服务页面表现，不直接等同底层 provider 原始状态。

---

## 3. 音频会话流

### 用户开始说话
- 前端切到 `listening`
- 音频帧开始推送

### 用户停止说话 / 一轮输入结束
- 前端切到 `thinking`
- 等待 AI 返回结果

### AI 开始回包
- 前端切到 `speaking`
- 若有 Live2D，可同步切到 speaking 态

### AI 回复完成
- 前端回到 `connected`

---

## 4. 挂断与结束

### 用户主动挂断
前端调用：
`POST /realtime/session/end`

请求体示例：
```json
{
  "sessionId": "rt_001",
  "duration": 182
}
```

### 后端返回
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "summaryId": "sum_001"
  }
}
```

### 后续动作
- 前端切到 `ended`
- 拉取摘要接口
- 展示总结卡

---

## 5. 摘要阶段

### 请求
`GET /realtime/session/:sessionId/summary`

### 返回
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "summary": "你们聊了工作压力和最近睡眠问题，她安慰了你。",
    "emotion": "care",
    "memoryCreated": [
      "你最近在忙项目",
      "你最近睡得不太好"
    ]
  }
}
```

### 前端展示
- 本次会话摘要
- 她当前情绪
- 新生成的记忆提示
- 按钮：回聊天 / 看回忆

---

## 6. 异常约束

### 连接失败
- 前端进入 `error`
- 提示用户稍后重试

### 会话中断
- 允许用户重新建立会话
- 不强行保留中断状态到下一次会话

### 摘要失败
- 不阻断挂断流程
- 可展示“摘要生成中”或“稍后在回忆页查看”

---

## 7. V1 边界

本协议只覆盖：
- 建立会话
- 基本状态切换
- 正常挂断
- 摘要读取

不覆盖：
- 后台保活
- 系统级通话模拟
- 多设备切换
- 复杂中断恢复
