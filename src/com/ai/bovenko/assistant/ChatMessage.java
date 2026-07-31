package com.ai.bovenko.assistant;

/**
 * Модель данных для хранения единичного сообщения в истории диалога.
 */
public class ChatMessage {
    private final String role; // user, assistant, system, tool
    private final String content;
    private final String toolCallId; // Заполняется только для роли tool/assistant при tool_calls
    private final String name;       // Имя инструмента для роли tool

    public ChatMessage(String role, String content) {
        this(role, content, null, null);
    }

    public ChatMessage(String role, String content, String toolCallId, String name) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.name = name;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public String getName() { return name; }
}