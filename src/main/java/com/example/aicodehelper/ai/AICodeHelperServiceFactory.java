package com.example.aicodehelper.ai;

import com.example.aicodehelper.ai.guardrail.SafeInputGuardrail;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.guardrail.InputGuardrails;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



//这一个文件就可以实现声明式aiService
@Configuration
public class AICodeHelperServiceFactory {  //工厂模式加bean
    @Resource
    private ChatModel  qwenChatModel;  //从配置文件app.yml放入容器，此时再注入  （默认模型不支持监听器

    @Resource //如果按名注入没有找到匹配的Bean，则按类型匹配:我在QwenChatModelConfig中的bean是my 而不是My，所以这里写my才能找到正确的bean
    private ChatModel  myQwenChatModel;  //从QwenChatModelConfig放入容器，此时再注入  （自定义模型支持监听器

    @Resource
    private ContentRetriever contentRetriever;//从RagConfig放入容器，此时再注入

    @Resource
    private McpToolProvider mcpToolProvider;//从McpConfig放入容器，此时再注入

    @Resource
    private StreamingChatModel qwenStreamingChatModel;
    //下面是返回值  存入容器
    @Bean
    public AICodeHelperService aiCodeHelperService() {

//        //1.会话记忆如何实现
         ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
         //创建服务
         AICodeHelperService aiCodeHelperService = AiServices.builder(AICodeHelperService.class)
//                 .chatModel(qwenChatModel)              //默认模型
                 .chatModel(myQwenChatModel)              //自定义模型（支持监听
                 .chatMemory(chatMemory)                //添加会话记忆
                 .chatMemoryProvider(memoryId ->
                         MessageWindowChatMemory.withMaxMessages(10)) // 每个会话独立存储
                 .contentRetriever(contentRetriever)    //添加RAG内容检索
                 .toolProvider(mcpToolProvider)         //添加MCP工具
                 .streamingChatModel(qwenStreamingChatModel)//流式输出
                 .build();
         return  aiCodeHelperService;

//        2.如何设置单次问答的温度等参数，直接在测试方法里加下面三行代码，这个文件里没法加

//        ChatRequestParameters customParams = ChatRequestParameters.builder()
//                .temperature(0.85)
//                .build();
//        //创建服务
//        AICodeHelperService aiCodeHelperService = AiServices.builder(AICodeHelperService.class)
//                .chatModel(qwenChatModel)
//                .build();
//
//        return  aiCodeHelperService;
    }
}
