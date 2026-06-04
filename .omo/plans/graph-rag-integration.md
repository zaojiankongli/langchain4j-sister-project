# VectorGraphRAG 实体图集成方案

## TL;DR

> **核心目标**：将 VectorGraphRAG 作为实体级跨会话关联层嵌入 Sister AI 项目，为 AI 提供用户人物关系、情绪因果链、偏好演变的感知能力。
>
> **设计原则**：不替代现有 hybrid search（叙事检索），两者并行互补。
>
> **部署架构**：2 台服务器。应用服务器 2核4G（Java + Redis + MySQL），Milvus 独立服务器（3 个集合）。
>
> **关键数字**：每用户 1000 实体上限 + LRU 淘汰，周级压缩合并，Milvus 远端所有计算不影响应用。
>
> **重要修正**：
> 1. Java 版 VectorGraphRAG 的最终重排不是“纯 IP 数学重排”，而是 **LLM few-shot rerank**。
> 2. 主项目的 DashScope `QwenEmbeddingModel` 返回的向量在现有代码里**没有显式归一化**；而 VectorGraphRAG 的 `EmbeddingClient` 会 `l2Normalize()`。如果两边要共用图集合，主项目写入图向量时必须自行做 L2 归一化。

---

## 一、部署架构

### 服务器拓扑

```
┌─────────────────────┐          ┌──────────────────────────┐
│  应用服务器 (2核4G)   │  network │  Milvus 独立服务器        │
│                      │          │                          │
│  Java (Sister)       │          │  milvus standalone       │
│  Redis               │          │    ├─ memory_store       │
│  MySQL               │  gRPC    │    ├─ vgrag_entities     │
│                      │◄────────►│    ├─ vgrag_relations    │
│  只发 gRPC 请求       │          │    └─ vgrag_passages     │
│  不做 Milvus 计算     │          │                          │
└─────────────────────┘          └──────────────────────────┘
```

### 资源分配（应用服务器 2核4G）

| 组件 | 预估内存 | 说明 |
|---|---|---|
| Java (Sister) | ~1.5 GB | Spring Boot + langchain4j |
| MySQL | ~300 MB | 常驻连接池 + buffer pool |
| Redis | ~100 MB | 去重 set、队列、快照 |
| OS + 其他 | ~800 MB | |
| **余量** | **~1.3 GB** | 预留增长空间 |
| **合计** | **~4 GB** | 稳定运行，后续可扩 |

### Milvus 独立服务器

所有向量操作（insert、AUTOINDEX、search、delete）在远端执行，应用服务器只发 gRPC 请求，**完全不受索引重构影响**。

### 当前已验证的代码结构事实

1. 主项目 native RAG 注入点已经存在：
   - `SummaryMemoryService.buildMemoryBlock()` 负责构造记忆块
   - `SisterChatService.chatWithVoice()` 负责在路由后把记忆块通过 `SystemMessage` 注入
2. 主项目现有 Milvus 写法是“直接构造 `InsertReq` JSON row → `MilvusClientV2.insert()`”，不是通过额外抽象层。
3. `EmotionAnchorService.handleAnchorEnded()` 在锚点结束时先调用 `EmotionAnchorSemanticService.generateSemanticFields()` 完成语义化，再持久化并发布 `AnchorEndedEvent`；这意味着图写入口挂在锚点结束链路上，比只挂在普通摘要链上更自然。
4. 当前项目没有现成的 `AnchorEndedEvent` 监听消费链，新增图监听器不会和既有监听器冲突。

---

## 二、存储设计

### 2.1 实体集合：`vgrag_entities`

| 字段 | 类型 | 说明 |
|---|---|---|
| **id** (主键) | VarChar | `"{userId}:{entityName}"` |
| user_id | VarChar | 用户隔离，建标量索引 |
| text | VarChar | 实体名（如"小王"、"加班"、"烦躁"） |
| type | VarChar | person / emotion / event / activity / preference / place |
| vector | FloatVector(1024) | text-embedding-v3 编码 |
| mention_count | Int64 | 提及次数，LRU 保护依据 |
| first_seen | Int64 | 首次出现时间戳 |
| last_seen | Int64 | 最近出现时间戳 |
| source_ids | VarChar[] | 来源摘要 ID 列表（溯源用） |

**主键说明**：`userId` 在 Milvus collection 内不唯一（一个用户有 N 个实体），不能做主键。主键必须是 `{userId}:{entityName}` 粒度。用户隔离靠 `user_id` 标量字段 + 查询时 filter。

### 2.2 关系集合：`vgrag_relations`

