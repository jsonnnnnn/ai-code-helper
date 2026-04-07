package com.example.aicodehelper.ai.memory;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 长期记忆服务。
 * <p>
 * 生效范围：路由模式下所有会话共享一份长期记忆，持久化到本地文件，程序重启后依然有效。
 * 生成时机：每次 AI 回答结束后，异步在独立线程中提炼本次对话并更新长期记忆。
 * 生效时机：每次 AI 回答前，由 Processor 读取并拼接到用户消息中。
 */
@Service
@Slf4j
public class LongTermMemoryService {

    @Resource
    private LongTermMemoryStore longTermMemoryStore;

    @Resource
    private ChatModel myQwenChatModel;

    /**
     * 获取当前长期记忆摘要（同步读取持久化文件）。
     */
    public String getLongTermMemory() {
        return longTermMemoryStore.load();
    }

    /**
     * 清除所有长期记忆（删除持久化文件）。
     */
    public void clearLongTermMemory() {
        longTermMemoryStore.clear();
        log.info("[长期记忆] 已清除全部长期记忆");
    }

    /**
     * 异步提炼本次对话并更新长期记忆。
     * 在独立线程中运行，不阻塞主流程。
     *
     * @param conversationId 会话ID（仅用于日志追踪）
     * @param userMessage    本次用户原始消息
     * @param aiResponse     本次 AI 完整回复
     */
    public void updateAsync(String conversationId, String userMessage, String aiResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[长期记忆] 开始异步提炼，conversationId={}", conversationId);
                String existingMemory = longTermMemoryStore.load();

                MemorySummarizerService summarizer = AiServices.create(
                        MemorySummarizerService.class, myQwenChatModel
                );
                String updatedMemory = summarizer.extractAndMergePreferences(
                        existingMemory, userMessage, aiResponse
                );

                longTermMemoryStore.save(updatedMemory);
                log.info("[长期记忆] 更新完成，conversationId={}", conversationId);
            } catch (Exception e) {
                log.error("[长期记忆] 更新失败，conversationId={}，原因: {}", conversationId, e.getMessage());
            }
        });
    }
}
