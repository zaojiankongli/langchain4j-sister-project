# WakeUp · Anchor · Emotion 三大核心系统流程图

> 项目：langchain4j_sister — AI 妹妹陪伴系统
> 时间：2026-05-27

---

## 一、Emotion — 情绪计算引擎（PAD 模型）

### 1.1 核心数据结构

```
EmotionalState (PAD 三要素)
  ├── pleasure (P) 愉悦度  [-1.0, +1.0]  快感/不悦
  ├── arousal  (A) 唤醒度  [-1.0, +1.0]  兴奋/冷静
  └── dominance(D) 支配感  [-1.0, +1.0]  控制/顺从

Personality (OCEAN 五大人格 → 映射 PAD 基线)
  ├── openness         开放性      → P, A 正相关
  ├── conscientiousness 尽责性      → P 正相关
  ├── extraversion     外向性       → P, A, D 正相关
  ├── agreeableness    宜人性       → P, A, D 正相关
  └── neuroticism      神经质       → P, A, D 负相关

DeltaEmotion (外部刺激 Δ 向量)
  ├── deltaP  愉悦度变化量
  ├── deltaA  唤醒度变化量
  └── deltaD  支配感变化量
```

### 1.2 情绪更新流程图

```mermaid
flowchart TD
    Start([外部事件触发更新]) --> Input[传入 DeltaEmotion ΔP, ΔA, ΔD]
    Input --> Lock{获取 Redisson 分布式锁<br/>lock:emotion:{userId}}
    Lock -->|获取失败| Skip[返回当前缓存情绪]
    Lock -->|获取成功| Load[加载当前 EmotionalState<br/>Caffeine 本地缓存 → Redis Hash]

    Load --> ApplyStimulus["施加刺激:<br/>newP = currentP + ΔP × sensitivity<br/>newA = currentA + ΔA × sensitivity<br/>newD = currentD + ΔD × sensitivity"]

    ApplyStimulus --> DecayRegression["衰减 + 回归基线:<br/>① 衰减: val = val × (1 - decayRate)<br/>② 回归: val = val + (baseVal - val) × regressionRate"]

    DecayRegression --> Save[保存至 Redis Hash<br/>KEY: user:emotion:{userId}<br/>FIELDS: pleasure, arousal, dominance, updatedAt]
    Save --> Cache[更新 Caffeine 本地缓存]
    Cache --> Unlock[释放锁]
    Unlock --> PublishEvent[发布 EmotionChangedEvent]
    PublishEvent --> End([返回新的 EmotionalState copy])
```

### 1.3 情绪衰减调度流程图

```mermaid
flowchart TD
    SchedulerStart([EmotionDecayScheduler<br/>每30分钟执行]) --> GetActive[UserActivityTracker<br/>获取近1天活跃用户 Set<String>]

    GetActive --> Loop{遍历每个活跃用户}
    Loop -->|next userId| DecayOne["emotionService.decayUserEmotion(userId)<br/>① 加锁<br/>② 加载当前情绪<br/>③ 衰减 + 回归<br/>④ 保存 Redis<br/>⑤ 更新本地缓存<br/>⑥ 释放锁"]
    DecayOne --> LogError["失败则 log.warn"]
    LogError --> Loop
    Loop -->|全部处理完| EndScheduler([完成])

    subgraph decayDetail["衰减回归公式详情"]
        direction LR
        D1["decay 消散:<br/>P = P × (1 - decayRate)<br/>A = A × (1 - decayRate)<br/>D = D × (1 - decayRate)"]
        D2["regression 回归:<br/>P = P + (baseP - P) × regRate<br/>A = A + (baseA - A) × regRate<br/>D = D + (baseD - D) × regRate"]
    end
```

### 1.4 心情描述映射

