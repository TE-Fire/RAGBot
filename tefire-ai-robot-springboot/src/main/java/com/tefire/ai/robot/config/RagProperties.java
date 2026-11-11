package com.tefire.ai.robot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Author: TE
 * @Date: 2026/10/8 13:44
 * @Version: v1.0.0
 * @Description: RAG 检索优化相关配置项
 *
 *  后续其他的优化项（混合检索、重排序等）的开关与参数，也会统一放到这里，方便随时切换「优化前 / 优化后」的效果做对比
 **/
@Data
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * 文档切分配置
     */
    private Splitter splitter = new Splitter();

    @Data
    public static class Splitter {

        /**
         * 是否启用 Token 切分
         */
        private boolean enabled = true;

        /**
         * 每个文档块的目标大小（单位: Token）
         */
        private int chunkSize = 800;

        /**
         * 块内按标点截断时，允许的最小块字符数
         */
        private int minChunkSizeChars = 350;
    }

    /**
     * Elasticsearch 关键词检索配置
     */
    private Elasticsearch elasticsearch = new Elasticsearch();

    @Data
    public static class Elasticsearch {

        /**
         * 是否启用 ES 双写双删（关闭后仅保留向量检索）
         */
        private boolean enabled = true;

        /**
         * 文档块索引名称
         */
        private String indexName = "rag_chunk";

        /**
         * 写入侧分词器。默认依赖 IK 插件；未安装 IK 时改成 standard 也能跑通（中文按单字切分，召回稍差）
         */
        private String indexAnalyzer = "ik_max_word";

        /**
         * 查询侧分词器。ik_smart 切得粗、词更干净，能减少跨词误命中
         */
        private String searchAnalyzer = "ik_smart";
    }

    /**
     * 检索策略配置
     */
    private Retrieval retrieval = new Retrieval();

    @Data
    public static class Retrieval {

        /**
         * 是否启用混合检索（向量 + ES 关键词双路召回，RRF 融合排序；关闭则只走向量检索）
         */
        private boolean hybridEnabled = false;

        /**
         * 混合检索时，向量路召回的候选数量
         */
        private int vectorTopK = 5;

        /**
         * 混合检索时，关键词路（ES BM25）召回的候选数量
         */
        private int keywordTopK = 5;

        /**
         * RRF 融合常数 k（默认 60）
         */
        private int rrfK = 60;
    }

    /**
     * Rerank 重排配置
     */
    private Rerank rerank = new Rerank();

    @Data
    public static class Rerank {

        /**
         * 是否启用 Rerank 重排
         */
        private boolean enabled = false;

        /**
         * 重排模型名称
         */
        private String model = "qwen3-rerank";

        /**
         * 百炼文本排序 API 地址
         */
        private String apiUrl = "";

        /**
         * 百炼 API Key
         */
        private String apiKey = "";

        /**
         * 重排后，最终截取喂给大模型的文档块数量（召只留最相关的 TopN）
         */
        private int topN = 3;
    }



}
