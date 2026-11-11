package com.tefire.ai.robot.model.vo.customerService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: TE
 * @Date: 2026/8/24 17:13
 * @Version: v1.0.0
 * @Description: 文件检查
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckFileRspVO {

    /**
     * 文件是否存在
     */
    private Boolean exists;

    /**
     * 是否需要上传
     */
    private Boolean needUpload;

    /**
     * 已上传成功的分片序号
     */
    private List<Integer> uploadedChunks;

}
