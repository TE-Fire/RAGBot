package com.quanxiaoha.ai.robot.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2026/10/28 16:56
 * @Version: v1.0.0
 * @Description: Rerank 重排序服务
 **/
public interface RerankService {

    /**
     * 对候选文档块，按与查询的语义相关性进行重排序
     *
     * @param query
     * @param candidates
     * @return
     */
    List<Document> rerank(String query, List<Document> candidates);

}