```mermaid
flowchart LR
    PAD[P, A, D] --> Judge{判断优先级}

    Judge -->|D < -0.5| Shy1["羞涩得不敢抬头..."]
    Judge -->|D < -0.3| Shy2["有些害羞..."]
    Judge -->|P > 0.5| Happy1["心里甜甜的..."]
    Judge -->|P > 0.2| Happy2["心情不错..."]
    Judge -->|P < -0.4| Sad1["心里酸酸的..."]
    Judge -->|P < -0.15| Sad2["心情有些低落..."]
    Judge -->|A > 0.5| Tense1["心跳好快..."]
    Judge -->|A > 0.2| Tense2["有点紧张..."]
    Judge -->|A < -0.5| Calm1["整个人很放松..."]
    Judge -->|A < -0.2| Calm2["感觉很安心..."]
    Judge -->|else| Neutral["安静地待在那里..."]

    Judge2{判断叠加态} -->|P > 0.3 AND A > 0.3 AND D < -0.2| Extra["...心里像有小鹿乱撞"]
    Judge2 -->|D < -0.4 AND P > 0.1| Extra2["...乖巧地听你说话"]
    Judge2 -->|P < -0.2 AND D < -0.2| Extra3["...咬着嘴唇不说话"]
```

### 1.5 用户个性与参数可配置

```mermaid
flowchart TD
    SetPersonality[SettingsController<br/>设置用户个性] --> Validate{Personality 非空}
    Validate -->|OK| Serialize[序列化 personality JSON]
    Serialize --> RedisSave[保存至 Redis<br/>KEY: user:personality:{userId}<br/>TTL: 30天]
    RedisSave --> InvalidateCache[清除 Caffeine 本地缓存<br/>personalityCache & localCache]
    InvalidateCache --> Recompute[根据 OCEAN→PAD 公式重新计算基线]

    SetConfig[设置情绪引擎参数] --> CheckRange{校验范围<br/>sensitivity [0,1]<br/>decayRate [0,1]<br/>regressionRate [0,1]}
    CheckRange -->|OK| SaveConfig[保存至 Redis<br/>KEY: user:emotion-config:{userId}]
```

---

## 二、Anchor — 情绪锚点系统

### 2.1 锚点生命周期概览

```mermaid
flowchart TD
    subgraph Monitor["EmotionAnchorMonitor (内存状态机)"]
        IDLE["状态: IDLE<br/>等待触发"]
        MONITORING["状态: MONITORING<br/>监测中"]
    end

    subgraph DB["数据库持久化"]
        INSERT["INSERT emotion_anchor_events<br/>(trigger 时)"]
        UPDATE["UPDATE emotion_anchor_events<br/>(end 时)"]
    end

    subgraph Semantic["EmotionAnchorSemanticService"]
        AI["调用 Qwen AI<br/>生成语义字段"]
        Cache["Redis 每日缓存<br/>避免重复调用"]
    end

    IDLE -->|"愉悦度变化 ΔP > 0.15"| Trigger["triggerEvent()"]
    Trigger -->|状态切换| MONITORING
    Trigger -->|异步| INSERT
    Trigger -->|发布| AnchorTriggeredEvent

    MONITORING -->|"每次情绪变化"| Evaluate{评估结束条件}

    Evaluate -->|"用户沉默 > 2h"| End1["endEvent()<br/>endReason: 用户沉默超过2小时"]
    Evaluate -->|"回归基准 |ΔP| < 0.05"| End2["endEvent()<br/>endReason: 情绪平稳回归基准"]
    Evaluate -->|"超时 > maxDuration"| End3["endEvent()<br/>endReason: 情绪持续偏移N分钟"]
    Evaluate -->|"ΔP 扩大"| Continue["更新 peakPleasure<br/>继续监测"]

    End1 -->|状态切换| IDLE
    End2 -->|状态切换| IDLE
    End3 -->|状态切换| IDLE

    End1 -->|异步| Semantic
    End2 -->|异步| Semantic
    End3 -->|异步| Semantic

    Semantic -->|生成 eventTitle, summary,<br/>triggerBehavior, endReason,<br/>highlightTraits, aiReflection| UPDATE
    Semantic -->|注入聊天历史| Inject["Redis LIST<br/>chat:history:{userId}"]

    UPDATE -->|发布| AnchorEndedEvent
```

### 2.2 完整锚点触发 → 结束流程图

