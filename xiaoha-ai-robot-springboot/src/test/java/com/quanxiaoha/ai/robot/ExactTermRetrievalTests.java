package com.quanxiaoha.ai.robot;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2026/10/15 16:21
 * @Version: v1.0.0
 * @Description: 向量检索的召回数据测试
 **/
@SpringBootTest
@Slf4j
public class ExactTermRetrievalTests {

    @Resource
    private VectorStore vectorStore;

    private static final String[] QUESTIONS = {
            "加入星球都能享受哪些服务呀？"
    };

    @Test
    void testExactTermRetrieval() {
        for (String question : QUESTIONS) {
            log.info("==================================================");
            log.info("【问题】{}", question);

            // 向量检索 topK=5（与线上客服链路 CustomerServiceAdvisor 一致）
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(5)
                    .build());

            printRetrieved(documents);
        }
    }

    /**
     * 把 topK 开到 20，看 "服务清单" 块到底排在第几
     * 用于验证 "调大 topK 能不能把它捞回来"
     */
    @Test
    void testServicesChunkRank() {
        String question = "加入星球都能享受哪些服务呀？";

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(20)
                .build());

        log.info("---- topK=20 全量明细，查找服务清单块（星球是做什么的）----");
        for (int i = 0; i < documents.size(); i++) {
            String preview = documents.get(i).getText().length() > 50
                    ? documents.get(i).getText().substring(0, 50) : documents.get(i).getText();
            log.info("  [{}] {}", i + 1, preview.replace("\n", " "));
        }
    }

    /**
     * 打印检索明细：排名、相似度距离（distance，值越小越相似）、内容预览
     */
    private void printRetrieved(List<Document> documents) {
        log.info("---- 向量检索到 {} 条文档（按相似度从高到低）----", documents.size());

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            // pgvector 返回的相似度距离存放在元数据 distance 中（值越小 = 语义越接近）
            Object distance = doc.getMetadata().get("distance");
            String preview = doc.getText().length() > 60
                    ? doc.getText().substring(0, 60) + "..." : doc.getText();

            log.info("  [{}] (distance={}) {}", i + 1, distance, preview.replace("\n", " "));
        }
    }

}
