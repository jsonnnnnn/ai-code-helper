package com.example.aicodehelper.ai.memory;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j 声明式 AI 服务，负责将一次对话提炼、合并进长期记忆。
 */
public interface MemorySummarizerService {

    @UserMessage("""
            你是用户偏好提炼专家。你的任务是根据最新的一次对话，更新并精炼用户的长期偏好记忆。
            
            【当前长期记忆】
            {{existingMemory}}
            
            【最新对话】
            用户: {{userMessage}}
            AI: {{aiResponse}}
            
            请综合以上信息，更新用户的长期偏好记忆，重点提炼：
            - 编程语言/框架偏好
            - 代码风格与规范习惯
            - 技术栈与关注的领域
            - 常见问题模式与偏好的解答方式
            
            要求：
            1. 保持简洁，不超过500字
            2. 仅输出更新后的记忆内容，不要包含任何解释或前缀
            3. 若当前长期记忆为空，则直接从本次对话提炼
            """)
    String extractAndMergePreferences(
            @V("existingMemory") String existingMemory,
            @V("userMessage") String userMessage,
            @V("aiResponse") String aiResponse
    );
}
