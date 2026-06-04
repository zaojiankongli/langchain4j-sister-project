# 性能与架构优化计划

## Phase 1: 动画性能优化
- 全局 GPU 合成层审计 (will-change/contain/content-visibility)
- GSAP 动画：使用 `gsap.quickTo()` / `gsap.quickSetter()` 减少函数调用
- 使用 `gsap.context()` 统一 cleanup（已有但需确认）
- requestAnimationFrame 降频

## Phase 2: ChatWindow 性能
- 消息列表虚拟滚动（防止数千条消息时 DOM 爆炸）
- v-memo 优化：根据消息类型细化依赖数组
- 流式追加时合并 DOM 更新

## Phase 3: HistoryPanel 优化
- 加载骨架屏优化
- 日期缓存（避免重复请求同一天）
- 消息列表虚拟滚动

## Phase 4: 整体架构
- 无用 watch/computed 清理
- 内存泄漏排查
- 懒加载确认
