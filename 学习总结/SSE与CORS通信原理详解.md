# 浏览器—服务器 SSE 通信与 CORS 原理详解

> **适用项目**: `ai-robot` (Spring Boot) + `ai-robot-vue3` (Vue3 前端)  
> **核心问题**: 浏览器如何与后端"保持连接"并逐字接收 AI 回复？CORS 在这个过程中起了什么作用？  
> **作者**: TE-Fire  
> **日期**: 2026-07-09

---

## 前言：从一个场景说起

打开 `ai-robot-vue3` 聊天页面，输入"帮我写一首诗"，点击发送。你会发现 AI 的回复**一个字一个字地冒出来**——不是等 10 秒后突然显示全文，而是像打字机一样逐字呈现。

这背后的技术叫 **SSE（Server-Sent Events，服务器推送事件）**。但要理解它，我们需要先理解普通的 HTTP 请求是什么样的，然后再看 SSE 有什么不同，最后弄明白浏览器为什么会"拦截"跨域请求（也就是 CORS 干了什么）。

本文由浅入深，分四层递进：

| 层级 | 内容 | 目标 |
|:---|:---|:---|
| **第一层** | 普通 HTTP 请求—响应模型 | 建立基础认知 |
| **第二层** | SSE：让服务器"持续推送" | 理解流式传输的本质 |
| **第三层** | CORS：浏览器的跨域安全机制 | 理解为什么需要 `CorsConfig` |
| **第四层** | 实战：JS + Spring Boot 完整通信链路 | 把代码串起来 |

---

## 第一层：普通 HTTP 请求—响应模型

### 1.1 一次"普通"的对话

你在浏览器输入 `http://localhost:8080/v1/ai/generate?message=你好`，发生了什么？

```
浏览器                                        服务器 (Spring Boot)
  │                                               │
  │──── 1. 建立 TCP 连接 ──────────────────────▶  │   (三次握手)
  │                                               │
  │──── 2. 发送 HTTP 请求 ──────────────────────▶ │
  │     GET /v1/ai/generate?message=你好           │
  │     Host: localhost:8080                       │
  │                                               │
  │                                               │   3. 服务器处理
  │                                               │      调用 DeepSeekChatModel
  │                                               │      等待 LLM 返回完整回答
  │                                               │
  │◀─── 4. 返回 HTTP 响应 ────────────────────── │
  │     HTTP/1.1 200 OK                            │
  │     Content-Type: text/plain                   │
  │     Content-Length: 42                         │
  │                                               │
  │     "你好！我是 DeepSeek AI 助手..."            │
  │                                               │
  │──── 5. TCP 连接关闭 ────────────────────────▶ │   (四次挥手)
  │                                               │
  ▼                                               ▼
显示完整回答                                    连接结束
```

**关键特征**:
- **一问一答**：客户端发一个请求，服务器回一个响应，连接关闭
- **服务器被动**：服务器不能主动给客户端发消息
- **全量返回**：等所有数据就绪后，一次性打包返回（`Content-Length: 42` 告诉浏览器"我有 42 字节的数据"）

### 1.2 HTTP 报文的真实面目

用 Chrome 开发者工具的 Network 面板，你能看到真实的请求报文：

**请求**（浏览器 → 服务器）:
```http
GET /v1/ai/generate?message=你好 HTTP/1.1
Host: localhost:8080
Accept: text/html,application/xhtml+xml
Accept-Language: zh-CN,zh;q=0.9
Connection: keep-alive
```

**响应**（服务器 → 浏览器）:
```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=utf-8
Content-Length: 42
Date: Wed, 09 Jul 2026 12:00:00 GMT

你好！我是 DeepSeek AI 助手，很高兴为你服务！
```

> **💡 关键洞察**: 普通 HTTP 的核心假设是 **"数据是有限的、已知的"**——服务器在发送响应之前就知道要发多少字节（`Content-Length`），发完就关连接。但 AI 流式回复不满足这个假设——服务器在开始发送时**不知道**最终会生成多少 token。

---

## 第二层：SSE —— 让服务器"持续推送"

### 2.1 为什么普通 HTTP 不行？

回到我们的 AI 聊天场景：
- LLM 生成回复是**逐 token 的**——每 50ms 左右吐一个字
- 如果等全部生成完再返回（普通 HTTP），用户干等 10 秒，体验极差
- 我们需要一种机制：**服务器生成一个字，就立刻推给浏览器一个字**

