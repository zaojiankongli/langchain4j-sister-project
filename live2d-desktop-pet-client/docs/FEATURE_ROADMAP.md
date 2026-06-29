## Live2D Desktop Pet — Feature Roadmap

### Current State (Phase 2 Complete)

The application currently supports: Live2D model rendering with PixiJS, STOMP WebSocket real-time communication, streaming chat with TTS audio playback, user authentication, PAD emotion model with mood history, pet personality settings, semantic memory search (Milvus), mailbox system, tap/double-tap interaction, user profile with level/EXP, content recommendations, 5-theme system, display preferences, and a debug panel.

Performance optimizations applied: messageGroups computation moved to child component, panel-open dedup guards, timer leak cleanup, RAF-based scroll throttling, Vite manual chunks, readonly composable returns, shared ApiResult type, and PetShell panel extraction.

---

### Tier 1 — Foundation (No Major Cross-Feature Dependencies)

**T1-1. Voice Input (STT)**
- What: Browser-native speech recognition (webkitSpeechRecognition) for chat input
- Why: Hands-free interaction, natural conversation flow
- Complexity: Medium — browser API integration, fallback UI for unsupported browsers
- Dependencies: None
- Estimated effort: 2-3 days

**T1-2. Desktop Notification System**
- What: Tauri notification API for events (new mail, mood change, daily reminder)
- Why: Keeps user engaged even when pet window is not focused
- Complexity: Low-Medium — Tauri notification plugin, notification preferences
- Dependencies: None
- Estimated effort: 1-2 days

**T1-3. Animation Library Browser**
- What: UI panel to browse and preview all available Live2D motions and expressions
- Why: Users can discover and play animations on their pet
- Complexity: Low — reads motion3.json metadata, triggers existing playSemanticMotion
- Dependencies: None
- Estimated effort: 2 days

**T1-4. Auto-start on System Boot**
- What: Tauri system tray integration with auto-start option
- Why: Desktop pet should persist across reboots
- Complexity: Medium — Tauri system tray API, startup registry, minimize-to-tray behavior
- Dependencies: None
- Estimated effort: 2-3 days

**T1-5. Screenshot / GIF Capture**
- What: Capture the pet canvas as PNG or short GIF
- Why: Share pet moments on social media
- Complexity: Medium — canvas toDataURL for PNG, frame capture loop for GIF
- Dependencies: None
- Estimated effort: 2 days

**T1-6. Multi-language i18n**
- What: Internationalization with vue-i18n (zh-CN, en-US, ja-JP)
- Why: Broaden user base beyond Chinese speakers
- Complexity: Medium — extract all strings, locale switching, RTL not needed
- Dependencies: None
- Estimated effort: 3-4 days

**T1-7. Offline Mode**
- What: Cached AI responses and local fallback when WebSocket disconnects
- Why: Pet should still respond with basic interactions offline
- Complexity: Low-Medium — local response templates, connection state UI
- Dependencies: None
- Estimated effort: 2 days

---

### Tier 2 — Core Enhancement (Builds on Tier 1 or Existing Features)

**T2-1. Voice Conversation Mode**
- What: Continuous voice chat with VAD (Voice Activity Detection) + STT + TTS pipeline
- Why: Natural spoken conversation with the pet
- Complexity: High — audio processing pipeline, VAD, real-time STT, interruption handling
- Dependencies: T1-1 (STT), existing TTS
- Estimated effort: 5-7 days

**T2-2. Daily Summary Reports**
- What: AI-generated daily interaction summary delivered to mailbox
- Why: Users reflect on their relationship with the pet
- Complexity: Medium — backend LangChain4j summarization, scheduled task
- Dependencies: Existing mailbox system, chat history
- Estimated effort: 3-4 days

**T2-3. Pet Growth System**
- What: Visible model changes (accessories, outfits) unlocked by level milestones
- Why: Gamification — visual reward for sustained interaction
- Complexity: High — multiple Live2D model variants, unlock logic, preview system
- Dependencies: Existing level/EXP system from user profile
- Estimated effort: 5-7 days

**T2-4. Context-aware Conversation (Local RAG)**
- What: RAG pipeline that indexes user's local documents for contextual chat
- Why: Pet becomes a personalized knowledge assistant
- Complexity: High — Tauri file picker, local embedding, Milvus collection per user
- Dependencies: Existing Milvus integration, LangChain4j backend
- Estimated effort: 7-10 days

**T2-5. Scene Backgrounds**
- What: Switchable virtual environments (bedroom, park, cafe) behind the pet
- Why: Visual variety and mood customization
- Complexity: Medium — background layer behind Live2D canvas, parallax effect
- Dependencies: None directly, enhances theme system
- Estimated effort: 3-4 days

