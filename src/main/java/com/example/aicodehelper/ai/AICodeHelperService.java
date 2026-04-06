package com.example.aicodehelper.ai;

import com.example.aicodehelper.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.*;
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
     Result<String> chatWithRag(@UserMessage String userMessage);  //其实用上面的chat已经可以支持rag了，这里只是返回值类型用result类可以额外获取检索到的内容和token消耗

     @SystemMessage(fromResource = "system-prompt.txt")
     Flux<String> chatStream(@MemoryId int memoryId, @UserMessage String userMessage);

     @UserMessage("""
        你是代码小抄的专业代码解释员。
        
        编程语言：{{language}}
        用户水平：{{user_level}}
        
        如果是初学者,请用简单易懂的语言解释，避免复杂的技术术语;
        
       如果是高级编程者，可以深入讲解技术细节和最佳实践。
        
        
        请解释以下代码的功能和工作原理：
        ```{{language}}
        {{code}}
        ```
        
        解释要求：
        1. 代码的主要功能
        2. 关键语法和概念
        3. 运行流程
        4. 可能的改进建议
        """
     )
     Result<String> chatWithDiffLevel(@V("code") String code, @V("language") String language, @V("user_level") String user_level);




}
