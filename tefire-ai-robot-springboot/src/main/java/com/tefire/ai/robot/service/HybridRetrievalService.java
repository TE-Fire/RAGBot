package com.tefire.ai.robot.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @Author: TE
 * @Date: 2026/10/26 16:40
 * @Version: v1.0.0
 * @Description: 混合检索
 **/
public interface HybridRetrievalService {

    /**
     * 向量召回 + 关键词召回后，再 RRF 融合，返回与查询最相关的文档块
     * @param query
     * @param topK
     * @return
     */
    List<Document> search(String query, int topK);
}
