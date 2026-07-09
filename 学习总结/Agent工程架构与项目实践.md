# AI Agent 工程架构实战手册 —— 从菜鸟到入门

> **适用版本**: Spring Boot 4.1.0 + Spring AI 2.0.0 + DeepSeek  
> **配套项目**: `ai-robot`（智能问答 Agent）  
> **作者**: TE-Fire  
> **日期**: 2026-07-09

---

## 前言：为什么要理解 Agent 架构？

当你第一次调用大模型 API 时，代码可能长这样：

```java
String answer = deepSeekChatModel.call("你是谁？");
```

这就像按一下开关，灯泡亮了——简单直接。但真正的 AI 应用远不止于此：

- 用户说"帮我查一下昨天的订单状态"，模型需要**记住**之前聊过什么
- 用户说"对比一下这三款产品的优劣"，模型需要**调用工具**去数据库查产品信息
- 用户说"根据公司制度回答"，模型需要**检索知识库**中的规章制度

**Agent（智能体）** 就是给大模型装上"大脑"、"记忆"、"手脚"和"外脑"之后的完整系统。本文将以当前 `ai-robot` 项目为实例，带你从理论到代码，一步步理解 Agent 工程的架构组成。

### 阅读指南

| 章节 | 适合谁 | 预计阅读时间 |
|:---|:---|:---|
| 第一部分：架构全景 | 想建立 Agent 全局认知 | 15 分钟 |
| 第二部分：全流程追踪 | 想理解代码执行细节 | 20 分钟 |
| 第三部分：代码映射 | 想对照项目代码学习 | 15 分钟 |
| 附录 | 想深入扩展 | 5 分钟 |

---

## 第一部分：Agent 工程主流架构组成

### 1.0 全景架构图

下面这张图展示了一个完整的 AI Agent 系统架构。别被复杂度吓到——我们会一层一层拆解。

![Agent 架构全景图](img/advisor.jpg)

从这张图可以看到，Agent 系统从上到下分为多个层次。我们用一个**餐厅**的类比来帮助理解：

| 架构层 | 餐厅类比 | 核心职责 |
|:---|:---|:---|
| 用户接入层 | 餐厅门口/服务员接单 | 接收用户输入，返回结果 |
| Agent 编排核心层 | 主厨（总指挥） | 理解需求、制定计划、协调各环节 |
| 记忆系统 | 点餐记录本 | 记住说过的话、做过的事 |
| 工具/插件层 | 厨房设备、外部供应商 | 执行具体操作（查库存、下单） |
| 知识/RAG 层 | 菜谱库、食材手册 | 提供领域知识 |
| 模型推理层 | 厨师的大脑 | 理解、推理、生成回复 |
| Advisor/拦截链 | 卫生检查、成本核算 | 不参与主流程，但每步都把关 |
| 基础设施层 | 水电煤气、仓储 | 底层支撑 |

下面我们逐一深入每一层。

---

### 1.1 LLM 引擎层 —— Agent 的"大脑"

#### 概念

LLM（Large Language Model，大语言模型）是 Agent 系统的核心引擎。它负责**理解**用户输入、**推理**问题、**生成**回复。没有它，Agent 就不存在。

#### 主流模型选型

| 厂商 | 模型名 | 特点 | 适用场景 |
|:---|:---|:---|:---|
| DeepSeek | `deepseek-v4-flash` | 快速、便宜 | 日常对话、客服 |
| DeepSeek | `deepseek-v4-pro` | 深度推理、思维链 | 复杂问题、数学推理 |
| OpenAI | `gpt-4o` | 多模态、综合能力强 | 通用场景 |
| Anthropic | `claude-sonnet-5` | 长上下文、代码能力强 | 编程、长文档分析 |
| 阿里 | `qwen-max` | 国产、中文优化 | 国内合规场景 |

#### 本项目对应

在我们的项目中，LLM 引擎由 Spring AI 自动配置的 **`DeepSeekChatModel`** 承载。它实现了 Spring AI 定义的 `ChatModel` 和 `StreamingChatModel` 接口，底层通过 HTTP 调用 `https://api.deepseek.com` 的 Chat Completion API。

[application.yml](ai-robot/src/main/resources/application.yml) 中的核心配置：

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_KEY}        # API 密钥（从环境变量读取，不要硬编码！）
      base-url: https://api.deepseek.com
      chat:
        model: deepseek-v4-flash       # 默认使用快速模型
        temperature: 0.8               # 0=严谨, 1=平衡, 2=创意
```

> **💡 调参建议**:  
> - 客服/事实问答 → `temperature: 0.3 ~ 0.5`  
> - 创意写作/头脑风暴 → `temperature: 0.8 ~ 1.2`  
> - 代码生成 → `temperature: 0 ~ 0.2`

---

### 1.2 规划与推理层 —— Agent 的"思考过程"

#### 概念

普通 LLM 是"直觉型"回答（System 1 思维），而高级 Agent 需要"分析型"思考（System 2 思维）。这层让 Agent 在回答之前**先想清楚**。

#### 核心技术

| 技术 | 原理 | 适用场景 |
|:---|:---|:---|
| **CoT** (Chain-of-Thought) | 提示模型"一步步思考" | 数学题、逻辑推理 |
| **ReAct** | 思考(Thought) → 行动(Action) → 观察(Observation) 循环 | 需要调用工具的复杂任务 |
| **推理模型** (如 DeepSeek R1) | 模型内部内置长思维链，自动深度思考 | 复杂推理、代码审查 |

#### 本项目对应

[DeepSeekR1ChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java) 展示了如何调用推理模型并提取**思维链**：

```java
// 动态切换为推理模型
DeepSeekChatOptions chatOptions = DeepSeekChatOptions.builder()
        .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue())  // 推理模型
        .build();