```mermaid
flowchart TD
    %% 触发阶段
    Change[对话引起的情绪变化] --> EmoService[EmotionService.updateUserEmotion]
    EmoService --> EmoListener[EmotionEventListener.onEmotionChanged<br/>日志记录]

    EmoService --> AnchorTrigger{EmotionAnchorMonitor.onEmotionChange}

    AnchorTrigger -->|"ΔP ∈ [-∞, -0.15) ∪ (0.15, +∞]"| TriggerEvent[triggerEvent]
    AnchorTrigger -->|"ΔP ∈ [-0.15, 0.15]"| Skip[跳过]

    TriggerEvent --> StateMachine["MonitorState: IDLE → MONITORING<br/>记录 startPleasure, peakPleasure<br/>startArousal, peakArousal<br/>startTime, lastMsgTime"]

    StateMachine --> BuildEvent[构建 EmotionAnchorEvent<br/>含 userId, startTime, startPleasure,<br/>peakPleasure, deltaPleasure]

    BuildEvent --> AsyncInsert[异步: EmotionAnchorService.handleAnchorTriggered]
    AsyncInsert --> InsertDB["INSERT emotion_anchor_events<br/>(start 字段, end 字段为 NULL)"]
    AsyncInsert --> Track["activeEventIds.put(userId, eventId)"]
    AsyncInsert --> PublishTrigger["发布 AnchorTriggeredEvent"]

    PublishTrigger --> Listener["EmotionEventListener.onAnchorTriggered<br/>日志记录"]

    %% 监测阶段
    subgraph MonitorPhase["监测阶段 (MONITORING)"]
        direction TB
        M_Change["后续每次情绪变化"] --> M_Eval{评估}
        M_Eval -->|"lastMsgTime 沉默 > 2h"| End_Silent
        M_Eval -->|"|newP - startP| < 0.05"| End_Return
        M_Eval -->|"持续时间 > maxDurationMin"| End_Timeout
        M_Eval -->|"newP > peakPleasure"| UpdatePeak["更新 peakPleasure"]
        M_Eval -->|"newA > peakArousal"| UpdatePeak2["更新 peakArousal"]
        M_Eval -->|"其余"| Ignore["忽略"]
    end

    End_Silent["endEvent: 沉默结束"]
    End_Return["endEvent: 回归结束"]
    End_Timeout["endEvent: 超时结束"]

    %% 结束阶段
    End_Silent --> DecideType{"endPleasure > 0.05<br/>? 正向结束 : 负向结束"}
    End_Return --> DecideType
    End_Timeout --> DecideType

    DecideType --> SetEndType["设置 EndType<br/>POSITIVE / NEGATIVE"]
    SetEndType --> BuildEndEvent["构建结束 EmotionAnchorEvent<br/>含 endTime, endPleasure, endArousal,<br/>endType, endReason, durationSeconds"]

    BuildEndEvent --> AsyncEnd[异步: EmotionAnchorService.handleAnchorEnded]
    AsyncEnd --> GenerateAI["EmotionAnchorSemanticService.generateSemanticFields"]

    GenerateAI --> ChatHistory[查询对话时间范围内的聊天记录]
    GenerateAI --> CheckCache{Redis 有当日缓存?}
    CheckCache -->|命中| DefaultFields[使用默认值]
    CheckCache -->|未命中| CallAI["调用 Qwen AI 模型<br/>生成 6 个语义字段"]

    CallAI --> ParseResult{"解析 JSON 成功?"}
    ParseResult -->|是| ApplyFields[赋值 eventTitle, triggerReason,<br/>highlightTraits, summary, endReason, aiReflection]
    ParseResult -->|否| FallbackText["手工提取 JSON<br/>失败则使用默认值"]
    FallbackText --> ApplyFields

    ApplyFields --> SetCache["Redis 缓存 24h"]
    ApplyFields --> UpdateDB["UPDATE emotion_anchor_events<br/>回写 end 字段 + 语义字段"]
    UpdateDB --> ActiveIdMatch{activeEventIds 中有?}
    ActiveIdMatch -->|有| UpdateById["UPDATE by id"]
    ActiveIdMatch -->|无| FallbackInsert["fallback INSERT"]
    UpdateById --> PublishEnd["发布 AnchorEndedEvent"]
    FallbackInsert --> PublishEnd

    PublishEnd --> InjectHistory["EListener: 注入锚点摘要到<br/>chat:history:{userId} (Redis LIST)"]
    PublishEnd --> StateReset["MonitorState: MONITORING → IDLE"]
```

### 2.3 锚点语义化 AI 调用

