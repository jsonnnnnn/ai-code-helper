package com.example.aicodehelper.ai;


import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@Service
@Slf4j
public class AICodeHelper {
    @Resource
    private ChatModel qwenChatModel;
    //多模态 qwen其实不支持多模态
    public String chatWithPic(UserMessage userMessage) {
//        log.info("用户输入：{}", userMessage);

        ChatResponse chatResponse = qwenChatModel.chat(userMessage);
        String text = chatResponse.aiMessage().text();
//        log.info("AI 输出：{}", text);

        return text;
    }


//  支持系统提示词，简单对话 //注意参数类型
private static final String SYSTEM_MESSAGE = """
            你是编程领域的小助手，帮助用户解答编程学习和求职面试相关的问题，并给出建议。重点关注 4 个方向：
            1. 规划清晰的编程学习路线
            2. 提供项目学习建议
            3. 给出程序员求职全流程指南（比如简历优化、投递技巧）
            4. 分享高频面试题和面试技巧
            请用简洁易懂的语言回答，助力用户高效学习与求职。
            """;
    public String chat(String message) {
//        log.info("用户输入：{}", message);
//        log.info("系统提示词：{}", SYSTEM_MESSAGE);
        SystemMessage systemMessage=SystemMessage.from(SYSTEM_MESSAGE);
        UserMessage  userMessage=UserMessage.from(message);
        ChatResponse chatResponse = qwenChatModel.chat(userMessage,systemMessage);
        String text = chatResponse.aiMessage().text();
//        log.info("AI 输出：{}", text);

        return text;
    }

    //结构化输出，但是qwen不支持

    public String chatWithStructuredOutPut(String message) {
//        1.创建格式
        ResponseFormat responseFormat = ResponseFormat.builder() //ResponseFormat包有两种，有可能导包导错
                .type(JSON) // type can be either TEXT (default) or JSON
                .jsonSchema(JsonSchema.builder()
                        .name("Person") // OpenAI requires specifying the name for the schema
                        .rootElement(JsonObjectSchema.builder() // see [1] below
                                .addStringProperty("name")
                                .addIntegerProperty("age")
                                .addNumberProperty("height")
                                .addBooleanProperty("married")
                                .required("name", "age", "height", "married") // see [2] below
                                .build())
                        .build())
                .build();
//        2.创建用户消息
        UserMessage userMessage = UserMessage.from(message);
//        log.info("用户输入：{}", userMessage);
//        3.整合用户消息和格式到请求中
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(userMessage)
                .build();

//        4.用请求来聊天
        ChatResponse chatResponse = qwenChatModel.chat(chatRequest);
        String text = chatResponse.aiMessage().text();
//        log.info("AI 输出：{}", text);
        return text;
    }

}
