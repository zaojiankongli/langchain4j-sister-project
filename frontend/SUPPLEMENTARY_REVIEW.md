## Zeeva 前端第三轮审查——补充发现

> 本轮在已有 CODE_REVIEW（24 项）和 PERFORMANCE_REVIEW（17 项）基础上，覆盖了之前未读的文件
> （CompleteProfileDialog、SettingsPersonality、PersonalityCloud、index.html、nginx 配置、Dockerfile）
> 以及对已读文件做了更深层次的跨组件数据流、CSS 工程和部署配置分析。
>
> 同时确认了近期已修复的 3 项问题，避免重复计数。

---

### 已确认修复的问题（不计入新增）

| 原编号 | 问题 | 修复方式 |
|--------|------|----------|
| CODE-12 | JWT 解码不一致（`atob` vs `base64UrlDecode`） | 新增 `utils/jwt.js` 统一导出，`auth.js` 和 `request.js` 均从此模块导入 |
| CODE-03 | Token 刷新双 Promise 单例 | `request.js` 的 `doRefresh()` 现委托给 `authStore.refreshTokens()`，单一事实来源 |
| CODE-08 | `settings.js` busy-wait 循环 | 已替换为 `_fetchPromise` 单例 + 30s TTL 缓存 |

---

### A. 网络请求与数据流（3 项）

**A1. ChatWindow 中 `await` 阻塞 WebSocket 连接**
- 位置：`ChatWindow.vue` onMounted（约 443-467 行）
- 现状：`await fetchTodayMessages()` 完成后才调用 `connect(userId)` 建立 WebSocket。
- 问题：HTTP 拉取和 WS 握手完全无依赖关系，但被串行化。假设 fetch 耗时 300-500ms，WS 握手 200ms，总延迟比并行多 200-500ms。
- 建议：改为 `Promise.all([fetchTodayMessages(), connect(userId)])` 或去掉 await，让两者并行。

**A2. StatusPanel 天气获取：顺序 fallback 可改为 Promise.race**
- 位置：`StatusPanel.vue`（约 157-200 行）
- 现状：先尝试 `navigator.geolocation`（策略 1），完全失败后才 fallback 到 `ip-api.com`（策略 2）。
- 问题：geolocation 在桌面浏览器上经常超时或需要用户授权，等待时间 0-5s。两个策略本质独立，却串行执行。
- 建议：`Promise.race([geoWeather(), ipWeather()])` 并行竞速，取先到者。最坏延迟从 ~7s 降至 ~3s。

**A3. API 配置中存在相同端点别名**
- 位置：`config/api.js` 第 20 行 `USER_PROFILE: '/user/profile'` 和第 51 行 `MY_PROFILE: '/user/profile'`
- 问题：同一端点有两个名字，分别被 `stores/user.js` 和 `UserProfile.vue` 使用。虽然当前不会同时触发，但缺乏请求去重机制——如果将来两处同时 mount，就会重复请求。
- 建议：统一为一个常量，或在 `userStore` 增加类似 `settingsStore` 的 `_fetchPromise` 去重。

---

### B. 状态管理与组件通信（3 项）

**B1. Dashboard 本地 ref 与 uiStore 双数据源**
- 位置：`Dashboard.vue` 第 117-118 行（`activeLayer`, `activeTab` 本地 ref）vs `stores/ui.js` 第 13-14 行（同名状态）
- 问题：Dashboard 的所有渲染逻辑和面板切换都使用本地 ref，但 `uiStore` 也定义了同名状态。`uiStore.closeAllPanels()` 只重置 store 的 `activeLayer`，对 Dashboard 的本地 ref 无效果。未来任何组件读取 `uiStore.activeLayer` 都会得到过期值。
- 建议：选择其一作为事实来源。如果面板状态需要跨组件共享，Dashboard 应读写 `uiStore` 而非本地 ref。

