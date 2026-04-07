package com.example.aicodehelper.routeChain;

import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.routeChain.ContentRoutingChain;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RoutingChainExampleTest {

    @Resource
    private ContentRoutingChain contentRoutingChain;

    @Test
    void testRoutingChain() {
        String[] testContents = {
                "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }",
                "https://www.codefather.cn - 程序员鱼皮的编程导航网站，提供全面的编程学习资源",
                "今天学习了LangChain4j的链式调用，感觉这个框架非常强大，可以很方便地构建复杂的AI工作流。",
                "手机号码：138-8888-8888，验证码：123456，订单金额：299.99元"
        };

        System.out.println("=== 剪切助手智能内容路由处理 ===");

        for (int i = 0; i < testContents.length; i++) {
            String content = testContents[i];
            System.out.println("\n--- 处理内容 " + (i + 1) + " ---");
            System.out.println("原始内容：" + content.substring(0, Math.min(50, content.length())) + "...");

//            String result = contentRoutingChain.processContent(content);
//            System.out.println("处理结果：" + result);
        }
    }
}