这就是 SSE。

### 2.2 SSE 的本质：把一次响应拉长

```
浏览器                                        服务器 (Spring Boot)
  │                                               │
  │──── GET /v1/ai/generateStream?message=你好 ──▶│
  │     Accept: text/event-stream                  │
  │                                               │
  │                                               │   DeepSeekChatModel.stream()
  │                                               │   逐 token 接收 LLM 输出
  │                                               │
  │◀──  HTTP/1.1 200 OK ──────────────────────── │
  │     Content-Type: text/event-stream            │  ← 关键！告诉浏览器"这是 SSE"
  │     Transfer-Encoding: chunked                 │  ← 关键！不知道总长度，分块传输
  │     Connection: keep-alive                     │  ← 关键！保持连接不关闭
  │                                               │
  │◀──  data: 你好                                  │  ← 第1个 token
  │                                               │
  │◀──  data: ！                                    │  ← 第2个 token
  │                                               │
  │◀──  data: 我是                                  │  ← 第3个 token
  │                                               │
  │◀──  data: DeepSeek                             │  ← ...持续推送...
  │                                               │
  │◀──  data: AI                                   │
  │                                               │
  │◀──  data: 助手                                  │
  │                                               │
  │◀──  (LLM 生成完毕，Flux 流结束)                  │
  │                                               │
  │◀──  (服务器关闭连接)                             │
  │                                               │
  ▼                                               ▼
逐字渲染在页面上                                 连接结束
```

### 2.3 SSE 协议三要素

SSE 不是什么新技术——它就是 **HTTP 协议**的一种使用方式，只不过响应头设置了三个关键字段：

| HTTP 响应头 | 值 | 作用 |
|:---|:---|:---|
| `Content-Type` | `text/event-stream` | 告诉浏览器"这不是普通响应，是 SSE 事件流" |
| `Transfer-Encoding` | `chunked` | 告诉浏览器"我不知道数据总长度，我会一块一块发" |
| `Connection` | `keep-alive` | 告诉浏览器"别断连，我还有更多数据" |

> **🔑 核心认知**: SSE **不是** WebSocket。SSE 是**单向**的（服务器 → 浏览器），基于普通 HTTP，更简单；WebSocket 是**双向**的，需要协议升级（HTTP → WebSocket），更复杂但更灵活。对于 AI 流式回复这种"客户端问一句，服务器源源不断回答"的场景，SSE 是最佳选择。

### 2.4 SSE 的数据格式

每个 SSE 消息的格式是固定的：

```
data: <消息内容>\n\n
```

- `data:` 是固定前缀
- 后面跟实际数据
- 以**两个换行符** `\n\n` 结束一条消息

例如服务器实际发送的原始字节流可能是：

```
data: 你好\n\n
data: ！\n\n
data: 我是\n\n
data: DeepSeek\n\n
```

浏览器端的 `EventSource` API 会自动解析这个格式——去掉 `data:` 前缀、识别消息边界，然后触发 `onmessage` 回调。

### 2.5 Spring Boot 如何开启 SSE

在 [DeepSeekChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekChatController.java) 中，一行代码搞定：

```java
@GetMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> generateStream(@RequestParam String message) {
    // ...
    return deepSeekChatModel.stream(prompt)
            .mapNotNull(r -> r.getResult().getOutput().getText());
}
```

这里两个关键点：

**① `produces = MediaType.TEXT_EVENT_STREAM_VALUE`**

`MediaType.TEXT_EVENT_STREAM_VALUE` 是一个常量，值为 `"text/event-stream"`。它做了两件事：
- 设置响应头 `Content-Type: text/event-stream`
- 告诉 Spring MVC 自动启用 SSE 传输模式

**② `Flux<String>`**

`Flux` 是 Reactor 框架的响应式流类型，代表"0 到 N 个异步发出的元素"。当 Spring MVC 发现返回值是 `Flux` 且 `Content-Type` 是 `text/event-stream` 时，会自动：
1. 设置 `Transfer-Encoding: chunked`
2. 将每个 `Flux` 元素包装为 `data: xxx\n\n` 格式
3. 一个接一个地推送给客户端

### 2.6 SSE vs WebSocket vs 普通 HTTP 对比

