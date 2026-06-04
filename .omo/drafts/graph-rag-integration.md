# Draft: VectorGraphRAG 图集成方案（整合版）

## 一、核心定位

不替代现有 hybrid search。hybrid search 负责叙事检索（对话摘要全文），graph 负责实体级跨会话关联。两者并行互补。

## 二、部署架构

| 服务器 | 规格 | 承载 |
|---|---|---|
| 应用服务器 | 2核4G（后续可扩） | Java (Sister)、Redis、MySQL |
| Milvus 独立服务器 | 自带资源 | Milvus standalone，3个集合 |

Milvus 所有计算（insert、索引、compact、delete）都在远端执行，应用服务器只发 gRPC 请求，不受影响。

## 三、存储设计

### 3 个 Milvus 集合（所有用户共享）

**① vgrag_entities**
```
主键:          "{userId}:{entityName}"          ← userId 不能唯一标识一行，必须加 entityName
标量字段:      user_id, type, mentionCount, firstSeen, lastSeen, source_ids[]
向量:          dense-1024（用于 LLM rerank 时的相似度计算）
用户隔离:      filter "user_id == '{userId}'"
主键作用:      仅防止不同用户同名实体冲突
```

**② vgrag_relations**
```
主键:          MD5("{userId}:{subj}:{pred}:{obj}")
标量字段:      user_id, subject, predicate, object, active, source_id, timestamp, confidence
向量:          dense-1024（LLM reranker 用 IP 打分排序）
active:        旧关系 active=false，不删、query 排除
```

**③ vgrag_passages**
```
主键:          UUID
标量字段:      user_id, text, entity_names[], source_type
无向量:       不进 embedding，纯标量存储用于溯源
```

## 四、写路径

### 触发时机
每个锚点事件保存后，`generateSummaryAsync()` 内异步追加

### 去重防线（不靠时间窗口限流）
```
防线1 - 内容 hash：
  输入 = anchor summary + triggerReason + endReason + aiReflection
  计算 MD5 hash → GET graph:lasthash:{userId}
  hash 相同 → 跳过（内容无变化）
  hash 不同 → 提取 → SET graph:lasthash:{userId}

防线2 - RapidFire 检测：
  30 秒内同一用户触发 ≥3 次提取请求（hash 都不同）
  → 怀疑刷接口 → 300s block → 记录告警日志
```

### 写操作
```
LLM 抽取三元组（qwen3.5-flash，~300ms，异步）
  → 每条三元组入 Redis List（batch 累计后批量 insert Milvus）
  → 同用户同实体名去重（Milvus upsert 按 pk）
  → 同用户同关系去重（MD5 判重，新关系 insert，重复跳过）
```

## 五、读路径

### 对话主线程（0 额外延迟）
不做快照注入，不做即时图查询

### 显式查询（用户问具体人/事时才触发）
```
RagRouter 判定 needGraphSearch=true
  → Milvus query:
     ① entities 集合: filter "user_id == '{userId}' and text == '{实体名}'"
     ② relations 集合: filter "user_id == '{userId}' and subject == '{实体名}'"
     ③ 结果做 LLM rerank（IP 相似度排序）
     ④ 拼成 memory block
     总耗时 ~30ms（纯 Milvus 操作，无额外 LLM）
```

### 快照注入（保留，加版本号控制）
```
Redis 存：
  graph:snapshot:{userId}         → String: LLM 编译的活跃实体摘要
  graph:snapshotVersion:{userId}  → String: 编译时的版本号
  graph:lastWriteBatch:{userId}   → String: 最近一次写入的 batch ID

对话前检查：
  snapshotVersion != lastWriteBatch → 触发懒重建（距上次重建 > 1h 才重建）
  snapshotVersion == lastWriteBatch → 直接用，0 延迟
```

## 六、压缩策略（周级，每周日凌晨 3 点）

### ① 近似实体合并
```
编辑距离 < 3 且 type 相同 → 保留 mentionCount 高的
关系引用被删实体的 → 改指向保留实体
```

### ② LRU 淘汰（阈值触发，不到不淘汰）
```
每 batch 写入后检查 count({userId}) > 500
  → 是：删除最久未提及实体，回到 400
       保护条件：mentionCount >= 5 或 type=person 不删
  → 否：不动
```

### ③ 清理孤立关系
```
关联实体已被删除的关系 → 一并从 Milvus 删除
```

## 七、关键数字

| 指标 | 预估值 |
|---|---|
| 每用户实体上限 | 500 |
| 每用户关系上限 | 1000 |
| 10000 用户总数据量 | ~1.6GB（Milvus 远端，不影响应用） |
| 对话路径额外延迟 | 0ms |
| 显式图查询延迟 | ~30ms |
| 快照重建频率 | 每个活跃用户最多 1 次/小时（懒触发） |
| LLM 额外调用 | 每天每活跃用户 ~3 次（提取）+ 显式查询时 ~1 次（rerank） |

## 八、不做的

- ~~纯 Redis 替代 Milvus 存图~~（需要向量做 rerank，Milvus 已在独立服务器）
- ~~MySQL relation_archive 归档表~~（旧关系标记 active=false 不搬走）
- ~~规则引擎取代 LLM 抽取~~（中文因果表达多变，规则漏抽严重）
- ~~时间窗口 SETNX 限频~~（内容 hash 去重 + RapidFire 替代）
