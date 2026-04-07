package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
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

        CodeProcessorService processor = AiServices.builder(CodeProcessorService.class)
                .chatModel(myQwenChatModel)
                .streamingChatModel(qwenStreamingChatModel)
                .chatMemory(memory)
                .build();

        System.out.println("使用代码处理器流式处理内容，conversationId=" + context.getConversationId());
        return processor.processCodeStream(context.getOriginalContent());
    }
}
