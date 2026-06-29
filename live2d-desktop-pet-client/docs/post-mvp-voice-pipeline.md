# 后 MVP 语音管线设计

## 1. 结论

语音在 MVP 之后再接入。第一版语音体验推荐使用 Push to Talk，也就是用户按住按钮录音，松开发送。客户端只负责采集麦克风、显示录音状态、播放后端推送的音频，并把语音状态映射到 Live2D 表现。STT、LLM 对话、TTS、音频分片推送都归 Java AI backend 管。

这样做可以保持当前 MVP 的边界不变。`Pet WebSocket Semantic Protocol v1` 继续以文本流、`pet.expression`、`pet.motion` 和结构化 `error` 为主。语音只是在后 MVP 阶段扩展输入和输出通道，不把渲染细节、模型参数或本地工具能力放进协议。

## 2. 当前相关模块地图

### Tauri/Vue client

1. `src/components/Live2DProbe.vue` 是当前人工验证面。它连接 STOMP，发送聊天文本，接收文本流、情绪、表情和动作事件，并调用 Live2D renderer handle。
2. `src/ws/petStompClient.ts` 是客户端 WebSocket 边界。它连接 `/ws/chat`，订阅 `/user/queue/chat` 和 `/user/queue/control`，向 `/app/chat` 发送 `{ text, enableAudio }`。
3. `src/live2d/pixiLive2dRenderer` 是 Live2D 映射层。后端只能发语义动作和语义表情，客户端再映射到模型资源。
4. `docs/protocol/pet-ws-v1.md` 是 MVP 语义协议。它明确要求后端和客户端契约保持语义化，并把 voice 留在 MVP 外。

### Java AI backend

1. `ChatStompController` 是 WebSocket 聊天入口。后 MVP 语音输入应从这里或同层语音入口进入统一聊天链路。
2. `SisterChatService` 是文本对话主链路。STT 结果应转成用户文本后进入同一条对话链路，避免语音聊天变成另一套业务。
3. `ChatVoiceService` 和 `ChatVoiceServiceImpl` 已表达聊天加语音输出的概念，当前有 `enableAudio`、TTS 初始化、文本流和音频推送协作。
4. `TtsStreamingService`、`VoiceSynthesisService` 和 `AudioBuffer` 已表达后端拥有 TTS 与音频缓冲。客户端不应直接调用 TTS 服务。
5. `ChatPushService.pushAudio` 已表达音频推送能力。后 MVP 客户端只接收和播放分片，不决定语音合成策略。
6. wakeup 相关流程已经有文本加语音推送兜底经验。语音聊天的失败处理应复用这个思路，先保证文本送达，音频失败不回滚文本。

## 3. 推荐用户体验

第一版语音只做 Push to Talk。

1. 用户按住语音按钮时，Tauri/Vue client 开始录音，界面进入 `recording` 状态，Live2D 可以播放 `thinking` 或轻量待机动作。
2. 用户松开按钮时，客户端停止录音，把音频交给 Java backend，并显示 `transcribing` 状态。
3. Java backend 完成 STT 后，把识别文本当作普通 `user.message` 进入聊天链路。
4. 后端流式返回 assistant 文本，同时按 `enableAudio` 决定是否启动 TTS。
5. 后端通过现有聊天队列推送文本、表情、动作和音频分片。
6. 客户端播放音频时，把 Live2D 切到 `speaking` 动作。音频结束后回到 `idle` 或后端指定的语义动作。

Push to Talk 比唤醒词和全双工更适合第一版，因为它有清晰的开始和结束边界。用户知道什么时候会录音，客户端也容易处理权限、取消、超时和重试。

## 3.1 前端面板和状态草图

语音入口应该贴近现有聊天面板，而不是新建一套语音聊天页面。用户仍然看到同一个桌宠、同一条消息流和同一个文本输入兜底。

1. `idle`，默认状态。语音按钮可用，文本输入可用，Live2D 保持当前语义动作或 `idle`。
2. `permission_needed`，首次按下语音按钮但没有麦克风权限。面板显示授权说明、重试按钮和继续打字入口。
3. `recording`，按住录音中。按钮展示按住中的视觉反馈、录音时长和松开发送提示，文本输入暂时不抢焦点。
4. `cancelling`，用户拖出按钮区域或点取消。面板说明本次不会发送，松手后回到 `idle`。
5. `transcribing`，录音已发送，等待 STT。消息流可以显示一条临时用户气泡，例如正在识别语音。
6. `responding_text`，STT 成功后展示识别文本，并复用现有 assistant 文本流。
7. `playing_audio`，TTS 音频播放中。Live2D 使用 `speaking`，同时文本仍然继续显示。
8. `voice_error`，权限、STT 或播放失败。错误状态只影响语音入口，不清空已收到的文本。

状态切换应以用户可理解的面板文案为准。不要只改变按钮颜色，也不要把录音、识别、播放混在一个加载态里。

## 3.2 前端实现约束

1. 语音按钮不能遮挡 Live2D 主体的关键可点击区域。小窗模式下优先放在聊天输入附近。
2. 文本输入始终是兜底路径。麦克风不可用、用户取消、STT 失败时，用户都能继续打字。
3. 录音状态必须可见。窗口失焦、WebSocket 断开或权限被系统收回时，客户端应取消本次录音并回到安全状态。
4. 客户端不展示 TTS 模型、音色、服务商或后端队列细节，只展示用户能理解的状态。
5. 如果文本和音频不同步，文本优先。用户应该先看到 assistant 内容，再把音频当成增强体验。

