## Zeeva 前端性能深度分析报告

---

### 一、GPU 合成层与渲染管线

**1. `backdrop-filter: blur()` 滥用——最大的渲染性能杀手**

项目中 11 个文件共使用了 17 次 `backdrop-filter: blur()`。这是目前 CSS 中 GPU 开销最高的属性之一：每次渲染时，GPU 需要对模糊区域内的每一个像素做高斯卷积采样，复杂度与模糊半径的平方成正比。

具体问题点：
- Login.vue 的 `.bg-blur-layer` 使用 `blur(15px)` 覆盖全屏（`inset: 0`），意味着每帧对 1920×1080 = 200 万像素做 15px 半径的高斯模糊
- ChatWindow.vue 的 `.glass-morph-bg` 使用 `blur(20px) saturate(120%)`，这是两个滤镜叠加
- HistoryPanel.vue 的 `.content-panel` 使用 `blur(25px)`
- MemoryFragment.vue 的 `.filter-dropdown` 使用 `blur(15px)`，且出现在下拉动画过程中
- main.css 的 `#oml2d-tips` 使用 `blur(12px)`，这个元素由 Live2D 库频繁更新位置

当面板打开/关闭动画过程中 backdrop-filter 参与过渡时，GPU 需要在每一帧重新计算模糊，在集显或低端设备上极易掉帧。

**建议替换方案：** 用半透明纯色背景 + `box-shadow` 模拟毛玻璃效果，或使用预渲染的模糊背景图（`background-image: url(blurred-bg.webp)`）。对于确实需要毛玻璃的场景，限制模糊区域的尺寸，避免全屏覆盖。如果浏览器支持，使用 `backdrop-filter` 时搭配 `will-change: backdrop-filter` 提示浏览器提前创建合成层，但注意不要滥用。

**2. `transition: all` 导致隐式属性动画**

全项目 14 个文件共使用了 53 次 `transition: all`。`transition: all` 会对元素的所有可动画 CSS 属性启用过渡，包括 `width`、`height`、`padding`、`margin`、`color`、`background` 等。当元素的 class 切换或 style 变化时，一些本不该动画的属性（如 `pointer-events`、`z-index`、`font-size`）也会被意外过渡，触发不必要的重排（layout）或重绘（paint）。

例如 Dashboard.vue 的 `.content-panel` 使用 `transition: transform 0.8s`（这个没问题），但 NavigationMenu.vue 的 `.bubble-inner` 使用 `transition: all 0.4s`，当 hover 时 `background`、`border-color`、`transform`、`box-shadow` 四个属性同时过渡，其中 `box-shadow` 的过渡会触发 paint，`background` 的变化在某些浏览器中也会触发 paint。

**建议：** 将所有 `transition: all` 替换为显式属性列表，如 `transition: transform 0.4s, opacity 0.4s`。只过渡 `transform` 和 `opacity` 这两个可以由 GPU 加速的属性。

**3. `will-change` 使用不当**

Dashboard.vue 第 577-578 行将 `will-change: transform, opacity` 同时给了 `.character-stage`、`.content-panel`、`.chat-window-container` 三个大容器。`will-change` 会为每个元素创建独立的 GPU 合成层，好处是 transform/opacity 动画不需要重排重绘，坏处是每个合成层都占用独立的 GPU 显存。

`.character-stage` 包含 Live2D canvas（本身已经是合成层），`.content-panel` 包含整个右侧面板（50vw × 100vh），`.chat-window-container` 包含聊天窗口。再加上 HistoryPanel 和 ChatWindow 各自的 `will-change`，Dashboard 页面可能创建 6-8 个大型合成层，在中低端 GPU 上可能导致显存压力，反而触发降级到 CPU 渲染。

**建议：** 只在实际做动画的元素上启用 `will-change`，动画结束后移除。对于静态容器，使用 `contain: layout style paint` 替代（项目中已有部分使用，做得好）。

---

### 二、Bundle 体积与加载性能

