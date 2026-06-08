# Phase 3 小程序文本聊天与历史记录协议

## 目标

实现小程序文本聊天 MVP：

```text
用户发送文本 → 后端保存用户消息 → 后端生成/返回 assistant 回复 → 保存 assistant 消息 → 小程序读取后端历史
```

Phase 3 的重点是后端历史优先，不再由小程序伪造“后端未连通”的正常回复。

## 接口列表

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/miniprogram/chat/send` | POST | 发送文本消息 |
| `/api/miniprogram/chat/history` | GET | 获取聊天历史 |

以上接口均需要业务 `Authorization: Bearer {accessToken}`。

## 接口 1：发送文本消息

```http
POST /api/miniprogram/chat/send
```

### 请求

```json
{
  "content": "你好",
  "messageType": "text"
}
```

### 返回

```json
{
  "reply": "我已经收到你的消息。",
  "messages": [
    {
      "id": "user-message-id",
      "role": "user",
      "content": "你好",
      "type": "text",
      "time": "2026-06-07T18:55:00"
    },
    {
      "id": "assistant-message-id",
      "role": "assistant",
      "content": "我已经收到你的消息。",
      "type": "text",
      "time": "2026-06-07T18:55:01"
    }
  ]
}
```

## 接口 2：获取聊天历史

```http
GET /api/miniprogram/chat/history?limit=20
```

### 返回

```json
{
  "list": [
    {
      "id": "message-id",
      "role": "assistant",
      "content": "最近回复",
      "type": "text",
      "time": "2026-06-07T18:55:01"
    }
  ]
}
```

## 小程序行为

| 场景 | 行为 |
|---|---|
| 发送成功 | 用后端返回消息替换本地 pending 消息 |
| 发送失败 | 保留用户消息为 failed，toast 提示错误 |
| 历史加载成功 | 使用后端历史覆盖本地缓存 |
| 历史加载失败 | 可读取本地缓存作为展示 fallback，但不伪造新消息 |

## MVP 说明

当前文本 MVP 先保证消息落库与历史读取。assistant 回复可先使用后端固定兜底文案，后续再接入完整 LLM 同步/流式回复。