| | 普通 HTTP | SSE | WebSocket |
|:---|:---|:---|:---|
| **方向** | 客户端 → 服务器 → 客户端 | 服务器 → 客户端 | 双向 |
| **协议** | HTTP | HTTP | 独立协议（ws://） |
| **连接** | 用完即关 | 长连接 | 长连接 |
| **实现复杂度** | 最低 | 低 | 中 |
| **浏览器 API** | `fetch()` / `XMLHttpRequest` | `EventSource` | `new WebSocket()` |
| **自动重连** | 不适用 | ✅ 内置 | 需手动实现 |
| **适用场景** | 普通请求 | 通知推送、AI 流式输出 | 聊天室、协作编辑 |
| **本项目用法** | `/generate` 端点 | `/generateStream` 端点 | 未使用 |

---

## 第三层：CORS —— 浏览器的跨域安全机制

### 3.1 问题场景

前端跑在 `http://localhost:5173`（Vite 开发服务器），后端跑在 `http://localhost:8080`（Spring Boot）。虽然都在同一台机器上，但**端口不同就是跨域**。

```
浏览器 (localhost:5173) ──SSE 请求──▶ 服务器 (localhost:8080)
         │                                    │
         │      协议相同 (http)                │
         │      域名相同 (localhost)           │
         │      端口不同 (5173 ≠ 8080)  ← 这就叫"跨域"！
         │                                    │
```

### 3.2 同源策略：浏览器的安全底线

**同源策略（Same-Origin Policy）** 是浏览器最核心的安全机制。它的规则很简单：

> 一个网页只能访问**同源**的资源。"同源" = **协议 + 域名 + 端口**三者完全相同。

| 页面来源 | 请求目标 | 是否同源？ |
|:---|:---|:---|
| `http://localhost:5173` | `http://localhost:5173/api` | ✅ 同源 |
| `http://localhost:5173` | `http://localhost:8080/api` | ❌ **端口不同** |
| `http://a.com` | `https://a.com` | ❌ **协议不同** |
| `http://a.com` | `http://b.com` | ❌ **域名不同** |
| `http://a.com` | `http://a.com:8080` | ❌ **端口不同** |

> **🤔 为什么要有同源策略？**  
> 假设没有它——你登录了银行网站 `bank.com`，浏览器保存了你的登录 Cookie。然后你访问了一个恶意网站 `evil.com`，这个网站偷偷用你的 Cookie 向 `bank.com` 发起转账请求……没有同源策略的话，这个请求就能成功。同源策略保证了**A 网站的 JavaScript 不能随意访问 B 网站的数据**。

### 3.3 CORS：有规矩地"破例"

同源策略很好，但现代 Web 开发中前后端分离是常态——前端在 `localhost:5173`，后端在 `localhost:8080`，这是合法的需求。**CORS（Cross-Origin Resource Sharing，跨域资源共享）** 就是浏览器和服务器之间的一套"破例"协议：

> 服务器可以**主动声明**："我允许来自 `localhost:5173` 的请求访问我"。

> 浏览器看到这个声明后，就放行。

### 3.4 CORS 的工作原理

#### 简单请求 vs 预检请求

浏览器把跨域请求分为两类：

**① 简单请求（Simple Request）**——直接发送，浏览器检查响应头

满足以下**所有条件**才是简单请求：
- 方法是 `GET`、`HEAD` 或 `POST`
- 只使用了"安全的"请求头（如 `Accept`、`Content-Language`）
- `Content-Type` 是 `application/x-www-form-urlencoded`、`multipart/form-data` 或 `text/plain`

```
浏览器                                        服务器
  │                                               │
  │──── GET /v1/ai/generateStream ──────────────▶ │  (直接发送请求)
  │     Origin: http://localhost:5173              │
  │                                               │
  │◀─── HTTP/1.1 200 OK ──────────────────────────│
  │     Access-Control-Allow-Origin: *              │  ← 浏览器检查这个头
  │                                               │
  │  ✅ Access-Control-Allow-Origin 包含请求来源    │
  │  ✅ 允许 JS 读取响应                            │
  ▼                                               ▼
```

**② 预检请求（Preflight Request）**——先"探路"，再发送

不满足简单请求条件的（比如用了自定义请求头、`Content-Type: application/json`、方法是 `PUT`/`DELETE`），浏览器会**先发一个 `OPTIONS` 请求**"探路"：