**4. live2d chunk 体积过大（954KB / 218KB brotli）**

构建产物中 `live2d-Bd60SLqj.js` 达到 954KB（brotli 压缩后 218KB），是整个应用最大的单个 chunk。虽然已经通过 `defineAsyncComponent` 延迟加载，但一旦用户进入 Dashboard（几乎所有登录用户都会），这个 218KB 的文件就会被下载、解析和执行。

oh-my-live2d 内部依赖了 pixi.js（PixiJS）和 Live2D Cubism SDK，这些库本身就很大。项目中只配置了一个 Live2D 模型（独角兽_4），但整个 SDK 的渲染管线、物理引擎、 motion 系统等全部被打包。

**建议替换方案：**
- 考虑用 **pixi-live2d-display**（~120KB gzip）替代 oh-my-live2d，它更轻量且 API 更灵活
- 或者直接使用 **pixi.js + Live2D Cubism Web SDK** 的原生方式，只加载需要的模块
- 如果坚持使用 oh-my-live2d，可以在 `import()` 时使用 Vite 的 `?url` 语法配合 Web Worker 在后台线程初始化
- 长远考虑：Live2D 模型渲染可以放到 Canvas/WebGL 的 OffscreenCanvas + Worker 中，不阻塞主线程

**5. vendor chunk 体积（110KB / 39KB brotli）**

vendor chunk 包含 vue、vue-router、pinia。Vue 3 本身约 33KB gzip，vue-router 5 约 15KB，pinia 3 约 5KB，合计约 53KB。但实际 vendor chunk 有 110KB（39KB brotli），说明有其他依赖被错误地打入了 vendor chunk。

检查 `manualChunks` 配置发现只指定了 `['vue', 'vue-router', 'pinia']`，但 Vite 7 的 Rollup 可能将 `@vue/shared`、`@vue/reactivity` 等内部包也归入 vendor，或者有其他 Vue 生态依赖被自动包含。

**建议：** 在 `manualChunks` 函数中使用更精确的控制，将 `@vue/*` 的内部包显式排除或合并。

**6. dist 目录累积膨胀（33MB，268 个 JS 文件 + 64 个 CSS 文件）**

当前 dist 目录有 33MB，包含 268 个 JS 文件和 64 个 CSS 文件。这是因为每次 `vite build` 生成新的 hash 文件名但不删除旧文件。部署时如果直接复制整个 dist 目录，会传输大量无用文件。

**建议：** 在 build 脚本中加入 `rm -rf dist` 或使用 Vite 的 `build.emptyOutDir: true`（默认行为，但如果 dist 是在 Docker 中挂载卷则可能不生效）。CI/CD 中每次 build 前先清理。

**7. 五张背景图全部打包（592KB）**

`bk1.webp` 到 `bk5.webp` 合计 592KB，全部通过 `new URL(...)` 静态引用，会被 Vite 打包到 dist 中。用户首次进入 Dashboard 时，`themeBgSrc` computed 只会用到一张（默认 bk1），但其他四张也会因为 `SettingsPanel.vue` 的主题网格预览而被加载。

**建议：**
- 主题预览图使用缩略图（如 320×180 的 webp，每张约 10-15KB），原图只在选中时才加载
- 使用 `<picture>` + `srcset` 根据屏幕分辨率加载不同尺寸
- 首屏背景图使用 `fetchpriority="high"` 的 `<link rel="preload">`，非首屏的延后

---

### 三、运行时 JavaScript 性能

**8. StatusPanel 每秒创建 Date 对象并触发 reflow**

`StatusPanel.vue` 的 `updateTime` 每秒执行一次，更新 `timeStr`、`dateStr`、`weekStr` 三个 ref。`.time-main` 的字号是 `5.5rem`（约 88px），这个巨大的文本变化会触发容器的 intrinsic size 重计算。虽然元素有 `contain: layout style`（在 Dashboard 的 CSS 中），但 StatusPanel 自身没有设置 `contain`。

