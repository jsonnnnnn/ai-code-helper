package com.example.aicodehelper.ai.routeChain.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

public interface CodeProcessorService {

    @UserMessage("请对以下代码进行全面分析，包括 Bug 识别、性能优化、代码规范和安全漏洞检查：\n```\n{{code}}\n```")
    String processCode(@V("code") String code);

    @SystemMessage(fromResource = "code-system-prompt.txt")
    @UserMessage("{{code}}")
    Flux<String> processCodeStream(@V("code") String code);
}
