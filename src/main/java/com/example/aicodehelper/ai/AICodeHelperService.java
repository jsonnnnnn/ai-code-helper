package com.example.aicodehelper.ai;

import com.example.aicodehelper.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

@InputGuardrails({SafeInputGuardrail.class})
public interface AICodeHelperService {
     @SystemMessage(fromResource = "system-prompt.txt")
     String chat(@UserMessage String userMessage);


     @SystemMessage(fromResource = "system-prompt.txt")
     String chat(@UserMessage String userMessage,@UserMessage ChatRequestParameters params);

     @SystemMessage(fromResource = "system-prompt.txt")
     Result<String> chatWithRag(@UserMessage String userMessage);  //其实用上面的chat已经可以支持rag了，这里只是返回值类型用result类

     @SystemMessage(fromResource = "system-prompt.txt")
     Flux<String> chatStream(@MemoryId int memoryId, @UserMessage String userMessage);

}