**建议：** 给 `.status-panel-container` 加上 `contain: layout style`，并将时间显示区域设为固定宽度（`font-variant-numeric: tabular-nums`），避免数字变化时的宽度抖动。

**9. 30 个 localStorage 操作散布在关键路径上**

项目共有 30 处 `localStorage` 调用。`localStorage` 是同步阻塞 API，每次 `getItem`/`setItem` 都会等待磁盘 I/O 完成。在关键路径上（如 `request.js` 的请求拦截器中每次请求都要 `safeGet(STORAGE_KEYS.ACCESS_TOKEN)`），这些同步调用会增加请求延迟。

更严重的是 `useChatMessages.js` 的 `commitToStorage()`，每次序列化最多 200 条消息的数组并写入两次（`storageKey` + `sessionKey`）。在流式消息接收期间，每 2 秒触发一次，可能导致主线程卡顿。

**建议替换方案：**
- 用 **IndexedDB** 替代 localStorage 存储聊天消息，IndexedDB 是异步 API，不会阻塞主线程。推荐使用 `idb-keyval`（~500 bytes）作为轻量封装
- 在 request 拦截器中，将 token 缓存在模块级变量中（当前 `auth.js` 的 `getAccessToken` 每次都读 localStorage），避免每次请求都做磁盘 I/O
- 将 `commitToStorage` 改为使用 `requestIdleCallback` + `navigator.locks`，在浏览器空闲时批量写入

**10. `requestAnimationFrame` 在 `useMouseParallax` 中的使用问题**

`useMouseParallax` 使用 `requestAnimationFrame` 节流 `mousemove` 事件。但 `requestAnimationFrame` 的回调在下一帧绘制前执行，这意味着如果鼠标快速移动，每秒可能触发 60 次 `mouseX.value = e.clientX` 的响应式更新。每次更新会触发所有依赖 `mouseX` 的 computed 重新求值（`charParallaxStyle`、`parallaxStyle` 等），进而可能触发 DOM 更新。

虽然做了 4px 量化（`Math.round(mouseX.value / 4) * 4`），但量化发生在 computed 内部，不是在源头。这意味着 `mouseX` ref 仍然每秒更新 60 次，所有 watcher 和 computed 都会响应。

**建议：** 将量化逻辑移到 `useMouseParallax` 内部，在 `requestAnimationFrame` 回调中比较量化后的值是否与上次相同，相同则不更新 ref。或者使用 `throttle`（如 `lodash/throttle` 的 16ms）替代 `requestAnimationFrame`。

**11. 28 个 setTimeout/setInterval 散布在 10 个文件中**

项目中有 28 处定时器调用。其中几个值得关注：
- `StatusPanel.vue`：每秒 `setInterval(updateTime, 1000)`，虽然有 visibilitychange 暂停机制（做得好），但每秒更新仍是不必要的——显示到分钟精度就够了，可以改为每分钟更新
- `Dashboard.vue`：`setInterval(() => { ambientHour.value = new Date().getHours() }, 60000)` 每分钟检查一次小时数，可以用 `setTimeout` 计算到下一个整点的精确毫秒数，只触发一次
- `chatWebSocket.js`：重连定时器使用指数退避（`1000 * 2^attempts`），最多 5 次，设计合理
- `useLive2dChat.js`：队列处理定时器 2800ms 间隔，合理

---

### 四、可以大胆替换的高性能方案

**12. GSAP → Web Animations API / CSS Animations**

gsap chunk 体积 69KB（brotli 25KB）。项目中 GSAP 主要用于入场交错动画（`entryStagger`）、波纹效果（`rippleEffect`）和 ChatWindow 的呼吸动画（`scale: 1.05, repeat: -1, yoyo: true`）。

现代浏览器的 **Web Animations API**（WAAPI）已经可以完成这些工作，且是浏览器原生实现，不需要下载额外 JS。对于交错动画，`element.animate()` + `Animation.commitStyles()` 完全够用。对于循环呼吸动画，CSS `@keyframes` + `animation` 即可。

