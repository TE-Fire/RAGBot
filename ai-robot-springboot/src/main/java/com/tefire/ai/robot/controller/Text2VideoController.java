package com.tefire.ai.robot.controller;

import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesis;
import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesisParam;
import com.alibaba.dashscope.aigc.videosynthesis.VideoSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/v12/ai")
@Slf4j
public class Text2VideoController {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * 调用阿里百炼图生视频大模型
     * @param prompt
     * @return
     */
    @GetMapping("/text2video")
    public String text2video(@RequestParam(value = "prompt") String prompt) {

        // 1. 准备首帧图片：将本地图片读取并转换为 Base64
        //    图生视频需要一张首帧图片作为视频的起始画面
        String imgUrl;
        try {
            imgUrl = encodeImageToBase64("E:/xiaojiejie.png");
        } catch (IOException e) {
            throw new RuntimeException("读取本地图片失败: " + e.getMessage(), e);
        }

        // 2. 创建视频生成客户端
        VideoSynthesis videoSynthesis = new VideoSynthesis();

        // 3. 构建多模态输入列表（万相 2.7 采用 media 结构，可同时传入图片、音频等多种素材）
        List<VideoSynthesisParam.Media> media = new ArrayList<VideoSynthesisParam.Media>(){{
            add(VideoSynthesisParam.Media.builder()
                    .url(imgUrl)
                    .type("first_frame") // 首帧图片：作为视频的起始画面
                    .build());
            add(VideoSynthesisParam.Media.builder()
                    .url("https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/ozwpvi/rap.mp3")
                    .type("driving_audio") // 驱动音频：为视频配乐
                    .build());
        }};

        // 4. 构建视频生成请求参数
        VideoSynthesisParam param =
                VideoSynthesisParam.builder()
                        .apiKey(apiKey)                    // 百炼 API Key
                        .model("wan2.7-i2v-2026-04-25")    // 模型：通义万相 2.7 图生视频（基于首帧生成视频）
                        .prompt(prompt)                    // 提示词：描述视频内容、镜头运动等
                        .media(media)                      // 多模态输入（首帧图片 + 背景音乐）
                        .watermark(true)                   // 是否给生成视频添加水印
                        .duration(10)                      // 视频时长（秒）
                        .resolution("720P")                // 输出分辨率：480P / 720P / 1080P
                        .build();

        // 5. 同步调用：call() 会阻塞当前线程，直到视频生成完成
        VideoSynthesisResult result = null;
        try {
            log.info("## 正在生成中, 请稍等...");
            result = videoSynthesis.call(param);
        } catch (ApiException | NoApiKeyException | InputRequiredException e){
            log.error("", e);
        }

        // 6. 返回视频生成结果
        return JsonUtils.toJson(result);
    }

    /**
     * 将本地图片文件编码为 Base64 data URI 字符串
     */
    private static String encodeImageToBase64(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        byte[] bytes = Files.readAllBytes(path);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String mimeType = Files.probeContentType(path);
        return "data:" + mimeType + ";base64," + encoded;
    }
}
