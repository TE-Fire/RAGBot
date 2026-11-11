package com.tefire.ai.robot.reader;

import cn.hutool.core.collection.CollUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @Author: TE
 * @Date: 2026/8/22 17:58
 * @Version: v1.0.0
 * @Description: Markdown 文件读取
 **/
@Component
public class MarkdownReader {

    /**
     * 匹配 Markdown 图片语法的正则：![替代文本](图片地址)
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)[^)]*\\)");

    /**
     * 读取 Markdown 文件为文档集合
     * @param resource
     * @param metadatas
     * @return
     */
    public List<Document> loadMarkdown(Resource resource, Map<String, Object> metadatas) {
        // 1. 读取 Markdown 原始内容
        String content;
        try (InputStream inputStream = resource.getInputStream()) {
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取 Markdown 文件失败", e);
        }

        // 2. 修复图片地址丢失的问题
        // 官方 MarkdownDocumentReader 解析文档时只收集文字，图片语法中的图片地址不会被收集
        // 导致向量化后的文档内容里没有图片链接，AI 也就无法把二维码等图片发给用户了
        // 修复方式：在图片语法后面追加一段 “行内代码” 形式的图片地址，行内代码（Code 节点）的内容是会被官方 Reader 收集的
        String patchedContent = IMAGE_PATTERN.matcher(content)
                .replaceAll(matchResult -> matchResult.group() + "`" + matchResult.group(2) + "`");

        // 3. 用修复后的内容构建新的 Resource（保留原文件名）
        ByteArrayResource patchedResource = new ByteArrayResource(
                patchedContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return resource.getFilename();
            }
        };

        // MarkdownDocumentReader 阅读器配置类
        MarkdownDocumentReaderConfig.Builder configBuilder = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true) // 遇到水平线 ---，则创建新文档
                .withIncludeCodeBlock(false) // 排除代码块（代码块生成单独文档）
                .withIncludeBlockquote(false); // 排除块引用（块引用生成单独文档）

        // 添加自定义元数据，如文件名称
        if (CollUtil.isNotEmpty(metadatas)) {
            configBuilder.withAdditionalMetadata(metadatas);
        }

        // 新建 MarkdownDocumentReader 阅读器
        MarkdownDocumentReader reader = new MarkdownDocumentReader(patchedResource, configBuilder.build());

        // 读取并转换为 Document 文档集合
        return reader.get();
    }
}
