package com.quanxiaoha.ai.robot.service.impl;

import com.quanxiaoha.ai.robot.config.RagProperties;
import com.quanxiaoha.ai.robot.service.EsChunkService;
import com.quanxiaoha.ai.robot.service.HybridRetrievalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: 犬小哈
 * @Date: 2026/10/26 16:44
 * @Version: v1.0.0
 * @Description: 混合检索
 **/
@Service
@Slf4j
public class HybridRetrievalServiceImpl implements HybridRetrievalService {

    @Resource
    private VectorStore vectorStore;
    @Resource
    private EsChunkService esChunkService;
    @Resource
    private RagProperties ragProperties;

    /**
     * 向量召回 + 关键词召回后，再 RRF 融合，返回与查询最相关的文档块
     *
     * @param query
     * @param topK
     * @return
     */
    @Override
    public List<Document> search(String query, int topK) {
        // 判断是否开启了混合检索，若无，则只走向量检索
        if (!ragProperties.getRetrieval().isHybridEnabled()) {
            return vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build());
        }

        // 获取混合检索配置参数
        RagProperties.Retrieval retrieval = ragProperties.getRetrieval();

        // 1. 向量检索召回
        List<Document> vectorDocs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(retrieval.getVectorTopK())
                .build());

        // 2. 关键词检索召回
        List<Document> keywordDocs = esChunkService.searchByKeyword(query, retrieval.getKeywordTopK());

        // 3. RRF 融合：两路排名合并，取融合得分最高的 topK
        List<Document> fused = rrfFuse(vectorDocs, keywordDocs, topK);

        log.info("## 混合检索完成, 向量路: {} 条, 关键词路: {} 条, 融合后取: {} 条",
                vectorDocs.size(), keywordDocs.size(), fused.size());

        return fused;
    }

    /**
     * RRF 融合：score(d) = Σ 1/(k + rank_i(d))，rank 从 1 开始，对包含文档 d 的每一路求和
     */
    private List<Document> rrfFuse(List<Document> vectorDocs, List<Document> keywordDocs, int topK) {
        // RRF 融合常数
        int k = ragProperties.getRetrieval().getRrfK();

        // 融合后的得分字典
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        // chunk id → 文档字典
        Map<String, Document> docById = new LinkedHashMap<>();

        // 向量路按名次累加 RRF 得分（第 1 名贡献 1/(k+1)，第 2 名 1/(k+2)...）
        for (int i = 0; i < vectorDocs.size(); i++) {
            Document doc = vectorDocs.get(i);
            rrfScores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            docById.putIfAbsent(doc.getId(), doc);
        }

        // 关键词路同理；两路都命中的文档 ID 相同，得分自然叠加
        for (int i = 0; i < keywordDocs.size(); i++) {
            Document doc = keywordDocs.get(i);
            rrfScores.merge(doc.getId(), 1.0 / (k + i + 1), Double::sum);
            docById.putIfAbsent(doc.getId(), doc);
        }

        // 按融合得分降序取 topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> docById.get(entry.getKey()))
                .toList();
    }
}