```mermaid
flowchart LR
    subgraph Input["输入"]
        TR["TriggerReason (技术)"]
        DP["ΔPleasure"]
        DA["ΔArousal"]
        DUR["DurationSeconds"]
        ET["EndType"]
        ER["EndReason (技术)"]
        CHAT["对话记录 (最近20条)"]
    end

    Input --> Prompt[构建 System + User Prompt]
    Prompt --> Qwen[QwenChatModel]

    Qwen --> Output["JSON 输出"]
    Output --> Fields["6 个语义字段:<br/>① eventTitle: 事件短标题<br/>② triggerBehavior: 用户行为描述<br/>③ highlightTraits: 情绪变化描述<br/>④ summary: 详细摘要 (≥200字)<br/>⑤ endReason: 结束原因语义描述<br/>⑥ aiReflection: AI 反思"]
```

### 2.4 悬念池 (Suspense Topics)

```mermaid
flowchart LR
    NEG[锚点负向结束] -->|抽取未解决话题| PendingTopics[INSERT pending_topics<br/>status=pending]
    PendingTopics -->|WakeUp 调用| GetSuspense["getSuspenseTopics(userId)<br/>selectRecentNegativeTopics(limit=2)"]
    GetSuspense --> WakeUpAgent[Generator Agent 获取悬念话题<br/>作为问候素材]
```

---

## 三、WakeUp — 主动唤醒系统

### 3.1 系统架构总览

```mermaid
flowchart TD
    subgraph Schedule["调度层"]
        Scheduler["WakeUpScheduler<br/>@Scheduled(cron = 0 0/30 * * * ?)<br/>每30分钟执行"]
    end

    subgraph Filter["过滤层"]
        DND["免打扰检查"]
        COOLDOWN["冷却期检查<br/>(默认冷缺时间)"]
        PROB["概率计算 + 随机采样"]
    end

    subgraph Generate["生成层 (3个并行 Agent)"]
        G1["Generator1 Agent<br/>侧重: 历史记忆引用"]
        G2["Generator2 Agent<br/>侧重: 最近话题延续"]
        G3["Generator3 Agent<br/>侧重: 情绪关心"]
    end

    subgraph Score["评分层 (3个并行 Agent)"]
        S1["Scorer1 Agent<br/>评分维度: 记忆引用精准度"]
        S2["Scorer2 Agent<br/>评分维度: 话题延续自然度"]
        S3["Scorer3 Agent<br/>评分维度: 情绪关心得当度"]
    end

    subgraph Arbiter["仲裁层"]
        ARB["Arbiter Agent<br/>决策: direct / merge / fallback"]
    end

    subgraph ABN_test["A/B 测试层"]
        TRACKER["WakeUpTracker<br/>5% 概率 A/B Swap"]
    end

    subgraph Send["发送层"]
        SAVE["保存消息到 DB"]
        TTS["语音合成 (TTS)"]
        PUSH["WebSocket 推送<br/>文本 + 语音"]
    end

    Schedule -->|获取7天活跃用户 Set<String>| Filter
    Filter -->|通过过滤| Generate
    Generate -->|解析 JSON 过滤无效| Score
    Score -->|解析分数| Arbiter
    Arbiter --> ABN_test
    ABN_test --> Send
```

### 3.2 完整唤醒流程（时序细节）

