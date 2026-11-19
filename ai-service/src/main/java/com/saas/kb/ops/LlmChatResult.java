package com.saas.kb.ops;

public class LlmChatResult {
    private String content;        // 大模型回复的文本
    private int promptTokens;      // 输入 Token
    private int completionTokens;  // 输出 Token
    private int totalTokens;       // 总 Token

    public LlmChatResult(String content, int promptTokens, int completionTokens, int totalTokens) {
        this.content = content;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    // Getter 和 Setter
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
}