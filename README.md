# TEFire AI Robot

一个把 Spring AI 接进真实后端系统的完整工程：AI 问答机器人、RAG 私有知识库客服、联网搜索三条链路，前后端分离。目标不是"能聊"，而是把上下文记忆、检索增强、提示词工程、异步入库这些环节按工程标准落地。

代码是学习材料，配套笔记在 [`notes/`](./notes/README.md)，按依赖顺序讲了每一处的取舍与坑。

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端 | Spring Boot 4.1 / JDK 21 / Spring AI 2.0.1 / MyBatis-Plus / Log4j2 |
| 模型 | 任意 OpenAI 兼容服务（默认阿里云百炼），分离 chat 与 embedding 模型 |
| 存储 | PostgreSQL 16 + pgvector（业务表 + 向量表） / Elasticsearch 9（关键词召回，IK 分词） |
| 检索 | 向量召回 + BM25 双路 → RRF 融合 → qwen3-rerank 精排 |
| 联网 | 自建 SearXNG 聚合搜索 + OkHttp 并发抓正文 + Jsoup 提纯 |
| 前端 | Vue 3 / Vite / Ant Design Vue / Tailwind CSS / Pinia |

## 三条链路

```
① 问答机器人  POST /chat/completion
   SSE ← ChatClient ← [CustomChatMemoryAdvisor(历史) → StreamLoggerAdvisor(落库)]

② 智能客服    POST /customer-service/chat/completion
   SSE ← ChatClient ← [CustomerServiceAdvisor(检索+重排+增强提示词)]

③ 联网搜索    同上，开关 networkSearch=true
   SSE ← ChatClient ← [NetworkSearchAdvisor(SearXNG→抓正文→拼上下文)]
```

知识库入库是另一条离线链路：分片上传 → 合并 → 发布事件 → 异步解析/切分/向量化 → 双写 pgvector 与 ES。

## 目录

```
.
├── docker-compose.yml              # PostgreSQL / Elasticsearch(含IK) / SearXNG
├── docker/
│   ├── es/Dockerfile               # ES + IK 分词插件
│   └── searxng/settings.yml        # 开启 json 输出格式
├── notes/                          # 配套知识笔记（建议先读）
├── tefire-ai-robot-springboot/
│   ├── src/main/java/com/tefire/ai/robot/
│   │   ├── advisor/                # 记忆、检索、联网、日志落库四个 Advisor
│   │   ├── config/                 # ChatClient、RAG 参数、线程池、OkHttp
│   │   ├── reader/                 # Markdown / PDF / Word / TXT 解析
│   │   ├── service/impl/           # 混合检索、Rerank、ES 双写、客服业务
│   │   └── event/                  # 文件入库事件与异步监听器
│   ├── src/main/resources/sql/      # 建表脚本（compose 首次启动自动执行）
│   └── 知识库文档/                  # 示例 FAQ（md / txt / docx / pdf 四种格式）
└── tefire-ai-robot-vue3/           # 聊天端 + 客服端 + 知识库管理
```

## 快速开始

### 1. 起中间件

```bash
docker compose up -d
```

三个容器：`tefire-pg`(5432)、`tefire-es`(9200)、`tefire-searxng`(8888)。首次启动会自动执行 `ai_robot.sql` 建表建扩展。

ES 镜像需要现场安装 IK 分词插件，构建时会联网（约 1.2GB 基础镜像）。若插件下载失败，可把 `application-dev.yml` 里的 `rag.elasticsearch.index-analyzer` / `search-analyzer` 改成 `standard`，功能仍可用，只是中文关键词召回会变差。

**端口冲突**：本机 9200 被其他程序占用时，用环境变量换端口，并同步告诉应用：

```bash
ES_PORT=9201 docker compose up -d elasticsearch
# 启动后端时：ES_URIS=http://localhost:9201
```

### 2. 配置模型密钥

密钥不入库，用环境变量注入：

```powershell
$env:AI_API_KEY = "sk-你的密钥"
# 若使用非百炼的 OpenAI 兼容服务，再加：
$env:AI_BASE_URL = "https://your-endpoint/v1"
```