Prompt prompt = new Prompt(message, chatOptions);

chatModel.stream(prompt).mapNotNull(chatResponse -> {
    // 强转为 DeepSeek 专属消息对象，以获取推理内容
    DeepSeekAssistantMessage msg =
        (DeepSeekAssistantMessage) chatResponse.getResult().getOutput();

    String reasoningContent = msg.getReasoningContent(); // 模型的"内心独白"
    String text = msg.getText();                          // 最终回答

    // text 为 null → 还在推理中，输出思考过程
    // text 有值 → 推理结束，输出最终答案（并在前面加 <hr> 分隔线）
    if (text != null) {
        return "<hr>" + text;  // 思考过程 | 分隔线 | 最终答案
    }
    return reasoningContent;
});
```

> **🔍 为什么需要提取思维链？**  
> - **可解释性**: 用户能看到模型是怎么推理的，不是"黑箱"  
> - **调试**: 如果回答错了，可以从思考过程中找到根因  
> - **用户体验**: 深度思考的展示让 AI 显得更"聪明"

---

### 1.3 记忆系统 —— Agent 的"海马体"

#### 概念

如果没有记忆，每次对话都是"初次见面"——这是最简单的 chatbot。有了记忆，Agent 才能记住前文、理解语境、持续服务。

#### 两种记忆对比

| 类型 | 存储位置 | 容量 | 检索方式 | 生命周期 | 类比 |
|:---|:---|:---|:---|:---|:---|
| **短期记忆** (Window) | 内存 / 轻量 DB | 有限（如 50 条） | FIFO 窗口 | 单次会话 | 你的工作记忆（当前在想的几件事） |
| **长期记忆** (Vector) | 向量数据库 | 海量 | 语义相似度检索 | 持久化 | 你的日记本（过去事情的存档） |

#### Spring AI 中的记忆抽象

```
ChatMemoryRepository          ← 存储接口（默认内存实现，可替换为 Redis/DB）
    └── MessageWindowChatMemory   ← 窗口记忆实现（保留最近 N 条消息）
           └── MessageChatMemoryAdvisor ← 记忆管理器（自动存取）
```

#### 本项目对应

[ChatMemoryConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatMemoryConfig.java) 配置了一个 **50 条消息窗口** 的短期记忆：

```java
@Configuration
public class ChatMemoryConfig {

    @Resource
    private ChatMemoryRepository chatMemoryRepository;  // Spring AI 自动提供

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(50)                    // 保留最近 50 条消息
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }
}
```

> **💡 为什么是 50 条？**  
> 窗口太小 → 上下文不够，模型"健忘"  
> 窗口太大 → Token 消耗过高，成本增加，且长上下文会稀释关键信息  
> 50 条是一个适合客服场景的经验值，你可以根据实际需求调整。

---

### 1.4 工具/函数调用层 —— Agent 的"手脚"

#### 概念

LLM 本身只会生成文本——它不能查数据库、发邮件、调用 API。**Function Calling** 让模型能"说"出它想调用哪个函数，由系统实际执行后把结果返回模型。

#### 工作流程

```
用户: "帮我查一下北京今天的天气"
   │
   ▼
模型: → 输出 tool_call: getWeather(city="北京")   ← 模型不回答，而是"请求"调用工具
   │
   ▼
系统: → 执行 getWeather("北京") → 返回 {temp: 25, weather: "晴"}
   │
   ▼
模型: → "北京今天晴天，气温25°C，适合出行"       ← 基于工具结果生成自然语言回答
```

#### 本项目现状

当前 `ai-robot` 项目**尚未集成 Function Calling**，但 Spring AI 提供了非常便捷的扩展方式：

```java
// 未来扩展示例（非项目现有代码）
@Tool(description = "查询指定城市的天气")
public String getWeather(@ToolParam(description = "城市名称") String city) {
    return weatherService.query(city);
}
```

> **🔮 扩展方向**: Spring AI 的 `@Tool` 注解 + MCP（Model Context Protocol）协议可以让你的 Agent 连接任意外部服务。

---

### 1.5 知识/RAG 层 —— Agent 的"外脑"

#### 概念

LLM 的训练数据有截止日期，且不包含你的私有文档。**RAG（Retrieval-Augmented Generation，检索增强生成）** 让 Agent 在回答问题前先去知识库"查资料"，再把查到的内容连同问题一起发给模型。

#### RAG 核心流程

```
文档(PDF/Word/Markdown)
    │
    ▼
[1] 文档加载 (DocumentReader)
    │
    ▼
[2] 文本分割 (TextSplitter)        ← 切成小块，每块 ~500 字
    │
    ▼
[3] 向量化 (EmbeddingModel)        ← 文字 → 数学向量
    │
    ▼
[4] 存储 (VectorStore)             ← 存入向量数据库（如 Milvus、Pinecone）
    │
    ▼
[5] 检索 (相似度搜索)              ← 用户问题 → 向量 → 找最相似的文档块
    │
    ▼
[6] 增强生成                       ← "请根据以下资料回答：{检索结果}\n\n问题：{用户问题}"
```

#### 与记忆系统的关键区别

| | 记忆系统 | RAG 知识库 |
|:---|:---|:---|
| **存什么** | 对话历史 | 领域知识文档 |
| **检索方式** | 按时间顺序 (FIFO) | 按语义相似度 |
| **生命周期** | 会话级 | 持久化 |
| **数据量** | 几十到几百条 | 成千上万文档 |

#### 本项目现状

当前项目名为 "RAGBot"，但 RAG 能力**尚未实现**。这是下一步建设的重要方向。

---

### 1.6 Advisor/拦截链 —— Agent 的"中间件"

#### 概念

Advisor（顾问/拦截器）不修改核心业务逻辑，但在每次请求的**前后**插入通用处理——就像 Web 开发中的 Filter、Interceptor、Middleware。

#### Advisor 链的责任链模式

```
请求进入
    │
    ▼
