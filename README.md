# 企业级 RAG 知识引擎（项目 7）

> 一个能扛住生产压力的 **SaaS 级智能知识问答平台**：员工（或客服、游客）用大白话提问，系统从知识库中**混合检索**出相关文档片段，再让大模型**基于检索结果**生成有据可查的回答，全程由 **治理层**（Failover / 限流熔断 / Token 计量 / RBAC）和 **评测层**（RAGAS）保驾护航，并用 **SkyWalking + Prometheus/Grafana** 实现全链路可观测。

**当前演示语料**：旅游攻略知识库（12 篇：西藏攻略、签证指南、装备清单、自驾路线、潜水考证等），包装场景为"旅行社客服知识库"。

---

## 目录

- [1. 项目背景与痛点](#1-项目背景与痛点)
- [2. 系统架构](#2-系统架构)
- [3. 核心数据流](#3-核心数据流)
- [4. 技术栈与工具](#4-技术栈与工具)
- [5. 快速开始（完整启动手册）](#5-快速开始完整启动手册)
- [6. 各服务详解与核心代码](#6-各服务详解与核心代码)
- [7. 遇到的问题与解决方案（踩坑记录）](#7-遇到的问题与解决方案踩坑记录)
- [8. 答辩演示脚本](#8-答辩演示脚本)
- [9. 安全说明](#9-安全说明)
- [10. 后续改进方向](#10-后续改进方向)

---

## 1. 项目背景与痛点

企业内部文档散落在各个系统、网盘里，员工找制度、流程、答案要翻半天，还经常找不到。传统搜索只能做**关键词匹配**，问"入职满一年能休几天年假"（换成旅游场景就是"去西藏要准备什么证件"）这种**自然语言问题**就失灵了。

直接问 ChatGPT 类大模型又存在致命问题：**一本正经地瞎编（幻觉）**，答案没有出处。

**RAG（Retrieval-Augmented Generation，检索增强生成）** 正是解法：先检索企业自己的文档，再让模型基于检索结果回答——**答案有出处、可溯源**。

但把 RAG 系统真正"上生产"，还需要解决一系列工程问题（见下节）。本项目的定位是**整合项目**：把微服务、LLM 治理、高级 RAG、自动评测、可观测性等能力用生产级标准重新组织成一个完整系统。

### 本项目解决的问题清单

| # | 生产问题 | 解决方案 | 所在组件 |
|---|---------|---------|---------|
| 1 | LLM 供应商单点故障（宕机/限流/超时/涨价） | 多供应商 Failover + 健康检查摘除 | ai-service |
| 2 | 雪崩效应（下游慢拖垮上游） | 限流 + 熔断 + 降级 + 重试四件套 | ai-service / doc-service |
| 3 | 成本失控（token 花了多少没数） | Token 计量（Redis 存储 + 成本报表） | ai-service |
| 4 | 越权访问 | RBAC 双层：网关过滤 + 服务内注解 AOP | gateway / doc-service |
| 5 | 文档改错无法回溯 | 追加式版本链 + 回滚 | doc-service |
| 6 | 自然语言问不到（关键词失灵） | 混合检索：关键词 + 向量加权融合 | search-service |
| 7 | 长文档检索粒度粗 | 文档分块（400 字符 + 50 重叠） | search-service |
| 8 | 上传与索引强耦合 | Kafka 事件驱动异步建索引 | doc-service / search-service |
| 9 | 大模型幻觉 | RAG 本身 + RAGAS faithfulness 指标检测 | 全链路 |
| 10 | 回答质量无法量化 | RAGAS 三指标自动评测（LLM 当裁判） | metric-service |
| 11 | 微服务慢在哪一跳不知道 | SkyWalking 全链路追踪（含跨语言） | 基础设施 |
| 12 | 整体趋势看不见 | Prometheus + Grafana 指标曲线 | 基础设施 |
| 13 | 评测阻塞用户请求 | 离线旁路设计：异步提交 + 轮询结果 | metric-service |

---

## 2. 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                      企业级 RAG 知识引擎                        │
├──────────────────────────────────────────────────────────────┤
│  前端 frontend（纯静态 HTML+JS，8099）                          │
│        │ 全部请求走网关（CORS 已配置）                           │
│        ▼                                                      │
│  网关 gateway（8080：统一入口 + 路由 + RBAC 鉴权）               │
│        ├─ /doc/**    → doc-service（8082：上传/解析/版本化）     │
│        ├─ /ai/**     → ai-service（8081：RAG 编排 + LLM 治理）  │
│        └─ /search/** → search-service（8083：混合检索 + 建索引） │
│                                                              │
│  metric-service（8090，Python）：RAGAS 评测，不进 Nacos，直连    │
├──────────────────────────────────────────────────────────────┤
│  治理层：Failover / 限流熔断 / Token 计量 / RBAC / 版本管理       │
│  评测层：RAGAS（RAG Triad 三指标 + LLM 自动出题）                │
│  运维层：SkyWalking + Prometheus/Grafana + Kafka + Docker       │
└──────────────────────────────────────────────────────────────┘
```

### 基础设施清单

| 组件 | 端口 | 部署方式 | 用途 |
|------|------|---------|------|
| Nacos | 8848/9848 | Docker | 服务注册与发现 |
| MySQL | 3306 | 本机 | doc_db（文档+版本）、search_db（分块） |
| Redis | 6379 | Docker | Token 计量存储 |
| Kafka | 9092 | Docker（KRaft 单节点） | 文档索引事件 |
| Ollama | 11434 | 本机 | 备用 LLM 供应商（qwen2.5:7b） |
| SkyWalking OAP+UI | 11800/12800/18080 | Docker | 链路追踪 |
| Prometheus | 9090 | Docker | 指标采集 |
| Grafana | 3000 | Docker | 指标可视化 |
| 前端静态页 | 8099 | Python http.server | 演示界面 |

---

## 3. 核心数据流

### 3.1 文档入库流（写路径，事件驱动）

```
管理员上传（文本 或 txt/md/pdf 文件）
  → gateway（RBAC：需 document:write）
  → doc-service：解析文本 → 存 kb_document（主表）+ kb_document_version（版本1）
  → 发 Kafka 事件「doc-indexed」（发完即返回，不等索引）
  → search-service 消费事件：分块（400字符+50重叠）→ 写 doc_chunk 表
  → 每块调用千问 text-embedding-v4 向量化 → 重建内存索引
```

### 3.2 问答流（读路径，核心链路）

```
用户提问
  → gateway → ai-service（RAG 编排者）
  → Feign 调 search-service 混合检索：关键词 2-gram 打分 × 0.4 + 向量余弦 × 0.6
  → 取 Top-3 分块拼提示词（约束"没有答案就说没有"）
  → 走治理链：限流（RateLimiter）→ 熔断（CircuitBreaker）→ 重试（Retry）
  → Failover 调 LLM（主 DeepSeek/千问 → 备 Ollama，连续失败 3 次摘除 30 秒）
  → 成功：自动记录 Token 到 Redis + Micrometer Timer 记录耗时
  → 返回 {query, contexts, answer, usage}
```

### 3.3 评测流（旁路，异步不阻塞）

```
问答产生四字段（question/contexts/answer/ground_truth）
  → ai-service 直连 metric-service：POST /evaluate（HTTP，不进 Nacos）
  → 立即返回 taskId（热路径零等待）
  → Python 后台线程：裁判 LLM（qwen-plus）打分 RAG Triad
  → 轮询 GET /task/{taskId} → faithfulness / answer_relevancy / context_precision
```

---

## 4. 技术栈与工具

| 类别 | 选型 | 版本 | 说明 |
|------|------|------|------|
| Java | JDK | 17 | 父工程统一管理 |
| 微服务框架 | Spring Boot | 3.2.4 | |
| 微服务生态 | Spring Cloud Alibaba | 2023.0.1.0 | Nacos 注册发现 |
| 服务调用 | OpenFeign + LoadBalancer | — | 声明式调用 + 轮询 |
| 治理 | Resilience4j | — | 限流/熔断/重试 |
| 消息 | Kafka | 3.7（Docker） | 事件驱动 |
| ORM | MyBatis-Plus | 3.5.9 | doc_chunk 等表 |
| 指标 | Micrometer + actuator | — | Prometheus 格式 |
| 追踪 | SkyWalking | OAP 9.7.0 + agent 9.8.0 | 含 OTel 跨语言 |
| 监控 | Prometheus + Grafana | 2.53.0 / 11.1.0 | 指标 + 看板 |
| Python | Python 3.12（venv） | — | metric-service |
| 评测 | RAGAS | 0.1.19 | RAG Triad |
| 跨语言追踪 | OpenTelemetry | 1.45.2 | OTLP gRPC 上报 |
| HTTP 服务 | FastAPI + uvicorn | — | metric-service API |
| 前端 | 原生 HTML/CSS/JS | — | 零构建 |

---

## 5. 快速开始（完整启动手册）

### 5.1 前置环境

- JDK 17、IDEA、Maven 3.9+
- MySQL 8（本机）、Ollama（本机，可选）
- Docker Desktop
- Python 3.12

### 5.2 启动顺序（6 个进程）

```bash
# ① 基础设施（Docker）：Nacos 已单独运行；本项目容器一起起
cd E:\springboot\project
docker compose up -d          # SkyWalking OAP+UI、Prometheus、Grafana、Kafka

# ② Redis（若没起）
docker start redis

# ③ Java 四个服务（IDEA 逐个启动，VM options 挂 SkyWalking agent）
#    gateway(8080) / ai-service(8081) / doc-service(8082) / search-service(8083)
#    每个服务 VM options：
#    -javaagent:E:\springboot\skywalking-agent\skywalking-agent.jar
#    -Dskywalking.agent.service_name=服务名
#    -Dskywalking.collector.backend_service=localhost:11800

# ④ Python 评测服务（cmd）
cd /d E:\python\project\metric-service
.venv\Scripts\activate
python api_server.py           # 8090 端口，黑窗口别关

# ⑤ 前端页面
cd /d E:\springboot\project\frontend
python -m http.server 8099

# ⑥ 浏览器
#    前端：http://localhost:8099
#    SkyWalking：http://localhost:18080
#    Grafana：http://localhost:3000（admin/admin）
#    Nacos：http://localhost:8848/nacos
```

### 5.3 metric-service 首次搭建（Python 环境）

```bash
cd metric-service
python -m venv .venv            # 必须 Python 3.12（3.14 装不上 numpy 1.26）
.venv\Scripts\activate
pip install -r requirements.txt # 版本已全部锁定，直接可用
python api_server.py
```

### 5.4 数据库初始化

- `doc_db`：`kb_document` + `kb_document_version`（见 `docs/travel-docs.sql`）
- `search_db`：`doc_chunk`
- 灌入演示语料：`mysql -uroot -p123456 --default-character-set=utf8mb4 < docs/travel-docs.sql`，然后 `POST http://localhost:8083/search/index/rebuild` 建索引（或上传任意文档，Kafka 会自动建索引）

---

## 6. 各服务详解与核心代码

### 6.1 gateway（8080）

职责：统一入口、路由转发、**RBAC 鉴权**。

```yaml
# application.yml 关键配置
spring:
  cloud:
    gateway:
      globalcors:            # 前端页面跨域
        cors-configurations:
          '[/**]': { allowedOrigins: "*", allowedMethods: "*", allowedHeaders: "*" }
      routes:
        - id: doc-route
          uri: lb://doc-service          # lb:// = 从 Nacos 发现 + 负载均衡
          predicates: [ Path=/doc/** ]
        - id: ai-route
          uri: lb://ai-service
          predicates: [ Path=/ai/** ]
        - id: search-route
          uri: lb://search-service
          predicates: [ Path=/search/** ]
```

**核心代码：RbacAuthFilter**（GlobalFilter，在路由转发前拦截）

```java
@Component
public class RbacAuthFilter implements GlobalFilter, Ordered {
    // 角色 → 权限映射（admin/editor/guest）
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "admin", Set.of("document:read", "document:write", "document:delete"),
            "editor", Set.of("document:read", "document:write"),
            "guest", Set.of("document:read"));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String required = requiredPermission(exchange.getRequest().getMethod(), path);
        if (required == null) return chain.filter(exchange); // 无需鉴权
        String role = ...从请求头 X-User-Role 取，缺省 guest...;
        if (!ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(required)) {
            // 403，请求不进下游
        }
        return chain.filter(exchange);
    }
    // POST /doc/** 需 document:write；GET 需 document:read
}
```

### 6.2 doc-service（8082）

职责：文档上传（文本 / txt / md / **PDF 解析**）、落库版本化、发 Kafka 事件、Text2SQL、RBAC 纵深防御。

**核心代码：上传主流程**（解析 → 主表+版本1 → 发事件 → AI 分析）

```java
private Map<String, Object> doUpload(String title, String content, Map<String, String> body) {
    DocumentVersion saved = versionService.saveNewDocument(title, content); // 主表 + 版本1
    eventService.publishDocumentIndexed(saved.getDocumentId(), 1L, title, content); // Kafka 事件
    return docService.analyzeWithProtection(body); // 调 ai-service（带熔断降级）
}
```

**PDF 解析**（PDFBox 提取文本）：

```java
try (PDDocument doc = PDDocument.load(file.getInputStream())) {
    return new PDFTextStripper().getText(doc).trim();
}
```

**Kafka 生产者**（发完即返回，索引异步完成）：

```java
kafkaTemplate.send(topic, String.valueOf(documentId),
    mapper.writeValueAsString(event)); // topic = doc-indexed
```

### 6.3 search-service（8083）

职责：混合检索、分块管理、Kafka 消费自动建索引、千问向量化。

**核心代码 1：混合检索打分**（HybridSearchService）

```java
public List<SearchResult> search(String query) {
    double[] queryVec = embeddingService.embed(query);      // 千问向量化
    for (DocVector doc : index) {
        double kw = keywordScore(query, doc.content);       // 2-gram 关键词命中率
        double vec = cosine(queryVec, doc.vector);          // 余弦相似度
        double total = keywordWeight * kw + vectorWeight * vec;  // 0.4 / 0.6 加权
        scored.add(new SearchResult(..., kw, vec, total));
    }
    scored.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
    return scored;
}
```

**核心代码 2：Kafka 消费自动建索引**（DocumentIndexService）

```java
@KafkaListener(topics = "${search.kafka-topic}", groupId = "search-service")
public void onDocumentIndexed(String message) {
    // 解析事件 → 删除该文档旧分块 → 按 400 字符/50 重叠重新分块入库 → 向量化 → 重建索引
}
```

**分块策略**：400 字符一块、块间重叠 50 字符，检索单元是 chunk 而非整篇文档。

### 6.4 ai-service（8081）—— 治理层核心

职责：RAG 编排（检索 + 生成）、Failover、限流熔断重试、Token 计量、评测提交、LLM 耗时指标。

**核心代码 1：治理链注解**（GovernedChatService）

```java
@RateLimiter(name = "llmRateLimiter", fallbackMethod = "rateLimitFallback")   // 限流
@CircuitBreaker(name = "llmBreaker", fallbackMethod = "circuitBreakerFallback") // 熔断
@Retry(name = "llmRetry", fallbackMethod = "retryFallback")                   // 重试
public LlmChatResult chat(String message) {
    Timer.Sample sample = Timer.start(meterRegistry);   // 作业：LLM 耗时 Timer
    try {
        return failoverChatService.chat(message);       // 真实 LLM 调用
    } finally {
        sample.stop(Timer.builder("llm_call_duration").tag("status", status)
                .register(meterRegistry));              // 暴露给 Prometheus
    }
}
```

**核心代码 2：Failover + 健康检查**（手写熔断状态机）

```java
// 每个供应商维护 failCount（连续失败次数）与 downUntil（摘除到期时间）
// CLOSED ──连续失败 3 次──▶ OPEN（摘除 30 秒，冷却期直接跳过）
// OPEN ──冷却到期──▶ HALF_OPEN（放行试探一次）──成功──▶ CLOSED
// 全部失败 → 统一 503 兜底（不裸抛堆栈给用户）
```

**核心代码 3：RAG 编排**（RagService，数据流按需求文档）

```java
public Map<String, Object> rag(String query) {
    List<Map<String,Object>> contexts = searchClient.search(Map.of("query", query))...; // Feign 检索
    String prompt = "请严格根据以下上下文回答问题，如果上下文没有答案，请回答\"文档中没有相关信息\"。\n\n"
            + "上下文：\n" + sb + "\n问题：" + query;
    LlmChatResult result = governedChatService.chat(prompt);  // 走治理链
    return ...{query, contexts, answer, usage};
}
```

**Token 计量**：每次 LLM 调用成功后自动 `tokenMeterService.record(provider, tenantId, promptTokens, completionTokens)` 写入 Redis，提供 `/ai/token/cost-report` 成本报表。

### 6.5 metric-service（8090，Python，不进 Nacos）

职责：RAGAS 评测（离线旁路），Java 直连 HTTP 调用。

**核心代码：异步评测 API**（api_server.py）

```python
@app.post("/evaluate")
def create_eval(req: EvalRequest):
    task_id = uuid.uuid4().hex[:12]
    TASKS[task_id] = {"status": "running"}
    threading.Thread(target=_run_eval, args=(task_id, req), daemon=True).start()
    return {"task_id": task_id}   # 立即返回，打分在后台跑 30~60 秒

@app.get("/task/{task_id}")
def get_task(task_id: str):
    return TASKS.get(task_id, {"status": "not_found"})  # 轮询结果
```

**中文适配**（三个关键适配）：

```python
class ChineseSentenceSegmenter:        # RAGAS 只认英文句号，中文需补 "."
    def segment(self, text):
        ...按。！？；切句并补 "."...

def build_metrics():
    return [
        Faithfulness(sentence_segmenter=ChineseSentenceSegmenter()),  # 修复中文 NaN
        AnswerRelevancy(strictness=1),   # DeepSeek/千问只支持 n=1
        ContextPrecision(),
    ]

OpenAIEmbeddings(..., check_embedding_ctx_length=False)  # 千问只收字符串，不收 token ID
```

**跨语言追踪**（otel_setup.py）：OTLP gRPC 上报 SkyWalking 11800，`ragas-evaluate` 手动埋点。

**自动出题**（generate_dataset.py）：LLM 从文档生成问答对，省人工标注。

### 6.6 前端（8099）

纯静态单页（原生 HTML/CSS/JS，零构建）：智能问答、文档管理（含 RBAC 角色切换演示）、检索调试、质量评测（轮询分数卡片）、Token 计量五大页签，全部走网关 8080。

---

## 7. 遇到的问题与解决方案（踩坑记录）

### Python / RAGAS 环境

| # | 问题 | 根因 | 解决 |
|---|------|------|------|
| 1 | numpy 编译失败 `NumPy requires GCC >= 8.4` | Python 3.14 没有 numpy 1.26 的 wheel，pip 源码编译遇老 GCC | venv 重建为 **Python 3.12** |
| 2 | `No module named langchain_community.chat_models.vertexai` | ragas 0.1.x 只兼容 langchain 0.2.x，装到 1.x 不兼容 | requirements 全量锁版本（ragas 0.1.19 + langchain 0.2.17 等） |
| 3 | pyarrow 26 与 numpy 1.26 冲突 | pyarrow 26 要求 numpy≥2，langchain-community 要求 numpy<2 | 锁 `pyarrow==17.0.0` |
| 4 | `Client.__init__() got an unexpected keyword argument 'proxies'` | httpx 0.28 移除了 proxies 参数，openai 1.40 还在传 | 锁 `httpx==0.27.2` |
| 5 | config 全为空报"以下配置为空" | `os.getenv("sk-xxx")` 把值当环境变量名 | 改为 `os.getenv("变量名", "默认值")` |
| 6 | faithfulness 全是 NaN | RAGAS 只保留以英文句号 `.` 结尾的句子，中文句子被全部过滤 | 自定义中文切分器按 `。！？；` 切句并补 `.` |
| 7 | `Invalid n value (only n=1 supported)` | answer_relevancy 默认 strictness=3 → n=3 调 LLM，DeepSeek/千问不支持 | `AnswerRelevancy(strictness=1)` |
| 8 | `input must be an array of strings` | langchain 默认把文本转 token ID 数组发给 embedding 接口，千问不认 | `check_embedding_ctx_length=False` |
| 9 | 老师代码字段 `"questions"` | RAGAS 只认单数 | 改 `question` |

### SkyWalking / 跨语言追踪

| # | 问题 | 根因 | 解决 |
|---|------|------|------|
| 10 | `Method not found: TraceService/Export` | OAP 9.7.0 默认只开 otlp-metrics/logs | 环境变量 `SW_OTEL_RECEIVER_ENABLED_HANDLERS=...,otlp-traces` |
| 11 | `receiver-zipkin missing` | OTLP trace 在 9.7.0 内部转 zipkin 处理，zipkin 接收器默认关 | `SW_RECEIVER_ZIPKIN=default` |
| 12 | 零时长 span 报 UNKNOWN | 9.7.0 zipkin 转换器对 0 时长 span 空指针 | 真实评测 span 有时长不受影响；测试 span 加 `time.sleep(0.1)` |
| 13 | 主 UI(18080) 看不到 OTLP span | 9.7.0 的 OTLP span 存 zipkin 专用存储（源码 `ZipkinSpan.scope()` 硬编码），主 UI 查另一套存储 | 开 zipkin-lens（9412 端口 + `SW_QUERY_ZIPKIN=default`），用 `/zipkin/api/v2/services` 验证 |
| 14 | agent 9.8.0 配 OAP 9.7.0 是否兼容 | 官方要求 agent ≤ 服务端 | 实测心跳/注册/上报全部正常，协议向后兼容 |

### 部署与联调

| # | 问题 | 根因 | 解决 |
|---|------|------|------|
| 15 | 电脑重启后 SkyWalking 访问不了 | 容器没配自动重启策略 | compose 加 `restart: unless-stopped` |
| 16 | 服务注册了但没 trace | 请求发在 OAP 启动完成之前 | 启动顺序：先 SkyWalking 就绪再发请求 |
| 17 | Prometheus 抓不到宿主机服务 | 容器内不能写 localhost | target 用 `host.docker.internal:8081` |
| 18 | Grafana 数据源连不上 | 填了 localhost:9090（浏览器视角） | 填容器名 `http://prometheus:9090`（Grafana 在 Docker 内） |
| 19 | Grafana 曲线断档 | `rate[5m]` 只统计最近 5 分钟，请求是突发式 | 持续发请求保持数据新鲜；这是指标反映真实活动的正常现象 |
| 20 | Git Bash 发中文 curl 返回 400 | 终端 GBK 编码，服务端按 UTF-8 解析失败 | 用 UTF-8 文件 `--data-binary @file` 发送 |
| 21 | .bat 双击报"文件名、目录名或卷标语法不正确" | 中文注释 + UTF-8 编码 + printf 转义把 `\a` 变成控制字符 | bat 用纯英文注释 + CRLF 换行 |
| 22 | 评测服务报 `[Errno 22] Invalid argument` | 后台进程输出管道被 head 截断，进度条写入失败 | 正常前台启动（不接管道）；服务端加完整 traceback 日志 |

---

## 8. 答辩演示脚本

1. **开场**（一句话主线）：这是一个能上生产的企业知识问答平台：文档管理 → 检索增强问答 → 治理 → 评测四个闭环，全程可观测。
2. **上传文档**：前端用 admin 上传一篇新攻略（或 txt/pdf 文件）→ 观察 search-service 控制台"已消费文档已索引事件" → 3 秒后新文档可检索（Kafka 事件驱动）。
3. **智能问答**："去西藏要准备什么证件？" → 展示答案 + 引用上下文（含三种得分）+ Token 用量。
4. **检索调试**：展示混合检索三种得分，讲解加权融合。
5. **质量评测**：一键评测 → 30~60 秒后三个分数卡片（faithfulness / answer_relevancy / context_precision），讲解低分含义。
6. **RBAC 演示**：角色切到 guest 再上传 → 403 红条。
7. **可观测**：SkyWalking 拓扑图（gateway → ai-service → search-service 调用线）+ Grafana LLM 耗时曲线。
8. **主动反思**（加分）：向量目前内存存储应换 Milvus/ES；Kafka 消费应做幂等+死信队列；RBAC 角色表应入库；分块可换语义分块。

---

## 9. 安全说明

⚠️ **密钥一律不写死在代码里**，通过环境变量注入（本仓库已按此规范清理，不含任何真实 Key）。

### 9.1 需要配置的环境变量

| 环境变量 | 用途 | 所属服务 |
|---------|------|---------|
| `DEEPSEEK_API_KEY` | DeepSeek 主供应商 Key | ai-service |
| `QWEN_API_KEY` | 千问平台 Key（embedding 用） | search-service |
| `LLM_API_KEY` | 裁判 LLM Key（评测） | metric-service（Python） |
| `EMBEDDING_API_KEY` | 向量模型 Key（评测） | metric-service（Python） |

### 9.2 Java 服务配置方法（IDEA）

Run → Edit Configurations → 选中服务的启动配置 → **Environment variables** 添加：

```
DEEPSEEK_API_KEY=sk-你的key;QWEN_API_KEY=sk-你的key
```

（ai-service 加 DEEPSEEK_API_KEY；search-service 加 QWEN_API_KEY）

### 9.3 Python 服务配置方法

```bat
set LLM_API_KEY=sk-你的key
set EMBEDDING_API_KEY=sk-你的key
python api_server.py
```

### 9.4 其他

- MySQL 密码为本地开发库默认值（123456），生产环境务必修改。
- 泄露过的 Key 请到对应平台重置。

---

## 10. 后续改进方向

1. 向量索引换 **Milvus / Elasticsearch**（当前内存索引，重启重建）
2. Kafka 消费端**幂等消费 + 死信队列**（当前 at-most-once + 手动 rebuild 兜底）
3. RBAC 角色/权限**入库 + 登录体系**（当前静态映射）
4. 分块升级为**语义分块**（按段落/标题边界）
5. 多租户**租户上下文传递与隔离**（当前 tenant_id 固定）
6. 前端接**登录态**，接口加真实鉴权
7. K8s + CI/CD（下一阶段）

---

*项目 7 · 企业级 RAG 知识引擎 —— 架构层（微服务）+ 治理层 + 评测层 + 可观测性*
