# AI Robot 接口文档

## 基础信息

- **服务名称**: AI Robot Springboot
- **服务地址**: `http://localhost:8080`
- **API 版本**: v1.0.0
- **文档生成时间**: 2026-07-24

---

## 接口列表

### 1. 新建对话

| 属性 | 值 |
|------|-----|
| **接口路径** | `/new` |
| **HTTP 方法** | POST |
| **功能描述** | 新建一个 AI 对话，发送用户消息并获取响应 |
| **接口分类** | 对话管理 |

#### 请求参数

**Content-Type**: `application/json`

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| message | String | 是 | 用户消息内容 |

**请求示例**:

```json
{
    "message": "你好，帮我查询一下天气"
}
```

#### 响应参数

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 请求是否成功 |
| message | String | 响应消息（成功时为空） |
| errorCode | String | 错误码（成功时为空） |
| data | Object | 响应数据 |
| data.summary | String | 对话摘要 |
| data.uuid | String | 对话唯一标识（UUID） |

**成功响应示例**:

```json
{
    "success": true,
    "message": null,
    "errorCode": null,
    "data": {
        "summary": "用户询问天气情况",
        "uuid": "550e8400-e29b-41d4-a716-446655440000"
    }
}
```

**失败响应示例**:

```json
{
    "success": false,
    "message": "用户消息不能为空",
    "errorCode": null,
    "data": null
}
```

---

## 通用响应结构

### Response<T>

所有接口统一返回此结构：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| success | boolean | 请求是否成功 |
| message | String | 响应消息 |
| errorCode | String | 错误码 |
| data | T | 响应数据（泛型） |

### 错误码说明

| 错误码 | 说明 |
|--------|------|
| null | 成功 |
| 其他 | 具体业务错误码 |
