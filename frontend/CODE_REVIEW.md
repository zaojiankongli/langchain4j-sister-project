## Zeeva 前端代码审查报告

审查时间：2026-06-07 | 技术栈：Vue 3 + Vite 7 + Pinia 3 + Vue Router 5

---

### 一、架构问题

**1. Dashboard.vue 是"上帝组件"（~700 行）**

Dashboard.vue 同时承担了 Live2D 初始化与重试、GSAP 入场动画编排、鼠标视差效果计算、面板导航与切换、主题背景管理、退出登录流程、多个 `createStyleCache` 实例管理共七项职责。建议将 Live2D 管理（init/destroy/retry/onLoad）抽为 `useLive2dInit` composable，将面板导航逻辑统一到 `useUiStore`，将样式计算函数移到独立的 composable 中。目标是将 Dashboard.vue 控制在 200 行以内。

**2. 认证状态存在双重数据源**

项目中同时存在 `stores/auth.js`（Pinia store）和 `utils/auth.js`（直接操作 localStorage）两套认证管理。`request.js` 的拦截器通过 `safeGet(STORAGE_KEYS.ACCESS_TOKEN)` 直接读 localStorage，而 `Dashboard.vue` 通过 `useAuthStore()` 操作。代码注释写着"逐步替代直接 localStorage 访问"，但实际上两套系统都在活跃使用。当 authStore 的 `clearAuth()` 被调用后，如果 `request.js` 的拦截器在同步链路中已经缓存了旧 token，就会产生不一致。应该让 `utils/auth.js` 完全退役，所有 token 读写统一走 authStore。

**3. Token 刷新逻辑重复实现**

`request.js` 实现了自己的 `doRefresh()` + `isRefreshing` + `refreshPromise` + `pendingRequests` 队列，而 `stores/auth.js` 也有 `_refreshPromise` 单例机制。两者通过懒加载 import 关联，形成了两条独立的刷新路径。在 401 场景下，`request.js` 调用 `doRefresh()`，而 `doRefresh()` 又委托给 `authStore.refreshTokens()`，后者内部有自己的 `_refreshPromise` 保护——但 `request.js` 的 `refreshPromise` 和 authStore 的 `_refreshPromise` 是完全独立的两个变量，理论上存在并发刷新窗口。应当删除 `request.js` 中的重复逻辑，完全委托给 authStore。

**4. WebSocket 模块的单例模式有隐患**

`chatWebSocket.js` 在模块级别定义了 `stompClient`、`isConnected` 等 ref 和 `callbacks` 对象作为全局单例。ChatWindow 组件通过 `setCallbacks()` 注册回调，通过 `disconnect()` 断开连接。这种模块级单例模式使得：组件卸载后重连时，旧的 `isManualDisconnect = true` 标记可能残留（虽然有 `connect()` 入口重置，但时序依赖微妙）；多个 ChatWindow 实例（如路由切换时）会互相覆盖回调；单元测试无法隔离。建议重构为 class 或 composable 模式，将连接生命周期绑定到组件。

**5. UserProfile.vue 与 userStore 职责重叠**

UserProfile.vue 内部使用 `useAsyncData` 调用 `request.get(API.MY_PROFILE)` 获取用户资料，同时项目中有 `useUserStore`（stores/user.js）也管理 `profile`、`fetchProfile()` 等。结果是同一份用户数据被两个独立的数据源获取和维护。UserProfile.vue 应当直接使用 `useUserStore`，不再自己发起请求。

---

### 二、代码质量问题

**6. 工具函数重复定义**

`formatTime` 在 `useChatMessages.js`（第 247 行）和 `ChatMessages.vue`（第 25 行）各定义了一次，逻辑完全相同。`formatDateLabel` 和 `isNewDateGroup` 同样如此。应当统一放到 `utils/date.js` 中。

**7. JWT 解码逻辑重复且不一致**

`utils/auth.js` 第 53 行使用 `atob(token.split('.')[1])` 直接解码，而 `request.js` 有专门的 `base64UrlDecode()` 函数处理 URL-safe 字符替换和 padding。当 JWT payload 中包含 URL-safe 字符（如 `-` 或 `_`）时，`auth.js` 的 `atob` 会解码失败。`auth.js` 和 `stores/auth.js` 的 `parseUserIdFromToken` 都应使用 `base64UrlDecode`。