```mermaid
flowchart TD
    %% ===== 触发 =====
    CRON["@Scheduled cron=0 0/30 * * * ?"] --> EnableCheck{"wakeUp.enabled?"}
    EnableCheck -->|false| RETURN1[返回]
    EnableCheck -->|true| ActiveUsers["userActivityTracker<br/>getActiveMemoryIdsInLastDays(7)"]
    ActiveUsers --> EmptyCheck{"活跃用户为空?"}
    EmptyCheck -->|是| RETURN2[返回]
    EmptyCheck -->|否| TIME["TimeContextTool<br/>获取 timeOfDay, specialMoment"]

    TIME --> PER_USER["并行处理每个用户<br/>(虚拟线程)"]

    %% ===== 用户级流程 =====
    PER_USER --> REDIS_LOCK["Redis SET NX EX 600s<br/>KEY: wakeup:processing:{userId}<br/>防止同一用户同时处理"]

    REDIS_LOCK -->|已存在| RETURN3[跳过]
    REDIS_LOCK -->|获取成功| DND_CHECK{"userStateTool<br/>isDoNotDisturb?"}
    DND_CHECK -->|是| RETURN4[跳过, 清理锁]
    DND_CHECK -->|否| COOLDOWN_CHECK{"getMinutesSinceLastWakeup<br/>< cooldownMinutes?"}
    COOLDOWN_CHECK -->|是 (冷却期)| RETURN5[跳过, 清理锁]
    COOLDOWN_CHECK -->|否| CALC_PROB["probability = calculateWakeProbability<br/>基于: silentHours, timeContext,<br/>历史回复率等"]

    CALC_PROB --> RANDOM{"Math.random() > probability?"}
    RANDOM -->|是 (未命中概率)| RETURN6[跳过概率, 清理锁]
    RANDOM -->|否| BUILD_STATE["buildStateSnapshot:<br/>moodDescription, moodScore<br/>silentHours, minutesSinceLastWakeup<br/>activeAnchorContext, recentAnchorSummary"]

    BUILD_STATE --> BUILD_ANCHOR["WakeUpPromptBuilder.buildAnchorHint<br/>拼接 activeAnchorContext + recentAnchorSummary"]

    %% ===== 3 路并行生成 =====
    BUILD_ANCHOR --> G1["Generator1Agent.generate<br/>(虚拟线程) 侧重: 记忆"]
    BUILD_ANCHOR --> G2["Generator2Agent.generate<br/>(虚拟线程) 侧重: 话题"]
    BUILD_ANCHOR --> G3["Generator3Agent.generate<br/>(虚拟线程) 侧重: 情绪"]

    G1 --> PARSE1["contentGenerator.parseGeneratorOutput"]
    G2 --> PARSE2
    G3 --> PARSE3

    PARSE1 --> VALID{"isValidCandidate<br/>(非null/非blank/合规)"}
    PARSE2 --> VALID
    PARSE3 --> VALID

    VALID --> FILTERED["List<GeneratorOutput> candidates<br/>有效保留, 无效置 null"]

    FILTERED --> COUNT_VALID{"有效候选数?"}

    COUNT_VALID -->|0| FALLBACK["Fallback<br/>使用时间问候语: ～今天过得怎么样呀"]
    COUNT_VALID -->|1| SINGLE["直接使用唯一候选"]
    COUNT_VALID -->|≥2| SCORE_PHASE

    FALLBACK --> SEND_FALLBACK["保存消息 + TTS + 推送"]
    SINGLE --> SEND_SINGLE["保存消息 + TTS + 推送"]

    %% ===== 3 路并行评分 =====
    SCORE_PHASE --> SC1["Scorer1Agent.score<br/>维度: 记忆引用精准自然 0-10"]
    SCORE_PHASE --> SC2["Scorer2Agent.score<br/>维度: 话题延续自然 0-10"]
    SCORE_PHASE --> SC3["Scorer3Agent.score<br/>维度: 情绪关心得当 0-10"]

    SC1 --> P_SC1["WakeUpScorer.parseScoreResult"]
    SC2 --> P_SC2
    SC3 --> P_SC3

    P_SC1 --> ARB_INPUT["WakeUpScoreResult<br/>(score + reason)"]
    P_SC2 --> ARB_INPUT
    P_SC3 --> ARB_INPUT

    %% ===== 仲裁 =====
    ARB_INPUT --> ARBITER["WakeUpArbiterAgent.decide"]
    ARBITER --> ARB_PARSE["WakeUpArbiter.parseArbiterResult"]

    ARB_PARSE --> ARB_DECIDE{"decision 类型?"}

    ARB_DECIDE -->|direct| BEST_IDX["selectedIndex → 最佳候选"]
    ARB_DECIDE -->|merge| MERGE["mergedMessage 作为融合消息"]
    ARB_DECIDE -->|fallback| FALLBACK2["fallback 问候"]

    BEST_IDX --> AB_TEST["WakeUpTracker.maybeSwap<br/>5% 概率 A/B 测试<br/>用次优替换最优"]
    MERGE --> AB_TEST

    AB_TEST --> SELECT_OUTPUT["contentGenerator.selectOutput<br/>按仲裁索引选取 finalOutput"]
    SELECT_OUTPUT --> GET_VOICE["获取 VoiceSynthesisParam<br/>(音量/语速/音高/情感指令)"]

    %% ===== 发送 =====
    GET_VOICE --> SAVE_MSG["converMessageService.saveMessage<br/>保存到 MySQL"]
    SAVE_MSG --> TTS_STEP{"voiceSynthesisService.synthesize"}

    TTS_STEP -->|成功| PUSH_AUDIO["chatPushService.pushText(文本)<br/>chatPushService.pushAudio(音频)"]
    TTS_STEP -->|失败| PUSH_FALLBACK["chatPushService.pushText(纯文本)"]

    PUSH_AUDIO --> RECORD["WakeUpTracker.recordSent<br/>存入 Redis List<br/>KEY: wakeup:record:{userId}:yyyyMMdd"]
    PUSH_FALLBACK --> RECORD

    RECORD --> CLEAN_LOCK["Redis DEL processingKey<br/>释放处理锁"]
    CLEAN_LOCK --> END_USER([用户处理完成])
```

