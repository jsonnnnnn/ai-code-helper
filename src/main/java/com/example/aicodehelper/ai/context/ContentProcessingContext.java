package com.example.aicodehelper.ai.context;

import java.util.HashMap;
import java.util.Map;

public class ContentProcessingContext {
    private String originalContent;
    private String conversationId;
    private ContentType contentType;
    private Map<String, Object> processingResults;
    private String finalOutput;

    public ContentProcessingContext(String originalContent, String conversationId) {
        this.originalContent = originalContent;
        this.conversationId = conversationId;
        this.processingResults = new HashMap<>();
    }

    // getter 和 setter 方法
    public String getOriginalContent() { return originalContent; }
    public String getConversationId() { return conversationId; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public Map<String, Object> getProcessingResults() { return processingResults; }
    public void addResult(String key, Object value) { processingResults.put(key, value); }
    public String getFinalOutput() { return finalOutput; }
    public void setFinalOutput(String finalOutput) { this.finalOutput = finalOutput; }
}