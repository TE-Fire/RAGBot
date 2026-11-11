# 笔记索引：这个项目在讲什么

这套笔记不解释 API 用法，只回答一个问题：**把大模型接进一个真实后端系统时，哪些地方会出问题，为什么这么解。**

五篇按依赖顺序排列，建议顺着读——后一篇会用到前一篇的结论。

| 编号 | 主题 | 核心结论 | 对应源码 |
| --- | --- | --- | --- |
| 01 | 对话链路与流式输出 | Spring AI 把一次调用拆成 ChatModel → ChatClient → Prompt → Advisor 链；流式响应有正文与推理两条通道，且推理是累积值 | `config/ChatClientConfig`、`controller/ChatController`、`config/JacksonConfig` |
| 02 | Advisor 与 Agent 工程化 | 记忆、检索、落库、审计都是调用链上的横切能力；order 决定洋葱层次，落库必须用编程式事务 | `advisor/*` |
| 03 | RAG 离线入库链路 | 检索效果的上限在入库时定死：语义切块、内容哈希幂等、双写 ID 对齐、状态机兜底 | `service/impl/CustomerServiceImpl`、`event/listener/*`、`reader/*` |
| 04 | RAG 在线检索与重排 | 向量召回错了要分三种归因；混合检索补关键词盲区，RRF 只用名次，rerank 精排且必须可降级 | `service/impl/HybridRetrievalServiceImpl`、`service/impl/RerankServiceImpl`、`service/impl/EsChunkServiceImpl` |
| 05 | 提示词工程与联网搜索 | 提示词是四段结构；联网搜索的价值在抓正文而不在搜索；并发抓取要分池、逐个超时、失败回退 | `advisor/CustomerServiceAdvisor`、`advisor/NetworkSearchAdvisor`、`service/impl/SearXNGServiceImpl`、`service/impl/SearchResultContentFetcherServiceImpl` |

## 怎么配合代码读

先跑通再说。启动顺序、依赖服务、环境变量见根目录 `README.md`。

跑通之后按这条线验证一遍，笔记里的每个结论都能自己复现：

1. 上传 `知识库文档/tefire实战专栏客服FAQ.md`，看状态从"待处理"走到"已完成"，查 `t_vector_store` 有多少块、`rag_chunk` 索引有多少条——验证离线链路与双写。
2. 跑 `ExactTermRetrievalTests`，把 topK 开到 20 找目标块的名次——验证"向量召回排太后"这个现象。
3. 跑 `HybridRetrievalVerifyTests`，对比每路候选 5 条与 10 条的融合结果——验证 RRF 的共识效应。
4. 跑 `RerankVerifyTests`，对比 RRF 顺序与 rerank 顺序——验证精排的作用。
5. 把 `rag.retrieval.hybrid-enabled` 和 `rag.rerank.enabled` 都关掉，重跑第 2 步，对比三种配置下的名次——这是"优化前后对照实验"的最小闭环。

## 一条判断主线

这套代码里有几处看起来"不完美"的地方，笔记里都标出来了：事务圈不住向量写入、`knowledge_base_id` 写了但检索没用、联网上下文没有预算控制。它们不是疏漏，而是当前阶段明确的取舍。**读代码时把"为什么先不做"和"以后怎么补"分开看**，比把每处都改一遍更有价值。