如果必须保留 GSAP（比如需要精确的时间轴控制），至少可以考虑只导入 `gsap/core` 而不导入整个 gsap 包。

**13. sockjs-client + stompjs → 原生 WebSocket + 轻量协议**

stomp chunk 体积 57KB（brotli 17KB）。`sockjs-client` 是一个 WebSocket 降级库（约 40KB），`stompjs` 是 STOMP 协议实现（约 17KB）。

在现代浏览器中（2026 年），WebSocket 支持率 98%+，不再需要 SockJS 的降级方案。如果后端支持原生 WebSocket（Spring WebSocket 默认支持），可以直接使用浏览器原生 WebSocket API + 自己封装一个简单的消息分发器（约 50 行代码），替代整个 sockjs + stomp 的 57KB。

**14. oh-my-live2d → pixi-live2d-display**

如第 4 点所述，`oh-my-live2d` 打包后 954KB，是最大的性能包袱。`pixi-live2d-display`（基于 PixiJS v7）打包后约 120KB gzip，且提供更底层的控制能力。更重要的是，pixi-live2d-display 支持将渲染放到 OffscreenCanvas 上，可以在 Web Worker 中运行，完全不阻塞主线程的 JS 执行和 DOM 操作。

**15. `transition: all` 全局替换为 CSS Houdini 或 View Transitions API**

如果目标浏览器支持（Chrome 111+、Edge 111+），可以使用 **View Transitions API** 替代 Vue 的 `<transition>` 组件来处理页面/面板切换动画。View Transitions 由浏览器引擎直接处理，在合成层上运行，比 JS 驱动的 Vue transition 更流畅，且不受 JS 主线程卡顿影响。

**16. 消息渲染：v-for → 虚拟滚动**

这是最应该优先做的性能替换。当前 ChatMessages.vue 用 `v-for` 渲染所有消息。当累积 200+ 条消息时，DOM 节点数轻松超过 1000 个（每条消息包含 `.message-item` > `.message-content` > `.text-wrapper` > `.msg-text` 等多层嵌套）。

推荐使用 `vue-virtual-scroller`（Vue 3 版本约 8KB gzip），只渲染可视区域内的消息。以 ChatWindow 的 `height: 350px`（boosted 模式）计算，一屏最多显示约 5-8 条消息，即使有 500 条历史消息也只需要渲染 10 个 DOM 节点。

**17. localStorage → IndexedDB 或 OPFS**

对于聊天消息这种频繁读写且可能达到数 KB 的数据，应该使用 IndexedDB 或 **Origin Private File System**（OPFS）。OPFS 是 2023 年开始广泛支持的新 API，提供高速的文件读写，特别适合结构化数据的持久化。搭配 `navigator.locks` API 可以实现安全的并发写入。

---

### 五、量化影响评估

| 问题 | 当前开销 | 优化后预期 | 影响面 |
|------|---------|-----------|-------|
| backdrop-filter 滥用 | 每帧 GPU 模糊 200万+像素 | 零 GPU 模糊开销 | 面板动画流畅度 |
| live2d chunk | 218KB brotli | ~50KB（替换库）或 Worker 中加载 | 首屏加载时间 -300ms |
| 消息列表无虚拟化 | 200+条 → 1000+ DOM 节点 | 固定 ~30 个 DOM 节点 | 滚动/切换流畅度 |
| transition: all × 53 | 隐式动画触发 layout/paint | 仅 GPU 加速的 transform/opacity | 全局交互响应 |
| localStorage 同步 I/O | 30 处同步阻塞 | 异步 IndexedDB | 请求延迟/消息卡顿 |
| GSAP 69KB | 25KB brotli | 0KB（WAAPI/CSS） | 入场动画 |
| sockjs+stomp 57KB | 17KB brotli | ~3KB（原生 WS） | WebSocket 连接 |