**8. settings store 的 busy-wait 轮询**

`stores/settings.js` 第 50-52 行，`saveSettings()` 使用 `while (_fetching.value) { await new Promise(resolve => setTimeout(resolve, 100)) }` 等待 fetch 完成。这是忙等待反模式，浪费 CPU 且难以推理。应当用 Promise 链：让 `fetchSettings()` 返回一个 Promise，`saveSettings` 直接 `await` 它。

**9. MemoryFragment.vue 的直接 DOM 操作**

`onImageError` 函数（第 288-299 行）直接创建 DOM 元素并 `appendChild`。这在 Vue 应用中是反模式——当组件重新渲染时，手动插入的元素会被 Vue 的 virtual DOM diff 清除，导致图片占位符闪烁消失。应当通过响应式状态控制 fallback 显示，比如在 `journalList` 中给对应 item 设置 `imageFailed: true`。

**10. ScriptProcessorNode 已废弃**

`useStreamingAudioPlayer.js` 第 48 行使用了 `createScriptProcessor`，代码注释也标注了 TODO。`ScriptProcessorNode` 在主线程上运行音频处理，容易因 JS 执行阻塞导致音频卡顿。应迁移到 `AudioWorkletNode`，将音频处理放到独立线程。

---

### 三、性能问题

**11. 聊天消息列表没有虚拟化**

ChatMessages.vue 使用 `v-for` 渲染所有消息（`earlierMessages` + `historyMessages` + `messages`），没有任何虚拟滚动。当用户累积数百条消息时，DOM 节点数会线性增长，滚动和渲染性能会明显下降。建议使用 `vue-virtual-scroller` 或自研简单的窗口化方案。

**12. keep-alive 缓存范围过大**

Dashboard.vue 的 `<keep-alive include="UserProfile,MemoryFragment,EmotionPulse,ActionCenter,SettingsPanel">` 缓存了 5 个面板组件。每个面板组件内部可能有自己的数据请求、定时器和 DOM 结构。5 个组件同时缓存在内存中，加上 ChatWindow 和 Live2D，内存占用偏高。建议只对用户频繁切换的 1-2 个面板使用 keep-alive，其余用 `v-if` 控制。

**13. 流式消息的 localStorage 高频写入**

`useChatMessages.js` 中，流式内容每 2 秒触发一次 `saveToStorage()`，该函数序列化 `historyMessages + messages`（最多 200 条）写入 localStorage。在长对话中，这意味着每 2 秒做一次大对象的 JSON 序列化 + 同步 I/O。建议降低频率到 5-10 秒，或使用 `requestIdleCallback` 在浏览器空闲时写入。

**14. 鼠标视差触发大量 computed 重算**

`useMouseParallax` 在每次 `mousemove` 时更新 `mouseX/mouseY`，虽然做了 4px 量化和 `requestAnimationFrame` 节流，但 `charParallaxStyle`、`parallaxStyle` 等多个 computed 都会因此重新求值。每个 computed 内部调用 `createStyleCache` 进行字符串签名比较。在 Live2D 加载等 CPU 密集场景下，这些额外的计算可能加剧掉帧。建议面板打开时（activeLayer !== 'idle'）暂停鼠标追踪。

**15. 登录页外部背景图无预加载**

Login.vue 第 275 行从 Unsplash CDN 加载背景图（`photo-1519681393784`），没有 `<link rel="preload">` 或 `fetchpriority="high"`。在慢网络下，用户可能先看到空白/纯色背景，然后图片突然加载。建议在 `index.html` 中预加载关键背景图，或使用低质量占位图（LQIP）。

---

### 四、安全问题

**16. Token 存储在 localStorage（XSS 风险）**

`accessToken` 和 `refreshToken` 都存储在 localStorage 中。如果应用存在任何 XSS 漏洞（包括第三方依赖中的），攻击者可以直接读取 token 冒充用户。对于这种带 Live2D 和富交互的应用，建议考虑使用 httpOnly cookie 存储 token，或使用 `sessionStorage` + 短期 token 策略。

**17. 外部图片资源缺少 SRI**