┌─────────────────────┐
│  SimpleLoggerAdvisor │  ← order=0, 记录 DEBUG 日志
│  (前置: 记录请求)     │
└─────────┬───────────┘
          │ chain.nextCall()
          ▼
┌─────────────────────┐
│  MyLoggerAdvisor     │  ← order=1, 记录 INFO 日志（当前未激活）
│  (前置: 记录请求)     │
└─────────┬───────────┘
          │ chain.nextCall()
          ▼
┌──────────────────────────┐
│ MessageChatMemoryAdvisor │  ← 自动管理记忆
│ (前置: 加载历史 → 后置: 保存对话) │
└─────────┬────────────────┘
          │ chain.nextCall()
          ▼
      调用 LLM
          │
          ▼
    响应沿链返回（各 Advisor 执行后置处理）
```

#### 本项目对应

[ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java) 中注册了两个 Advisor：

```java
@Bean
public ChatClient chatClient(DeepSeekChatModel chatModel) {
    return ChatClient.builder(chatModel)
        .defaultSystem("扮演一名拼多多客服")
        .defaultAdvisors(
            new SimpleLoggerAdvisor(),                          // Spring AI 内置日志
            MessageChatMemoryAdvisor.builder(chatMemory).build() // 记忆管理
        )
        .build();
}
```

自定义的 [MyLoggerAdvisor.java](ai-robot/src/main/java/com/tefire/ai_robot/advisor/MyLoggerAdvisor.java) 实现了 `CallAdvisor` 接口：

```java
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest chatClientRequest,
            CallAdvisorChain callAdvisorChain) {

        log.info("## 请求入参: {}", chatClientRequest);              // ← 前置：记录请求

        ChatClientResponse chatClientResponse =
            callAdvisorChain.nextCall(chatClientRequest);          // ← 调用下一个 Advisor / LLM

        log.info("## 请求出参: {}", chatClientResponse);            // ← 后置：记录响应
        return chatClientResponse;
    }

    @Override
    public int getOrder() { return 1; }  // 值越小越先执行

    @Override
    public String getName() { return this.getClass().getSimpleName(); }
}
```

> **🔧 你可以用 Advisor 模式做什么？**  
> - Token 用量统计与计费  
> - 敏感词过滤 / 内容审核  
> - 请求限流 / 熔断  
> - 响应缓存  
> - 多模型自动切换（主模型挂了切备选）

---

### 1.7 编排器 —— Agent 的"总指挥"

#### 概念

编排器（Orchestrator）是 Agent 的**中枢神经系统**——它把 LLM、记忆、Advisor、工具等所有组件串联成一个有机整体。

#### Spring AI 中的三个抽象层次

| 层级 | API | 能力 | 本项目使用处 |
|:---|:---|:---|:---|
| **低级** | `DeepSeekChatModel.call()` | 纯 LLM 调用，无记忆无拦截 | [DeepSeekChatController](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekChatController.java) |
| **中级** | `ChatClient.prompt().user().call()` | 带 Advisor 链、记忆、系统提示词 | [ChatClientController](ai-robot/src/main/java/com/tefire/ai_robot/controller/ChatClientController.java) |
| **高级** | Spring AI Agent（规划中） | 自主规划、工具调用、多步执行 | 尚未实现 |

#### 本项目对应

`ChatClient` 是本项目的核心编排器，它的 Fluent API 让调用链一目了然：

```java
// 同步调用
String answer = chatClient.prompt()
        .user(message)           // 设置用户消息
        .call()                  // 执行整个 Advisor 链 + LLM 调用
        .content();              // 提取纯文本回答

// 流式调用（带记忆）
Flux<String> stream = chatClient.prompt()
        .user(message)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))  // 指定会话 ID
        .stream()               // 流式输出
        .content();             // 提取纯文本流
```

---

### 第一部分小结

把 Agent 比作一个**智能客服人员**：

| 架构层 | 类比 | 本项目核心类 |
|:---|:---|:---|
| LLM 引擎 | 大脑（理解和生成语言） | `DeepSeekChatModel` |
| 规划与推理 | 深度思考能力 | `DeepSeekR1ChatController` + `DeepSeekAssistantMessage` |
| 记忆系统 | 记住对话内容 | `MessageWindowChatMemory` + `MessageChatMemoryAdvisor` |
| 工具调用 | 动手操作（查系统、发邮件） | 待建设 |
| 知识/RAG | 查阅知识库 | 待建设 |
| Advisor 链 | 质检 / 记录 / 监督 | `SimpleLoggerAdvisor` + `MyLoggerAdvisor` |
| 编排器 | 总指挥（协调一切） | `ChatClient` |

---

## 第二部分：从用户 Prompt 到最终输出的全流程

### 2.0 流程全景图

现在我们把所有组件串起来，追踪一个用户请求的**完整生命周期**。以 `/v2/ai/generateStream` 为例：

```
用户浏览器 ──GET /v2/ai/generateStream?message=你好&chatId=session_123──▶

