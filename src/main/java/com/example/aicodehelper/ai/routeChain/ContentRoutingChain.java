package com.example.aicodehelper.ai.routeChain;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.context.ContentType;
import com.example.aicodehelper.ai.routeChain.classifier.ContentTypeClassifier;
import com.example.aicodehelper.ai.routeChain.processor.ContentProcessor;
import com.example.aicodehelper.ai.routeChain.processor.CodeContentProcessor;
import com.example.aicodehelper.ai.routeChain.processor.DefaultContentProcessor;
import com.example.aicodehelper.ai.routeChain.processor.TextContentProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Service
public class ContentRoutingChain {

    @Resource
    private ContentTypeClassifier classifier;

    @Resource
    private TextContentProcessor textContentProcessor;

    @Resource
    private CodeContentProcessor codeContentProcessor;

    @Resource
    private DefaultContentProcessor defaultContentProcessor;

    private Map<ContentType, ContentProcessor> processors;

    @PostConstruct
    public void init() {
        processors = new HashMap<>();
        processors.put(ContentType.TEXT, textContentProcessor);
        processors.put(ContentType.CODE, codeContentProcessor);
//        processors.put(ContentType.URL, new UrlContentProcessor());
    }

    public String processContent(String content, String conversationId) {
        ContentProcessingContext context = new ContentProcessingContext(content, conversationId);

        try {
            System.out.println("正在识别内容类型...");
            
            ContentType contentType = classifier.classifyContent(content);
            context.setContentType(contentType);
            System.out.println("识别结果：" + contentType);

            ContentProcessor processor = processors.getOrDefault(contentType, defaultContentProcessor);
            System.out.println("选择处理器：" + processor.getProcessorName());
            context = processor.process(context);

        } catch (Exception e) {
            context.setFinalOutput("处理失败：" + e.getMessage());
            System.out.println("内容处理失败：" + e.getMessage());
        }

        return context.getFinalOutput();
    }

    public Flux<String> processContentStream(String content, String conversationId) {
        ContentProcessingContext context = new ContentProcessingContext(content, conversationId);
        try {
            System.out.println("正在识别内容类型（流式）...");
            ContentType contentType = classifier.classifyContent(content);
            context.setContentType(contentType);
            System.out.println("识别结果：" + contentType);

            ContentProcessor processor = processors.getOrDefault(contentType, defaultContentProcessor);
            System.out.println("选择处理器：" + processor.getProcessorName());
            return processor.processStream(context);
        } catch (Exception e) {
            System.out.println("内容处理失败：" + e.getMessage());
            return Flux.error(e);
        }
    }
}