package com.example.aicodehelper.ai.routeChain.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

public interface TextProcessorService {

    @UserMessage("请对以下文本进行处理，包括摘要和关键词提取：\n{{text}}")
    String processText(@V("text") String text);

    @SystemMessage(fromResource = "system-prompt.txt")
    @UserMessage("请对以下文本进行处理，包括摘要和关键词提取：\n{{text}}")
    Flux<String> processTextStream(@V("text") String text);
}