┌──────────────────────────────────────────────────────────────────────┐
│ STEP 1 ─ Controller 接收                                             │
│   ChatClientController.generateStream("你好", "session_123")          │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 2 ─ Advisor 前置拦截链                                           │
│   SimpleLoggerAdvisor: "收到请求"                                     │
│   MessageChatMemoryAdvisor: 准备加载记忆...                            │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 3 ─ 记忆检索                                                     │
│   从 MessageWindowChatMemory 中加载 session_123 的历史消息              │
│   结果: [User("你好"), Assistant("你好！有什么可以帮您？"), ...]        │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 4 ─ Prompt 组装                                                  │
│   ┌──────────────────────────────────┐                               │
│   │ System: "扮演一名拼多多客服"       │  ← defaultSystem()            │
│   ├──────────────────────────────────┤                               │
│   │ User: "你好"                      │  ← 历史消息1                   │
│   │ Assistant: "你好！有什么可以帮您？" │                               │
│   │ User: "我要退货"                  │  ← 历史消息2                   │
│   │ Assistant: "请问您的订单号是？"    │                               │
│   ├──────────────────────────────────┤                               │
│   │ User: "你好"                      │  ← 当前用户输入                │
│   └──────────────────────────────────┘                               │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 5 ─ LLM 推理调用                                                 │
│   HTTP POST https://api.deepseek.com/v1/chat/completions              │
│   Header: Authorization: Bearer sk-xxxx                               │
│   Body: { model: "deepseek-v4-flash", messages: [...], stream: true } │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 6 ─ 响应流式解析                                                 │
│   SSE chunk → ChatResponse → getResult().getOutput().getText()        │
│   "你" → "好" → "！" → "请" → "问" → "有" → "什" → "么"...           │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 7 ─ 流式推送给浏览器                                             │
│   Flux<String> → SSE → 逐字渲染 → 用户体验"打字"效果                   │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 8 ─ 记忆持久化                                                   │
│   MessageChatMemoryAdvisor 后置处理:                                   │
│   保存 User("你好") + Assistant("你好！请问有什么可以帮您...")          │
└──────────────────────────┬───────────────────────────────────────────┘
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│ STEP 9 ─ Advisor 后置拦截                                             │
│   SimpleLoggerAdvisor: "响应完成，耗时 1.2s, Token: 45"               │
└──────────────────────────────────────────────────────────────────────┘

用户浏览器 ◀── SSE 流结束 ◀──
```

---

### 2.1 步骤 1：用户输入 → Controller 接收

```java
// ChatClientController.java - 第39-48行
@GetMapping(value = "/generateStream", produces = "text/html;charset=utf-8")
public Flux<String> generateStream(
        @RequestParam(value = "message", defaultValue = "你是谁？") String message,
        @RequestParam(value = "chatId") String chatId) {

    return chatClient.prompt()
            .user(message)                                          // 设置用户消息
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)) // 传递会话ID
            .stream()                                               // 启动流式调用
            .content();                                             // 提取文本内容
}
```

HTTP 请求到达后，Spring MVC 做了三件事：
1. **路由匹配**: `/v2/ai/generateStream` → `ChatClientController.generateStream()`
2. **参数绑定**: URL 中的 `message` 和 `chatId` 绑定到方法参数
3. **返回类型识别**: `Flux<String>` → 自动启用 SSE（Server-Sent Events）传输模式

---

### 2.2 步骤 2：Advisor 拦截链前置处理

`ChatClient` 内部维护了一条 Advisor 链。当 `.stream()` 被调用时，请求会**依次经过**每个 Advisor 的前置处理：

```java
// SimpleLoggerAdvisor（Spring AI 内置）的前置处理逻辑示意：
// 在 DEBUG 级别记录：
//   "Request: model=deepseek-v4-flash, messages=[SystemMessage(...), UserMessage(你好)]"

// MessageChatMemoryAdvisor 的前置处理逻辑示意：
//   "检测到 CONVERSATION_ID=session_123，查找记忆..."
```

Advisor 链采用**责任链模式**——每个 Advisor 调用 `chain.nextCall(request)` 把请求传给下一个，自己决定在前置还是后置做处理。

---

### 2.3 步骤 3：记忆检索 —— 加载历史对话

`MessageChatMemoryAdvisor` 在调用 LLM **之前**（前置处理），执行以下逻辑：

```
1. 读取参数: ChatMemory.CONVERSATION_ID = "session_123"
2. 调用: chatMemory.get("session_123", maxMessages=50)
3. 返回: List<Message> = [
     UserMessage("你好"),
     AssistantMessage("你好！有什么可以帮您？"),
     UserMessage("我要退货"),
     AssistantMessage("请问您的订单号是？"),
     ...
   ]
```

> **⚠️ 窗口截断**: 如果 `session_123` 的对话超过 50 条，`MessageWindowChatMemory` 会按 FIFO（先进先出）淘汰最旧的消息，只保留最近的 50 条。

---

### 2.4 步骤 4：Prompt 组装 —— 把"食材"放进锅里

这是 Agent 最关键的一步——构建发送给 LLM 的完整 Prompt。最终发给 DeepSeek 的消息数组结构如下：

```json
{
  "model": "deepseek-v4-flash",
  "messages": [
    {
      "role": "system",
      "content": "扮演一名拼多多客服"              // ← ChatClientConfig.defaultSystem()
    },
    {
      "role": "user",
      "content": "你好"                            // ← 历史消息1（来自 ChatMemory）
    },
    {
      "role": "assistant",
      "content": "你好！有什么可以帮您？"            // ← 历史消息2（来自 ChatMemory）
    },
    {
      "role": "user",
      "content": "我要退货"                        // ← 历史消息3（来自 ChatMemory）
    },
    {
      "role": "assistant",
      "content": "请问您的订单号是？"               // ← 历史消息4（来自 ChatMemory）
    },
    {
      "role": "user",
      "content": "你好"                            // ← 当前用户输入
    }
  ],
  "temperature": 0.8,
  "stream": true
}
```

三个来源在这里汇合：
- **System Prompt** → 来自 `ChatClientConfig` 的 `.defaultSystem("扮演一名拼多多客服")`
- **历史消息** → 来自 `MessageChatMemoryAdvisor` 从 `ChatMemory` 中检索
- **当前输入** → 来自 Controller 接收的 `message` 参数

---

### 2.5 步骤 5：LLM 推理调用

`DeepSeekChatModel` 内部做的事情：

```
1. 序列化: 将 Spring AI 的 Prompt 对象 → DeepSeek API 的 JSON 格式
2. 发送: HTTP POST → https://api.deepseek.com/v1/chat/completions
3. 请求头:
   - Authorization: Bearer sk-ccccf59eabc64d1b9804cec7e09281df
   - Content-Type: application/json