**B2. UserProfile.vue 绕过 userStore 直接调用 API**
- 位置：`UserProfile.vue`
  - `changeAIType`（约 177-189 行）：直接 `request.post(API.USER_UPDATE_AI_TYPE, ...)`
  - `handleAvatarChange`（约 105-124 行）：直接 `request.post(API.USER_AVATAR, ...)`
- 而 `userStore` 已经有 `updateAIType()` 和 `updateAvatar()` 方法（带 `_generation` 竞态保护）。
- 问题：绕过 store 意味着这两项操作缺少 `_generation` 计数器保护——如果用户快速切换 AI 类型，过时的响应可能覆盖当前状态。
- 建议：统一通过 `userStore` 调用，避免 API 调用散落在组件中。

**B3. SettingsPersonality.vue 直接变异 props**
- 位置：`SettingsPersonality.vue` 第 121-126 行
  ```js
  function selectPreset(preset) {
    props.form.personalityPreset = preset.id
    for (const t of oceanKeys) {
      props.form[t.key] = preset[t.key]
    }
  }
  ```
- 问题：Vue 最佳实践中 props 应为只读。直接修改 props 上的属性虽然技术上可行（对象引用），但绕过了 Vue 的单向数据流约定，且不会触发 `update:form` 事件让父组件感知变更。
- 建议：改用 `emit('update:form', { ...props.form, ...presetValues })` 或 `v-model` 模式。

---

### C. CSS 工程质量（4 项）

**C1. CompleteProfileDialog 新增 2 处 backdrop-filter**
- 位置：
  - `.background-overlay`（第 350 行）：`backdrop-filter: blur(25px) brightness(0.9)` — 全屏遮罩
  - `.ai-bubble`（第 367 行）：`backdrop-filter: blur(10px)` — AI 气泡
  - `.date-picker-group`（第 420 行）：`backdrop-filter: blur(10px)` — 日期选择器
- 影响：延续前两轮报告的 GPU 合成层问题。全屏遮罩的 `blur(25px)` 尤其昂贵。
- 建议：参考第二轮报告中提出的替代方案（半透明背景 + inset shadow 模拟）。

**C2. CompleteProfileDialog 的 transition: all（3 处）**
- `.date-picker-group`（第 423 行）：`transition: all 0.3s ease`
- `.sync-stage-row`（第 495 行）：`transition: all 0.4s ease`
- `.slide-fade-enter-active`（第 513 行）：`transition: all 0.5s ease-out`
- `.action-pop-enter-active`（第 516 行）：`transition: all 0.6s ...`
- 问题：`transition: all` 会隐式动画所有 CSS 属性变化，包括不需要动画的属性（如 `display`、`z-index`），导致意外的布局抖动和性能开销。
- 建议：替换为具体属性，如 `transition: opacity 0.4s ease, transform 0.4s ease`。

**C3. PersonalityCloud 入场动画与 hover 交互冲突**
- 位置：`PersonalityCloud.vue`
  - `.parameter-card` 入场动画（第 191 行）：`animation: card-appear 0.8s forwards` → 最终状态 `transform: translateY(0)`
  - `.parameter-card:hover`（第 215 行）：`transform: translateX(5px)`
- 问题：hover 的 `translateX(5px)` 会完全覆盖 `animation-fill-mode: forwards` 保持的 `translateY(0)`。当用户 hover 时，卡片会突然跳回初始 Y 位置再水平移动，产生明显的布局跳动。
- 建议：hover 应同时保留 Y 位移：`transform: translateY(0) translateX(5px)`，或使用 `transition` 代替 `animation` 实现入场效果。

**C4. @keyframes spin 跨组件重复定义**
- 出现在 3 个 scoped 组件中：
  - `UserProfile.vue`
  - `ChatWindow.vue`
  - `PersonalityCloud.vue`（第 343 行）
- 问题：相同的 `@keyframes spin { to { transform: rotate(360deg); } }` 在 3 个文件中重复声明。scoped CSS 虽然不会冲突，但增加了维护成本。
- 建议：提取到 `main.css` 作为全局 keyframe，或创建 `@/assets/animations.css` 统一管理。

