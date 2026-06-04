# Vector Graph RAG (Java)

**Graph RAG with pure vector search — 不需要图数据库。**

这是 [zilliztech/vector-graph-rag](https://github.com/zilliztech/vector-graph-rag) 的 Java 移植，完整实现了 Graph RAG 管线：将文档抽取为知识图谱三元组 → 向量化后存入 Milvus → 查询时通过实体/关系多路检索 + 子图扩展 + LLM 重排序 + 答案生成。

---

## 目录

- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [配置参考](#配置参考)
- [API 说明](#api-说明)
- [管线详解](#管线详解)
- [与 Python 参考实现的差异](#与-python-参考实现的差异)
- [依赖项](#依赖项)

---

## 架构概览

```
                    ┌──────────────────┐
                    │   VectorGraphRAG │  ← 统一入口
                    └────────┬─────────┘
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                   ▼
   ┌────────────┐    ┌──────────────┐    ┌──────────────┐
   │ Triplet    │    │  GraphBuilder│    │  GraphRetriever│
   │ Extractor  │    │  (建图)       │    │  (检索)        │
   └────────────┘    └──────────────┘    └──────────────┘
          │                  │                   │
          ▼                  ▼                   ▼
   ┌────────────┐    ┌──────────────┐    ┌──────────────┐
   │  OpenAI    │    │  MilvusStore │    │  SubGraph    │
   │  Client    │    │  (向量存储)   │    │  (子图扩展)    │
   └────────────┘    └──────────────┘    └──────────────┘
```

### 数据模型

系统维护 **3 个 Milvus 集合**：

| 集合 | 内容 | 元数据（邻接信息） |
|---|---|---|
| `vgrag_entities` | 实体节点 | `relation_ids`, `passage_ids` |
| `vgrag_relations` | 关系边 (subject-predicate-object) | `entity_ids`, `passage_ids`, `subject`, `predicate`, `object` |
| `vgrag_passages` | 原始文档段落 | `entity_ids`, `relation_ids` |

集合名前缀可通过 `collectionPrefix` 配置，用于隔离多组数据。

### 核心流程

**索引阶段：**
```
文档列表 → LLM 三元组提取 → GraphBuilder 构建图结构 →
各节点生成 Embedding → 写入 Milvus (3个并行集合)
```

**查询阶段：**
```
问题 → NER 提取实体 → 实体向量检索 + 关系向量检索 →
子图扩展（多跳邻居）→（可选）逐出策略控制规模 →
LLM 重排序 → 获取关联段落 → 答案生成
```

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.zjkl</groupId>
    <artifactId>vector-graph-rag</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. 设置环境变量

```bash
export OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx

# 可选：配置 Milvus
# export VGRAG_MILVUS_URI=http://localhost:19530
```

### 3. 基础用法

```java
// 默认配置（从 OPENAI_API_KEY 环境变量读取密钥）
VectorGraphRagSettings settings = VectorGraphRagSettings.builder()
        .openaiApiKey(System.getenv("OPENAI_API_KEY"))
        .build();

VectorGraphRAG rag = new VectorGraphRAG(settings);

// 索引文档
rag.addTexts(List.of(
    "Albert Einstein developed the theory of relativity.",
    "The theory of relativity revolutionized our understanding of space and time.",
    "Einstein worked at the Institute for Advanced Study in Princeton."
));

// 查询
QueryResult result = rag.query("What did Einstein develop?");
System.out.println(result.getAnswer());
// → "Albert Einstein developed the theory of relativity."
```

### 4. 完整示例

```java
// 自定义配置
VectorGraphRagSettings settings = VectorGraphRagSettings.builder()
        .openaiApiKey(System.getenv("OPENAI_API_KEY"))
        .milvusUri("./my_graph.db")                // Milvus Lite 本地文件
        .llmModel("gpt-4o")                         // LLM 模型
        .embeddingModel("text-embedding-3-large")    // Embedding 模型
        .embeddingDimension(3072)
        .collectionPrefix("my_project")             // 多数据集隔离
        .entityTopK(30)
        .relationTopK(30)
        .finalTopK(5)
        .build();

VectorGraphRAG rag = new VectorGraphRAG(settings);

// 批量添加文档
rag.addTexts(List.of(
    "The Great Wall of China was built over several dynasties.",
    "The Forbidden City is located in Beijing, China.",
    "Beijing is the capital of China."
));

// 查询
QueryResult answer = rag.query("Where is the Forbidden City?");
System.out.println(answer.getAnswer());

// 查看检索过程
System.out.println("Entity IDs: " + answer.getRetrievalDetail().getEntityIds());
System.out.println("Relation scores: " + answer.getRetrievalDetail().getRelationScores());
System.out.println("Reranked relations: " + answer.getRerankedRelations());
System.out.println("Final passages: " + answer.getPassages());
```

### 5. 带预提取三元组的文档

```java
List<Map<String, Object>> docs = List.of(
    Map.of(
        "id", "doc_001",
        "passage", "Einstein developed relativity at Princeton.",
        "triplets", List.of(
            List.of("Einstein", "developed", "relativity"),
            List.of("Einstein", "worked at", "Princeton")
        )
    )
);

rag.addDocumentsWithTriplets(docs);
// 跳过 LLM 提取步骤，直接进入建图
```

---

## 配置参考

### `VectorGraphRagSettings` 全部配置项

| 字段 | 默认值 | 说明 |
|---|---|---|
| **OpenAI** | | |
| `openaiApiKey` | `OPENAI_API_KEY` env | API 密钥 |
| `openaiBaseUrl` | `""` | 自定义 API 端点（兼容 OpenAI API 的代理） |
| **模型** | | |
| `llmModel` | `"gpt-4o-mini"` | 三元组提取/重排序/答案生成用模型 |
| `embeddingModel` | `"text-embedding-3-large"` | 向量化模型 |
| `embeddingDimension` | `3072` | 向量维度（对应 text-embedding-3-large） |
| **Milvus** | | |
| `milvusUri` | `"./vector_graph_rag.db"` | Milvus 连接 URI（文件路径→Lite；URI→远程） |
| `milvusToken` | `""` | Zilliz Cloud 认证 token |
| `milvusDb` | `""` | 数据库名（Milvus 2.3+ 多数据库支持） |
| `milvusIndexType` | `"AUTOINDEX"` | 索引类型：AUTOINDEX / IVF_FLAT / HNSW 等 |
| `milvusMetricType` | `"IP"` | 距离度量：IP / COSINE / L2 |
| `milvusConsistencyLevel` | `"Bounded"` | 一致性级别：Strong / Bounded / Session / Eventually |
| `collectionPrefix` | `""` | 集合名前缀（多数据集隔离） |
| `entityCollection` | `"vgrag_entities"` | 实体集合名 |
| `relationCollection` | `"vgrag_relations"` | 关系集合名 |
| `passageCollection` | `"vgrag_passages"` | 段落集合名 |
| **检索** | | |
| `entityTopK` | `20` | 实体检索 topK |
| `relationTopK` | `20` | 关系检索 topK |
| `entitySimilarityThreshold` | `0.9` | 实体相似度阈值（低于此值过滤） |
| `relationSimilarityThreshold` | `-1.0` | 关系相似度阈值（-1 = 不过滤） |
| `expansionDegree` | `1` | 子图扩展度数（1或2推荐） |
| `relationNumberThreshold` | `1000` | 扩展关系数超过此值触发逐出策略 |
| `finalTopK` | `3` | 最终返回的段落数 |
| **LLM** | | |
| `llmTemperature` | `0.0` | LLM 温度 |
| `llmMaxRetries` | `3` | LLM 调用最大重试次数 |
| `useLlmCache` | `true` | 启用 LLM 响应缓存（去重） |
| **处理** | | |
| `batchSize` | `32` | Embedding 批大小 |

### 环境变量配置

也支持通过 `VGRAG_` 前缀的环境变量配置（基于 Spring Boot `@Value` 注入）：

```properties
# .env 或 application.properties
VGRAG_LLM_MODEL=gpt-4o
VGRAG_EMBEDDING_MODEL=text-embedding-3-large
VGRAG_MILVUS_URI=http://localhost:19530
VGRAG_MILVUS_TOKEN=your-token
```

---

## API 说明

### `VectorGraphRAG` 主类

#### 构造

```java
// 默认配置（从 OPENAI_API_KEY 环境变量读取）
VectorGraphRAG rag = VectorGraphRAG.createDefault();

// 自定义配置
VectorGraphRagSettings settings = VectorGraphRagSettings.builder()
        .milvusUri("./data.db")
        .build();
VectorGraphRAG rag = new VectorGraphRAG(settings);
```

#### 索引

```java
// 方式1：直接添加文本
ExtractionResult result = rag.addTexts(List.of("text1", "text2"));
// 带自定义 ID
rag.addTexts(List.of("text"), List.of("doc_001"), true);

// 方式2：用 Document 对象（包含 triplets 字段）
List<Document> docs = List.of(
    Document.builder().id("d1").text("Einstein developed relativity.")
            .triplets(List.of(new Triplet("Einstein", "developed", "relativity")))
            .build()
);
rag.addDocuments(docs, false, true);  // extractTriplets=false = 跳过 LLM

// 方式3：预提取三元组
List<Map<String, Object>> docsWithTriplets = List.of(
    Map.of("passage", "...", "triplets", List.of(List.of("s", "p", "o")))
);
rag.addDocumentsWithTriplets(docsWithTriplets);
```

`ExtractionResult` 包含：
- `documents` — 处理后的文档列表
- `entities` — 抽取的全部实体
- `relations` — 抽取的全部关系
- `entityToRelationIds` — 实体→关系 ID 映射
- `relationToPassageIds` — 关系→段落 ID 映射

#### 查询

```java
// 完整查询
QueryResult result = rag.query("What did Einstein develop?");
System.out.println(result.getAnswer());

// 仅获取答案文本
String answer = rag.querySimple("What did Einstein develop?");

// 对比：朴素 RAG（直接搜段落，不用图）
QueryResult naive = rag.queryNaive("What did Einstein develop?", null);
```

`QueryResult` 包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `query` | String | 原始问题 |
| `answer` | String | 生成的答案 |
| `queryEntities` | List\<String\> | 从问题中提取的实体 |
| `retrievedPassages` | List\<String\> | 最终用于生成的段落 |
| `retrievedRelations` | List\<String\> | 初次检索到的关系 |
| `expandedRelations` | List\<String\> | 子图扩展后的关系 |
| `rerankedRelations` | List\<String\> | LLM 重排序后的关系 |
| `retrievalDetail` | RetrievalDetail | 检索详细信息（实体/关系 ID、文本、分数） |
| `rerankResult` | RerankResult | 重排序结果 |
| `evictionResult` | EvictionResult | 逐出策略信息 |
| `subgraph` | SubGraph | 扩展后的子图对象（可用于调试/可视化） |

#### 工具方法

```java
// 知识库统计
Map<String, Integer> stats = rag.getStats();
// → {entities: 12, relations: 15, passages: 5}

// 清空知识库
rag.reset();

// 访问底层组件
Graph graph = rag.getGraph();
MilvusStore store = rag.getStore();
```

### `Graph` 低层级 CRUD

```java
Graph graph = rag.getGraph();

// 创建段落（自动 embedding 入库）
String pid = graph.createPassage("Einstein was born in Germany.", null, List.of(
    new Triplet("Einstein", "was born in", "Germany")
));

// 查询段落
Passage passage = graph.getPassage(pid);

// 相似段落搜索
List<Passage> results = graph.searchPassages("Einstein", 5);

// 更新/删除（级联清理 entity 和 relation 上的引用）
graph.updatePassage(pid, "Updated text", null, null);
graph.deletePassage(pid);
```

### `SubGraph` 调试/可视化

```java
SubGraph subgraph = new SubGraph(store);
subgraph.addEntities(entityIds);      // 种子实体
subgraph.addRelations(relationIds);   // 种子关系
subgraph.expand(2);                   // 2度扩展

// 查看扩展历史
for (Map<String, Object> step : subgraph.getExpansionHistory()) {
    System.out.println(step);
}

// 获取扩展后的节点
List<SubGraphEntity> entities = subgraph.getEntities();
List<SubGraphRelation> relations = subgraph.getRelations();
List<SubGraphPassage> passages = subgraph.getPassages();
```

---

## 管线详解

### 1. 三元组提取（TripletExtractor）

使用 LLM 从文档中抽取 `<实体, 关系, 实体>` 三元组。提示词包含 few-shot 示例：

```
系统: "You are an expert knowledge graph builder..."
用户: "Text: Albert Einstein was born in Ulm, Germany in 1879..."
助手: {"triplets": [["Albert Einstein", "was born in", "Ulm, Germany"], ...]}
用户: "Text: {实际文档}"
```

结果以 JSON 格式返回，解析为 `Triplet` 对象列表，存入 `Document.metadata["triplets"]`。

### 2. 图构建（GraphBuilder）

从文档中的三元组构建邻接图：

- 实体去重：名称经过 `normalizePhrase()`（去除非字母数字 + 转小写）后做 key
- 关系去重：`subject + predicate + object` 拼成文本做 key
- 维护 6 个双向邻接映射：`entity↔relation`, `entity↔passage`, `relation↔passage`

### 3. Milvus 索引

三个集合使用相同的 schema：
- `id`: VARCHAR(64), 主键（UUID 或用户提供）
- `vector`: FLOAT_VECTOR(dim)
- `text`: VARCHAR(65535)
- JSON 动态字段存储邻接元数据

支持 Milvus Lite（本地文件）、远程 Milvus 服务器、以及 Zilliz Cloud。

### 4. 多路检索（GraphRetriever）

查询时并行执行两条检索路径：

**实体路径：**
1. 从问题中提取命名实体（NER LLM 调用）
2. 实体名向量化 → 在 `vgrag_entities` 中搜相似实体
3. 按 `entitySimilarityThreshold` 过滤

**关系路径：**
1. 问题文本直接向量化 → 在 `vgrag_relations` 中搜相似关系
2. 按 `relationSimilarityThreshold` 过滤

### 5. 子图扩展（SubGraph）

从种子实体和关系出发，逐跳扩展：

```
Step 0: 种子实体 → 相连关系（合并到初始关系集）
Degree 1: 当前关系 → 相连实体 → 这些实体的相连关系（新关系）
Degree 2: 再次重复（关系→实体→关系）
最后: 从所有关系中收集关联段落
```

**逐出策略**：当扩展后的关系数超过 `relationNumberThreshold`（默认 1000）时，使用向量相似度对关系重新排序，只保留最相关的 N 个。

### 6. LLM 重排序（LLMReranker）

使用 3 个 few-shot 示例（1-hop、2-hop、3-hop 推理）提示 GPT 从候选关系中选择最相关的 5 条：

```
用户: "I will provide you with a set of relationship descriptions..."
  示例1: 2-hop "When did Lothair II's mother die?"
  示例2: 2-hop "What country is the composer of 'Erta Eterna' from?"
  示例3: 3-hop "Who is the director of the film that won the award also won by 'The Hurt Locker'?"
用户: "Question: {问题}\nRelationship descriptions:\n[id] text\n..."
```

LLM 返回 JSON 格式 `{"thought_process": "...", "useful_relations": ["...", ...]}`。

### 7. 答案生成（AnswerGenerator）

将重排序后选中的段落作为上下文，调用 LLM 生成最终答案：

```
System: "Use the following pieces of retrieved context to answer the question..."
User: "Question: {问题}\n\nContext: {段落1}\n\n{段落2}..."
```

### 8. 混合回退

当图检索产出的段落数不足 `finalTopK` 时，自动回退到朴素向量检索（直接在 `vgrag_passages` 中搜索）来补足，确保返回足够的上下文。

---

## 与 Python 参考实现的差异

| 差异点 | Python (zilliztech) | Java (本实现) | 说明 |
|---|---|---|---|
| NER 失败回退 | 返回空列表 | 返回空列表（已对齐） | Java 原先有整段 query fallback，已移除 |
| 混合检索回退 | `retrieve()` 有 naive 补足 | `query()` 已有 naive 补足（已添加） | 图检索段落不足时自动补 |
| NER TSV 缓存 | 支持 HippoRAG 格式缓存 | 不支持 | 仅评估场景需要 |
| 文档导入器 | `DocumentImporter` 支持 URL/PDF/DOCX | 无，需调用方自行处理 | langchain4j 生态有对应工具 |
| 默认 embedding | `text-embedding-3-large` (3072d) | 已统一为 same | 对齐评估指标 |
| Graph CRUD | 无独立类 | `Graph.java` 提供完整 CRUD + 级联清理 | Java 独有增强 |
| 前端可视化 | React 前端 + FastAPI | 无（纯后端） | Java 版专注后端集成 |
| 部署方式 | `pip install vector-graph-rag` | Maven 依赖 | 语言生态差异 |

---

## 依赖项

| 库 | 用途 |
|---|---|
| Spring Boot 3.5.x | Web 框架（可选，按需引入） |
| Milvus SDK Java 2.6.x | Milvus 向量数据库客户端 |
| Gson 2.13.x | JSON 序列化 |
| Caffeine 3.2.x | LLM 响应缓存 |
| Lombok 1.18.x | 简化 POJO |
| SLF4J 2.x | 日志 |

```xml
<!-- pom.xml 核心依赖 -->
<dependency>
    <groupId>io.milvus</groupId>
    <artifactId>milvus-sdk-java</artifactId>
    <version>2.6.18</version>
</dependency>
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.13.2</version>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.0</version>
</dependency>
```

---

## 构建

```bash
# 编译
mvn clean compile

# 打包
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests
```

---

## 参考

- [zilliztech/vector-graph-rag](https://github.com/zilliztech/vector-graph-rag) — Python 参考实现
- [Milvus 文档](https://milvus.io/docs) — 向量数据库
- [HippoRAG (NeurIPS 2024)](https://arxiv.org/abs/2405.14831) — 本项目的理论基础
