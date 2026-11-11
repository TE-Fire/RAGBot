package com.tefire.ai.robot.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tefire.ai.robot.config.RagProperties;
import com.tefire.ai.robot.service.RerankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: TE
 * @Date: 2026/10/28 16:57
 * @Version: v1.0.0
 * @Description: Rerank 重排序服务
 **/
@Service
@Slf4j
public class RerankServiceImpl implements RerankService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Resource
    private OkHttpClient okHttpClient;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RagProperties ragProperties;

    /**
     * 对候选文档块，按与查询的语义相关性进行重排序
     *
     * @param query
     * @param candidates
     * @return
     */
    @Override
    public List<Document> rerank(String query, List<Document> candidates) {
        // 判断重排序开关
        if (!ragProperties.getRerank().isEnabled()) {
            return candidates;
        }

        // 候选文档块，如果为空，或只有一条，则无需重排序
        if (candidates == null || candidates.size() <= 1) {
            return candidates;
        }

        // 重排序配置
        RagProperties.Rerank rerank = ragProperties.getRerank();

        try {
            // 1. 组装请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            // 模型名称
            requestBody.put("model", rerank.getModel());

            Map<String, Object> input = new LinkedHashMap<>();
            // 查询语句
            input.put("query", query);

            // 候选文档块
            List<String> documents = candidates.stream().map(Document::getText).toList();
            input.put("documents", documents);
            requestBody.put("input", input);

            // 请求 Json
            String requestJson = objectMapper.writeValueAsString(requestBody);

            // 2. 发起 HTTP 调用（Bearer 鉴权需要带上 API Key）
            Request request = new Request.Builder()
                    .url(rerank.getApiUrl())
                    .header("Authorization", "Bearer " + rerank.getApiKey())
                    .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                // 若重排序接口请求失败，则回退为 RRF, 不要影响主业务功能
                if (!response.isSuccessful()) {
                    log.error("## Rerank 接口调用失败, httpCode: {}, body: {}",
                            response.code(), response.body() != null ? response.body().string() : "");
                    return candidates;
                }

                String body = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readValue(body, JsonNode.class);

                // 3. 解析排序结果：results 已按 relevance_score 降序，index 指向请求 documents 的下标
                List<Document> reranked = new ArrayList<>();

                for (JsonNode result : root.path("output").path("results")) {

                    int index = result.path("index").asInt(-1);
                    if (index < 0 || index >= candidates.size()) {
                        continue; // 对于下标越界的结果，直接丢弃
                    }

                    Document doc = candidates.get(index);
                    double relevanceScore = result.path("relevance_score")
                            .asDouble(0.0);

                    // 把相关性得分写入 metadata，方便日志观察与后续诊断
                    Map<String, Object> metadata = new LinkedHashMap<>(doc.getMetadata());
                    metadata.put("rerank_score", relevanceScore);

                    reranked.add(Document.builder()
                            .id(doc.getId())
                            .text(doc.getText())
                            .metadata(metadata)
                            .build());
                }

                log.info("## Rerank 重排完成, 候选: {} 条, 重排后返回: {} 条, TotalTokens: {}",
                        candidates.size(), reranked.size(), root.path("usage").path("total_tokens").asInt());

                return reranked;
            }
        } catch (Exception e) {
            // 异常兜底：网络抖动、超时、响应解析失败等，一律退回原候选顺序，服务不能中断
            log.error("## Rerank 重排异常, 退回 RRF 融合顺序", e);
            return candidates;
        }

    }
}