## 4. 后端拥有 STT 和 TTS

STT 和 TTS 都放在 Java AI backend。

1. 客户端上传的是一次用户主动录制的音频片段，不上传长期监听流。
2. 后端执行 STT，并产出可记录、可审计、可复用的用户文本。
3. 用户文本进入现有 `SisterChatService`，保持记忆、情绪、工具确认和安全策略在同一条链路。
4. 后端执行 TTS，使用已有 `VoiceSynthesisService` 或 `TtsStreamingService`，并通过 `ChatPushService.pushAudio` 推送音频。
5. 客户端只播放音频，不选择 TTS 模型、音色、情绪参数或合成格式。

这个归属能减少客户端复杂度，也能让 Java backend 统一处理鉴权、限流、服务商错误、日志和用户设置。

## 5. 协议扩展方向

后 MVP 可以保留现有文本语义协议，再增加语音专用消息。建议先定义语义事件，不绑定具体音频编码细节。

1. 客户端到后端增加 `user.audio.start`、`user.audio.chunk`、`user.audio.end` 或等价 HTTP 上传入口。
2. 后端到客户端增加 `assistant.audio.chunk`、`assistant.audio.done`，用于播放 TTS 分片。
3. 后端到客户端增加 `voice.status`，表达 `transcribing`、`synthesizing`、`playing`、`failed`。
4. 现有 `assistant.message.delta` 仍然是主输出。音频只是 assistant 文本的伴随输出。
5. 现有 `pet.motion` 的 `speaking` 可先承担说话状态，不急着新增模型参数级事件。

如果短期使用 HTTP 上传录音，也应让 STOMP 继续承担结果推送。这样调用关系清楚，上传是一次请求，回复还是后端主动推送。

## 6. 未来唇形同步

唇形同步放在语音输出跑通之后。

第一阶段只用粗粒度状态。客户端收到音频开始播放时触发 `speaking`，播放结束后停止。这能先验证音频播放、文本流和 Live2D 动作切换。

第二阶段再接入音量驱动。客户端在播放后端音频时读取播放电平，映射到 Live2D 的嘴部开合参数。这个映射仍属于客户端 Live2D renderer 层，后端不发送 `ParamMouthOpenY` 这类模型参数。

第三阶段才考虑后端返回音素、时间戳或 viseme 数据。只有当 TTS 服务稳定提供时间轴，并且多个模型都能复用时，才值得把它放进语义协议。

## 7. 失败处理

语音失败不能破坏文本聊天。

1. 麦克风权限失败时，客户端提示用户授权，并保留文本输入入口。
2. 录音太短或用户取消时，客户端不发起聊天请求，只回到 idle。
3. STT 失败时，后端返回结构化错误，客户端显示可重试状态，不创建空文本聊天。
4. LLM 文本流成功但 TTS 失败时，继续展示文本，并让 Live2D 回到非说话状态。
5. 音频分片播放失败时，客户端停止当前音频，保留已经收到的文本，不要求后端重放整段对话。
6. WebSocket 断开时，沿用当前重连策略。若正在录音，客户端应取消本次录音，避免用户误以为已经发送。
7. 后端音频推送失败时，按 wakeup 流程已有经验处理，文本已经送达就不回滚消息。

错误码应继续走协议里的结构化 `error` 思路。语音可以增加 `AUDIO_PERMISSION_DENIED`、`AUDIO_TOO_SHORT`、`STT_FAILED`、`TTS_FAILED`、`AUDIO_PLAYBACK_FAILED` 等后 MVP 错误码。

## 8. 为什么暂缓唤醒词

唤醒词不适合作为第一版语音入口。

1. 它需要持续监听，桌面端权限、隐私提示和误触发成本都更高。
2. 它会引入本地 VAD、唤醒词模型、设备热插拔、后台运行策略等问题。
3. 当前 MVP 的目标是验证桌宠文本聊天、Live2D 语义动作和后端推送，不需要长期监听。
4. Java backend 已有 wakeup 领域，但那是定时主动触达，不等同于麦克风唤醒词。

等 Push to Talk 的录音、STT、TTS、音频播放和失败处理稳定之后，再评估唤醒词是否值得进入客户端。

## 9. 为什么暂缓全双工

全双工也不适合作为第一版语音入口。

1. 它要求用户说话、STT、LLM、TTS 和播放同时进行，状态机复杂度远高于按住说话。
2. 它需要打断处理，例如用户打断宠物说话时是否停止 TTS、是否保留上下文、是否重新生成回复。
3. 它会带来回声消除、端点检测、分片确认和乱序处理。
4. 当前 Java backend 已有音频缓冲和推送概念，但还不需要把聊天链路升级为实时语音会议。

第一版语音应先证明单轮 Push to Talk 能稳定工作。全双工可以作为后续体验优化，不应阻塞 MVP 后的第一条语音管线。

## 10. 接入顺序

1. 在客户端加语音按钮、状态展示和权限提示，但不改变 Live2D 渲染边界。
2. 先用前端假状态或开发开关走通 `idle`、`recording`、`transcribing`、`playing_audio`、`voice_error` 的面板流。
3. 在后端加 STT 入口，把识别结果转成现有聊天输入。
4. 复用后端 `enableAudio`、TTS 和 `pushAudio` 能力，向客户端推送音频。
5. 客户端加音频播放队列，并用 `speaking` 动作表达说话状态。
6. 补齐结构化错误、超时、取消和重连后的状态恢复。
7. 语音稳定后，再评估音量驱动唇形同步。
