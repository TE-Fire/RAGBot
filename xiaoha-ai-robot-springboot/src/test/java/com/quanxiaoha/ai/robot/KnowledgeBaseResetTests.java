package com.quanxiaoha.ai.robot;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @Author: 犬小哈
 * @Date: 2026/9/28 12:00
 * @Version: v1.0.0
 * @Description: 清空本地知识库，再导入「小哈星球客服 FAQ」 文档
 **/
@SpringBootTest
@Slf4j
public class KnowledgeBaseResetTests {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void resetKnowledgeBase() {
        // 清空知识库：直接清空三张关联表
        jdbcTemplate.update("DELETE FROM t_ai_customer_service_file_storage"); // 文件记录表
        jdbcTemplate.update("DELETE FROM t_vector_store");                     // 向量表
        jdbcTemplate.update("DELETE FROM t_file_chunk_info");                  // 分片记录表

        // 确认一下是否真的清空了
        Integer totalVectors = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_vector_store", Integer.class);

        log.info("==> 知识库已清空，剩余向量总数: {}", totalVectors);

        // 断言
        Assertions.assertEquals(0, totalVectors);
    }


}