4. 关键参数:
   - stream: true     → 告诉服务器用 SSE 流式返回（如果不设置则等全部生成完再返回）
5. 接收: SSE chunk → 解析为 Spring AI 的 ChatResponse 对象
```

**流式输出 vs 一次性输出**:

| | 流式 (stream=true) | 非流式 (stream=false) |
|:---|:---|:---|
| 首字延迟 | ~200ms | 全量生成完（可能 5-10s） |
| 用户体验 | 逐字显示，即时反馈 | 等待后一次性显示 |
| 实现方式 | `Flux<String>` / SSE | `String` |
| 本项目用法 | `/generateStream` 端点 | `/generate` 端点 |

---

### 2.6 步骤 6：推理链处理（DeepSeek R1 专属）

对于普通模型（`deepseek-v4-flash`），每个 SSE chunk 直接包含生成的文本。但对于推理模型（`deepseek-v4-pro`），DeepSeek API 返回的每个 chunk 包含**两个字段**：

```json
{
  "choices": [{
    "delta": {
      "reasoning_content": "让我分析一下这个问题...首先...其次...",   // 思考过程
      "content": null                                               // 还没有正式回答
    }
  }]
}
// ... 多个 chunk 后 ...
{
  "choices": [{
    "delta": {
      "reasoning_content": null,                                    // 思考结束
      "content": "您好！根据您的订单情况..."                         // 正式回答开始
    }
  }]
}
```

`DeepSeekR1ChatController` 的处理逻辑精准地区分这两个阶段：

```java
// DeepSeekR1ChatController.java - 第43-72行
AtomicBoolean needSeparator = new AtomicBoolean(true);  // 确保分隔线只出现一次

return chatModel.stream(prompt).mapNotNull(chatResponse -> {
    DeepSeekAssistantMessage msg =
        (DeepSeekAssistantMessage) chatResponse.getResult().getOutput();

    String reasoningContent = msg.getReasoningContent(); // 思考内容
    String text = msg.getText();                          // 正式回答

    String rawContent;
    boolean isTextResponse = false;

    if (text == null) {
        rawContent = reasoningContent;  // 阶段1: 还在思考，输出思考过程
    } else {
        rawContent = text;              // 阶段2: 思考完毕，输出正式回答
        isTextResponse = true;
    }

    // \n → <br> 保证浏览器正确换行
    String processed = rawContent != null ?
        rawContent.replace("\n", "<br>") : rawContent;

    // 在思考→回答的切换点插入 <hr> 分隔线（仅一次）
    if (isTextResponse && needSeparator.compareAndSet(true, false)) {
        processed = "<hr>" + processed;
    }

    return processed;
});
```

> **🔑 关键细节**: `AtomicBoolean.compareAndSet(true, false)` 保证了**线程安全**且**只执行一次**——即使多个 SSE chunk 同时到达，分隔线也只会插入一次。

---

### 2.7 步骤 7：响应流式输出

`Flux<String>` 是 Reactor 框架的响应式流类型。它和普通 `List<String>` 的区别在于**数据不是一次性返回的**：

```java
// 非流式：等所有数据就绪，一次性返回
String answer = chatClient.prompt().user(message).call().content();
// 用户等待 5 秒 → 突然看到完整回答

