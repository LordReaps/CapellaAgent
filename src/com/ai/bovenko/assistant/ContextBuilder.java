package com.ai.bovenko.assistant;

import java.util.List;
import java.util.Map;
import org.eclipse.emf.ecore.EObject;

/**
 * Сборщик итоговой JSON-нагрузки под протокол OpenAI на чистом Java.
 * Оптимизирован под жесткие требования и ограничения API GigaChat Сбера.
 */
public class ContextBuilder {

    public static String buildPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"GigaChat\","); // Исправлено: Валидное имя базовой модели Сбера
        sb.append("\"temperature\":0.2,");
        
        // 1. Формируем массив истории диалога "messages"
        sb.append("\"messages\":[");
        List<ChatMessage> history = SessionManager.getInstance().getChatHistory();
        boolean firstMsg = true;
        
        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (!firstMsg) sb.append(",");
            firstMsg = false;
            
            sb.append("{");
            sb.append("\"role\":\"").append(escapeJson(msg.getRole())).append("\"");
            
            if (msg.getContent() != null) {
                String content = msg.getContent();
                
                // Если это самое последнее сообщение в истории и оно принадлежит пользователю,
                // мы незаметно обогащаем его текст контекстом UI Capella, избегая использования роли system.
                if (i == history.size() - 1 && "user".equals(msg.getRole())) {
                    EObject activeElement = SessionManager.getInstance().getCurrentSelectedElement();
                    if (activeElement != null) {
                        String id = CapellaModelAdapter.getElementId(activeElement);
                        String type = CapellaModelAdapter.getElementType(activeElement);
                        String name = CapellaModelAdapter.getElementName(activeElement);
                        
                        String liveCtxPrompt = "[Контекст UI Capella: Инженер сейчас выделил и смотрит на элемент [ID: " 
                                                + id + ", Тип: " + type + ", Имя: " + name + "]].\n";
                        
                        // Склеиваем контекст и исходный вопрос инженера («проанализируй модель»)
                        content = liveCtxPrompt + content;
                    }
                }
                
                sb.append(",\"content\":\"").append(escapeJson(content)).append("\"");
            }
            
            // Если в истории лежит ответ с tool_calls или результат вызова инструмента tool
            if (msg.getToolCallId() != null) {
                if ("tool".equals(msg.getRole())) {
                    sb.append(",\"tool_call_id\":\"").append(escapeJson(msg.getToolCallId())).append("\"");
                    sb.append(",\"name\":\"").append(escapeJson(msg.getName())).append("\"");
                } else if ("assistant".equals(msg.getRole())) {
                    // Передаем закэшированный JSON-массив вызовов функций ассистента без кавычек
                    sb.append(",\"tool_calls\":").append(msg.getToolCallId());
                }
            }
            sb.append("}");
        }
        
        // Блок принудительной вставки system-сообщения отсюда ПОЛНОСТЬЮ удален, чтобы не провоцировать ошибку 422
        sb.append("],");
        
        // 2. Формируем массив декларируемых функций "tools"
        sb.append("\"tools\":[");
        Map<String, CapellaTool> tools = ToolRegistry.getInstance().getTools();
        boolean firstTool = true;
        
        for (CapellaTool tool : tools.values()) {
            if (!firstTool) sb.append(",");
            firstTool = false;
            
            sb.append("{")
              .append("\"type\":\"function\",")
              .append("\"function\":{")
              .append("\"name\":\"").append(escapeJson(tool.getName())).append("\",")
              .append("\"description\":\"").append(escapeJson(tool.getDescription())).append("\",")
              .append("\"parameters\":").append(tool.getInputSchema()) // Схема уже валидный JSON
              .append("}")
              .append("}");
        }
        sb.append("]");
        sb.append("}");
        
        return sb.toString();
    }
    
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}