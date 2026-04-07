package com.example.aicodehelper.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 路由模式下的对话记忆管理器。
 * 每个 conversationId 对应一个独立的 ChatMemory 实例，
 * 同一对话内的所有内容处理器通过同一个 conversationId 共享该记忆。
 */
@Component
public class RouteMemoryManager {

    private final ConcurrentHashMap<String, ChatMemory> memoryStore = new ConcurrentHashMap<>();

    public ChatMemory getOrCreate(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return memoryStore.computeIfAbsent(conversationId,
                id -> MessageWindowChatMemory.withMaxMessages(20));
    }

    public void remove(String conversationId) {
        memoryStore.remove(conversationId);
    }
}
