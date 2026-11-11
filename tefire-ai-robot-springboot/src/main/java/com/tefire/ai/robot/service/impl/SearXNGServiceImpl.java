package com.tefire.ai.robot.service.impl;

import com.tefire.ai.robot.model.dto.SearchResultDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tefire.ai.robot.service.SearXNGService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @Author: TE
 * @Date: 2025/7/30 12:15
 * @Version: v1.0.0
 * @Description: TODO
 **/
@Service
@Slf4j
public class SearXNGServiceImpl implements SearXNGService {

    @Resource
    private OkHttpClient  okHttpClient;
    @Resource
    private ObjectMapper objectMapper;
    @Value("${searxng.url}")
    private String searxngUrl;
    @Value("${searxng.count}")
    private int count;
    /**
     * 聚合的目标搜索引擎，逗号分隔；留空则用 SearXNG 默认引擎集
     */
    @Value("${searxng.engines:}")
    private String engines;

    @Override
    public List<SearchResultDTO> search(String query) {
        // 构建 SearXNG API 请求 URL
        HttpUrl.Builder urlBuilder = HttpUrl.parse(searxngUrl).newBuilder()
                .addQueryParameter("q", query) // 设置搜索关键词
                .addQueryParameter("format", "json"); // 指定返回 JSON 格式

        // 未配置 engines 时交给 SearXNG 用默认引擎集，避免传空值把召回打成 0 条
        if (StringUtils.isNotBlank(engines)) {
            urlBuilder.addQueryParameter("engines", engines); // 指定聚合的目标搜索引擎（选本机网络能访问通的）
        }

        HttpUrl httpUrl = urlBuilder.build();

        // 创建 HTTP GET 请求
        Request request = new Request.Builder()
                        .url(httpUrl)
                        .get()
                        .build();

        // 发送 HTTP 请求
        try (Response response = okHttpClient.newCall(request).execute()) {
            // 判断请求是否成功
            if (response.isSuccessful()) {
                // 拿到返回结果
                String result = response.body().string();
                log.info("## SearXNG 搜索结果: {}", result);

                // 解析 JSON 响应
                JsonNode root = objectMapper.readValue(result, JsonNode.class);
                JsonNode results = root.get("results"); // 获取结果数组节点

                // 定义 Record 类型：用于临时存储分数和节点引用
                record NodeWithUrlAndScore(double score, JsonNode node) {}

                // 处理搜索结果流：
                // 1. 提取评分
                // 2. 按评分降序排序
                // 3. 限制返回结果数量
                List<NodeWithUrlAndScore> nodesWithScore = StreamSupport.stream(results.spliterator(), false)
                        .map(node -> {
                            // 只提取分数，避免构建完整对象
                            double score = node.path("score").asDouble(0.0); // 提取评分
                            return new NodeWithUrlAndScore(score, node);
                        })
                        .sorted(Comparator.comparingDouble(NodeWithUrlAndScore::score).reversed()) // 按评分降序
                        .limit(count) // 限制返回结果数量
                        .toList();

                // 转换为 SearchResult 对象集合
                return nodesWithScore.stream()
                        .map(n -> {
                            JsonNode node = n.node();
                            String originalUrl = node.path("url").asText(""); // 提取 URL
                            return SearchResultDTO.builder()
                                    .url(originalUrl)
                                    .score(n.score()) // 保留评分
                                    .build();
                        })
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("", e);
        }
        // 返回空集合
        return Collections.emptyList() ;
    }

}
