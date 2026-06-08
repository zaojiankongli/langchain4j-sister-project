# AI 女友微信小程序 Mock Payload 样例 v1

## 目标
提供前后端联调前可直接使用的样例数据，降低页面开发、接口联调、状态管理实现的等待成本。

---

## 1. 用户资料

### `GET /user/profile`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": "u_001",
    "nickname": "阿策",
    "avatarUrl": "https://cdn.example.com/avatar/u_001.png",
    "createdAt": "2026-06-01T08:00:00+08:00",
    "companionDays": 7,
    "chatCount": 128,
    "voiceMinutes": 46
  }
}
```

---

## 2. 聊天发送

### `POST /chat/send`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "userMessage": {
      "messageId": "msg_user_001",
      "sessionId": "chat_001",
      "role": "user",
      "type": "text",
      "content": "今天有点累。",
      "createdAt": "2026-06-07T20:30:00+08:00"
    },
    "aiMessage": {
      "messageId": "msg_ai_001",
      "sessionId": "chat_001",
      "role": "assistant",
      "type": "text",
      "content": "辛苦啦，我在。今天发生了什么？",
      "audioUrl": "https://cdn.example.com/audio/msg_ai_001.mp3",
      "hasAudio": true,
      "memoryHints": [
        "她记得你最近经常熬夜"
      ],
      "createdAt": "2026-06-07T20:30:02+08:00"
    }
  }
}
```

---

## 3. 聊天历史

### `GET /chat/history`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "list": [
      {
        "messageId": "msg_001",
        "sessionId": "chat_001",
        "role": "user",
        "type": "text",
        "content": "晚安",
        "createdAt": "2026-06-06T23:00:00+08:00"
      },
      {
        "messageId": "msg_002",
        "sessionId": "chat_001",
        "role": "assistant",
        "type": "text",
        "content": "晚安呀，做个好梦。",
        "audioUrl": "https://cdn.example.com/audio/msg_002.mp3",
        "hasAudio": true,
        "createdAt": "2026-06-06T23:00:02+08:00"
      }
    ],
    "nextCursor": "cursor_002"
  }
}
```

---

## 4. 记忆列表

### `GET /memory/list`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "list": [
      {
        "memoryId": "mem_001",
        "type": "preference",
        "level": "long",
        "summary": "你喜欢深夜聊天",
        "source": "chat",
        "importance": 0.82,
        "emotionTag": "calm",
        "createdAt": "2026-06-05T23:10:00+08:00"
      },
      {
        "memoryId": "mem_002",
        "type": "shared_experience",
        "level": "long",
        "summary": "你们第一次深夜语音陪伴",
        "source": "realtime",
        "importance": 0.93,
        "emotionTag": "care",
        "createdAt": "2026-06-06T00:20:00+08:00"
      }
    ],
    "nextCursor": null
  }
}
```

---

## 5. 记忆时间线

### `GET /memory/timeline`
```json
{
  "code": 200,
  "message": "ok",
  "data": [
    {
      "id": "timeline_001",
      "source": "realtime",
      "title": "第一次深夜语音陪伴",
      "summary": "你告诉她最近压力有点大，她安慰了你。",
      "createdAt": "2026-06-06T00:20:00+08:00"
    },
    {
      "id": "timeline_002",
      "source": "chat",
      "title": "她记住了你的作息",
      "summary": "她知道你最近总在夜里还没睡。",
      "createdAt": "2026-06-07T00:05:00+08:00"
    }
  ]
}
```

---

## 6. 设置读取

### `GET /settings`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "replyStyle": "gentle",
    "initiativeLevel": "normal",
    "nicknameStyle": "宝宝",
    "ttsEnabled": true,
    "chatBackground": "bg_night"
  }
}
```

---

## 7. 设置保存

### `PUT /settings`
```json
{
  "code": 200,
  "message": "保存成功",
  "data": {
    "replyStyle": "gentle",
    "initiativeLevel": "normal",
    "nicknameStyle": "宝宝",
    "ttsEnabled": true,
    "chatBackground": "bg_night"
  }
}
```

---

## 8. 关系状态

### `GET /relationship/status`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "intimacy": 73,
    "familiarity": 58,
    "emotionState": "miss_you",
    "stage": "心动",
    "todayDelta": 3,
    "statusText": "她今天有点想你"
  }
}
```

---

## 9. 首页陪伴摘要

### `GET /home/companion`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "avatarUrl": "https://cdn.example.com/ai/avatar-main.png",
    "statusText": "刚刚在想你",
    "emotion": "happy",
    "quickActions": ["发消息", "语音陪伴"],
    "memoryPreview": [
      "她记得你不爱早起",
      "她记得你最近在忙项目"
    ]
  }
}
```

---

## 10. 实时语音创建会话

### `POST /realtime/session/create`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "sessionId": "rt_001",
    "provider": "qwen-omni-realtime",
    "token": "temporary_token_001",
    "wsUrl": "wss://realtime.example.com/session/rt_001"
  }
}
```

---

## 11. 实时语音结束会话

### `POST /realtime/session/end`
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "summaryId": "sum_001"
  }
}
```

---

## 12. 实时语音摘要

### `GET /realtime/session/:id/summary`
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
