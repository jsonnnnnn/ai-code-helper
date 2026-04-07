package com.example.aicodehelper.ai.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 长期记忆持久化存储。
 * 将用户偏好摘要以 JSON 格式存储到本地文件，程序重启后仍可读取。
 * 所有路由模式会话共享同一份长期记忆。
 */
@Component
@Slf4j
public class LongTermMemoryStore {

    private static final String FILE_PATH = "long-term-memory.json";
    private static final String SUMMARY_KEY = "summary";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 读取当前长期记忆摘要，文件不存在时返回空字符串。
     */
    public String load() {
        lock.readLock().lock();
        try {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                return "";
            }
            Map<String, String> data = objectMapper.readValue(file, new TypeReference<>() {});
            return data.getOrDefault(SUMMARY_KEY, "");
        } catch (Exception e) {
            log.warn("读取长期记忆失败: {}", e.getMessage());
            return "";
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清除长期记忆：删除持久化文件。
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            File file = new File(FILE_PATH);
            if (file.exists() && file.delete()) {
                log.info("长期记忆文件已删除: {}", FILE_PATH);
            } else {
                log.info("长期记忆文件不存在，无需删除: {}", FILE_PATH);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 将更新后的长期记忆摘要持久化到文件。
     */
    public void save(String summary) {
        lock.writeLock().lock();
        try {
            Map<String, String> data = new HashMap<>();
            data.put(SUMMARY_KEY, summary);
            objectMapper.writeValue(new File(FILE_PATH), data);
            log.info("长期记忆已持久化至 {}", FILE_PATH);
        } catch (Exception e) {
            log.error("持久化长期记忆失败: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
}
