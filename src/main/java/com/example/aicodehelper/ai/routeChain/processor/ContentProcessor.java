package com.example.aicodehelper.ai.routeChain.processor;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import reactor.core.publisher.Flux;

// 内容处理器接口
public interface ContentProcessor {
    String getProcessorName();
    ContentProcessingContext process(ContentProcessingContext context) throws Exception;

    default Flux<String> processStream(ContentProcessingContext context) {
        try {
            ContentProcessingContext result = process(context);
            return Flux.just(result.getFinalOutput());
        } catch (Exception e) {
            return Flux.error(e);
        }
    }
}