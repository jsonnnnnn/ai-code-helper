package com.example.aicodehelper;

import com.example.aicodehelper.ai.AICodeHelper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeHelperApplicationTests {
    @Resource
    private AICodeHelper aiCodeHelper;

    @Test
    void contextLoads() {
    }

    @Test
    void chat() {
        aiCodeHelper.chat("你好你好");

    }

    @Test
    void chatWithPic() {
        UserMessage userMessage=UserMessage.from(
            TextContent.from("这是图片"),
            ImageContent.from("https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png")
        );
        aiCodeHelper.chatWithPic( userMessage);
    }
    @Test
    void chatWithStructuredOutPut() {
         String  res= aiCodeHelper.chatWithStructuredOutPut("你好我是懒羊羊，今年18岁，180cm，未婚");
         System.out.println(res);

    }
}
