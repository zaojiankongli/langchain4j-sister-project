# 后端三端业务边界整理

## 目标

后端保持传统 `controller -> service -> mapper` 结构。公共业务只实现一次，放在普通业务 service；网页、桌宠、小程序只有在存在端专用业务时才使用专门命名。

## 分层原则

```text
客户端入口
  -> Controller / STOMP Controller
  -> Service（公共业务或端特有业务）
  -> Mapper / OSS / Redis / LLM / WebSocket 等基础能力
```

- **公共业务服务层**：跨网页、桌宠、小程序复用的能力，放在 service 中。
- **Controller 层**：只处理 HTTP/STOMP 路由、当前用户识别、限流、请求 DTO、响应包装。
- **端特有业务**：只服务某一端的协议或交互模型，但仍应落在 service 中，controller 不承载业务编排。

## 命名规则

- **公共业务用业务名**：例如 `AuthService`、`UserProfileService`、`OssService`、`ConverMessageService`、`SettingsService`。
- **小程序专用用 `Miniprogram*`**：例如 `MiniprogramController`、`MiniprogramService`；设备绑定这种小程序专用能力可以继续放在 `MiniprogramService` 或后续命名为 `MiniprogramDeviceService`。
- **桌宠专用用 `Pet*` 或 `Desktop*`**：例如 `PetMessageChatService`、`PetRealtimeStompController`、`DesktopOmniRealtimeSessionService`。
- **网页专用才用 `Web*`**：如果没有网页独有业务，就继续使用普通公共 controller/service 名称。
- 不引入 `adapter/web`、`adapter/miniprogram`、`adapter/desktop` 这类新目录；当前项目用传统 controller/service/mapper 就够。

## 公共业务服务层

| 业务域 | 当前共享模块 | 职责 |
| --- | --- | --- |
| 认证 | `AuthService` | 邮箱验证码、登录、微信登录、刷新、登出、绑定邮箱、完善资料入口。 |
| 用户资料 | `UserProfileService` / `UserProfileServiceImpl` / `UserProfileManageService` | 资料查询与更新、基础资料、兴趣、AI 类型、头像上传后持久化到 `users.avatar_url`。 |
| 文件上传 | `OssService` | 头像、聊天图片、语音、远程 URL 上传、删除、预签名 URL。 |
| 聊天消息 | `ConverMessageService` | 消息历史、最新消息、按日期查询、会话预览、DTO 转换。 |
| 桌宠聊天 | `PetMessageChatService` | STOMP 文本/图片聊天校验、限流、唤醒状态标记、语音聊天调用。 |
| 桌宠实时语音 | `DesktopOmniRealtimeSessionService` / `DesktopOmniRealtimeSession` | 桌宠到 Qwen-Omni-Realtime 的一对一会话生命周期、音频追加、停止与清理。 |
| 配置 | `SettingsService` | 用户配置读写、Caffeine/Redis/MySQL 缓存链路、人格/情绪参数同步。 |
| 情绪 | `EmotionService` / `EmotionRecordService` | 情绪状态、心情描述、演化事件、历史、重置。 |
| 记忆 | `MemoryQueryService` / `SummaryMemoryService` / gallery services | 心路日记查询、记忆详情、按日期查询、RAG 记忆块构建、记忆图库。 |
| 邮件 | `MailService` | 用户信件列表、欢迎信幂等创建、已读、全部已读、缓存失效。 |
| 推荐 | `RecommendationService` | 推荐生成、并发控制、去重锁、结果解析、入库、点击状态。 |
| 图片 | `ImageDescriptionService` / `ImageElementExtractor` / `WanxImageService` | 图片描述、记忆元素抽取、图片生成。 |
| 锚点 | `AnchorService` / `AnchorSemanticService` / `AnchorEventService` | 重要时刻查询与语义锚点能力。 |

## 三端入口与专用命名

### 网页端

网页端主要复用通用 REST 与传统 STOMP 聊天入口。没有网页独有业务时，不需要为了网页单独创建 `Web*Service`。