| 字段 | 类型 | 说明 |
|---|---|---|
| **id** (主键) | VarChar | `MD5("{userId}:{subj}:{pred}:{obj}")` |
| user_id | VarChar | 用户隔离 |
| subject | VarChar | 主体实体名 |
| predicate | VarChar | 谓词（导致 / 关联 / 偏好_旧 / 偏好_新 / 社交关系 / 提及） |
| object | VarChar | 客体实体名 |
| relation_type | VarChar | 关系类型标签 |
| vector | FloatVector(1024) | text-embedding-v3 编码，用于 LLM rerank IP 打分 |
| confidence | Float | 置信度 [0, 1] |
| timestamp | Int64 | 关系创建时间 |
| source_id | VarChar | 来源摘要 ID |

### 2.3 段落集合：`vgrag_passages`

| 字段 | 类型 | 说明 |
|---|---|---|
| **id** (主键) | VarChar | UUID |
| user_id | VarChar | 用户隔离 |
| text | VarChar | 来源原文段落 |
| entity_names | VarChar[] | 该段涉及的所有实体名 |
| source_type | VarChar | anchor / summary |

**不做 embedding，纯标量存储。** 仅用于溯源（用户问"你怎么知道的"时反查原文）。

### 2.4 用户隔离机制

```
三个集合都所有用户共享。

隔离方式：
  写入：每条记录携带 user_id 字段
  查询：filter "user_id == '{userId}'"
  主键：{userId}:{name} 保证不同用户同名不冲突

不需要为每个用户创建独立 collection。
Milvus collection 元数据在 5000+ 个时会有性能问题。
```

---

## 三、写路径

### 3.1 触发链路

```
用户对话 → EmotionEngine → EmotionAnchorService.handleAnchorEnded()
  ├─ EmotionAnchorSemanticService.generateSemanticFields() 完成锚点语义化
  ├─ 回写 EmotionAnchorEvent（summary / triggerReason / highlightTraits / endReason / aiReflection 已齐）
  ├─ 发布 AnchorEndedEvent
  └─ ★ 图监听器消费 AnchorEndedEvent → 实体提取 → 入图

并行保留现有链：
  generateSummaryAsync()（已有，@Async）
    └─ 继续负责 memory_store 的摘要记忆，不承担图层主写入口
```

### 3.2 去重策略（不靠时间窗口限流）

**防线 1：内容 hash 比对**

```
输入原文 = anchor.summary + anchor.triggerReason + anchor.endReason
         + anchor.aiReflection + anchor.highlightTraits

提取前：
  MD5 hash 输入原文
  Redis GET graph:lasthash:{userId}
  
  相同 → 跳过（内容无变化）
  不同 → 调 LLM 抽取 → SET graph:lasthash:{userId}

不设 SETNX 时间窗口，因为 hash 去重已经拦截了重复提取。
```

**防线 2：RapidFire 检测**

```
同一用户 30 秒内触发 ≥3 次提取请求（hash 都不同）
  → 怀疑刷接口
  → Redis SET graph:ratelimit:{userId} ttl=300s
  → 记录告警日志
  → block 期间跳过提取
```

### 3.3 抽取与写入

```
LLM 抽取（qwen3.5-flash，异步 ~300ms）：

  输入：锚点摘要文本
  输出：三元组列表 [{subject, predicate, object, confidence}, ...]

  示例输入：
    "今天小王又在加班到很晚，回来的时候心情很差。他说项目 deadline 压得太紧了。后来我们一起听了一会儿爵士乐，他才好一点。"

  示例输出：
    [{subj:"小王", pred:"经历", obj:"加班", type:"person->event"},
     {subj:"加班", pred:"导致", obj:"烦躁", type:"event->emotion"},
     {subj:"烦躁", pred:"缓解方式", obj:"听爵士乐", type:"emotion->activity"}]
```

```
批量写入（异步线程）：

  每条三元组 →
    LPUSH graph:queue <serialized_json>
  
  每累积 50 条 或 每 5 分钟 →
    LRANGE → batch insert to Milvus
    → flush() 强制封口 segment 立即建索引

  写入向量规范：
    图集合使用 IP 作为 metric type
    因此主项目在写入 vgrag_* 集合前必须自行对 dense embedding 做 L2 归一化
    这样才与 VectorGraphRAG / Milvus 的 IP 语义保持一致

  flush 理由：
    我们的场景是微批次写入（50条/5-10分钟），不是流式高频。
    flush 后数据即刻索引，代价仅 ~30ms（在异步线程中，不阻塞用户）。
    暴力搜索窗口完全消除。
```

