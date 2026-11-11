package com.quanxiaoha.ai.robot.service;

import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;

/**
 * @Author: 犬小哈
 * @Date: 2026/10/19 15:50
 * @Version: v1.0.0
 * @Description: Elasticsearch 文档块
 **/

public interface EsChunkService {

    /**
     * 确保 ES 索引存在
     * @throws IOException
     */
    void ensureIndex() throws IOException;

    /**
     * 批量写入文档块
     * @param documents
     * @throws IOException
     */
    void saveChunks(List<Document> documents) throws IOException;

    /**
     * 按文件 ID 删除该文件写入 ES 的所有文档块
     * @param fileId
     * @throws IOException
     */
    void deleteByFileId(Long fileId) throws IOException;

    /**
     * 关键词检索
     * @param query 用户提问的原始问题
     * @param topK 召回数量
     * @return
     */
    List<Document> searchByKeyword(String query, int topK);

}