```
浏览器                                        服务器
  │                                               │
  │──── OPTIONS /v1/ai/generateStream ──────────▶ │  (预检请求)
  │     Origin: http://localhost:5173              │
  │     Access-Control-Request-Method: POST        │
  │                                               │
  │◀─── HTTP/1.1 200 OK ──────────────────────────│
  │     Access-Control-Allow-Origin: *              │
  │     Access-Control-Allow-Methods: GET, POST     │
  │     Access-Control-Max-Age: 3600                │  ← 预检结果缓存 1 小时
  │                                               │
  │  ✅ 预检通过                                    │
  │                                               │
  │──── POST /v1/ai/generateStream ─────────────▶ │  (正式请求)
  │     ...                                       │
```

#### CORS 响应头详解

| 响应头 | 我们的配置 | 含义 |
|:---|:---|:---|
| `Access-Control-Allow-Origin` | `*`（任意域名） | 允许哪些域名访问 |
| `Access-Control-Allow-Methods` | `GET, POST, PUT, DELETE, OPTIONS` | 允许哪些 HTTP 方法 |
| `Access-Control-Allow-Headers` | `*`（任意请求头） | 允许携带哪些自定义请求头 |
| `Access-Control-Allow-Credentials` | `true` | 是否允许携带 Cookie |
| `Access-Control-Max-Age` | `3600`（秒） | 预检结果可以缓存多久 |

> **⚠️ 生产环境警告**: `.allowedOriginPatterns("*")` 允许**任意域名**访问你的后端，这在开发阶段没问题，但生产环境应该改为具体域名，如 `.allowedOriginPatterns("https://your-app.com")`。

### 3.5 本项目 CORS 配置详解