### 3.4 实体/关系去重

```
实体去重：
  Milvus upset 按 pk
  pk = "{userId}:{normalizedEntityName}"
  同用户同名实体 → upsert 更新 mentionCount、lastSeen

关系去重：
  pk = MD5("{userId}:{subj}:{pred}:{obj}")
  先查 -> 存在则跳过（不重复插入）
  不存在 -> insert
```

---

## 四、读路径

### 4.1 对话主线程（0 额外延迟）

```
chatWithVoice()
  ├─ hybrid search（已有，搜记忆摘要）
  ├─ Redis GET graph:snapshot:{userId}（快照，如有可用的）
  └─ 不做即时图查询
```

对话路径不自带图查询。实体上下文通过两种方式进入 AI：

### 4.2 快照注入（主动感知）

**在对话开始时，Redis 有快照就注入 AI 的 system prompt，没有就不注入。不阻塞。**

```
Redis 存：
  graph:snapshot:{userId}          → String: LLM 编译的实体关系摘要
  graph:snapshotVersion:{userId}   → String: 版本号（等价于快照编译时的数据版本）
  graph:lastWriteBatch:{userId}    → String: 最新一次写入的 batch ID

对话开始时检查：
  GET graph:snapshotVersion:{userId}
  GET graph:lastWriteBatch:{userId}
  
  snapshotVersion == lastWriteBatch → 快照是最新的，直接注入（0 延迟）
  snapshotVersion != lastWriteBatch → 数据有更新，快照过期
    └─ 距上次重建 > 1 小时 → 异步触发重建，对话先用旧快照
    └─ 距上次重建 < 1 小时 → 用旧快照，不阻塞
```

### 4.3 显式查询（用户主动问人/事时）

```
用户："小王最近怎么样？"
  → RagRouter: needSearch=true + needGraphSearch=true

  双路并行：
    路 A: hybrid search（已有）→ 搜到含"小王"的摘要
    路 B: ★ 图查询（新增）
      ┌─ entities: search filter "user_id == '{userId}' and text like '小王'"
      ├─ 命中 → relations: filter "user_id == '{userId}' and subject == '小王'"
      ├─ LLM rerank（few-shot 选择最有用关系，不是纯 IP 排序）
      ├─ 拼成实体块（~200 tokens）
      └─ 合并到 memory block
      
  总耗时 ~30ms（纯 Milvus search + filter，无额外 LLM）
```

---

## 五、压缩与淘汰策略

**周级定时任务，每周日凌晨 3 点执行（@Scheduled）。**

### ① 近似实体合并

```
遍历全量用户（按 batch 分页）：
  对每个用户的所有实体：
    编辑距离 < 3 且 type 相同
      → 保留 mentionCount 最高的
      → 被删实体的关系引用 → 改指向保留实体
      → 删除冗余实体
```

### ② LRU 淘汰（阈值触发，不到不淘汰）

```
每次 batch 写入后检查：
  Milvus count(filter "user_id == '{userId}'") > 1000 ?
  
  否 → 不动
  是 → 查询该用户所有实体，按 last_seen 升序排序
       删除最旧的直到回到 800（保留 200 缓冲空间）
       
       保护规则（永不淘汰）：
         mentionCount >= 5：说明是核心社交对象
         type = "person"：人物实体全部保护
```

### ③ 清理孤立关系

```
删除实体时级联删除：
  查询 relations where subject == 被删实体名 or object == 被删实体名
  → 批量 delete from Milvus
```

---

## 六、快照重建（版本号控制）

### 触发条件

```
懒触发，不轮询。

任何以下事件发生后：
  ┌─ 新 batch 写入完成
  ├─ 周级压缩合并完成
  └─ LRU 淘汰完成

都会更新 Redis graph:lastWriteBatch:{userId}

对话开始时版本号不等 → 触发条件
但触发不等于立即执行——有节流：
  └─ 距上次重建 > 1 小时 → 执行
  └─ 距上次重建 < 1 小时 → 跳过，下次再检查
```

### 重建内容

```
输入：该用户所有活跃实体 + 关系（前 20 条按 mentionCount + timestamp 加权排序）
LLM 编译：利用 qwen3.5-flash 生成自然语言摘要
  示例输出：
    "你最近经常提到小王，他是你的同事，经常加班导致情绪烦躁。
     加班后你倾向于听爵士乐缓解。"

  → SET graph:snapshot:{userId}
  → SET graph:snapshotVersion:{userId} = 当前版本号

每天每活跃用户最多 1 次快照重建。
10000 DAU → 10000 次/天 × 0.005元 = 50 元/天。
```

