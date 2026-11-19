package com.saas.kb.ops;

public interface LlmProvider {
    String name();
    // 返回值修改为 LlmChatResult
    LlmChatResult chat(String message) throws Exception;
}