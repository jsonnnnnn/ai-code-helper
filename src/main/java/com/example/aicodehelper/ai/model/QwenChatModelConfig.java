package com.example.aicodehelper.ai.model;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.community.dashscope.chat-model")
@Data
public class QwenChatModelConfig {
    //spring配置中的默认Qwen大模型不支持可观测性，为了支持可观测性质，需要自定义模型的一些参数,所以要创建这个类

    private String modelName;
    //1.依赖注入使用@value的场景：想要注入一个值到bean中，忘了怎么注入自己搜
    //2.依赖注入使用setter的场景：想要批量注入一些值到bean中，@ConfigurationProperties +setter组合使用
    // application.yaml里的model-name会直接通过注解@ConfigurationProperties取到，通过 @Data 生成的 setter 方法注入这个值
    private String apiKey;//同理

    @Resource
    private ChatModelListener chatModelListener;

    @Bean
    public ChatModel myQwenChatModel() {   //注意不要和spring默认注入的模型name相同了，这里加了个my
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .listeners(List.of(chatModelListener))//加入监听器，来支持可观测性
                .temperature(0.85f)
                .build();
    }
}