// 流式：来一个 token 发一个 token
Flux<String> stream = chatClient.prompt().user(message).stream().content();
// 用户等待 0.3 秒 → "你" → "好" → "！" → ... 逐字显示
```

浏览器端通过 SSE（Server-Sent Events）接收数据流。由于 Controller 设置了 `produces = "text/html;charset=utf-8"`，`<br>` 标签和 `<hr>` 标签都能被浏览器正确渲染。

---

### 2.8 步骤 8：记忆持久化 —— 保存本轮对话

LLM 完整响应返回后，`MessageChatMemoryAdvisor` 在**后置处理**中执行：

```
1. 从响应中提取 AssistantMessage: "你好！请问有什么可以帮您？"
2. 调用 chatMemory.add("session_123", userMessage)
3. 调用 chatMemory.add("session_123", assistantMessage)
4. 检查窗口大小是否超过 50 条，超过则淘汰最旧消息
```

这样，下一次同一 `chatId` 的请求就能"记起"本次对话内容。

#### 三个 Controller 的记忆对比

| 端点 | 有记忆？ | 原因 |
|:---|:---|:---|
| `/ai/*` | ❌ 无 | 直接用 `DeepSeekChatModel`，没有 `MessageChatMemoryAdvisor` |
| `/v1/ai/*` | ❌ 无 | 同上 |
| `/v2/ai/generate` | ⚠️ 隐式 | 用了 `ChatClient`（有默认 Advisor），但没传 `chatId`，行为不确定 |
| `/v2/ai/generateStream` | ✅ 有 | 传了 `chatId`，每个会话独立记忆 |

---

### 2.9 步骤 9：Advisor 拦截链后置处理

LLM 响应返回后，所有 Advisor 的**后置逻辑**沿链逆序执行：

```
DeepSeekChatModel 返回 ChatResponse
    │
    ▼
MessageChatMemoryAdvisor 后置: 保存对话到 ChatMemory
    │
    ▼
MyLoggerAdvisor 后置: log.info("## 请求出参: {}", response);
    │
    ▼
SimpleLoggerAdvisor 后置: DEBUG "Response: tokens=45, finish_reason=stop"
    │
    ▼
最终返回给 Controller → 推送给用户
```

`MyLoggerAdvisor` 虽然当前被注释掉了，但它的设计展示了后置处理的标准模式——**在 `chain.nextCall()` 返回之后**、**return 之前**执行。

---

### 第二部分小结

| 步骤 | 谁在做 | 做了什么 | 耗时占比 |
|:---|:---|:---|:---|
| 1 | Controller | 接收请求，参数绑定 | < 1ms |
| 2 | Advisor 链 | 前置拦截（日志、校验） | < 1ms |
| 3 | MessageChatMemoryAdvisor | 检索历史对话 | < 1ms (内存) |
| 4 | ChatClient | 组装 System + History + User Prompt | < 1ms |
| 5 | DeepSeekChatModel | **HTTP 调用 LLM API** | **> 95%** |
| 6 | DeepSeekR1ChatController | 提取推理链（可选） | < 1ms |
| 7 | Flux | 逐 token 流式推送 | 与步骤5重叠 |
| 8 | MessageChatMemoryAdvisor | 保存对话到记忆 | < 1ms |
| 9 | Advisor 链 | 后置拦截（日志、监控） | < 1ms |

> **🔑 核心认知**: 步骤 5（LLM API 调用）占据了 **95% 以上**的时间。所有优化都应该围绕这一环节——选择更快的模型、启用流式输出减少感知延迟、对常见问题做缓存。

---

## 第三部分：项目代码与 Agent 组件的映射

### 3.0 映射总表

下表是**全文核心**——把 Agent 架构的每个理论组件，精确映射到 `ai-robot` 项目中的具体代码：

| Agent 架构组件 | 角色/职责 | 项目中的类/接口/配置 | 文件位置 |
|:---|:---|:---|:---|
| **LLM 引擎** | 模型推理，理解与生成文本 | `DeepSeekChatModel` | Spring AI 自动配置（`application.yml` 驱动） |
| **模型配置** | 控制模型行为（温度、Token 等） | `DeepSeekChatOptions` | [DeepSeekR1ChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java):35-37 |
| **Agent 编排器** | 串联所有组件，统一调度 | `ChatClient` | [ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java):31-39 |
| **系统提示词** | 设定 Agent 的角色和行为边界 | `ChatClientConfig.defaultSystem("扮演一名拼多多客服")` | [ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java):33 |
| **短期记忆存储** | 滑动窗口存储对话历史 | `MessageWindowChatMemory` | [ChatMemoryConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatMemoryConfig.java):27-30 |
| **记忆仓储抽象** | 记忆的底层存储接口 | `ChatMemoryRepository` | [ChatMemoryConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatMemoryConfig.java):23 |
| **记忆管理器** | 自动存取对话记忆（Advisor） | `MessageChatMemoryAdvisor` | [ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java):36 |
| **内置日志拦截器** | DEBUG 级别请求/响应日志 | `SimpleLoggerAdvisor` | [ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java):34 |
| **自定义日志拦截器** | INFO 级别请求/响应日志 | `MyLoggerAdvisor` | [MyLoggerAdvisor.java](ai-robot/src/main/java/com/tefire/ai_robot/advisor/MyLoggerAdvisor.java) |
| **推理链处理器** | 提取并展示模型思维链 | `DeepSeekR1ChatController` | [DeepSeekR1ChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java) |
| **推理消息载体** | 承载推理内容 + 正式回答 | `DeepSeekAssistantMessage` | [DeepSeekR1ChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java):46 |
| **提示词构建** | 构建发送给 LLM 的消息对象 | `Prompt` / `UserMessage` | [DeepSeekChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekChatController.java):36-39 |
| **流式响应** | 基于 Reactor 的响应式流 | `Flux<String>` | 所有 Controller 的 generateStream 方法 |
| **全局 Agent 配置** | 统一管理所有配置项 | `application.yml` | [application.yml](ai-robot/src/main/resources/application.yml) |
| **应用入口** | Spring Boot 启动与自动装配 | `AiRobotApplication` | [AiRobotApplication.java](ai-robot/src/main/java/com/tefire/ai_robot/AiRobotApplication.java) |
| **依赖管理** | Maven 依赖声明与版本控制 | `pom.xml` | [pom.xml](ai-robot/pom.xml) |

---

### 3.1 三个 Controller 的渐进式对比

这是理解 Agent 架构演进的最佳视角——三个 Controller 代表了**三种不同成熟度的 Agent 实现**：

| 维度 | `/ai` | `/v1/ai` | `/v2/ai` |
|:---|:---|:---|:---|
| **文件** | [DeepSeekChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekChatController.java) | [DeepSeekR1ChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java) | [ChatClientController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/ChatClientController.java) |
| **底层组件** | `DeepSeekChatModel` 直接调用 | `DeepSeekChatModel` + 自定义 Options | `ChatClient` + Advisor 链 |
| **默认模型** | `deepseek-v4-flash` | **`deepseek-v4-pro`** (推理) | `deepseek-v4-flash` |
| **对话记忆** | ❌ | ❌ | ✅ |
| **系统提示词** | ❌ | ❌ | ✅ ("扮演一名拼多多客服") |
| **Advisor 拦截** | ❌ | ❌ | ✅ (日志 + 记忆管理) |
| **推理链展示** | ❌ | ✅ (思维链 + 分隔线) | 取决于模型 |
| **流式输出** | ✅ | ✅ | ✅ |
| **同步接口** | ✅ (`/generate`) | ❌ | ✅ (`/generate`) |
| **学习价值** | 理解最底层 API | 理解推理模型 | **理解完整 Agent** |
| **适合场景** | 最简单的模型调用测试 | 需要展示推理过程 | **生产级对话 Agent** |

**演进关系**：

```
/ai (裸模型调用) ──加上推理链处理──▶ /v1/ai (推理模型)
                                          │
/ai (裸模型调用) ──加上ChatClient+记忆──▶ /v2/ai (完整Agent)
                                          │
                              加上推理模型 = 最强形态
```

---

### 3.2 逐文件详解

#### `AiRobotApplication.java` —— 启动入口

```java
@SpringBootApplication
public class AiRobotApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiRobotApplication.class, args);
    }
}
```

`@SpringBootApplication` 是一个组合注解，等价于：
- `@Configuration` → 允许定义 Bean
- `@EnableAutoConfiguration` → **自动装配** Spring AI 的 `DeepSeekChatModel`（从 `application.yml` 读取配置）
- `@ComponentScan` → 扫描并注册 Controller、Config 等组件

它是整个 Agent 系统的"电源开关"。

#### `DeepSeekChatController.java` —— 最朴素的调用

```java
// 同步调用：等模型全部生成完，一次性返回
@GetMapping("/generate")
public String generate(@RequestParam String message) {
    return deepSeekChatModel.call(message);  // 一行代码完成 LLM 调用
}

// 流式调用：逐 token 推送
@GetMapping("/generateStream")
public Flux<String> generateStream(@RequestParam String message) {
    Prompt prompt = new Prompt(new UserMessage(message));
    return deepSeekChatModel.stream(prompt)
            .mapNotNull(r -> r.getResult().getOutput().getText());
}
```

**没有系统提示词、没有记忆、没有 Advisor**。这是一个很好的"Hello World"起点——先让模型能说话，再逐步加功能。

#### `DeepSeekR1ChatController.java` —— 让思维可见

在裸模型调用的基础上增加了：
1. **动态模型切换**: `DeepSeekChatOptions.builder().model("deepseek-v4-pro").build()`
2. **思维链提取**: 从 `DeepSeekAssistantMessage` 中分离 `reasoningContent` 和 `text`
3. **可视化分隔**: 用 `<hr>` 标签分隔思考过程和最终回答

#### `ChatClientController.java` —— 生产级写法

在 `ChatClient` 的加持下，三行代码实现了**带记忆的完整 Agent 对话**：

```java
return chatClient.prompt()
        .user(message)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
        .stream()
        .content();
```

`ChatClient` 内部自动处理了：系统提示词注入、Advisor 链执行、记忆自动存取。

#### `ChatClientConfig.java` —— 编排中枢

这是整个项目的"指挥中心"——它决定了 Agent 使用什么模型、什么角色、什么 Advisor：

```java
@Bean
public ChatClient chatClient(DeepSeekChatModel chatModel) {
    return ChatClient.builder(chatModel)                    // 1. 以 DeepSeek 为引擎
        .defaultSystem("扮演一名拼多多客服")                  // 2. 设定全局系统提示词
        .defaultAdvisors(                                   // 3. 注册 Advisor 链
            new SimpleLoggerAdvisor(),                      //    - 日志记录
            MessageChatMemoryAdvisor.builder(chatMemory)    //    - 记忆管理
                .build()
        )
        .build();
}
```

#### `ChatMemoryConfig.java` —— 记忆配置

决定 Agent "能记住多少"：

```java
@Bean
public ChatMemory chatMemory() {
    return MessageWindowChatMemory.builder()
            .maxMessages(50)                             // 最多记住 50 条消息
            .chatMemoryRepository(chatMemoryRepository)  // 默认内存存储
            .build();
}
```

#### `MyLoggerAdvisor.java` —— 自定义拦截器模板

这是一个**可复用的自定义 Advisor 模板**——复制它，修改 `adviseCall` 中的逻辑，就能实现 Token 统计、内容审核、缓存等自定义功能。

#### `application.yml` —— 配置文件全景

```yaml
server:
  port: 8080                     # 服务端口

spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_KEY}   # 环境变量引用（安全！不要硬编码 key）
      base-url: https://api.deepseek.com
      chat:
        model: deepseek-v4-flash # 默认模型
        temperature: 0.8         # 创造力参数

logging:
  level:
    '[org.springframework.ai.chat.client.advisor]': debug  # Advisor 调试日志
```

#### `pom.xml` —— 核心依赖

```xml
<!-- 一行依赖接入 DeepSeek -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-deepseek</artifactId>
</dependency>

<!-- 版本统一管理 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 第四部分：当前项目能力与扩展方向

### 已具备 ✅

| 能力 | 实现方式 |
|:---|:---|
| LLM 同步/流式调用 | `DeepSeekChatModel.call()` / `.stream()` |
| 推理模型 + 思维链展示 | `DeepSeekR1ChatController` + `DeepSeekAssistantMessage` |
| 短期对话记忆 | `MessageWindowChatMemory` (50 条窗口) |
| Advisor 拦截链 | `SimpleLoggerAdvisor` + `MessageChatMemoryAdvisor` |
| 系统提示词配置 | `ChatClientConfig.defaultSystem()` |
| 多会话隔离 | 通过 `chatId` 区分不同会话 |
| 流式输出 (SSE) | `Flux<String>` + Reactor |
| 环境变量安全管理 | `application.yml` 中的 `${DEEPSEEK_KEY}` |

### 待建设 🔮

| 能力 | 重要性 | Spring AI 实现方式 |
|:---|:---|:---|
| **RAG 知识库检索** | ⭐⭐⭐⭐⭐ | `DocumentReader` + `VectorStore` + `QuestionAnswerAdvisor` |
| **Function Calling** | ⭐⭐⭐⭐⭐ | `@Tool` 注解 + `ToolCallback` |
| **长期记忆（向量存储）** | ⭐⭐⭐⭐ | `VectorStore` (Milvus / Pinecone / PGVector) |
| **多模态输入（图片）** | ⭐⭐⭐⭐ | DeepSeek V4 已支持 `vision` 模式 |
| **提示词注入防御** | ⭐⭐⭐ | 自定义 Advisor 做输入过滤 |
| **Token 用量统计** | ⭐⭐⭐ | 自定义 Advisor 统计+告警 |
| **响应缓存** | ⭐⭐⭐ | 自定义 Advisor + Redis |
| **MCP 协议集成** | ⭐⭐⭐ | Spring AI MCP Starter |
| **多 Agent 协作** | ⭐⭐ | Spring AI Agent API（规划中） |
| **会话持久化** | ⭐⭐ | 自定义 `ChatMemoryRepository` 实现（Redis/DB） |
| **监控与可观测性** | ⭐⭐ | Micrometer + Prometheus |
| **A/B 模型路由** | ⭐ | 自定义 Advisor 按规则切换模型 |

---

## 总结

### 核心公式

> **Agent = LLM + Memory + Tools + Orchestration**

| 要素 | 作用 | 本项目实现 |
|:---|:---|:---|
| **LLM** | 大脑，理解与生成 | `DeepSeekChatModel` |
| **Memory** | 海马体，记住对话 | `MessageWindowChatMemory` + `MessageChatMemoryAdvisor` |
| **Tools** | 手脚，执行操作 | 待建设（`@Tool` / Function Calling） |
| **Orchestration** | 中枢，协调一切 | `ChatClient` + Advisor 链 |

### Spring AI 的分层哲学

Spring AI 为 Agent 开发提供了**渐进式的抽象层次**：

```
DeepSeekChatModel.call("你好")               ← 最低层：一行代码调 LLM
    ↓ 加上 Prompt 构建
Prompt + UserMessage + stream()              ← 结构化消息 + 流式输出
    ↓ 加上 ChatClient
ChatClient + Advisor + Memory                ← 完整 Agent 编排
    ↓ 加上 Tool + RAG
Agent + Function Calling + VectorStore       ← 终极形态（持续建设中）
```

每一层都是对下一层的封装和增强，你可以**按需选择**适合当前场景的抽象级别。

### 推荐学习路径

1. **跑通本项目**: Clone → 配置 `DEEPSEEK_KEY` → 依次访问 `/ai`、`/v1/ai`、`/v2/ai` 三个端点
2. **加一个自己的 Advisor**: 复制 `MyLoggerAdvisor.java`，改成统计 Token 用量的逻辑
3. **接入 RAG**: 用 Spring AI 的 `VectorStore` 接入一个向量数据库，让 Agent 能回答私有文档内容
4. **接入 Tool Calling**: 写一个 `@Tool` 方法，让 Agent 能调用你的业务 API

---

## 附录

### A. 关键术语中英对照

| 中文 | English | 一句话解释 |
|:---|:---|:---|
| 智能体 | Agent | 能自主感知、规划、执行任务的 AI 系统 |
| 大语言模型 | LLM (Large Language Model) | 生成文本的深度学习模型 |
| 提示词 | Prompt | 发给模型的输入文本 |
| 思维链 | CoT (Chain-of-Thought) | 模型显式输出推理步骤 |
| 检索增强生成 | RAG (Retrieval-Augmented Generation) | 先从知识库检索，再基于检索结果生成回答 |
| 函数调用 | Function Calling | 模型输出调用哪个函数的指令，由系统执行 |
| 顾问/拦截器 | Advisor | 请求处理流程中的横切关注点（日志、记忆、安全） |
| 响应式流 | Flux (Reactor) | 支持背压的异步流式数据类型 |
| 服务器推送 | SSE (Server-Sent Events) | HTTP 协议上的单向流式传输 |
| 令牌 | Token | 文本的最小处理单元（约 0.75 个英文单词或 0.5 个中文字） |
| 向量嵌入 | Embedding | 将文本转为固定长度的数值向量 |
| 向量数据库 | VectorStore | 专门存储和检索向量数据的数据库 |
| 模型上下文协议 | MCP (Model Context Protocol) | Agent 与外部工具/数据源的标准化连接协议 |

### B. 常见问题 FAQ

**Q: `temperature` 设多少合适？**

| 场景 | 建议值 |
|:---|:---|
| 代码生成、事实性问答 | 0 ~ 0.3 |
| 客服对话、信息提取 | 0.3 ~ 0.6 |
| 通用对话、文案润色 | 0.6 ~ 0.9 |
| 创意写作、头脑风暴 | 0.9 ~ 1.5 |

**Q: 为什么 `DeepSeekR1ChatController` 返回很慢？**

推理模型（`deepseek-v4-pro`）会先经过内部深度思考，这个过程可能持续 10-30 秒。如果你不需要展示思维链，使用 `deepseek-v4-flash` 会快很多。

**Q: `ChatMemory` 存在哪里？重启服务会丢吗？**

默认存储在 JVM 内存中（`InMemoryChatMemoryRepository`），**重启即丢失**。生产环境可以替换为 Redis 或数据库实现。

**Q: 怎么添加新的 Advisor？**

1. 创建类实现 `CallAdvisor` 接口（参考 [MyLoggerAdvisor.java](ai-robot/src/main/java/com/tefire/ai_robot/advisor/MyLoggerAdvisor.java)）
2. 在 [ChatClientConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/ChatClientConfig.java) 的 `.defaultAdvisors(...)` 中注册

**Q: 如何在 `/v2/ai/generate` 中也启用记忆？**

给 `.advisors()` 传参即可，和 `generateStream` 一样：

```java
chatClient.prompt()
    .user(message)
    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
    .call()
    .content();
```

### C. 参考资源

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [DeepSeek API 文档](https://api-docs.deepseek.com/)
- [Spring AI DeepSeek Starter](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)
- [本项目 GitHub](https://github.com/TE-Fire/RAGBot)

---

> **📝 本文档基于 `ai-robot` 项目 v0.0.1-SNAPSHOT 编写。**  
> 项目持续演进中，最新代码以仓库为准。