### 3.3 Generator Agent Prompt 策略

```mermaid
flowchart TD
    G_AGENT[Generator Agent 启动] --> Tools{"可用工具"}

    Tools --> M1[searchMemories<br/>搜索历史记忆]
    Tools --> M2[getRecentChatContext<br/>获取最近聊天]
    Tools --> M3[getSuspenseTopics<br/>获取悬念话题]

    M1 --> Strategy{"策略优先级"}

    Strategy -->|1st| Try1["searchMemories<br/>按时间/情绪关键词搜索记忆"]
    Try1 --> Found1{"有相关内容?"}
    Found1 -->|是| Gen1["基于记忆生成问候"]
    Found1 -->|否| Try2

    Strategy -->|2nd| Try2["getRecentChatContext<br/>获取最近聊天"]
    Try2 --> Found2{"有可用话题?"}
    Found2 -->|是| Gen2["延续最近话题生成问候"]
    Found2 -->|否| Try3

    Strategy -->|3rd| Try3["getSuspenseTopics<br/>获取悬念话题"]
    Try3 --> Found3{"有未完结话题?"}
    Found3 -->|是| Gen3["基于悬念生成关心问候"]
    Found3 -->|否| FreeStyle["自由发挥<br/>撒娇/分享趣事/吐槽"]

    Gen1 --> CheckReply{"上次话题用户已回复?"}
    CheckReply -->|是| Switch["换新话题"]
    CheckReply -->|否| Use["使用"]

    Switch --> Gen1
    Use --> Output["输出 JSON<br/>{ message, voiceParams }"]
    Gen2 --> Output
    Gen3 --> Output
    FreeStyle --> Output
```

### 3.4 Scorer Agent 评分维度

```mermaid
flowchart LR
    subgraph Scorer1["Scorer1 Agent"]
        S1_MEM["历史记忆引用<br/>是否精准自然"]
    end

    subgraph Scorer2["Scorer2 Agent"]
        S2_TOPIC["最近话题延续<br/>是否自然不生硬"]
    end

    subgraph Scorer3["Scorer3 Agent"]
        S3_CARE["情绪关心<br/>是否得当"]
    end

    S1_MEM -->|"评分 + 理由"| ARBITER
    S2_TOPIC -->|"评分 + 理由"| ARBITER
    S3_CARE -->|"评分 + 理由"| ARBITER

    ARBITER[Arbiter Agent] --> Decide{"决策依据"}

    Decide --> Rule1["① 优先最个性化<br/>(引用到用户具体信息)"]
    Decide --> Rule2["② 其次自然度<br/>(不突兀不生硬)"]
    Decide --> Rule3["③ 负面锚点时<br/>优先体现关心的"]
    Decide --> Rule4["④ 含'无可用'关键词排除"]
    Decide --> Rule5["⑤ 两条高分互补 → merge"]
    Decide --> Rule6["⑥ 最高分 < 5/10 → fallback"]
```

### 3.5 A/B 测试机制

```mermaid
flowchart TD
    BEST[仲裁选出最佳索引 bestIndex] --> THRESHOLD{"validIndices.size >= 2<br/>且<br/>random.nextDouble < 5%?"}

    THRESHOLD -->|否| SEND_BEST[发送最佳候选]
    THRESHOLD -->|是| SWAP["findSwapCandidate<br/>随机选非最佳的索引"]
    SWAP --> SEND_SWAP[发送替代候选]

    SEND_BEST --> RECORD_BEST[WakeUpTracker.recordSent<br/>记录 bestIndex, actualIndex, scores]
    SEND_SWAP --> RECORD_SWAP

    subgraph Future["未来分析"]
        ACTUAL[实际发送索引 vs 最佳索引] --> COMPARE[比较回复率]
        COMPARE --> ITERATE["调整评分权重 / AB 比例"]
    end
```

