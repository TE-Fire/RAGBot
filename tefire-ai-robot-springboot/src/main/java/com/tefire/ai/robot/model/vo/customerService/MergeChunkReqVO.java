package com.tefire.ai.robot.model.vo.customerService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: TE
 * @Date: 2026/8/24 17:31
 * @Version: v1.0.0
 * @Description: 文件分片合并
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MergeChunkReqVO {

    @NotBlank(message = "文件 MD5 不能为空")
    private String fileMd5;

    /**
     * 所属知识库 ID（文件打标，检索时按库隔离）
     */
    @NotNull(message = "所属知识库 ID 不能为空")
    private Long knowledgeBaseId;

}
