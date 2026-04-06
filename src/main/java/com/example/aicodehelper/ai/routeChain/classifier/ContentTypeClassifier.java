package com.example.aicodehelper.ai.routeChain.classifier;

import com.example.aicodehelper.ai.context.ContentType;
import com.example.aicodehelper.ai.routeChain.service.ContentClassifierService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ContentTypeClassifier {

    @Resource
    private ChatModel myQwenChatModel;

    public ContentType classifyContent(String content) {
        ContentClassifierService classifier = AiServices.create(ContentClassifierService.class, myQwenChatModel);
        String classification = classifier.classifyContent(content);//调用ai服务

        // 解析分类结果
        classification = classification.toLowerCase().trim();

        if (classification.contains("code") || classification.contains("代码")) {
            return ContentType.CODE;
        } else if (classification.contains("url") || classification.contains("链接")) {
            return ContentType.URL;
        } else if (classification.contains("image") || classification.contains("图片")) {
            return ContentType.IMAGE_INFO;
        } else if (classification.contains("number") || classification.contains("数字")) {
            return ContentType.NUMBER;
        } else if (classification.contains("text") || classification.contains("文本")) {
            return ContentType.TEXT;
        } else {
            return ContentType.UNKNOWN;
        }
    }
}