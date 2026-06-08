# 桌宠 Qwen-Omni-Realtime 对接架构

## 目标

桌宠语音聊天使用 Qwen-Omni-Realtime，网页端继续使用现有文本模型 + 传统 TTS。两条链路必须隔离，避免桌宠误入传统 TTS。

## 链路边界

### 网页端传统链路（保持不变）

```text
/app/chat
  -> ChatStompController
  -> ChatVoiceServiceImpl
  -> SisterChatService / QwenStreamingChatModel
  -> TtsStreamingService / VoiceSynthesisService
  -> ChatPushService TEXT/AUDIO
```

### 桌宠实时链路

```text
/app/pet/realtime/start|audio|stop
  -> PetRealtimeStompController
  -> DesktopOmniRealtimeSessionService
  -> DesktopOmniRealtimeSession
  -> OmniRealtimeEventMapper
  -> DashScope Qwen-Omni-Realtime WebSocket
  -> ChatPushService TEXT/AUDIO/PET_MOTION/ERROR/SYSTEM
```

桌宠前端只连接自家后端 STOMP，不直接连接 DashScope，不暴露 `DASHSCOPE_API_KEY`。

前端实时语音必须由用户显式开启/关闭。聊天输入框聚焦不会自动启动麦克风；`PetChatInput` 暴露一个低视觉权重的 microphone toggle，`PetShell` 根据 `usePetRealtimeAudioStream.isStreaming` 控制 `/app/pet/realtime/start|stop`。

## 会话模型

每个 `userId` 同时最多一条 Realtime 会话。新的 start 会关闭旧会话并创建新会话。

启动后端会向 DashScope 发送：

```json
{
  "type": "session.update",
  "session": {
    "modalities": ["text", "audio"],
    "voice": "Ethan",
    "input_audio_format": "pcm",
    "output_audio_format": "pcm",
    "input_audio_transcription": { "model": "qwen3-asr-flash-realtime" },
    "turn_detection": {
      "type": "semantic_vad",
      "threshold": 0.5,
      "silence_duration_ms": 800
    }
  }
}
```

前端持续上传 16 kHz mono PCM16 Base64 音频块。官方 `client-events` 文档确认 `input_audio_buffer.append` 的字段为 `audio`。轮次边界由 Qwen `semantic_vad` 决定；VAD 模式下官方文档明确不需要客户端发送 `input_audio_buffer.commit` 或 `response.create`。

## 事件映射

| Qwen Realtime 事件 | 后端动作 | 推给桌宠 |
| --- | --- | --- |
| `session.created` / `session.updated` | 标记会话可用，flush 启动早期音频缓冲 | `SYSTEM` 已连接 |
| `input_audio_buffer.speech_started` | 清空当前助手临时文本 | `PET_MOTION listening` |
| `input_audio_buffer.speech_stopped` | 进入模型思考态 | `PET_MOTION thinking` |
| `conversation.item.input_audio_transcription.completed` | 保存用户转录文本到 MySQL，去重 | 暂不新增前端事件 |
| `response.audio_transcript.delta` / `response.text.delta` | 累积助手回复文本 | `TEXT { content, isComplete:false }` |
| `response.audio_transcript.done` / `response.text.done` | 完成气泡并保存助手文本到 MySQL，去重 | `TEXT { content:"", isComplete:true }` |
| `response.audio.delta` | 解码 Qwen Base64 PCM 后复用现有 pushAudio | `AUDIO { audioData }` |
| `response.done` | 本轮结束 | `PET_MOTION idle` |
| `error` / WebSocket error | 关闭 Realtime 会话 | `ERROR` + `PET_MOTION idle` |

实现细节：只有收到 `session.updated` 才标记 `sessionReady=true` 并 flush 启动期音频缓冲，确保 `semantic_vad` 等配置已被服务端接受。`session.created` 只表示连接的默认配置已创建，不代表我们的桌宠配置已生效。