[CorsConfig.java](ai-robot/src/main/java/com/tefire/ai_robot/config/CorsConfig.java) 的每一行都是干什么的？

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                  // ①
            .allowedOriginPatterns("*")              // ②
            .allowedMethods("GET", "POST",           // ③
                "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")                     // ④
            .allowCredentials(true)                  // ⑤
            .maxAge(3600);                           // ⑥
    }
}
```

| 配置项 | 代码 | 实际效果 |
|:---|:---|:---|
| ① 路径匹配 | `addMapping("/**")` | 对**所有接口**生效（`/v1/ai/*`、`/v2/ai/*` 等） |
| ② 允许的域名 | `allowedOriginPatterns("*")` | 任何域名的页面都可以调用 |
| ③ 允许的方法 | `allowedMethods("GET", ...)` | 允许这 5 种 HTTP 方法跨域 |
| ④ 允许的请求头 | `allowedHeaders("*")` | 允许携带任意自定义请求头 |
| ⑤ 凭证支持 | `allowCredentials(true)` | 允许跨域请求携带 Cookie |
| ⑥ 预检缓存 | `maxAge(3600)` | 预检结果缓存 1 小时（减少 OPTIONS 请求次数） |

### 3.6 SSE 与 CORS 的特殊关系

SSE 请求是 `GET` 方法且使用标准请求头，属于**简单请求**——浏览器不会发预检 `OPTIONS` 请求。但是：

1. **`Origin` 头仍然存在**：浏览器会在 GET 请求中自动加上 `Origin: http://localhost:5173`
2. **服务器必须返回 CORS 头**：响应中必须包含 `Access-Control-Allow-Origin`，否则浏览器的 `EventSource` 会拒绝连接
3. **没有 CORS 配置会怎样**：

```
// 如果没有 CorsConfig，浏览器控制台会报错：
❌ Access to fetch at 'http://localhost:8080/v1/ai/generateStream'
   from origin 'http://localhost:5173' has been blocked by CORS policy:
   No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

---

## 第四层：实战 —— 完整通信链路拆解

### 4.1 一次 SSE 通信的完整时间线

现在我们把前面学的所有知识串起来，追踪一次完整的 AI 对话：

```
时间轴  Vue前端 (localhost:5173)               Spring Boot (localhost:8080)         DeepSeek API
────── ──────────────────────────── ───────────────────────────────────────── ───────────────────
       [用户点击发送按钮 "你好"]
 0ms   │ sendMessage() 被调用                     │                                    │
       │ chatList.push(用户消息)                   │                                    │
       │                                          │                                    │
 1ms   │ new EventSource(                         │                                    │
       │  "http://localhost:8080/v1/ai/           │                                    │
       │   generateStream?message=你好")           │                                    │
       │                                          │                                    │
       │──── HTTP GET ────────────────────────▶  │                                    │
       │    Host: localhost:8080                  │                                    │
       │    Origin: http://localhost:5173         │  ← 浏览器自动添加（因为跨域）        │
       │    Accept: text/event-stream             │  ← EventSource 自动设置            │
       │                                          │                                    │
       │                                          │  CorsConfig 检查 Origin 头         │
       │                                          │  → ✅ 匹配 "*"，允许跨域            │
       │                                          │                                    │
       │                                          │  DeepSeekChatController             │
       │                                          │  .generateStream("你好")            │
       │                                          │                                    │
       │                                          │  Prompt(message)                   │
       │                                          │                                    │
       │                                          │  deepSeekChatModel.stream(prompt)   │
       │                                          │                                    │
       │                                          │──── HTTP POST ─────────────────▶  │
       │                                          │    { model, messages, stream:true } │
       │                                          │                                    │
 200ms│◀── HTTP 200 OK ──────────────────────── │                                    │
       │   Content-Type: text/event-stream        │◀── SSE chunk: "你好" ───────────── │
       │   Access-Control-Allow-Origin: *         │                                    │
       │   Transfer-Encoding: chunked             │                                    │
       │                                          │                                    │
       │◀── data: 你好                             │◀── SSE chunk: "！" ─────────────── │
       │   EventSource 解析 → onmessage 触发       │                                    │
       │   responseText += "你好"                  │                                    │
       │   页面显示: "你好"                         │                                    │
       │                                          │                                    │
 300ms│◀── data: ！                                │◀── SSE chunk: "我是" ──────────────│
       │   页面显示: "你好！"                       │                                    │
       │                                          │                                    │
 350ms│◀── data: 我是                              │◀── SSE chunk: "DeepSeek" ──────────│
       │   页面显示: "你好！我是"                    │                                    │
       │                                          │                                    │
 ...  │   ...持续推送...                           │   ...持续接收...                    │   ...持续生成...
       │                                          │                                    │
 5s   │◀── data: 助手                              │◀── SSE chunk: [DONE] ───────────── │
       │   页面显示完整回答                          │                                    │
       │                                          │   Flux 流结束                       │
       │                                          │   关闭 HTTP 连接                    │
       │                                          │                                    │
       │   EventSource 检测到连接关闭               │                                    │
       │   触发 onerror (eventPhase=CLOSED)        │                                    │
       │   closeSSE() 清理资源                      │                                    │
       │                                          │                                    │
       ▼  用户看到完整回答                           ▼                                    ▼
```

### 4.2 后端：Spring Boot 的角色

让我们聚焦 [DeepSeekChatController.java](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekChatController.java) 第 34 行，理解它做了什么：

```java
@GetMapping(value = "/generateStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> generateStream(@RequestParam(value = "message",
    defaultValue = "你是谁？") String message) {

    Prompt prompt = new Prompt(new UserMessage(message));

    return deepSeekChatModel.stream(prompt)
            .mapNotNull(chatResponse ->
                chatResponse.getResult().getOutput().getText());
}
```

**`produces = MediaType.TEXT_EVENT_STREAM_VALUE` 做了什么？**

1. **设置响应 `Content-Type`**: 等价于 `produces = "text/event-stream"`
2. **激活 Spring MVC SSE 模式**: Spring 检测到 `Flux` + `text/event-stream`，自动：
   - 启用分块传输（`Transfer-Encoding: chunked`）
   - 保持连接打开（`Connection: keep-alive`）
   - 将 `Flux` 的每个元素包装为 `data: xxx\n\n` 格式
3. **禁用响应压缩**: SSE 数据需要实时推送，不能被 Gzip 缓冲

**返回 `Flux<String>` 的含义：**

`Flux` 是一个 **Publisher（发布者）**——它在说："我会零到多次地给你数据，但我不知道什么时候给完"。`DeepSeekChatModel.stream()` 内部订阅了 DeepSeek API 的 SSE 流，每收到一个 token，就往 `Flux` 里发射一个元素。Spring MVC 拿到这个元素后立即通过 HTTP 推送给浏览器。

### 4.3 前端：Vue3 + EventSource 的角色

聚焦 [HomeIndex.vue](ai-robot-vue3/src/views/HomeIndex.vue) 中 SSE 的核心代码：

```javascript
// 第107行 —— 建立 SSE 连接
eventSource = new EventSource(
  `http://localhost:8080/v1/ai/generateStream?message=${encodeURIComponent(userMessage)}`
);

// 第112行 —— 处理每条推送消息
eventSource.onmessage = (event) => {
  if (event.data) {
    responseText += event.data;                          // 拼接到累积文本
    chatList.value[chatList.value.length - 1].content = responseText; // 更新UI
  }
};

// 第124行 —— 处理连接关闭/错误
eventSource.onerror = (error) => {
  if (error.eventPhase === EventSource.CLOSED) {
    console.log('SSE正常关闭');                            // 传输完成，正常情况
  } else {
    chatList.value[chatList.value.length - 1].content =
      '抱歉，请求出错了，请稍后重试。';                    // 真正的网络错误
  }
  closeSSE();                                             // 释放连接
};

// 第145行 —— 手动关闭连接
const closeSSE = () => {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
};

// 第153行 —— 组件卸载时清理，防止内存泄漏
onBeforeUnmount(() => {
  closeSSE();
});
```

### 4.4 EventSource API 详解

| API | 说明 |
|:---|:---|
| `new EventSource(url)` | 创建连接，浏览器立即发送 GET 请求 |
| `eventSource.onmessage` | 收到服务器推送的消息时触发 |
| `eventSource.onerror` | 连接出错时触发（**注意**：传输完成关闭连接也会触发，`eventPhase === EventSource.CLOSED` 是正常情况） |
| `eventSource.onopen` | 连接成功建立时触发（本项目未使用） |
| `eventSource.close()` | 主动关闭连接 |
| `eventSource.readyState` | `0`=连接中, `1`=已连接, `2`=已关闭 |

**EventSource 的自动重连机制**：

`EventSource` 有一个内置特性——连接断开后会**自动重连**。对于 AI 对话场景，这反而是个问题（重连会重新调用整个 API）。所以我们用 `onerror` 回调中调用 `closeSSE()` 来阻止自动重连。

### 4.5 一个常见陷阱：`\n` 被当成了消息边界

注意到 [DeepSeekR1ChatController](ai-robot/src/main/java/com/tefire/ai_robot/controller/DeepSeekR1ChatController.java) 中有这样一行：

```java
String processed = rawContent.replace("\n", "<br>");
```

为什么要把 `\n` 替换成 `<br>`？因为 SSE 协议用 `\n\n` 作为消息边界。如果 LLM 回复中包含 `\n`，可能会被浏览器误解析为消息结束标记，导致显示截断。替换为 `<br>` 就避免了这个问题，同时 `<br>` 在 HTML 中照样能换行。

但是！在当前的 `DeepSeekChatController.java` 中使用的是 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`（即 `text/event-stream`），Spring 会自动将每个 Flux 元素包装为标准 SSE 格式，`\n` 不会被误解析——因为 Spring 做了转义处理。只有当 `produces` 设为 `text/html` 时，才需要手动替换 `\n` 为 `<br>`。

### 4.6 浏览器端如何调试 SSE

打开 Chrome DevTools → Network 标签页：

1. **过滤请求**: 在搜索框输入 `generateStream`
2. **查看请求头**: 点击请求 → Headers 标签 → 确认 `Accept: text/event-stream`
3. **查看响应头**: 确认 `Content-Type: text/event-stream`、`Access-Control-Allow-Origin: *`
4. **实时查看数据流**: 点击 **EventStream** 标签（Chrome 专为 SSE 提供的子标签）——可以看到每条 `data:` 消息实时出现
5. **查看完整响应**: 点击 Response 标签可以看到所有收到的 SSE 消息

```
Chrome DevTools → Network → 选中请求 → EventStream 标签页：

  时间      数据
  ────────  ──────────
  0.2s      你好
  0.3s      ！
  0.4s      我是
  0.5s      DeepSeek
  0.6s      助手
  ...
```

---

## 总结：四张图吃透 SSE + CORS

### 图 1：普通 HTTP（一次请求，一次响应）

```
浏览器 ──请求──▶ 服务器
浏览器 ◀──响应──  服务器
         ❌ 断开
```

### 图 2：SSE（一次请求，持续响应）

```
浏览器 ──请求──▶ 服务器
浏览器 ◀──数据1─  服务器
浏览器 ◀──数据2─  服务器
浏览器 ◀──数据3─  服务器
        ...持续...
浏览器 ◀──结束──  服务器
         ❌ 断开
```

### 图 3：同源 vs 跨域

```
同源 ✅                          跨域 ❌
http://a.com:80/page        http://localhost:5173/page
       ↓                             ↓
http://a.com:80/api          http://localhost:8080/api
  (协议、域名、端口完全相同)      (端口不同！5173 ≠ 8080)
```

### 图 4：CORS 让跨域 SSE 成为可能

```
前端 (5173)                                      后端 (8080)
     │                                               │
     │── GET + Origin: http://localhost:5173 ──────▶ │
     │                                               │ CorsConfig 检查
     │                                               │ .allowedOriginPatterns("*") ✅
     │                                               │
     │◀── Access-Control-Allow-Origin: * ────────── │
     │◀── Content-Type: text/event-stream ───────── │
     │◀── data: 你好 ─────────────────────────────── │
     │◀── data: ！ ──────────────────────────────── │
     │                                               │
     ▼  ✅ 浏览器允许读取响应                          ▼
```

### 核心公式

```
SSE 通信 = 普通 HTTP GET
         + Content-Type: text/event-stream    (Spring: produces = MediaType.TEXT_EVENT_STREAM_VALUE)
         + Transfer-Encoding: chunked          (Spring: 返回值是 Flux 则自动启用)
         + Connection: keep-alive              (Spring: SSE 模式自动保持)
         + Access-Control-Allow-Origin         (Spring: CorsConfig)
         + 浏览器 EventSource API              (前端: new EventSource(url))
```

---

## 附录：补充网络知识

### A. TCP 连接与 HTTP 的关系

SSE 的长连接建立在 **TCP** 之上。TCP 连接的建立需要"三次握手"：

```
客户端                          服务器
  │──── SYN ──────────────────▶ │  (1. 我想建立连接)
  │◀─── SYN + ACK ──────────── │  (2. 好的，我准备好了)
  │──── ACK ──────────────────▶ │  (3. 确认)

  │         TCP 连接已建立        │
  │──── HTTP 请求 ─────────────▶ │
  │◀─── HTTP 响应 ───────────── │
  │        (SSE 持续传输...)      │
  │◀─── FIN ─────────────────── │  (传输完毕，关闭连接)
```

HTTP/1.1 默认开启 `Connection: keep-alive`，意味着同一个 TCP 连接可以发送多个 HTTP 请求。SSE 则更进一步——**一个 HTTP 请求**对应一个长期保持的 TCP 连接。

### B. `Transfer-Encoding: chunked` 原理

普通响应用 `Content-Length` 告诉浏览器数据有多少字节：

```http
HTTP/1.1 200 OK
Content-Length: 42

<42 字节的数据>
```

分块传输不知道总长度，所以用 `chunked` 编码。每个 chunk 前有一个十六进制的长度：

```http
HTTP/1.1 200 OK
Transfer-Encoding: chunked
Content-Type: text/event-stream

6\r\n           ← 下一个 chunk 有 6 字节
data: \r\n      ← chunk 内容（被 Spring 包装的 SSE 格式）
6\r\n
你好\r\n         ← 实际数据
6\r\n
data: \r\n
3\r\n
！\r\n
0\r\n           ← 长度为 0，表示传输结束
\r\n
```

这就是浏览器如何知道"这是一段一段的数据"的——每个 chunk 前都有长度标记。

### C. 为什么不用 WebSocket？

| 场景 | 推荐方案 | 原因 |
|:---|:---|:---|
| AI 对话（一问一答，流式输出） | **SSE** | 单向数据流，简单高效，浏览器自动重连 |
| 实时聊天室（多人双向） | WebSocket | 需要双向通信 |
| 股票行情推送（纯服务端推送） | **SSE** | 比 WebSocket 更轻量 |
| 协同编辑（频繁双向同步） | WebSocket | 需要低延迟双向 |

---

> **📝 本文档基于 `ai-robot` v0.0.1-SNAPSHOT + `ai-robot-vue3` 编写。**