### 版本号判断

```
版本号 = lastWriteBatch（每次写入的单调递增 ID）
快照编译完成后 snapshotVersion 更新为该写入 ID

一致检查（O(1) Redis GET × 2）：
  snapshotVersion == lastWriteBatch → 最新
  snapshotVersion != lastWriteBatch → 过期
```

---

## 七、关键指标

| 指标 | 预估值 |
|---|---|
| 每用户实体上限 | 1000（确认提高） |
| 10000 用户全量 Milvus 数据 | ~1.6 GB（远端，不影响应用） |
| 对话路径额外延迟 | 0 ms |
| 显式图查询延迟 | ~30 ms（纯 Milvus，无 LLM） |
| 批量写入延迟 | ~40 ms（insert + flush，异步线程） |
| 快照重建频率 | 每活跃用户最多 1 次/天（懒触发 + 节流） |
| LLM 每日调用 | ~3次/用户（抽取）+ ~1次/活跃用户（快照） |
| 索引行为 | batch insert → flush → 即刻索引，暴力搜索窗口=0 |
| 周级压缩 | 每周日凌晨 3 点，近似合并 + LRU + 孤清理 |
| 归档 | 不归档，靠压缩合并覆盖旧关系 |

---

## 八、不做清单

| 不做 | 原因 |
|---|---|
| 纯 Redis 存图替代 Milvus | 需要向量做 LLM rerank，Milvus 已在独立服务器 |
| 规则引擎取代 LLM 抽取 | 中文因果表达多变，规则漏抽严重 |
| MySQL relation_archive 归档表 | 旧关系靠周级压缩合并处理，不搬走 |
| SETNX 时间窗口限频 | 内容 hash 去重 + RapidFire 足够 |
| 替代 hybrid search | 两者分工不同，不替代 |
| 对话主线程查图 | 0 延迟增长，维持现有响应速度 |
| 定期全量重建索引 | AUTOINDEX + flush 已持续维护 |

## 九、最终实现路径（已收口）

### 9.1 结论

**不把 `VectorGraphRag/` 作为主项目 Maven 依赖接进运行链。**

主项目直接复用自己现有的：
- `MilvusClientV2`
- `EmbeddingModel`
- `QwenChatModel`
- `PromptTemplateService` / `PromptCacheService`
- `StringRedisTemplate`

自己实现一层轻量 graph 服务，覆盖：
1. 锚点事件 → 三元组抽取 → 图写入
2. 图 snapshot 编译与缓存
3. 显式图查询（query-entity / search relations / LLM rerank）

### 9.2 为什么不直接依赖本地 Java 版 VectorGraphRAG

1. **模块边界不匹配**
   - `VectorGraphRag/` 是独立 Maven 工程，不在主项目 `pom.xml` 的依赖树里。
   - 直接接入会引入本地 jar 安装、版本同步、双生命周期管理。

2. **写路径模型不匹配**
   - `VectorGraphRAG.addDocuments()/addTexts()` 当前是批处理模式，会 `dropCollections() + createCollections(true)`。
   - 主项目需要的是“增量、多租户、长期在线写入”，不能走这条入口。

3. **查询链路可借鉴，但不必强耦合**
   - Java 版 VectorGraphRAG 的关键能力本质上就是：NER → embedding → entity/relation search → subgraph expand → LLM rerank。
   - 这些能力主项目已经具备同等基础设施，只需轻量实现，不需要把整个独立模块拉进主运行链。

4. **filter 行为在本地 Java port 中不够直接**
   - 上游 Python API 明确支持 `filter`，但本地 Java port 的 filter 主要围绕 passage 层传递。
   - 主项目自实现查询器后，可直接在 entity/relation 查询层加 `user_id` filter，行为更可控。

### 9.3 代码级改动清单（最终版）

#### 新增文件

1. `src/main/java/com/zjkl/memory/constant/GraphRedisKeys.java`
   - 图层 Redis key 前缀与 TTL 常量
   - 包含：`graph:lasthash:`、`graph:ratelimit:`、`graph:snapshot:`、`graph:snapshotVersion:`、`graph:lastWriteBatch:`、`graph:queue:`、`graph:knownUsers`

2. `src/main/java/com/zjkl/memory/config/GraphMilvusCollectionManager.java`
   - 启动时创建 / load 三个图集合
   - schema 显式字段：
     - entities: `id`, `user_id`, `text`, `type`, `vector`, `mention_count`, `first_seen`, `last_seen`
     - relations: `id`, `user_id`, `text`, `subject`, `predicate`, `object`, `relation_type`, `vector`, `confidence`, `timestamp`, `source_id`
     - passages: `id`, `user_id`, `text`, `source_type`
   - 其余数组字段（如 `source_ids`, `entity_names`）继续走 dynamic fields

