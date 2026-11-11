# 离线入库：RAG 的效果上限在这一步就定死了

检索增强的直觉是"先把资料塞进向量库，再让模型查着回答"。真正决定效果的不是"塞"，而是**塞成什么样**：块切得对不对、元数据全不全、重复上传会不会产生脏数据、失败之后能不能看出来。在线检索的所有调参，都是在给离线链路的缺陷擦屁股。所以这一篇从文件进门开始讲。

## 文件不是"上传"，是"分片 + 合并 + 状态流转"

知识库文档动辄几 MB 到几十 MB，一次性上传在弱网下几乎必然失败。项目采用分片上传，流程是三步：

先 `POST /file/check`，前端算好文件 MD5 后带上来。服务端按 MD5 查 `t_ai_customer_service_file_storage`：没记录 → 需要上传；有记录且状态不是"上传中" → **秒传**，直接结束；有记录且状态是"上传中" → 返回已上传的分片序号列表，前端只补传缺失的分片（断点续传）。

再 `POST /file/upload-chunk` 逐片上传。每个分片写盘（`chunk/{md5}/{序号}.chunk`）并记录一条 `t_file_chunk_info`，同时把主表的 `uploaded_chunks` 加一。注意这里用了 `setSql("uploaded_chunks = uploaded_chunks + 1")` 而**不是**"查出来加一再写回"——并发补传时后者会丢更新。

最后 `POST /file/merge-chunk` 合并。合并前校验分片数是否等于总数，然后按序号顺序流式写进同一个文件（8KB 缓冲，避免把整个文件读进内存），落库状态改为"待处理"，删掉分片目录与记录，最后**发布事件**触发向量化。

主表状态是一个五态机：0 上传中、1 待处理、2 向量化中、3 已完成、4 失败。删除接口里有一条容易被忽略的约束——处于 1、2 状态的文件不允许删。原因很实在：向量化线程正拿着这个文件的路径和 ID 干活，此时删记录会导致"向量已写入但文件记录不存在"，成为永远清不掉的孤儿数据。

## 向量化为什么必须异步，且要发布事件

合并接口如果同步做完整链路（解析 → 切分 → 调 embedding → 写 PG → 写 ES），一个 5MB 的 PDF 要几十秒，接口必然超时、前端只能干等。所以合并接口只负责"文件落地 + 状态置为待处理 + 发事件"，剩下的交给监听器，接口立刻返回。

事件用 Spring 的 `ApplicationEventPublisher` + `@EventListener` + `@Async("eventTaskExecutor")`，线程池单独配（core 5 / max 10 / queue 50 / CallerRunsPolicy）。用事件而不是直接调 Service，好处是**解耦加可扩展**：以后要给文件加"敏感词扫描""OCR 补充解析"，再挂一个监听器即可，合并逻辑一行不用改。用独立线程池而不是默认的 `SimpleAsyncTaskExecutor`，是因为默认实现每次新建线程、无上限，一批文件同时上传就会把机器打满。

## 这里的事务是"半个事务"，必须讲清楚

监听器里套了一层 `TransactionTemplate`，把"读文件解析 → 向量化 → 双写 ES → 更新状态"整体包起来，异常时 `setRollbackOnly()`，外层再把状态改成"失败"。这个写法看起来严谨，但有一个必须承认的事实：**PGVectorStore 的写入不走这个事务**。

`VectorStore.add()` 内部自己拿连接、自己提交，跟外层 `TransactionTemplate` 不是同一个连接，因此它不会随事务回滚。ES 更是另一个系统，根本谈不上事务。所以这层事务真正能保证的只有"状态更新要么成功要么不更新"，不能保证"数据和状态一致"。

这不是设计失误，是分布式场景的固有难题，业界通行的解法有三种：靠状态机幂等重试（失败就重跑，因为 ID 是内容哈希，重跑覆盖而不是新增）、靠对账任务定时比对 PG 与 ES 的块数、或者引入 outbox 表记录待办。本项目选了第一种，且在代码里留了 TODO 说明生产环境要额外保证一致性。**读这类代码时，判断"这个事务有没有用"的标准是看它圈住的东西是不是同一个连接**，别被注解骗了。

## 解析：按格式分流，按语义切块

四种格式四个 Reader：Markdown 用 `MarkdownDocumentReader`、PDF 用 `PagePdfDocumentReader`（一页一块）、Word 用 `TikaDocumentReader`（Tika 能覆盖 doc/docx/ppt/html 等）、TXT 用 `TextReader`。分流的依据是扩展名，白名单之外直接抛业务异常。

Markdown 的解析配置是整条链路里最关键的一处：