| 入口 | 适配职责 | 下游公共服务 |
| --- | --- | --- |
| `AuthController` | 登录/注册相关 HTTP 路由、限流、结果包装。 | `AuthService` |
| `UserProfileController` | 用户资料 HTTP 路由、头像上传限流、当前用户检查。 | `UserProfileService` |
| `MessageController` | 网页消息历史查询路由、分页/limit 边界。 | `ConverMessageService` |
| `ChatStompController` | `/app/chat` 与 `/app/ping` STOMP 入口、连接/断开事件。 | `PetMessageChatService` / `ChatPushService` |
| `SettingsController` | 配置读取/保存、预设列表响应。 | `SettingsService` / `Personality` |
| `MemoryController` / `MemorySearchController` / `MemoryGalleryController` | 记忆列表、搜索、图库、debug/backfill 入口。 | memory services |
| `EmotionController` | 情绪状态、心情、演化、历史、重置入口。 | `EmotionService` |
| `MailController` | 信件列表、已读、一键已读入口。 | `MailService` |
| `RecommendationController` | 推荐列表、点击、异步生成入口。 | `RecommendationService` |
| `ImageController` | 图片描述、元素抽取、生成入口。 | image services |

### 桌宠端

桌宠端的独特性在实时语音协议和运动/音频推送，不应和网页传统聊天链路混在一个 controller 中。

| 入口 | 适配职责 | 下游服务 |
| --- | --- | --- |
| `ChatStompController` `/app/chat` | 桌宠或网页传统文本/图片聊天入口。 | `PetMessageChatService` |
| `PetRealtimeStompController` `/app/pet/realtime/start|audio|stop` | 桌宠实时语音启动、音频分片、停止、限流、Principal 校验。 | `DesktopOmniRealtimeSessionService` |
| `ChatPushService` | TEXT/AUDIO/PET_MOTION/ERROR/SYSTEM/PONG 推送能力。 | `ChatPushServiceImpl` |

桌宠特有业务：实时语音会话、音频分片限制、Qwen-Omni-Realtime 映射、桌宠动作状态。它们可以是端特有 service，但不能下沉到 controller。

### 小程序端

小程序端需要自己的 `/api/miniprogram/*` 路由，但底层应复用公共服务。

| 入口 | 适配职责 | 下游服务 |
| --- | --- | --- |
| `MiniprogramController.homeSummary` | 小程序首页摘要响应格式。 | `MiniprogramService` |
| `MiniprogramController.bindDevice/status` | 小程序设备绑定、状态查询。 | `MiniprogramService` / `UserDeviceMapper` |
| `MiniprogramController.chat/send/history` | 小程序聊天发送与历史响应格式。 | `MiniprogramService` / `ConverMessageMapper` |
| `MiniprogramController.uploadAvatar` | 小程序头像上传适配，返回 `{ url }`。 | `UserProfileService.uploadAvatar` |
| `MiniprogramController.uploadVoice` | 小程序语音上传适配，返回 `{ url }`。 | `MiniprogramService.uploadVoice` -> `OssService.uploadVoice` |

小程序特有业务：设备绑定、首页摘要 DTO、小程序路径兼容。头像、语音、用户资料、聊天历史不应复制一套。

## 已完成的收口点

1. 头像上传与持久化已统一在 `UserProfileServiceImpl.uploadAvatar()`：上传 OSS 后更新 `users.avatar_url`。
2. 小程序头像接口已复用 `UserProfileService.uploadAvatar()`，只做 `{ url }` 响应适配。
3. 小程序语音上传已通过 `MiniprogramService.uploadVoice()` 转发到 `OssService.uploadVoice()`。
4. 桌宠传统聊天逻辑已在 `PetMessageChatService`，`ChatStompController` 只做 STOMP 入口。
5. 桌宠实时语音逻辑已在 `DesktopOmniRealtimeSessionService` / `DesktopOmniRealtimeSession`，`PetRealtimeStompController` 只做协议入口。

## 不建议继续硬拆的部分

- `MailController`、`MemoryController`、`SettingsController`、`EmotionController`、`MessageController` 当前大多已经是薄适配层。
- controller 中的 `userContext` 检查、`Result` 包装、简单 limit 归一化属于适配职责，可以保留。
- 不要为每个端新建一份 `AuthService`、`UserProfileService`、`OssService`、`ConverMessageService`。
- 不要把桌宠 realtime 和网页传统 TTS 混成一个入口；它们是不同协议链路。
- 不要为了“拆分”而迁移目录；优先通过清晰命名表达公共业务和端专用业务。

## 后续拆分规则

新增功能时按以下问题决定放哪里：

1. **三端都会用吗？** 放公共 service。
2. **只有某端路径或返回格式不同吗？** controller 保留薄方法，service 复用公共能力。
3. **只有某端协议独有吗？** 建端特有 service，controller 仍只做入口。
4. **是否涉及数据库、OSS、Redis、LLM、外部 API、推送编排？** 不放 controller，放 service。
5. **删除这个模块后复杂度会不会散到多个 controller？** 会，则它应该保留为 service seam。
