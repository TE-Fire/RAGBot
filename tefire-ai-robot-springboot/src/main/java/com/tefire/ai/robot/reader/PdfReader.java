package com.tefire.ai.robot.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: TE
 * @Date: 2026/10/10 15:46
 * @Version: v1.0.0
 * @Description: PDF 文档读取
 **/
@Component
public class PdfReader {

    /**
     * 读取 PDF 文件为 Document 文档集合
     *
     * @param resource
     * @param metadatas
     * @return
     */
    public List<Document> read(Resource resource, Map<String, Object> metadatas) {
        // 按页解析 PDF：一页一个文档块
        PagePdfDocumentReader pdfDocumentReader = new PagePdfDocumentReader(resource,
                PdfDocumentReaderConfig.builder().build());

        // Reader 自身不支持传入业务元数据，读取后统一附加（文件存储 ID、文件名等）
        return withMetadata(pdfDocumentReader.get(), metadatas);
    }

    /**
     * 将业务元数据附加到每个文档块上（合并 Reader 自带的元数据）
     */
    private List<Document> withMetadata(List<Document> documents, Map<String, Object> metadatas) {
        return documents.stream()
                .map(doc -> {
                    // 先保留 Reader 自带的元数据，再合并业务元数据
                    Map<String, Object> mergedMetadata = new HashMap<>(doc.getMetadata());
                    mergedMetadata.putAll(metadatas);
                    return Document.builder()
                            .text(doc.getText())
                            .metadata(mergedMetadata)
                            .build();
                })
                .toList();
    }

}
