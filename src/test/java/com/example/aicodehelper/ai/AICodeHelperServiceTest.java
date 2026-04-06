package com.example.aicodehelper.ai;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.Result;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AICodeHelperServiceTest {
    @Resource
    private AICodeHelperService aiCodeHelperService;

    @Test
    void chatWithTemp() {
        ChatRequestParameters customParams = ChatRequestParameters.builder()
                .temperature(1.5)
                .build();
        String result1= aiCodeHelperService.chat("你好我是大一学生");
        System.out.println(result1);
        System.out.println("........................................");
        String result= aiCodeHelperService.chat("你好我是大一学生",customParams);
        System.out.println(result);
    }
    @Test
    void chatWithMem() {
        String result= aiCodeHelperService.chat("你好，天气咋样");
        System.out.println(result);
        result= aiCodeHelperService.chat("你好,今天星期几");
        System.out.println(result);
        System.out.println("............................................");
        result= aiCodeHelperService.chat("还记得第一个问题吗？");
        System.out.println(result);
    }
    @Test
    void chatWithRAG() {
        Result<String> result= aiCodeHelperService.chatWithRag("新手怎么规划学习路线");
        System.out.println(result.sources());//这个是Rag检索到的内容（设置的至多5条）
        System.out.println(result.content());//这个是最终ai返回的结果
        System.out.println(result.tokenUsage());

    }


    @Test
    void chatWithMcp() {
        String result = aiCodeHelperService.chat("什么是程序员鱼皮的编程导航？");
        System.out.println(result);
    }

    @Test
    void chatWithGuardrail() {
        String result = aiCodeHelperService.chat("kill the game");
        System.out.println(result);
    }
    @Test
    void chatWithListener() {
        String result = aiCodeHelperService.chat("什么是程序员鱼皮");
        System.out.println(result);
    }

    @Test
    void chatWithDiffLevel() {
        //初学者
        Result<String> result = aiCodeHelperService.chatWithDiffLevel("System.out.println();","java", "beginner");
        System.out.println(result.content());
        System.out.println(".........................................................");
        //大佬
         result = aiCodeHelperService.chatWithDiffLevel("System.out.println();","java", "advanced");
        System.out.println(result.content());

    }



}