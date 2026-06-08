# AI 女友微信小程序 交付索引 v1

## 目的
为项目成员提供一个单一入口，明确“先看什么、后看什么、开发时用什么、上线前查什么”。

---

## 1. 如果你是产品

先看：
1. `wechat-miniapp-mvp-schedule.md`
2. `wechat-miniapp-mvp-acceptance-checklist.md`
3. `wechat-miniapp-mvp-cutline.md`
4. `wechat-miniapp-risk-register.md`
5. `wechat-miniapp-open-questions.md`
6. `wechat-miniapp-user-journeys.md`

用途：
- 确认 MVP 范围
- 明确什么算完成
- 排期压力下知道先砍什么

---

## 2. 如果你是前端

先看：
1. `wechat-miniapp-implementation-matrix.md`
2. `wechat-miniapp-issue-backlog.md`
3. `wechat-miniapp-api-field-dictionary.md`
4. `wechat-miniapp-realtime-protocol.md`
5. `wechat-miniapp-scaffold-blueprint.md`
6. `wechat-miniapp-mock-payloads.md`
7. `wechat-miniapp-error-codes.md`
8. `wechat-miniapp-chat-sequence.md`

用途：
- 明确页面与接口依赖
- 直接拆 FE issue
- 知道字段命名和实时语音状态机怎么接

---

## 3. 如果你是后端

先看：
1. `wechat-miniapp-issue-backlog.md`
2. `wechat-miniapp-entity-mapping.md`
3. `wechat-miniapp-ai-orchestration-contract.md`
4. `wechat-miniapp-prompt-contract.md`
5. `backend-owned-memory-controls.md`
6. `wechat-miniapp-backend-blueprint.md`
7. `wechat-miniapp-error-codes.md`
8. `wechat-miniapp-risk-register.md`
9. `wechat-miniapp-open-questions.md`
10. `wechat-miniapp-chat-sequence.md`
11. `wechat-miniapp-realtime-sequence.md`

用途：
- 明确接口优先级
- 明确数据实体
- 明确记忆仍由后端托管
- 明确聊天/实时语音的模型编排边界

---

## 4. 如果你是 AI / 算法 / 编排同学

先看：
1. `wechat-miniapp-ai-orchestration-contract.md`
2. `wechat-miniapp-prompt-contract.md`
3. `wechat-miniapp-relationship-rules.md`
4. `wechat-miniapp-entity-mapping.md`
5. `wechat-miniapp-memory-lifecycle.md`
6. `wechat-miniapp-decisions-assumptions.md`
7. `wechat-miniapp-glossary.md`
7. `wechat-miniapp-chat-sequence.md`
8. `wechat-miniapp-realtime-sequence.md`

用途：
- 明确 prompt 由谁拼装
- 明确记忆怎么注入
- 明确关系值如何变化

---

## 5. 如果你要直接开工

建议顺序：
1. `wechat-miniapp-week1-sprint-board.md`
2. `wechat-miniapp-issue-backlog.md`
3. `wechat-miniapp-dependency-blockers.md`
4. `wechat-miniapp-kickoff-handoff.md`

用途：
- 今天就能开第一批工单
- 知道哪些可并行，哪些会卡关键路径

---

## 6. 如果你要准备上线

先看：
1. `wechat-miniapp-mvp-acceptance-checklist.md`
2. `wechat-miniapp-realtime-recovery.md`
3. `wechat-miniapp-monitoring-plan.md`
4. `wechat-miniapp-launch-runbook.md`
5. `wechat-miniapp-mvp-cutline.md`
6. `wechat-miniapp-risk-register.md`
7. `wechat-miniapp-qa-smoke-checklist.md`

---

## 7. 如果你要准备联调

先看：
1. `wechat-miniapp-realtime-event-dictionary.md`
2. `wechat-miniapp-memory-lifecycle.md`
3. `wechat-miniapp-mock-payloads.md`
4. `wechat-miniapp-phase-handoffs.md`
5. `wechat-miniapp-user-journeys.md`
6. `wechat-miniapp-qa-smoke-checklist.md`

用途：
- 明确 Realtime 状态语义
- 明确记忆从写入到召回流程
- 使用统一 mock 数据提前并行开发
- 明确联调阶段的交接规则

---

## 8. 文档总表

| 文档 | 作用 |
|---|---|
| `wechat-miniapp-mvp-schedule.md` | MVP 周级排期 |
| `wechat-miniapp-implementation-matrix.md` | 页面 / 状态 / 接口 / 阶段矩阵 |
| `wechat-miniapp-entity-mapping.md` | 数据实体与功能映射 |
| `wechat-miniapp-issue-backlog.md` | 可直接分配的 FE/BE issue |
| `wechat-miniapp-realtime-protocol.md` | 实时语音基础协议 |
| `wechat-miniapp-ai-orchestration-contract.md` | 模型/后端/前端职责边界 |
| `wechat-miniapp-api-field-dictionary.md` | 字段命名字典 |
| `wechat-miniapp-relationship-rules.md` | 关系值变化规则 |
| `wechat-miniapp-prompt-contract.md` | 文本聊天/实时语音 prompt 契约 |
| `wechat-miniapp-mvp-acceptance-checklist.md` | MVP 验收标准 |
| `wechat-miniapp-dependency-blockers.md` | 依赖与阻塞图 |
| `wechat-miniapp-realtime-recovery.md` | Realtime 错误恢复策略 |
| `wechat-miniapp-monitoring-plan.md` | 埋点与监控方案 |
| `wechat-miniapp-week1-sprint-board.md` | 第 1 周冲刺执行板 |
| `wechat-miniapp-mvp-cutline.md` | 砍线规则 |
| `wechat-miniapp-launch-runbook.md` | 上线运行手册 |
| `wechat-miniapp-delivery-index.md` | 全部文档统一入口 |
| `wechat-miniapp-realtime-event-dictionary.md` | Realtime 状态与事件字典 |
| `wechat-miniapp-memory-lifecycle.md` | 记忆生命周期流程 |
| `wechat-miniapp-mock-payloads.md` | 联调 mock 数据样例 |
| `wechat-miniapp-scaffold-blueprint.md` | 小程序目录/状态/服务层脚手架蓝图 |
| `wechat-miniapp-error-codes.md` | 错误码约定 |
| `wechat-miniapp-kickoff-handoff.md` | Day 1 启动交接单 |
| `wechat-miniapp-backend-blueprint.md` | Java 后端模块蓝图 |
| `wechat-miniapp-phase-handoffs.md` | 阶段交接清单 |
| `wechat-miniapp-decisions-assumptions.md` | 决策与默认假设台账 |
| `wechat-miniapp-compatibility-checklist.md` | 小程序兼容性与降级检查清单 |
| `wechat-miniapp-release-cadence.md` | 版本节奏计划 |
| `wechat-miniapp-launch-ops.md` | 首发运营与观察重点 |
| `wechat-miniapp-monetization-boundary.md` | 商业化边界说明 |
| `wechat-miniapp-risk-register.md` | 风险台账 |
| `wechat-miniapp-open-questions.md` | 待确认问题清单 |
| `wechat-miniapp-user-journeys.md` | 端到端用户旅程脚本 |
| `wechat-miniapp-qa-smoke-checklist.md` | QA 冒烟场景清单 |
| `wechat-miniapp-chat-sequence.md` | 文本聊天时序流 |
| `wechat-miniapp-realtime-sequence.md` | 实时语音时序流 |
| `wechat-miniapp-glossary.md` | 术语与状态词汇表 |