### 3.6 用户回复追踪

```mermaid
flowchart TD
    MSG[用户发送消息] --> ChatController[ChatStompController]
    ChatController --> TRACK["WakeUpTracker.markUserReplied"]

    TRACK --> ReadRedis["读 Redis List<br/>wakeup:record:{userId}:yyyyMMdd"]
    ReadRedis --> FindRecent{"30分钟内<br/>有未回复的唤醒记录?"}
    FindRecent -->|是| Mark["标记 userReplied=true"]
    FindRecent -->|否| Ignore2[忽略]
```

---

## 四、三大系统联动关系

```mermaid
flowchart TD
    %% Emotion → Anchor
    CHAT[用户对话] --> EMOTION[EmotionService<br/>更新 PAD 情绪]
    EMOTION --> ANCHOR_MON[EmotionAnchorMonitor<br/>检测情绪波动]
    ANCHOR_MON -->|触发锚点| ANCHOR_SVC[EmotionAnchorService<br/>持久化 + 语义化]

    %% Anchor → WakeUp
    ANCHOR_SVC -->|悬念话题| SUSPENSE["pending_topics 表"]
    SUSPENSE --> WAKEUP_TOPIC["WakeUp Generator<br/>getSuspenseTopics()"]
    ANCHOR_MON -->|activeAnchorContext| WAKEUP_ANCHOR["WakeUp PromptBuilder<br/>buildAnchorHint()"]

    %% Emotion → WakeUp
    EMOTION -->|moodDescription| WAKEUP_MOOD["WakeUp Generator<br/>情绪上下文"]
    EMOTION -->|moodScore| WAKEUP_SCORE["WakeUp Scorer<br/>情绪关心评分"]

    %% WakeUp → Emotion
    WAKEUP["WakeUp 发送后"] -->|用户回复| CHAT
    CHAT -->|回复内容影响情绪| EMOTION

    %% Anchor → Emotion
    ANCHOR_END[锚点结束] -->|注入聊天历史| CHAT_HISTORY["chat:history:{userId}"]
    CHAT_HISTORY -->|后续对话 AI 感知| AI_CONTEXT["大模型感知用户情绪背景"]

    %% WakeUp → Anchor → DB
    WAKEUP_SENT[WakeUp 发送] --> RECORD_DB["WakeUpTracker.recordSent<br/>(Redis)"]
    ANCHOR_EVENTS[锚点事件] --> ANCHOR_DB["emotion_anchor_events 表<br/>(MySQL)"]

    %% Sum
    subgraph Legend["系统协作说明"]
        L1["Emotion: 情绪引擎 - 持续计算 PAD 数值"]
        L2["Anchor: 锚点系统 - 情绪波动触发 → 监测 → 结束 → 语义化"]
        L3["WakeUp: 主动唤醒 - 30分钟心跳 → 3路生成 → 3路评分 → 仲裁 → A/B → 发送"]
    end
```

---

## 五、数据流汇总

### 5.1 核心 Redis Key 一览

| Key 模式 | 类型 | 用途 | TTL |
|---|---|---|---|
| `user:emotion:{userId}` | Hash | PAD 情绪状态 | 7天 |
| `user:personality:{userId}` | String | OCEAN 人格 | 30天 |
| `user:emotion-config:{userId}` | String | 情绪引擎参数 | 30天 |
| `wakeup:processing:{userId}` | String | 处理锁（防止并行） | 600s |
| `wakeup:record:{userId}:yyyyMMdd` | String | 唤醒发送记录列表 | 永久 |
| `lock:emotion:{userId}` | Redisson Lock | 情绪更新分布式锁 | - |
| `chat:history:{userId}` | List | 聊天历史（含锚点摘要） | 永久 |
| `anchor:summary:{userId}:yyyy-MM-dd` | String | AI 语义化每日缓存 | 24h |

### 5.2 核心 MySQL 表一览

| 表名 | 用途 |
|---|---|
| `emotion_anchor_events` | 锚点事件（含 trigger + end + 语义字段） |
| `user_emotions` | 历史情绪记录快照 |
| `pending_topics` | 未解决话题（悬念池） |
| `conver_messages` | 聊天记录 |
| `conversation_memories` | 日记化记忆 |
