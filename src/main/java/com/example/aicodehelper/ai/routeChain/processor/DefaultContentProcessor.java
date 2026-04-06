package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultContentProcessor implements ContentProcessor {
    @Override
    public String getProcessorName() {
        return "默认处理器";
    }

    @Override
    public ContentProcessingContext process(ContentProcessingContext context) throws Exception {
        String content = context.getOriginalContent();
        String result = "内容类型未知，原样保存：" + content;

        context.addResult("default_processing", result);
        context.setFinalOutput(result);

        System.out.println("使用默认处理器处理内容");
        return context;
    }
}