3. `src/main/java/com/zjkl/memory/service/GraphEntityService.java`
   - 核心图写服务
   - 负责：
     - anchor 语义字段 hash 去重
     - RapidFire 检测
     - LLM 三元组抽取（依赖 `QwenChatModel`）
     - 向量归一化 + Milvus batch insert/upsert + flush
     - 用户实体数阈值检查与 LRU 淘汰
     - 孤立关系清理
     - 记录 `knownUsers`

4. `src/main/java/com/zjkl/memory/service/GraphSnapshotService.java`
   - 负责：
     - 读取图集合、编译 snapshot
     - Redis snapshot/version 读写
     - 懒重建触发（对话前检查版本差）

5. `src/main/java/com/zjkl/ai/chat/service/GraphQueryService.java`
   - 负责显式图查询：
     - query NER（依赖 `QwenChatModel`）
     - embedding 查询 entities / relations
     - user_id filter
     - 1-hop 关系展开
     - LLM few-shot rerank
     - 输出实体关系 memory block

6. `src/main/java/com/zjkl/memory/listener/GraphAnchorListener.java`
   - 消费 `AnchorEndedEvent`
   - 调 `GraphEntityService.ingestAnchorEvent(...)`

#### 修改文件

7. `src/main/java/com/zjkl/ai/chat/service/SisterChatService.java`
   - 在 `chatWithVoice()` 里：
     - 路由后先尝试取 snapshot 并注入
     - 若 `needSearch()` 为 true，再并行/串行补 memory block
     - 若判定是显式回忆/人物事件查询，再附加 graph query block

8. `src/main/java/com/zjkl/ai/chat/service/RagRouter.java`
   - 继续复用现有路由，不额外新增 `needGraphSearch` 字段
   - 图查询触发策略改为本地轻量规则判断（例如包含“记得/上次/小王/那件事/谁/怎么回事”等，并结合 snapshot/实体命中）
   - 这样避免修改 AiServices schema 和 prompt 后带来的兼容风险

9. `src/main/java/com/zjkl/memory/config/MilvusClientConfig.java`
   - 无结构变化
   - 复用现有 `MilvusClientV2` bean 给 graph 层使用

10. `src/main/java/com/zjkl/common/config/properties/MilvusProperties.java`
    - 增加 graph collection 名配置项：
      - `graphEntityCollectionName`
      - `graphRelationCollectionName`
      - `graphPassageCollectionName`

11. `src/main/resources/application.yml`
    - 增加 `app.milvus.graph-entity-collection-name`
    - 增加 `app.milvus.graph-relation-collection-name`
    - 增加 `app.milvus.graph-passage-collection-name`

12. `src/main/resources/prompts/graph-triplets.txt`
    - 锚点事件 → 三元组抽取 prompt

13. `src/main/resources/prompts/graph-query-entities.txt`
    - 从用户问题中抽关键实体名的 prompt

14. `src/main/resources/prompts/graph-rerank.txt`
    - 对候选关系做 few-shot 选择/排序的 prompt

15. `src/main/resources/prompts/graph-snapshot.txt`
    - 把 top entities + relations 编译成自然语言 snapshot 的 prompt

### 9.4 实施顺序

1. 先建 `GraphRedisKeys` + `MilvusProperties` + `application.yml`
2. 再建 `GraphMilvusCollectionManager`（启动时保证 3 个集合就绪）
3. 再做 `GraphEntityService`（写路径 + flush + LRU）
4. 再做 `GraphAnchorListener`（把写入口挂到 `AnchorEndedEvent`）
5. 再做 `GraphSnapshotService`
6. 再做 `GraphQueryService`
7. 最后改 `SisterChatService` 接入 snapshot + graph query

### 9.5 每一步验证要求

1. 新增/修改一个服务后立刻：
   - 跑 `mvn -q -DskipTests compile`
   - 检查相关文件诊断（若 LSP 不可用，则以编译结果为准）
2. 图集合初始化完成后：
   - 启动应用日志确认三个集合就绪
3. 图写入完成后：
   - 用最小 driver 或接口调用触发一次 anchor 结束
   - 查询 Milvus 中该用户的 entity/relation/passage 是否存在
4. 接入 chat 后：
   - 真实调用 `chatWithVoice()` 验证 snapshot 注入和显式图查询块