变量名与默认值见 `application-dev.yml`：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_API_KEY` | 空（必填） | 模型服务密钥 |
| `AI_BASE_URL` | 百炼 compatible-mode | OpenAI 兼容地址 |
| `AI_CHAT_MODEL` | `deepseek-v4.1-flash` | 客服链路模型 |
| `AI_EMBEDDING_MODEL` | `text-embedding-v4` | 向量化模型 |
| `AI_EMBEDDING_DIMENSIONS` | `1536` | 必须与 `t_vector_store.embedding` 列一致 |
| `AI_RERANK_MODEL` / `AI_RERANK_URL` | `qwen3-rerank` / 百炼 rerank 接口 | 精排 |
| `ES_URIS` | `http://localhost:9200` | ES 地址，端口改过时同步 |

换 embedding 模型时注意维度：`dimensions` 会随请求发出，服务端不支持裁剪时返回的维度必须与表结构匹配，否则要改 `ai_robot.sql` 里的 `vector(N)` 并重建表。

### 3. 启动后端

```powershell
cd tefire-ai-robot-springboot
mvn spring-boot:run
```

启动后监听 8080。上传的文件落在模块目录下的 `data/files`（已在 .gitignore 中）。

### 4. 启动前端

```bash
cd tefire-ai-robot-vue3
npm install
npm run dev
```

Vite 默认 5173，`/api` 已代理到 8080。

### 5. 灌入示例知识库

打开前端「智能客服」页，上传 `tefire-ai-robot-springboot/知识库文档/tefire实战专栏客服FAQ.md`，等列表状态变为「已完成」，再提问验证。

也可以传同目录下的 `.txt` / `.docx` / `.pdf`，走的是另外三个 Reader。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/chat/new` | 新建对话，返回对话 UUID |
| POST | `/chat/completion` | 流式对话（SSE），支持 `networkSearch` 开关 |
| POST | `/chat/message/list` | 历史消息分页 |
| POST | `/chat/list` | 历史对话分页 |
| POST | `/chat/summary/rename` | 重命名对话 |
| POST | `/chat/delete` | 删除对话 |
| POST | `/customer-service/file/check` | 秒传 / 断点续传检查（按 MD5） |
| POST | `/customer-service/file/upload-chunk` | 分片上传（multipart） |
| POST | `/customer-service/file/merge-chunk` | 合并分片并触发入库 |
| POST | `/customer-service/file/list` | 知识库文件分页 |
| POST | `/customer-service/file/delete` | 删除文件（向量与 ES 双删） |
| POST | `/customer-service/chat/completion` | 智能客服流式对话（RAG） |

## 验证与调参

`src/test` 下有四个可复跑的验证类，配合 `notes/` 里的方法使用：

- `ExactTermRetrievalTests`：把 topK 开到 20，看目标块到底排第几
- `HybridRetrievalVerifyTests`：对比每路候选 5 条 / 10 条的 RRF 融合结果
- `RerankVerifyTests`：对比 RRF 顺序与 rerank 顺序的名次变化
- `RetrievalPainPointTests`：端到端打印检索结果与模型回答

开关都在 `application-dev.yml` 的 `rag` 节点下，可以随时关掉混合检索或重排，做「优化前 / 优化后」对照。

## 已知取舍

- 向量写入与状态更新**不在同一事务**内（跨组件），一致性靠状态机 + 内容哈希幂等重跑兜底，生产环境需补对账。
- `knowledge_base_id` 已写入 PG metadata 与 ES 字段，但检索侧尚未加过滤，多知识库会互相串。
- 联网搜索的上下文没有字符预算控制，长页面多时可能超窗口被截断。
- 网页内容是外部不可信输入，仅做了提示词层面的隔离，未做输出侧校验。

这几条在笔记里都有对应说明与改法，属于明确留出的练习点，不是遗漏。

## 说明

`知识库文档/` 下的 FAQ 是自拟的示例数据，仅用于演示多格式解析与检索效果，与任何真实业务无关。
