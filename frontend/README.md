# AI 女友微信小程序规划工作区

这个目录原本是一个 Vue 3 + Vite 前端模板仓位，但当前主要工作重点已经切换到：

**AI 女友微信小程序的产品、接口、记忆、Realtime、排期与执行文档设计。**

如果你现在是第一次打开这个目录，不要先从旧的 Web 模板说明开始看，而是直接按下面的顺序进入。

---

## 1. 最快入口

### 如果你想知道这件事到底做成什么样
先看：

1. `docs/wechat-miniapp-delivery-index.md`
2. `docs/wechat-miniapp-mvp-schedule.md`
3. `docs/wechat-miniapp-user-journeys.md`

---

## 2. 如果你今天就要开工

先看：

1. `docs/wechat-miniapp-week1-sprint-board.md`
2. `docs/wechat-miniapp-issue-backlog.md`
3. `docs/wechat-miniapp-implementation-matrix.md`
4. `docs/wechat-miniapp-kickoff-handoff.md`

---

## 3. 如果你是前端

先看：

1. `docs/wechat-miniapp-implementation-matrix.md`
2. `docs/wechat-miniapp-api-field-dictionary.md`
3. `docs/wechat-miniapp-scaffold-blueprint.md`
4. `docs/wechat-miniapp-mock-payloads.md`
5. `docs/wechat-miniapp-error-codes.md`
6. `docs/wechat-miniapp-chat-sequence.md`

---

## 4. 如果你是后端

先看：

1. `docs/wechat-miniapp-backend-blueprint.md`
2. `docs/wechat-miniapp-entity-mapping.md`
3. `docs/wechat-miniapp-ai-orchestration-contract.md`
4. `docs/wechat-miniapp-error-codes.md`
5. `docs/backend-owned-memory-controls.md`
6. `docs/wechat-miniapp-risk-register.md`
7. `docs/wechat-miniapp-open-questions.md`
8. `docs/wechat-miniapp-chat-sequence.md`
9. `docs/wechat-miniapp-realtime-sequence.md`

---

## 5. 如果你是 AI / 编排同学

先看：

1. `docs/wechat-miniapp-prompt-contract.md`
2. `docs/wechat-miniapp-ai-orchestration-contract.md`
3. `docs/wechat-miniapp-relationship-rules.md`
4. `docs/wechat-miniapp-memory-lifecycle.md`
5. `docs/wechat-miniapp-decisions-assumptions.md`
6. `docs/wechat-miniapp-glossary.md`

---

## 6. 如果你在准备联调或上线

联调看：

1. `docs/wechat-miniapp-realtime-event-dictionary.md`
2. `docs/wechat-miniapp-realtime-protocol.md`
3. `docs/wechat-miniapp-mock-payloads.md`
4. `docs/wechat-miniapp-phase-handoffs.md`

上线看：

1. `docs/wechat-miniapp-mvp-acceptance-checklist.md`
2. `docs/wechat-miniapp-monitoring-plan.md`
3. `docs/wechat-miniapp-realtime-recovery.md`
4. `docs/wechat-miniapp-launch-runbook.md`
5. `docs/wechat-miniapp-risk-register.md`
6. `docs/wechat-miniapp-open-questions.md`

如果你要给产品 / 开发 / QA 用统一剧本过流程，再看：

7. `docs/wechat-miniapp-user-journeys.md`
8. `docs/wechat-miniapp-qa-smoke-checklist.md`

---

## 7. 当前已完成的核心产物

- MVP 排期表
- 页面/接口/状态实施矩阵
- 数据实体映射
- FE/BE issue backlog
- Realtime 协议草案
- AI 编排与 Prompt 契约
- 关系值规则
- MVP 验收清单
- 依赖/阻塞图
- 监控/埋点方案
- 第 1 周冲刺板
- 砍线规则
- 上线运行手册
- 脚手架蓝图
- 错误码约定
- 开发启动交接单
- 阶段交接清单
- 决策与假设台账
- 兼容性检查清单
- 版本节奏与首发运营策略
- 商业化边界说明
- 风险台账
- 待确认问题清单
- QA 冒烟清单
- 聊天与实时语音时序流
- 术语与状态词汇表

---

## 8. 说明

这轮工作以文档与规划为主，不涉及生产代码行为修改。
同时，所有记忆相关设计都以 `docs/backend-owned-memory-controls.md` 的“后端托管记忆”原则为约束。
