package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.memory.LongTermMemoryService;
import com.example.aicodehelper.ai.memory.RouteMemoryManager;
import com.example.aicodehelper.ai.routeChain.service.CodeProcessorService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class CodeContentProcessor implements ContentProcessor {

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
        return "代码处理器";
    }

    @Override
    public ContentProcessingContext process(ContentProcessingContext context) throws Exception {
        CodeProcessorService processor = AiServices.create(CodeProcessorService.class, myQwenChatModel);

        String content = context.getOriginalContent();
        String processedCode = processor.processCode(content);
        System.out.println("代码处理结果：" + processedCode);
        context.addResult("processed_code", processedCode);
        context.setFinalOutput("代码处理完成：" + processedCode);

        System.out.println("使用代码处理器处理内容");
        return context;
    }

    @Override
    public Flux<String> processStream(ContentProcessingContext context) {
        ChatMemory memory = routeMemoryManager.getOrCreate(context.getConversationId());

        // 【生效时机】回答前读取长期记忆，拼接到用户消息，让 AI 参考用户偏好
        String longTermMemory = longTermMemoryService.getLongTermMemory();
        String enrichedContent = buildEnrichedContent(context.getOriginalContent(), longTermMemory);

        CodeProcessorService processor = AiServices.builder(CodeProcessorService.class)
                .chatModel(myQwenChatModel)
                .streamingChatModel(qwenStreamingChatModel)
                .chatMemory(memory)
                .build();

        System.out.println("使用代码处理器流式处理内容，conversationId=" + context.getConversationId());

        // 累积 AI 完整回复，用于回答后异步提炼长期记忆
        StringBuilder responseAccumulator = new StringBuilder();
        String originalContent = context.getOriginalContent();

        return processor.processCodeStream(enrichedContent)
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
     */
    private String buildEnrichedContent(String content, String longTermMemory) {
        if (longTermMemory == null || longTermMemory.isBlank()) {
            return content;
        }
        return "[长期记忆参考（了解用户偏好，无需在回复中提及）]\n"
                + longTermMemory
                + "\n\n[用户当前代码]\n"
                + content;
    }
}