**T2-6. Mini-games**
- What: Simple interactive games (rock-paper-scissors, quiz, fortune) with the pet
- Why: Entertainment and engagement
- Complexity: Medium — game state machine, pet reactions to win/lose
- Dependencies: Existing interaction system
- Estimated effort: 4-5 days

**T2-7. Widget Mode**
- What: Compact transparent overlay (small pet, no panels) for always-visible mode
- Why: Minimal footprint while working
- Complexity: Medium — Tauri window resize, simplified render path
- Dependencies: T1-4 (system tray for mode switching)
- Estimated effort: 3-4 days

---

### Tier 3 — Advanced (Complex, Multiple Dependencies)

**T3-1. Multi-pet System**
- What: Multiple pets with distinct personalities sharing the desktop
- Why: Richer companion experience, different pets for different moods
- Complexity: Very High — multiple PixiJS stages, personality routing, shared state
- Dependencies: Theme system, settings system, pet growth (T2-3)
- Estimated effort: 10-14 days

**T3-2. AI Memory Consolidation**
- What: Periodic AI summarization of chat history to compress long-term memory
- Why: Prevent context window overflow, maintain coherent long-term relationship
- Complexity: High — scheduled summarization, vector store management, retrieval strategy
- Dependencies: Existing memory system, LangChain4j, Milvus
- Estimated effort: 5-7 days

**T3-3. Social Features**
- What: Share pet profiles, screenshots, and interaction stats with other users
- Why: Community building and social engagement
- Complexity: Very High — backend social API, friend system, feed, privacy controls
- Dependencies: T1-5 (screenshots), user profile system, T1-6 (i18n for global)
- Estimated effort: 14-20 days

**T3-4. Plugin System**
- What: User-created extensions (custom animations, chat commands, widgets)
- Why: Community-driven feature expansion
- Complexity: Very High — plugin SDK, sandboxed execution, marketplace
- Dependencies: Stable core API, T1-6 (i18n)
- Estimated effort: 14-20 days

**T3-5. Mobile Companion App**
- What: Cross-platform mobile app synced with desktop pet via cloud
- Why: Take the pet everywhere, continuous companionship
- Complexity: Very High — React Native or Flutter, cloud sync, push notifications
- Dependencies: All backend APIs, cloud infrastructure
- Estimated effort: 20-30 days

---

### Dependency Graph

```
T1-1 (STT) ──────→ T2-1 (Voice Conversation)
T1-4 (Auto-start) ─→ T2-7 (Widget Mode)
T1-5 (Screenshot) ──→ T3-3 (Social Features)
T1-6 (i18n) ───────→ T3-3 (Social Features)
T1-6 (i18n) ───────→ T3-4 (Plugin System)

Existing Level System ─→ T2-3 (Pet Growth)
Existing Milvus ───────→ T2-4 (Local RAG)
Existing Memory ──────→ T3-2 (Memory Consolidation)
```

### Suggested Sprint Plan

**Sprint 1 (Week 1-2):** T1-2 Notifications + T1-3 Animation Browser + T1-5 Screenshot
Quick wins that add visible value with low risk.

**Sprint 2 (Week 3-4):** T1-1 Voice Input + T1-4 Auto-start
Foundation features that enable Tier 2 voice and widget features.

**Sprint 3 (Week 5-7):** T2-1 Voice Conversation + T2-5 Scene Backgrounds
High-impact user experience upgrades.

**Sprint 4 (Week 8-9):** T1-6 i18n + T1-7 Offline Mode
Polish and accessibility.

**Sprint 5 (Week 10-12):** T2-2 Daily Summary + T2-6 Mini-games + T2-7 Widget Mode
Engagement and retention features.

**Sprint 6+ (Week 13+):** T2-3 Pet Growth, T2-4 Local RAG, T3-2 Memory Consolidation
Advanced features based on user feedback and priorities.

---

### Performance / Architecture Backlog (Continuous)

These items should be addressed incrementally alongside feature work:

- Extract PetShell further: separate `usePetShellOrchestrator` for composable wiring
- Add `readonly()` returns to remaining composables (usePetSettings, usePetMemory, useChatMessages)
- Expand SockJS type declaration (currently minimal)
- Add composable pattern documentation (singleton vs instance guidelines)
- Consider migrating to Pinia for complex shared state (useChatMessages, usePetSettings)
- Add unit tests for composables (especially useChatMessages stream buffering)
- Lazy-load PixiJS renderer (currently blocks initial paint)
