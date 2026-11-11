package com.tefire.ai.robot;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * @Author: TE
 * @Date: 2026/9/28 16:58
 * @Version: v1.0.0
 * @Description: 测试 AI 客服真的能正确的回答问题吗？
 **/
@SpringBootTest
@Slf4j
public class RetrievalPainPointTests {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private OpenAiChatModel openAiChatModel;

    /**
     * 三个测试问题
     *
     * Tip: 每个问题都能在 FAQ 中找到对应的确定答案
     */
    private static final String[] QUESTIONS = {
            "加入专栏都能享受哪些服务呀？",
            "邀请好友加入，我能拿到百分之多少的分成呀？",
            "新人优惠券是多少钱呀？",
            "怎么加入专栏？"
    };

    @Test
    void reproducePainPoints() {
        for (String question : QUESTIONS) {
            log.info("==================================================");
            log.info("【问题】{}", question);

            // 1. 向量检索 topK=5（比之前小节 topK=3 多捞出来两条，方便观察 "谁挤掉了谁"）
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(5)
                    .build());

            printRetrieved(documents);

            // 2. 用和之前小节一致的提示词，把检索结果喂给大模型生成回答
            String answer = callChatModel(question, buildContext(documents));
            log.info("【AI 的回答】");
            log.info("{}", answer);
        }
    }

    /**
     * 打印检索明细：排名、相似度距离（distance，值越小越相似）、内容预览
     */
    private void printRetrieved(List<Document> documents) {
        log.info("---- 向量检索到 {} 条文档（按相似度从高到低）----", documents.size());

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            // pgvector 返回的相似度距离存放在元数据 distance 中（值越小 = 语义越接近）
            Object distance = doc.getMetadata().get("distance");
            String preview = doc.getText().length() > 60
                    ? doc.getText().substring(0, 60) + "..." : doc.getText();
            log.info("  [{}] (distance={}) {}", i + 1, distance, preview.replace("\n", " "));
        }
    }

    /**
     * 拼接上下文
     */
    private String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (Document document : documents) {
            context.append(String.format("""
                    %s
                    ---
                    """, document.getText()));
        }
        return context.toString();
    }

    /**
     * 调用大模型生成回答
     * 提示词、模型、温度值全部与 CustomerServiceAdvisor 链路一致（deepseek-v3 / 温度 0.0），
     * 保证测试结果能代表线上真实效果
     */
    private String callChatModel(String question, String context) {
        String prompt = """
                你是一个专业的客服，名为 “TEFire AI 助手”。请根据以下上下文信息回答用户问题。

                ## 上下文信息
                %s

                请根据上下文内容来回复用户：

                ## 用户问题
                %s

                ## 回答要求

                **核心规则**：
                1. **严格基于上下文**：只能使用提供的上下文信息回答问题
                2. **服务风格**：热情、耐心、专业，可以使用适当的 Emoji 表情 😊
                3. **禁止用语**：避免使用"根据上下文"、"所提供的信息"等生硬表述

                **回答范围判断**：
                - ✅ 如果用户问题与上下文信息直接相关，请提供详细、准确的回答
                - ✅ 如果用户问题与上下文信息间接相关，可以基于已有信息进行合理推断
                - ❌ 如果用户问题完全超出上下文范围，或者上下文信息不足以回答该问题

                **无法回答时的统一回复**：
                当遇到以下情况时，请统一回复：
                "此问题暂时无法回答，可以在「个人中心 - 帮助中心」提交工单，我们会尽快回复您"

                现在请根据以上要求回答问题。
                """.formatted(context, question);

        return ChatClient.create(openAiChatModel)
                .prompt()
                .options(OpenAiChatOptions.builder()
                        .model("deepseek-v3")   // 使用一致的模型
                        .temperature(0.0))      // 温度 0，减少随机性对实验的干扰
                .user(prompt)
                .call()
                .content();
    }

}
