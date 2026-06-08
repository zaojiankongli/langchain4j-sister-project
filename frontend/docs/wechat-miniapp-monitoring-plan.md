# AI 女友微信小程序 监控与埋点方案 v1

## 目标
让 MVP 上线后能回答三类问题：

1. 用户有没有真的在用？
2. 哪条主链路最容易掉？
3. 出问题时是前端、接口、记忆还是 realtime？

---

## 1. 指标分层

## A. 产品指标
- 日活用户数
- 首次聊天完成率
- AI 语音播放率
- 实时语音发起率
- 回忆页访问率
- 次日留存

## B. 功能链路指标
- `/chat/send` 成功率
- 聊天首屏历史加载成功率
- TTS 可播放率
- 记忆写入成功率
- Realtime 会话创建成功率
- Realtime 会话摘要成功率

## C. 稳定性指标
- 前端页面崩溃率
- Realtime 中断率
- Live2D 降级触发率
- 设置保存失败率

---

## 2. 埋点事件建议

## 聊天页
- `chat_page_view`
- `chat_send_clicked`
- `chat_send_success`
- `chat_send_failed`
- `chat_history_loaded`
- `chat_tts_play`
- `chat_tts_play_failed`

## 回忆页
- `memory_page_view`
- `memory_card_click`
- `memory_timeline_loaded`

## 设置页
- `settings_page_view`
- `settings_save_clicked`
- `settings_save_success`
- `settings_save_failed`
- `chat_background_changed`

## 实时语音页
- `realtime_page_view`
- `realtime_session_create_start`
- `realtime_session_create_success`
- `realtime_session_create_failed`
- `realtime_session_end`
- `realtime_summary_loaded`
- `realtime_summary_failed`
- `realtime_live2d_fallback_used`

## 首页
- `home_page_view`
- `home_start_chat_click`
- `home_start_realtime_click`
- `home_memory_preview_click`

---

## 3. 埋点字段建议

所有核心事件建议尽量带：
- `userId`
- `sessionId`（有则带）
- `page`
- `timestamp`
- `networkType`
- `deviceType`

错误类事件额外带：
- `errorCode`
- `errorMessage`
- `stage`

---

## 4. 最关键的漏斗

### 漏斗 1：主聊天漏斗
`进入聊天页 → 发送首条消息 → AI 成功回复 → 播放语音（可选）`

### 漏斗 2：记忆漏斗
`一次有效聊天 → 记忆提炼成功 → 回忆页可见`

### 漏斗 3：实时语音漏斗
`进入实时语音页 → 创建会话成功 → 完成一轮语音 → 摘要成功展示`

---

## 5. 最小告警建议

当以下情况持续发生时，需要告警：

1. `/chat/send` 成功率明显下降
2. `realtime_session_create_failed` 激增
3. `realtime_summary_failed` 持续高于阈值
4. `chat_tts_play_failed` 明显高于正常水平
5. `realtime_live2d_fallback_used` 激增

---

## 6. V1 原则

1. 先埋主链路，不求一步到位全埋。
2. 埋点服务于“定位问题”和“看留存”，不是为了数据好看。
3. 所有埋点不应泄露原始聊天文本和隐私内容。
