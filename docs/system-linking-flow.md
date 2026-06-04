# 三大系统联动流程图

```mermaid
flowchart TD
    subgraph E[Emotion 情绪引擎]
        E1["EmotionService<br/>PAD 计算/衰减/回归"]
        E2["EmotionDecayScheduler<br/>每30分钟衰减"]
        E3["EmotionRecordScheduler<br/>每天4次记录"]
    end

    subgraph A[Anchor 锚点系统]
        A1["EmotionAnchorMonitor<br/>状态机: IDLE↔MONITORING"]
        A2["EmotionAnchorService<br/>触发Insert+结束Update"]
        A3["EmotionAnchorSemanticService<br/>Qwen语义化"]
        A4["pending_topics<br/>悬念池"]
    end

    subgraph W[WakeUp 主动唤醒]
        W1["WakeUpScheduler<br/>每30分钟心跳"]
        W2["Generator ×3<br/>并行生成问候"]
        W3["Scorer ×3<br/>并行评分"]
        W4["Arbiter<br/>仲裁选最优"]
        W5["WakeUpTracker<br/>A/B测试+记录"]
        W6["TTS + WebSocket<br/>语音推送"]
    end

    subgraph DB[(Redis + MySQL)]
        DB1["user:emotion:{userId}<br/>PAD 实时情绪"]
        DB2["emotion_anchor_events<br/>锚点事件持久化"]
        DB3["chat:history:{userId}<br/>对话历史+锚点摘要"]
        DB4["wakeup:record:{userId}:date<br/>唤醒发送记录"]
    end

    %% ---- 核心联动 A ----
    E1 -->|"EmotionChangedEvent"| A1
    A1 -->|"ΔP > 0.15 触发"| A2
    A2 -->|"锚点结束"| A3
    A3 -->|"负向结束"| A4
    A3 -->|"摘要注入"| DB3
    A2 --> DB2

    %% ---- 核心联动 B ----
    A1 -->|"activeAnchorContext"| W["WakeUp<br/>buildAnchorHint"]
    A4 -->|"getSuspenseTopics"| W2
    E1 -->|"moodDescription"| W2
    E1 -->|"moodScore"| W3
    DB3 -->|"对话上下文"| W2

    %% ---- 核心联动 C ----
    W6 -->|"用户回复"| E1["情绪变化"]
    W5 --> DB4

    %% ---- 数据流转 ----
    E1 --> DB1
    DB1 -->|"getUserEmotion"| E1
    DB1 -->|"getUserEmotion"| W

    %% ---- 时间线 ----
    TIMELINE>情绪驱动锚点, 锚点驱动唤醒素材, 唤醒驱动新对话, 新对话驱动情绪变化]
```

```mermaid
sequenceDiagram
    participant Chat as 用户对话
    participant Emo as EmotionService
    participant Mon as AnchorMonitor
    participant Anc as AnchorService
    participant Sem as SemanticService
    participant Wake as WakeUpScheduler
    participant Gen as Generator Agent
    participant TTS as TTS+WebSocket

    Note over Chat,Wake: 30分钟周期内各系统交互时序

    Chat ->> Emo: 发送消息
    Emo ->> Emo: updateUserEmotion(ΔP,ΔA,ΔD)
    Emo ->> Mon: onEmotionChange(old, new)

    alt |ΔP| > 0.15
        Mon ->> Mon: IDLE → MONITORING
        Mon ->> Anc: handleAnchorTriggered
        Anc ->> Anc: INSERT emotion_anchor_events
    end

    Note over Mon,Wake: 监测期间后续情绪变化持续评估

    alt 回归/沉默/超时
        Mon ->> Mon: MONITORING → IDLE
        Mon ->> Anc: handleAnchorEnded
        Anc ->> Sem: generateSemanticFields
        Sem ->> Sem: Qwen AI 生成语义字段
        Sem ->> Anc: UPDATE emotion_anchor_events
        Anc ->> Anc: 注入摘要到 chat:history
    end

    Note over Wake,TTS: 每30分钟

    Wake ->> Wake: checkUsersForWakeUp
    Wake ->> Emo: getUserEmotion(userId)
    Emo ->> Wake: moodDescription, moodScore
    Wake ->> Mon: getAnchorContext
    Mon ->> Wake: activeAnchorContext
    Wake ->> Gen: 并行3路生成
    Gen ->> Gen: searchMemories/getRecentChat/getSuspenseTopics
    Gen ->> Wake: 3条候选消息
    Wake ->> Wake: 并行3路评分 + 仲裁
    Wake ->> TTS: 最终消息
    TTS ->> Chat: 推送文本+语音

    Chat ->> Emo: 用户回复(新一轮)
```

```mermaid
flowchart LR
    subgraph DataFlow[关键数据流转]
        DIR1["Emotion: PAD 数值 → <br/>心情描述 → WakeUp 生成素材"]
        DIR2["Anchor: 锚点状态 → <br/>WakeUp 关心提示"]
        DIR3["Anchor: 锚点摘要 → <br/>chat:history → <br/>后续对话 AI 感知"]
        DIR4["Anchor: 负向结束 → <br/>pending_topics → <br/>WakeUp 取用悬念话题"]
        DIR5["WakeUp: 唤醒消息 → <br/>用户回复 → <br/>新一轮情绪变化"]
    end

    DataFlow --> CORE>一个循环闭环:<br/>情绪触发锚点 → 锚点积累素材 → 唤醒使用素材 → 对话产生新情绪]
```
