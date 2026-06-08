# AI 女友微信小程序 Realtime 事件字典 v1

## 目标
统一前端页面状态、后端会话状态、以及 Realtime provider 侧事件语义，避免联调时出现“同一个状态三种叫法”。

---

## 1. 前端页面状态

| 状态 | 含义 | 用户可见表现 |
|---|---|---|
| `idle` | 尚未建立会话 | 初始页/待发起 |
| `connecting` | 正在创建并连接会话 | 显示“连接中” |
| `connected` | 会话已建立，等待说话 | 显示“已连接” |
| `listening` | 正在采集用户语音 | 显示“正在听你说” |
| `thinking` | 用户说完，等待 AI 生成 | 显示“正在思考” |
| `speaking` | AI 正在回包/播报 | 显示“正在回答” |
| `ended` | 会话正常结束 | 展示总结卡 |
| `error_recoverable` | 当前会话异常但可重试 | 提供重试按钮 |
| `error_terminal` | 当前会话不可恢复 | 提供返回聊天 |

---

## 2. 建议前端埋点事件

| 事件名 | 触发时机 |
|---|---|
| `realtime_page_view` | 进入实时语音页 |
| `realtime_connect_start` | 开始创建会话 |
| `realtime_connect_success` | 会话创建成功 |
| `realtime_connect_failed` | 会话创建失败 |
| `realtime_listening_start` | 开始收音 |
| `realtime_thinking_start` | 用户说完，等待 AI |
| `realtime_speaking_start` | AI 开始回包 |
| `realtime_session_end` | 用户挂断或正常结束 |
| `realtime_summary_loaded` | 摘要成功展示 |
| `realtime_summary_failed` | 摘要获取失败 |
| `realtime_live2d_fallback_used` | Live2D 降级启用 |

---

## 3. 后端会话状态建议

| 后端状态 | 含义 | 前端映射 |
|---|---|---|
| `created` | session 已创建 | `connecting` |
| `ready` | provider 可通信 | `connected` |
| `streaming_input` | 正在接收音频 | `listening` |
| `waiting_model` | 等待模型输出 | `thinking` |
| `streaming_output` | 正在输出音频/文本 | `speaking` |
| `completed` | 正常结束 | `ended` |
| `failed` | 会话失败 | `error_recoverable` / `error_terminal` |

---

## 4. Provider 事件归一化建议

后端不应把 provider 原始事件直接透传给前端，建议先归一化：

| Provider 原始事件 | 后端内部归一化 | 前端状态 |
|---|---|---|
| socket open / session ready | `ready` | `connected` |
| input audio start | `streaming_input` | `listening` |
| model processing | `waiting_model` | `thinking` |
| output audio chunk start | `streaming_output` | `speaking` |
| session completed | `completed` | `ended` |
| socket close / provider error | `failed` | `error_recoverable` 或 `error_terminal` |

---

## 5. 联调约束

1. 前端只认本文档定义的页面状态，不直接耦合 provider 术语。
2. 后端负责把 provider 事件转成统一状态。
3. 埋点名称要稳定，不要和 UI 文案绑定。
