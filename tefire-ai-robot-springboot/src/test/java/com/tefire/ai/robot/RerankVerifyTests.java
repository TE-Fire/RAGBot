package com.tefire.ai.robot;

import com.tefire.ai.robot.config.RagProperties;
import com.tefire.ai.robot.service.HybridRetrievalService;
import com.tefire.ai.robot.service.RerankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @Author: TE
 * @Date: 2026/10/28 19:25
 * @Version: v1.0.0
 * @Description: Rerank 重排序测试
 **/
@SpringBootTest
@Slf4j
public class RerankVerifyTests {

    /**
     * 发生车祸的原问题
     */
    private static final String QUESTION = "加入专栏都能享受哪些服务呀？";

    /**
     * 服务清单对应的文档块 ID
     */
    private static final String SERVICES_CHUNK_KEYWORD = "会员服务清单";

    @Resource
    private HybridRetrievalService hybridRetrievalService;
    @Resource
    private RerankService rerankService;
    @Resource
    private RagProperties ragProperties;

    @Test
    void testRerank() {
        log.info("【问题】{}", QUESTION);

        // ========== 第一段：混合检索（RRF 融合），候选池临时扩到每路 10 条 ==========
        // 15.7 单元测试的观察：候选池越大，双路共识才有机会相遇（服务清单块 V8+E1 得分翻倍）
        ragProperties.getRetrieval().setVectorTopK(10);
        ragProperties.getRetrieval().setKeywordTopK(10);
        List<Document> fused = hybridRetrievalService.search(QUESTION, 10);

        log.info("==== RRF 融合顺序（重排前）====");
        printDetail(fused);

        // ========== 第二段：开启 Rerank 重排序 ==========
        ragProperties.getRerank().setEnabled(true);
        List<Document> reranked = rerankService.rerank(QUESTION, fused);

        log.info("==== Rerank 重排顺序（qwen3-rerank）====");
        printDetail(reranked);

        // ========== 结论行：服务清单块名次变化 ==========
        log.info("## 服务清单块名次: RRF={}, Rerank={}",
                rankOf(fused, SERVICES_CHUNK_KEYWORD),
                rankOf(reranked, SERVICES_CHUNK_KEYWORD));

    }

    /**
     * 打印文档明细：名次 + rerank_score（有才打印）+ 内容预览，服务清单块加标记
     */
    private void printDetail(List<Document> documents) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Object score = doc.getMetadata().get("rerank_score");
            log.info("  [{}] {}{} {}", i + 1,
                    score != null ? String.format("score=%.4f ", (Double) score) : "",
                    preview(doc.getText()),
                    doc.getText().contains(SERVICES_CHUNK_KEYWORD) ? " ← 服务清单块" : "");
        }
    }

    /**
     * 判断两个文档列表的顺序是否完全一致（按文档 ID 逐位比较）
     */
    private boolean sameOrder(List<Document> a, List<Document> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getId().equals(b.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查某个文档块在列表中的名次（从 1 开始；不在列表中返回 0）
     */
    private int rankOf(List<Document> documents, String keyword) {
        for (int i = 0; i < documents.size(); i++) {
            if (documents.get(i).getText().contains(keyword)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 内容预览：截取前 30 个字符，换行替换为空格，保证一行显示
     */
    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace("\n", " ").replace("\r", "");
        return flat.length() > 30 ? flat.substring(0, 30) + "..." : flat;
    }
}