---

### D. 构建与部署配置（6 项）

**D1. Permissions-Policy 阻止了 geolocation——但 StatusPanel 需要使用它**
- 位置：`nginx.conf` 第 17 行
  ```
  Permissions-Policy "camera=(), microphone=(), geolocation=()"
  ```
- 问题：`geolocation=()` 禁用了浏览器的地理定位 API，但 `StatusPanel.vue` 第 162 行调用了 `navigator.geolocation.getCurrentPosition()`。浏览器会直接拒绝调用，导致每次都走 IP fallback 路径（多一次网络请求 + 精度更低）。
- 建议：如果需要 geolocation，改为 `geolocation=(self)`；如果确定不用，应从 StatusPanel 中移除 geolocation 代码以避免死路径。

**D2. Vite 仅生成 Brotli 压缩文件，缺少 gzip**
- 位置：`vite.config.js` 第 18-23 行
- 问题：`viteCompression` 只配置了 `brotliCompress`。虽然现代浏览器都支持 Brotli，但 CDN 边缘节点、反向代理、以及部分企业网络中间件可能只支持 gzip。nginx 可以动态 gzip，但缺少预压缩文件意味着每次请求都要实时压缩，增加 CPU 开销。
- 建议：增加第二个 `viteCompression` 实例生成 gzip 文件：
  ```js
  viteCompression({ algorithm: 'gzip', threshold: 1024, ext: '.gz' })
  ```

**D3. index.html `lang` 属性为空**
- 位置：`index.html` 第 2 行
  ```html
  <html lang="">
  ```
- 问题：空的 `lang` 属性会让屏幕阅读器无法确定页面语言，影响可访问性。同时影响搜索引擎的语言判断。
- 建议：改为 `<html lang="zh-CN">`。

**D4. index.html 对 localhost 的 dns-prefetch/preconnect 无意义**
- 位置：`index.html` 第 11-12 行
  ```html
  <link rel="dns-prefetch" href="//localhost">
  <link rel="preconnect" href="//localhost">
  ```
- 问题：`localhost` 不需要 DNS 解析（直接映射到 127.0.0.1），preconnect 到它在生产环境中无意义（API 通过 nginx 同源代理），在开发环境中也只是浪费一个 TCP 连接预热。
- 建议：删除这两行。如果需要 preconnect API 域名，应使用实际的生产域名。

**D5. index.html 缺少关键资源预加载提示**
- 问题：没有 `<link rel="modulepreload">` 来提示浏览器提前加载关键的 JS chunk（vendor、首屏组件）。没有 `<meta name="theme-color">` 来控制移动端浏览器状态栏颜色。没有字体预加载（如果使用了自定义字体）。
- 建议：
  ```html
  <meta name="theme-color" content="#0a0e1a">
  <link rel="modulepreload" href="/assets/vendor-HASH.js">
  ```
  注意：modulepreload 的 hash 会随构建变化，通常由 Vite 插件自动生成（如 `vite-plugin-preload`）。

**D6. 三份 nginx 配置的安全头不一致**
- 位置：
  - `nginx.conf`：有完整 5 个安全头（X-Content-Type-Options, X-Frame-Options, X-XSS-Protection, Referrer-Policy, Permissions-Policy）
  - `nginx.local.conf`：只有 4 个（缺 Permissions-Policy）
  - `nginx.vm.conf`：0 个安全头
- 问题：虽然 local/vm 是测试环境，但安全头不一致意味着测试环境无法准确验证安全策略。
- 建议：提取安全头到一个 `include` 文件，三份配置共享。
- 补充：三份配置均缺少 `Content-Security-Policy` 头。对于 SPA 项目，至少应有 `CSP: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'`。

---

### E. 代码质量与潜在 Bug（3 项）

