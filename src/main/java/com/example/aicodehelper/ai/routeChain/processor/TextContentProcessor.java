package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.memory.LongTermMemoryService;
import com.example.aicodehelper.ai.memory.RouteMemoryManager;
import com.example.aicodehelper.ai.routeChain.service.TextProcessorService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TextContentProcessor implements ContentProcessor {

    @Resource
    private ChatModel myQwenChatModel;

    @Resource
    private StreamingChatModel qwenStreamingChatModel;

    @Resource
    private RouteMemoryManager routeMemoryManager;

    @Resource
    private LongTermMemoryService longTermMemoryService;

    @Override
    public String getProcessorName() {
        return "文本处理器";
    }

    @Override
    public ContentProcessingContext process(ContentProcessingContext context) throws Exception {
        TextProcessorService processor = AiServices.create(TextProcessorService.class, myQwenChatModel);

        String content = context.getOriginalContent();
        String processedText = processor.processText(content);
        System.out.println("文本处理结果：" + processedText);
        context.addResult("processed_text", processedText);
        context.setFinalOutput("文本处理完成：" + processedText);

        System.out.println("使用文本处理器处理内容");
        return context;
    }

    @Override
    public Flux<String> processStream(ContentProcessingContext context) {
        ChatMemory memory = routeMemoryManager.getOrCreate(context.getConversationId());

        // 【生效时机】回答前读取长期记忆，拼接到用户消息，让 AI 参考用户偏好
        String longTermMemory = longTermMemoryService.getLongTermMemory();
        String enrichedContent = buildEnrichedContent(context.getOriginalContent(), longTermMemory);

        TextProcessorService processor = AiServices.builder(TextProcessorService.class)
                .chatModel(myQwenChatModel)
                .streamingChatModel(qwenStreamingChatModel)
                .chatMemory(memory)
                .build();

        System.out.println("使用文本处理器流式处理内容，conversationId=" + context.getConversationId());

        // 累积 AI 完整回复，用于回答后异步提炼长期记忆
        StringBuilder responseAccumulator = new StringBuilder();
        String originalContent = context.getOriginalContent();

        return processor.processTextStream(enrichedContent)
                // 【生成时机】流结束后异步提炼并持久化长期记忆
                .doOnNext(responseAccumulator::append)
                .doOnComplete(() -> longTermMemoryService.updateAsync(
                        context.getConversationId(),
                        originalContent,
                        responseAccumulator.toString()
                ));
    }

    /**
     * 若存在长期记忆，将其作为隐式上下文前缀拼接到用户消息前。
     * AI 系统提示词要求其无需在回复中提及此块内容。
     */
    private String buildEnrichedContent(String content, String longTermMemory) {
        if (longTermMemory == null || longTermMemory.isBlank()) {
            return content;
        }
        return "[长期记忆参考（了解用户偏好，无需在回复中提及）]\n"
                + longTermMemory
                + "\n\n[用户当前问题]\n"
                + content;
    }
}