Login.vue 直接引用 `https://images.unsplash.com/photo-...` 作为 CSS 背景图，没有 Subresource Integrity 校验。如果 Unsplash CDN 被劫持或替换内容，用户会加载恶意资源。建议将背景图下载到项目 assets 中，或使用 `<link rel="preload" integrity="...">` 加载。

**18. Token 解码不校验签名**

`parseUserIdFromToken`（auth store 第 32 行）和 `getUserId`（auth utils 第 53 行）都只解码 JWT payload 获取 userId，不校验签名。虽然前端本身无法校验签名（需要密钥），但如果 token 被篡改（比如手动修改 payload 中的 userId），前端会直接使用篡改后的 ID 发起 API 请求。后端应做严格校验，前端也应在登录成功后缓存 userId 而非每次都从 token 解析。

---

### 五、设计与可访问性问题

**19. 完全没有响应式适配**

整个应用的 CSS 没有使用任何 `@media` 查询（Login.vue 在 480px 有一个简单的 padding 调整是唯一例外）。Dashboard 的 `.content-panel` 固定 `width: 50%`，`.live2d-box` 固定 `600px`，`.chat-window-container` 固定 `600px`。在平板（768px）上，50% 宽的面板只有 384px；在手机上完全不可用。建议至少添加 768px 和 480px 两个断点。

**20. 可访问性（a11y）几乎为零**

主要问题包括：所有交互元素（`.action-btn`、`.decor-btn`、`.mini-tag` 等）使用 `<div>` 或 `<span>` 而非 `<button>`，缺少 `role` 和 `tabindex`；聊天输入框没有关联的 `<label>`；情绪状态仅通过颜色圆点区分（色盲用户无法辨别）；模态弹窗（MemoryFragment 的 `.memory-modal-overlay`）打开时没有焦点陷阱（focus trap）；`ErrorBoundary` 使用了 emoji 作为唯一的错误指示。

**21. 颜色对比度不足**

多处文本使用了低对比度配色：`.msg-time` 的 `rgba(0,0,0,0.25)` 在白色背景上对比度约 1.6:1（WCAG 要求至少 4.5:1）；`.section-tag` 的 `rgba(255,255,255,0.5)` 在深色背景上约 3.5:1；`.loading-text` 的 `rgba(255,255,255,0.5)` 同样不达标。

---

### 六、构建与工程化

**22. 没有 ESLint 配置生效**

`package.json` 安装了 `eslint` 和 `eslint-plugin-vue`，但项目根目录没有 `.eslintrc` 或 `eslint.config.js` 配置文件，`scripts` 中也没有 `lint` 命令。代码风格完全靠人工维护，已经出现了缩进不一致（有的函数 2 空格有的 4 空格）、尾逗号不统一等问题。

**23. 没有测试**

整个项目没有任何 `.test.js`、`.spec.js` 或 `__tests__` 目录。对于有 WebSocket 连接管理、token 刷新队列、消息状态机等复杂逻辑的项目，至少应有单元测试覆盖 `request.js` 的拦截器逻辑、`useChatMessages` 的状态机、`chatWebSocket` 的连接/重连流程。

**24. vite-plugin-compression 版本过旧**

`vite-plugin-compression@0.5.1` 是为 Vite 2/3 设计的，在 Vite 7 上可能存在兼容性问题。建议迁移到 `vite-plugin-compression2` 或使用 Vite 内置的 `build.assetsInlineLimit` 配合 Nginx 的 `gzip_static`/`brotli_static`。

---

### 七、优先级排序建议

按影响面和修复成本排序，建议优先处理以下问题：

- **P0（立即）**：#2 认证双数据源（可能导致 token 不同步的登录态异常）、#3 Token 刷新重复（可能导致并发刷新竞争）、#7 JWT 解码不一致（特定 token 会解析失败）
- **P1（本迭代）**：#1 Dashboard 拆分、#5 UserProfile 数据源统一、#11 消息列表虚拟化、#19 响应式适配
- **P2（下迭代）**：#4 WebSocket 重构、#6 工具函数合并、#8 busy-wait 修复、#9 DOM 操作修复、#16 Token 存储安全
- **P3（长期）**：#10 AudioWorklet 迁移、#13 localStorage 优化、#20 可访问性、#22 ESLint 配置、#23 测试覆盖
