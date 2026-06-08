# AI 女友微信小程序 项目脚手架蓝图 v1

## 目标
为小程序前端仓库提供一套可直接起项目的目录蓝图，避免页面、组件、状态、服务层散落无序。

---

## 1. 建议目录结构

```text
miniprogram/
  app.(js|ts)
  app.json
  app.wxss
  pages/
    chat/
    realtime-voice/
    memory/
    home/
    profile/
    preferences/
  components/
    chat/
      message-list/
      message-item-text/
      message-item-audio/
      chat-input-bar/
      ai-status-bar/
    memory/
      memory-card/
      memory-timeline/
      memory-filter-tabs/
    relationship/
      relationship-card/
      companion-status-card/
    realtime/
      live2d-stage/
      voice-session-panel/
      voice-end-summary-card/
    common/
      loading/
      empty-state/
      error-state/
  stores/
    chat-store.(js|ts)
    realtime-store.(js|ts)
    memory-store.(js|ts)
    settings-store.(js|ts)
    user-store.(js|ts)
    relationship-store.(js|ts)
  services/
    http.(js|ts)
    chat-service.(js|ts)
    memory-service.(js|ts)
    realtime-service.(js|ts)
    settings-service.(js|ts)
    user-service.(js|ts)
    relationship-service.(js|ts)
  utils/
    audio.(js|ts)
    time.(js|ts)
    format.(js|ts)
    storage.(js|ts)
    constants.(js|ts)
  mock/
    chat.mock.json
    memory.mock.json
    settings.mock.json
    realtime.mock.json
```

---

## 2. 页面职责

### `pages/chat`
- 只负责文本聊天主链路
- 不承载实时语音状态机

### `pages/realtime-voice`
- 只负责实时语音会话和 Live2D 展示
- 不复用聊天页消息流布局

### `pages/memory`
- 只负责记忆列表、时间线和详情跳转

### `pages/home`
- 只做陪伴首页和快速跳转入口

### `pages/profile`
- 只做基础信息和统计

### `pages/preferences`
- 只做偏好设置和背景切换

---

## 3. 组件分层原则

1. 业务组件按领域分目录，不按视觉风格分目录。
2. 通用 loading / empty / error 状态放到 `components/common/`。
3. Live2D 相关组件只放在 `components/realtime/`，不要污染聊天页组件树。

---

## 4. Store 分层原则

### `chat-store`
- 当前消息列表
- 历史游标
- 发送状态
- 当前播放语音 ID

### `realtime-store`
- sessionId
- 连接状态
- listening/thinking/speaking 状态
- 摘要数据

### `memory-store`
- 记忆列表
- 时间线
- 当前筛选条件

### `settings-store`
- 回复风格
- 主动程度
- 称呼方式
- TTS 开关
- 背景设置

### `user-store`
- 用户基础资料
- 互动统计

### `relationship-store`
- 亲密度
- 熟悉度
- 情绪状态
- 首页状态摘要

---

## 5. Service 分层原则

1. 所有接口请求统一从 `http` 出去。
2. 页面不直接拼 URL。
3. 每个 service 文件只处理一个领域接口。
4. 实时语音连接逻辑只放在 `realtime-service`，页面不直接处理 provider 协议。

---

## 6. Mock 使用原则

1. 联调前优先使用 `mock/` 中的样例。
2. mock 字段必须与 `wechat-miniapp-api-field-dictionary.md` 保持一致。
3. mock 数据只服务页面开发，不成为前端本地记忆来源。
