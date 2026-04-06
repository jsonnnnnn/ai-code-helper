package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.routeChain.service.TextProcessorService;
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
        TextProcessorService processor = AiServices.builder(TextProcessorService.class)
                .streamingChatModel(qwenStreamingChatModel)
                .build();

        System.out.println("使用文本处理器流式处理内容");
        return processor.processTextStream(context.getOriginalContent());
    }
}