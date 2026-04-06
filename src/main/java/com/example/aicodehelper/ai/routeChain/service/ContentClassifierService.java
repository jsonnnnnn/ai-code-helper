package com.example.aicodehelper.ai.routeChain.service;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ContentClassifierService {
    @UserMessage("请识别以下内容的类型（代码、文本、URL链接、图片信息、数字数据），只返回类型名称：\n{{content}}")
    String classifyContent(@V("content") String content);
}