`OmniRealtimeEventMapper` 是协议 JSON seam：事件类型、错误消息、assistant 完整文本提取都集中在这里。`DesktopOmniRealtimeSession` 只保留生命周期、背压、持久化和 `ChatPushService` 推送编排。

完成文本兜底顺序：

1. 优先使用 `response.audio_transcript.done.transcript` 或 `response.text.done.text`。
2. 若 done 事件缺失，使用 `response.content_part.done.part.text`、`response.output_item.done.item.content[*].text/transcript`。
3. 最后使用 `response.done.response.output[*].content[*].text/transcript`。
4. 若仍无完整文本，使用已累积的 delta 文本。

## 兜底机制

### 1. DashScope 连接失败

- 后端推送 `ERROR: 实时语音连接失败`。
- 会话从 `DesktopOmniRealtimeSessionService` 移除。
- 前端 runtime 进入 `error` 后停止麦克风推流。
- 文字聊天仍可通过 `/app/chat` 走传统链路；语音 Realtime 不做假成功。

### 2. 会话未 ready 时前端已开始推音频

- 后端维护有界启动缓冲 `MAX_PENDING_AUDIO_CHUNKS=50`。
- 会话 ready 后 flush。
- 超限丢弃最老音频，防止内存无限增长。

### 3. Realtime 中途报错

- 后端推 `ERROR` 和 `PET_MOTION idle`。
- 后端关闭 DashScope WebSocket。
- 前端监听 `petRuntimeState === 'error'`，停止本地麦克风采集。

### 4. 用户打断播放

- Qwen 推 `input_audio_buffer.speech_started`。
- 后端映射 `PET_MOTION listening`。
- 前端 `usePetSocketEventPipeline` 收到 listening 后调用 `audioPlayer.stop()`，清空旧音频队列。

### 5. 重复事件/重复保存

- 用户转录文本和助手回复文本按最近一次内容去重。
- 避免 `done` 重放或重连边缘情况下重复写 MySQL。

### 6. 网页端保护

- 不修改 `/app/chat`。
- 不在 `ChatVoiceServiceImpl` 内加桌宠分支。
- 桌宠实时链路只走 `/app/pet/realtime/*`。

### 7. 发送背压与性能

- 后端对发往 DashScope 的 WebSocket JSON 事件做串行化发送；后一条发送等待前一条 `sendText` 完成后再发，避免持续音频分片在同一个 `WebSocket` 上并发 flood。
- 后端发送队列最多允许 `MAX_OUTBOUND_BACKLOG=200` 个待完成发送。超过后关闭 Realtime 会话并通知前端，避免网络抖动时音频分片无限堆积。
- 启动期音频缓冲只覆盖连接/`session.updated` 的短窗口，不作为长期队列；Realtime 不可用时直接推 `ERROR` 并停止前端麦克风。
- 前端一旦 `sendAudioChunk()` 返回失败，立即停止麦克风采集并发送 stop，避免 STOMP 断开后继续 CPU 编码和 Base64 分配。
- 助手回复 completion 有单轮去重：多个官方完成事件到达时只触发一次 `TEXT isComplete=true` 和一次 MySQL 保存。

## 当前不做

- 不把现有 LangChain4j RAG/Redis memory 注入 Realtime 上下文。
- 不让前端直连 DashScope。
- 不在 Realtime 失败时把音频伪装成传统 TTS；没有 ASR 时不能可靠降级语音输入。
- 不启动服务验证；当前以静态代码审查、LSP、Maven compile、Vue build 作为验证门。

## 后续可演进

1. 将 persona/prompt 从 prompt 模板服务注入 `instructions`。
2. 将记忆摘要作为短 System context 注入 Realtime，而不是复用 LangChain4j ChatMemory。
3. 新增前端显式“实时语音开关”，不要仅靠聊天框 focus 触发。
4. 用 AudioWorklet 替代 ScriptProcessorNode，降低浏览器弃用风险。
5. 为 Realtime 原始事件增加 debug 面板显示。