```java
MarkdownDocumentReaderConfig.builder()
    .withHorizontalRuleCreateDocument(true) // 遇到 --- 就断块
    .withIncludeCodeBlock(false)
    .withIncludeBlockquote(false);
```

`withHorizontalRuleCreateDocument(true)` 意味着**一个"问答对"就是一块**。FAQ 文档用 `---` 分隔每条问答，这个配置天然实现了"按语义边界切分"，比按固定字数切准得多——一块里只有一个问题，语义不被稀释，向量也就不会"什么都沾一点"。这是本项目 FAQ 场景能用较小 topK 命中的根本原因，而不是检索算法有多高级。

有一个坑值得单独说：官方 Markdown Reader **只收集文字节点，图片语法里的地址会被丢弃**。表现是：FAQ 里明明写了图片链接，AI 回答时却从来不带图。原因在于图片是 AST 里的 image 节点，Reader 只取 text。项目的解法是在解析前做一次文本预处理——在每个 `![](url)` 后面追加一段反引号包裹的 URL（行内代码节点），因为代码节点的内容会被收集。这是典型的"绕开框架缺陷"的工程手法：不 fork 框架，不重写解析器，只在入口把数据改成框架愿意接收的形状。

## 切分：为什么按 Token 而不是按字符

按 `---` 切出来的块大小不均，长块可能上千字、混了多个主题。`TokenTextSplitter` 负责二次切分，参数就两个：`chunkSize=800`（目标 token 数）、`minChunkSizeChars=350`（按标点截断时允许的最小字符数）。

按 token 而非字符，是因为 **embedding 模型和 LLM 的窗口都以 token 计**，而 token 与字符的比例随语言和内容浮动（中文大致 1 字 ≈ 1 token，英文一个词可能只占 0.3 token，代码和标点更碎）。按字符切，同一个参数在中文文档上会切出两倍于预期的 token 量，超限就静默截断，表现为"检索到了但内容不全"。

`minChunkSizeChars` 是防碎片化的下限：切分器在标点处断开时，如果剩下的尾巴太短，就并回上一块，避免产出"一条半句话"这种毫无检索价值的块。

## 幂等：ID 从哪来，决定了重复上传会不会变脏

Spring AI 的 `Document` 默认用随机 UUID 作 ID，同一份文件传两次就有两份数据，检索时互相挤占 topK。项目的解法是**内容哈希 ID**：

```java
JdkSha256HexIdGenerator sha = new JdkSha256HexIdGenerator();
IdGenerator contentHashIdGenerator = contents -> sha.generateId(contents[0]);
```

把 ID 换成 `SHA-256(正文)`，同一段文字永远落到同一个主键上，PGVector 里就是 upsert，天然幂等。这个设计的代价要说清楚：**它假设"正文相同即同一块"**。如果两本不同的书里有完全相同的段落，后者会覆盖前者的 metadata（比如 `originalFileName`），检索时归属就错了。对 FAQ 知识库这种每个答案唯一的场景没问题，对多来源采集的场景要改用 `文件ID + 块序号` 作为 ID。

另一个必须注意的限制是**批量大小**。embedding 服务端限制单次最多 10 条，所以代码里 `Lists.partition(documents, 10)` 分批写。这个数字是服务商给的硬约束，超过就整批失败——不是"慢一点"。

## 双写 ES：ID 对齐是混合检索的前提

写完 PG 紧接着写 ES。索引 mapping 里 `content` 用 IK 分词（写入 `ik_max_word`、查询 `ik_smart`），另外三个 keyword 字段：`file_id`、`original_file_name`、`knowledge_base_id`。

最关键的一行是 `.id(doc.getId())` —— **ES 的 `_id` 直接复用 PGVector 的主键**。混合检索用 RRF 融合两路结果时，判断"是不是同一个块"完全依赖 ID 相等；如果两边各生成各的 ID，融合就退化成"两路结果简单拼接"，去重和加权都无从谈起。

`content` 写入用 `ik_max_word`（切得细，召回高）、查询用 `ik_smart`（切得粗而干净，减少跨词误命中）——这是 IK 的经典搭配，本质是"索引侧求全、查询侧求准"。

删除同理，要**双删**：先 `vectorStore.delete("mdStorageId == {id}")`，再按 `file_id` 删 ES（`deleteByQuery` 加 `refresh(true)` 立即生效）。少删一边，用户删了文件却还能从 AI 那里问出内容，是最容易被投诉的 bug。

## 这一层的判据

评估离线链路做得好不好，不看"能不能检索到"，而看四个问题：同一份文件传两次，数据会不会翻倍；一个 30MB 的 PDF 上传，接口会不会超时；向量化中途异常，列表页能不能看出是"失败"而不是"已完成"；删掉一个文件，两边的数据是不是都干净了。这四个问题答"是"，在线链路才有调参的意义。
