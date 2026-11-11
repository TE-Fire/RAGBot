package com.tefire.ai.robot;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tefire.ai.robot.config.RagProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

/**
 * @Author: TE
 * @Date: 2026/10/22 17:52
 * @Version: v1.0.0
 * @Description: RRF 融合测试
 **/
@SpringBootTest
@Slf4j
public class HybridRetrievalVerifyTests {

    /**
     * 发生车祸的原问题
     */
    private static final String QUESTION = "加入专栏都能享受哪些服务呀？";

    /**
     * 服务清单块（专栏介绍）的定位关键词，用于判断有没有被捞进最终上下文
     * 用关键词而非固定 ID：文档内容一旦调整，内容哈希生成的 ID 就会变，硬编码会失效
     */
    private static final String SERVICES_CHUNK_KEYWORD = "会员服务清单";

    /**
     * RRF 调和常数，业界一般默认 60
     */
    private static final int RRF_K = 60;

    @Resource
    private VectorStore vectorStore;
    @Resource
    private ElasticsearchClient elasticsearchClient;
    @Resource
    private RagProperties ragProperties;

    @Test
    void testHybridRrfRetrieval() throws Exception {
        // 1. 向量路：语义召回 top10
        List<Document> vectorTop10 = vectorStore.similaritySearch(SearchRequest.builder()
                .query(QUESTION)
                .topK(10)
                .build());

        // 2. 关键词路：ES match 查询（走 ik_smart 查询分词），同样取 top10
        // 索引名称
        String indexName = ragProperties.getElasticsearch().getIndexName();
        // 查询结果
        SearchResponse<Map> esResp = elasticsearchClient.search(s -> s
                        .index(indexName)
                        .query(q -> q.match(m -> m.field("content").query(QUESTION)))
                        .size(10),
                Map.class);
        // 获取结果
        List<Hit<Map>> esTop10 = esResp.hits().hits();

        // 3. RRF 融合（每组候选数不同，各跑一组对照）
        fuseAndPrint("每路候选 top5", vectorTop10.subList(0, 5), esTop10.subList(0, 5), 5);
        fuseAndPrint("每路候选 top10", vectorTop10, esTop10, 5);
    }

    /**
     * RRF 融合：score = Σ 1/(k + rank)，rank 从 1 开始计
     */
    private void fuseAndPrint(String label, List<Document> vectorHits, List<Hit<Map>> esHits,
                              int finalK) {
        // 两路各自的 "文档 id → 名次" 排名表
        Map<String, Integer> vectorRank = new HashMap<>();
        // "文档 id → 正文"，用于按关键词定位服务清单块
        Map<String, String> textById = new HashMap<>();

        for (int i = 0; i < vectorHits.size(); i++) {
            Document doc = vectorHits.get(i);
            vectorRank.put(doc.getId(), i + 1);
            textById.put(doc.getId(), doc.getText());
        }

        Map<String, Integer> esRank = new HashMap<>();
        for (int i = 0; i < esHits.size(); i++) {
            Hit<Map> hit = esHits.get(i);
            esRank.put(hit.id(), i + 1);
            textById.putIfAbsent(hit.id(), String.valueOf(hit.source().get("content")));
        }

        // 融合打分：两路排名转 RRF 分并求和
        Map<String, Double> rrfScore = new HashMap<>();
        vectorRank.forEach((id, rank) ->
                rrfScore.merge(id, 1.0 / (RRF_K + rank), Double::sum));  // 向量路贡献
        esRank.forEach((id, rank) ->
                rrfScore.merge(id, 1.0 / (RRF_K + rank), Double::sum));  // 关键词路贡献

        // 排序：RRF 分数降序，取前 finalK 条
        List<String> fused = new ArrayList<>(rrfScore.keySet());
        fused.sort(Comparator.comparing((String id) -> rrfScore.get(id)).reversed());

        // 打印融合结果，并标记两路来源（V=向量路名次，E=关键词路名次）
        for (int i = 0; i < finalK && i < fused.size(); i++) {
            String id = fused.get(i);
            String vectorPart = vectorRank.containsKey(id) ? "V" + vectorRank.get(id) : "";
            String esPart = esRank.containsKey(id) ? "E" + esRank.get(id) : "";
            String source = vectorPart + (vectorPart.isEmpty() || esPart.isEmpty() ? "" : "+") + esPart;
            log.info("  [{}] rrf={} 来源=[{}] {}", i + 1,
                    String.format("%.5f", rrfScore.get(id)), source, id);
        }

        // 结论判定：服务清单块排第几（候选池里没出现即视为未召回）
        int rank = 0;
        for (int i = 0; i < fused.size(); i++) {
            String text = textById.getOrDefault(fused.get(i), "");
            if (text.contains(SERVICES_CHUNK_KEYWORD)) {
                rank = i + 1;
                break;
            }
        }
        log.info("## [{}] 服务清单块排名: {}", label, rank > 0 ? rank : "未召回");
    }


}
