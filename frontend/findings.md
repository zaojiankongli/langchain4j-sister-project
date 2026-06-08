# Findings

## Existing docs
- `plan.md`：现有是 Web 前端性能与架构优化计划，不约束小程序产品规划。
- `docs/backend-owned-memory-controls.md`：明确“记忆由后端托管”，这对小程序记忆设计是强约束，应保持一致。

## Product direction
- 用户已明确：核心是和 AI 女友互动。
- 普通聊天页：仅文本输入，AI 可回文字和 TTS 语音。
- 实时语音：单独页面，承担“说话”能力。
- Live2D：放在实时语音页，用 `oh-my-live2d`。
- 个人中心不做会员中心，只展示基础信息、偏好设置、隐私反馈。

## Key design constraints
- 聊天页优先级最高。
- 不做 STT。
- 记忆是第二价值支柱，且必须与后端托管方案一致。

## Execution artifacts created
- `docs/wechat-miniapp-mvp-schedule.md`：周级 MVP 排期。
- `docs/wechat-miniapp-implementation-matrix.md`：页面/接口/状态/阶段对照矩阵。
- `docs/wechat-miniapp-entity-mapping.md`：数据实体与功能映射。
- `docs/wechat-miniapp-issue-backlog.md`：可直接建 issue 的开发 backlog。
- `docs/wechat-miniapp-realtime-protocol.md`：实时语音页最小协议草案。
- `docs/wechat-miniapp-ai-orchestration-contract.md`：模型/后端/前端职责边界。
- `docs/wechat-miniapp-api-field-dictionary.md`：接口字段命名字典。
- `docs/wechat-miniapp-relationship-rules.md`：关系值更新规则。
- `docs/wechat-miniapp-prompt-contract.md`：聊天与实时语音 Prompt 契约。
- `docs/wechat-miniapp-mvp-acceptance-checklist.md`：MVP 上线验收标准。
- `docs/wechat-miniapp-dependency-blockers.md`：开发关键路径与阻塞图。
- `docs/wechat-miniapp-realtime-recovery.md`：实时语音失败恢复策略。
- `docs/wechat-miniapp-monitoring-plan.md`：埋点、漏斗、告警建议。
- `docs/wechat-miniapp-week1-sprint-board.md`：第 1 周冲刺执行板。
- `docs/wechat-miniapp-mvp-cutline.md`：MVP 砍线规则。
- `docs/wechat-miniapp-launch-runbook.md`：上线前检查、回退与排障手册。
- `docs/wechat-miniapp-delivery-index.md`：整套规划文档的统一入口。
- `docs/wechat-miniapp-realtime-event-dictionary.md`：Realtime 状态与事件字典。
- `docs/wechat-miniapp-memory-lifecycle.md`：记忆从输入到召回的生命周期流程。
- `docs/wechat-miniapp-mock-payloads.md`：聊天、记忆、设置、Realtime 的联调样例数据。
- `docs/wechat-miniapp-scaffold-blueprint.md`：前端小程序目录/状态/服务层脚手架蓝图。
- `docs/wechat-miniapp-error-codes.md`：接口错误码约定。
- `docs/wechat-miniapp-kickoff-handoff.md`：前端/后端/AI 编排 Day 1 启动交接单。
- `docs/wechat-miniapp-backend-blueprint.md`：Java 后端模块职责蓝图。
- `docs/wechat-miniapp-phase-handoffs.md`：阶段切换交接清单。
- `README.md`：仓库级总览入口，指向小程序文档集。
- `docs/wechat-miniapp-decisions-assumptions.md`：关键决策与默认假设台账。
- `docs/wechat-miniapp-compatibility-checklist.md`：小程序兼容性与降级检查清单。
- `docs/wechat-miniapp-release-cadence.md`：MVP 到后续版本的节奏计划。
- `docs/wechat-miniapp-launch-ops.md`：首发运营与观察重点。
- `docs/wechat-miniapp-monetization-boundary.md`：首发阶段不做哪些商业化设计。
- `docs/wechat-miniapp-risk-register.md`：产品/技术/交付风险台账。
- `docs/wechat-miniapp-open-questions.md`：仍需拍板的问题清单。
- `docs/wechat-miniapp-user-journeys.md`：首访、回访、实时语音陪伴等核心用户剧本。
- `docs/wechat-miniapp-self-review.md`：本轮人工一致性审查记录。
- `docs/wechat-miniapp-qa-smoke-checklist.md`：产品/开发/QA 共用的最小冒烟清单。
- `docs/wechat-miniapp-chat-sequence.md`：文本聊天链路时序流。
- `docs/wechat-miniapp-realtime-sequence.md`：实时语音链路时序流。
- `docs/wechat-miniapp-glossary.md`：产品、记忆、关系、Realtime 术语与状态词汇表。
