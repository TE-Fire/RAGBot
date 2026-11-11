# Advisor：把 Agent 的横切能力从业务代码里剥出来

AI 应用写到第三个接口就会开始重复：每个对话入口都要查历史、都要落库、都要打日志、都要注入检索结果。这些逻辑跟"聊天"和"客服"这两个业务没有关系，它们属于**模型调用链路本身**。Spring AI 为此留了 Advisor 这个扩展点，它本质上是把 AOP 的思想搬进了 AI 调用：请求进来时改写提示词，响应出去时拦截结果，中间夹着真正的模型调用。

理解 Advisor 只需要记住一句话：**它是链，不是钩子**。每个 Advisor 拿到请求后，可以改造它、可以旁路观测，最后必须调用 `chain.next(request)` 把请求交给下一个。真正的模型调用发生在链尾。所以"在模型调用前后能做什么"，取决于你把自己放在链的哪一环。

## order 决定洋葱的层数

`getOrder()` 返回的整数越小，越先执行，也就越靠外。本项目三个 Advisor 的排布是有讲究的：

| Advisor | order | 位置 | 职责 |
| --- | --- | --- | --- |
| `CustomerServiceAdvisor` | 1 | 最外层 | 检索 + 拼增强提示词 |
| `CustomChatMemoryAdvisor` | 2 | 中层 | 拉历史消息并入 prompt |
| `CustomStreamLoggerAndMessage2DBAdvisor` | 99 | 最内层 | 观测流、聚合完整回答、落库 |

这个顺序不是随便排的。**检索必须在记忆之前**：`CustomerServiceAdvisor` 会整体重建 `ChatClientRequest`，如果它排在内层，外层记忆拼好的历史会被这次重建吃掉。反过来，**日志和落库必须最内层**：只有紧贴模型调用，`doOnNext` 才能拿到每一个原始 chunk，`doOnComplete` 才能拿到完整回答。放到外层也能跑，但拦截到的是被其他 Advisor 包装过的流，细节（比如 reasoningContent 的元数据）可能已经变形。

注意检索链和记忆链在本项目里是**互斥**的：`ChatController` 里"开了联网搜索就挂搜索 Advisor，否则挂记忆 Advisor"。这不是偷懒——联网场景下外部资料本身就是上下文，再叠 50 条历史会把 prompt 撑爆，而且历史里的旧结论会和新搜到的信息打架。真实产品通常做更细的裁剪（保留最近两三轮 + 摘要压缩），这是可以继续演进的地方。

## 改写请求的三种手法，差别在"丢不丢东西"

Advisor 里改造请求有三种写法，混用会出问题：

**一是 `mutate()` 追加**。`CustomChatMemoryAdvisor` 用的是这种：取原有 `instructions`，前面拼上历史消息，再 `prompt.mutate().messages(list).build()`。它保留原有的一切（options、工具定义、已有消息），只改消息列表。**顺序是历史在前、当前问题在后**，这是对话模型的格式要求，颠倒会让模型把当前问题当成历史的一部分。

**二是 `builder()` 重建**。`CustomerServiceAdvisor` 和 `NetworkSearchAdvisor` 都是这种：整段提示词由模板重新生成，旧的消息列表被完全替换。这种做法适合"原始问题已经没用了，要用增强后的问题取代它"的 RAG 场景，但**必须手动把 `options` 搬过去**（`PromptTemplate.create(vars, prompt.getOptions())`），否则模型名、温度全部回退默认值，而且不报错。

**三是直接改 messages**。适合只插入一条 system 消息的轻量场景。

三种手法的选择标准很简单：**只增不改用 mutate，整体替换用 builder 并显式搬运 options**。项目里两者都用对了，这也是它能同时支持"多模型可选"和"检索增强"的原因。

## 记忆为什么自己写，不用内置的 ChatMemory

Spring AI 自带会话记忆的 Advisor 与 `ChatMemory` 抽象（内存版、JDBC 版）。不用它的理由不是因为不能用，而是因为**业务已经有自己的消息表**：前端要分页拉历史（`/chat/message/list`）、要按对话 UUID 隔离、要存推理过程（`reasoning_content` 单独一列）、要支持重命名和删除对话。这些需求意味着消息的"真相"在业务库里，而不是在框架的 memory 表里。

自己写的代价是要处理几个细节。查最近 50 条必须 `ORDER BY create_time DESC LIMIT 50` 再**升序重排**——直接升序取 50 条拿到的是最早的 50 条，对话越长越答非所问。角色映射只认 `user` 和 `assistant`，其他值（比如系统的 `tool`）直接丢弃，避免构造出模型不接受的序列。

真正的硬性约束是**成对**。message 表里如果只有 user 没有 assistant（上一轮请求中途失败），模型看到的就是连续两条 user，多数厂商的接口会报错或行为异常。本项目的兜底是把"存 user + 存 assistant"放进同一个编程式事务：要么两条都进，要么都不进。这是记忆能长期稳定的前提，比"记得更多"重要得多。

## 落库为什么放在 Advisor，且必须用编程式事务

把落库写在 Controller 里是行不通的：流式响应返回的是 `Flux`，Controller 方法在第一个 chunk 发出前就已经返回，方法体结束时回答还没生成完。**能拿到完整回答的位置只有流的终止回调**，也就是 `doOnComplete`。

这里有个必须踩过的坑：`doOnComplete` 的执行线程不是请求线程，`@Transactional` 依赖 ThreadLocal 绑定连接，在 reactive 回调里根本不生效（不报错，只是没有事务）。所以 `CustomStreamLoggerAndMessage2DBAdvisor` 注入 `TransactionTemplate`，显式开事务：

```java
transactionTemplate.execute(status -> {
    chatMessageMapper.insert(userMessage);       // 用户消息
    chatMessageMapper.insert(assistantMessage);  // AI 回答 + reasoning
    return true;
});
```

顺带一个细节：`reasoningContent` 是逐帧累积的，直接取最后一帧的值就是完整推理，不用自己拼。而正文 `getText()` 是增量的，必须 `StringBuilder.append` 累积——两者语义相反，写反了就是"回答只剩最后一个字"。

## Advisor 的实例生命周期与状态

这三个 Advisor 都是 `new` 出来的，不是 Bean。因为每个请求要带自己的 `chatId`、`userMessage`，而 Spring 的单例 Bean 不能持有请求级状态。

如果非要做成 Bean，有两条路：改成 prototype 作用域（每次注入新建），或者把状态从构造参数挪到 `ChatClientRequest.context()` 里传。后者更"框架原生"，但取值时得处理空值，代码反而更绕。**当一个组件天然携带请求上下文时，按请求创建实例是最诚实的做法**，只要别在构造里做重活（数据库连接、HTTP 客户端都应该注入，本项目也是这样）。

## 边界：Advisor 不该干重活

`CustomerServiceAdvisor` 在同一次调用里做了检索 + 重排两次网络请求，这在链上是**串行阻塞**的——用户等待的首个 token 时间因此被拉长。这是当前设计的代价，也是它最简单的形态。可选的优化方向有三个：把检索和重排合并成一次（有些 rerank 服务支持传候选列表）、对检索结果做短 TTL 缓存（同一问题重复问时）、以及把 rerank 改成"只对 Top-N 精排"而不是全量精排（项目里已经是这个策略）。

但有些事坚决不该放进 Advisor：**知识库向量化、文件解析、批量重算 embedding**。它们是分钟级任务，放进调用链会把 HTTP 线程池拖死。这类工作的正确位置是事件 + 异步线程池，也就是下一层要讲的离线链路。