**E1. CompleteProfileDialog.vue 可能缺少 `computed` 导入**
- 位置：第 2 行导入列表 vs 第 75 行使用
  ```js
  import { ref, reactive, watch, onBeforeUnmount } from 'vue'  // 无 computed
  // ...
  const daysInMonth = computed(() => { ... })  // 使用了 computed
  ```
- 问题：如果项目没有配置 `unplugin-auto-import`，这会在运行时抛出 `ReferenceError: computed is not defined`。
- 建议：确认是否有 auto-import 配置，否则需要在 import 中补充 `computed`。

**E2. CompleteProfileDialog 常量数组在 setup 作用域内定义**
- 位置：第 31-37 行 `syncStages` 和第 48-57 行 `aiMessages`
- 问题：这两个数组是纯常量，但定义在 `<script setup>` 的作用域内。每次组件挂载都会重新创建数组实例。虽然性能影响微乎其微（数组很小），但不符合常量提升的最佳实践。
- 建议：提取到 `<script>` 块（非 setup）或单独的常量文件中。

**E3. `useAsyncData` 的 `_isMounted` 守卫依赖注册顺序**
- 位置：`useAsyncData.js` 第 10-13 行
  ```js
  let _isMounted = false
  onMounted(() => { _isMounted = true })
  onBeforeUnmount(() => { _isMounted = false })
  ```
- 问题：这个守卫之所以能正常工作，是因为 Vue 按注册顺序触发 `onMounted` 回调——`useAsyncData` 内部的 `onMounted` 在消费者的 `onMounted` 之前注册，所以先执行。但这是 Vue 的内部行为约定，而非显式 API 保证。如果将来 Vue 改变回调顺序，或者有人把 `execute()` 调用从 `onMounted` 移到 setup 顶层，守卫会静默丢弃结果。
- 建议：用更健壮的模式替代，如在 `execute()` 内部使用 `getCurrentInstance()` 检查挂载状态，或直接使用 `onMounted` 包装 execute 调用。

---

### F. Dockerfile（2 项）

**F1. 未固定 nginx 运行时版本**
- 位置：`Dockerfile` 第 14 行
  ```dockerfile
  FROM nginx:stable-alpine
  ```
- 问题：`stable-alpine` 是一个滚动标签，指向不同的具体版本。这意味着不同时间的构建可能产生不同的运行时行为。
- 建议：固定为具体版本，如 `nginx:1.26-alpine`，并在更新时显式升级。

**F2. 缺少 `.dockerignore` 文件**
- 问题：`COPY . .` 会把 `node_modules`、`.git`、`dist` 等目录也复制到构建上下文中，增加构建时间和镜像层大小。虽然 `npm ci` 会覆盖 `node_modules`，但多余的 `.git` 和旧 `dist` 仍会进入 builder 阶段。
- 建议：创建 `.dockerignore`：
  ```
  node_modules
  dist
  .git
  .gitignore
  *.md
  nginx*.conf
  Dockerfile
  ```

---

### 总结

| 类别 | 新增问题数 | 高/中/低优先级 |
|------|-----------|---------------|
| A. 网络请求与数据流 | 3 | 1 高 / 1 中 / 1 低 |
| B. 状态管理与组件通信 | 3 | 1 高 / 1 中 / 1 中 |
| C. CSS 工程质量 | 4 | 0 高 / 2 中 / 2 低 |
| D. 构建与部署配置 | 6 | 1 高 / 2 中 / 3 低 |
| E. 代码质量与潜在 Bug | 3 | 1 高 / 1 低 / 1 低 |
| F. Dockerfile | 2 | 0 高 / 1 中 / 1 低 |
| **合计** | **21** | **4 高 / 8 中 / 9 低** |

---

### 三轮审查累计统计

| 报告 | 问题数 |
|------|--------|
| CODE_REVIEW（第一轮） | 24 |
| PERFORMANCE_REVIEW（第二轮） | 17 |
| 补充发现（第三轮） | 21 |
| 已确认修复 | -3 |
| **净未修复总计** | **59** |
