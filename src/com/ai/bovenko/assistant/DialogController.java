package com.ai.bovenko.assistant;

import org.eclipse.swt.widgets.Display;

/**
 * Оркестратор диалога. Управляет фоновыми потоками и обрабатывает 
 * многошаговый цикл Function Calling (вызов инструментов ИИ) исключительно через GigaChat.
 */
public class DialogController {

    /**
     * Точка входа для обработки сообщения пользователя.
     * @param userText Текст из текстового поля чата
     * @param chatView Ссылка на класс интерфейса для вывода ответов
     */
    public static void processUserMessage(String userText, final ChatView chatView) {
        if (userText == null || userText.trim().isEmpty()) return;

        // 1. Добавляем сообщение пользователя в память сессии
        SessionManager.getInstance().addMessage(new ChatMessage("user", userText));

        // 2. Запускаем фоновый поток (Thread), чтобы сетевые задержки не вешали UI Capella
        new Thread(() -> {
            try {
                // Классифицируем интент с помощью Роутера
                String intent = Router.classify(userText);
                GigaChatAdapter gigaChatAdapter = new GigaChatAdapter();

                if ("controlled_write".equals(intent)) {
                    updateUiMessage(chatView, "[Система]: Обнаружен запрос на изменение модели. Направление запроса в транспортный слой...");
                    
                    
                    String systemPrompt = "Ты — ИИ-ассистент в Capella. Максимальная длина твоего ответа - 500 символов. Тебе доступны инструменты создания элементов модели через паттерн Controlled Write. \n" 
                    		+ "При запросах на создание:\n"
                            + "1. Если пользователь хочет создать ОПЕРАЦИОННУЮ АКТИВНОСТЬ (OperationalActivity), ты ДОЛЖЕН вызвать инструмент `capella.propose_create_operational_activity`.\n"
                            + "2. Если пользователь хочет создать ОПЕРАЦИОННУЮ СУЩНОСТЬ / Актора / Роль (OperationalEntity), ты ДОЛЖЕН вызвать инструмент `capella.propose_create_operational_entity`.\n"
                            + "Для вызова инструмента ты должен вернуть СТРОГО JSON-объект в следующем формате (без лишних слов, markdown-разметки или backticks):\n"
                            + "```json\n"
                            + "{\n"
                            + "  \"tool\": \"capella.propose_create_operational_activity (или соответствуюющий метод для другого элемента)\",\n"
                            + "  \"arguments\": {\n"
                            + "    \"name\": \"Новая Операция\",\n"
                            + "    \"description\": \"Описание операции\"\n"
                            + "    \"target_container_id\": \"<ID контейнера, если известен>\"\n"
                            + "  }\n"
                            + "}\n"
                            + "```";
                    
                    // Выполняем прямой запрос к GigaChat API Сбера
                    String rawGigaChatResponse = gigaChatAdapter.sendChatCompletion(userText, systemPrompt);
                    
                    // Извлекаем текстовое содержимое ассистента (поле content)
                    String cleanAssistantText = extractJsonString(rawGigaChatResponse, "content");
                    
                    if (cleanAssistantText == null || cleanAssistantText.isEmpty()) {
                        cleanAssistantText = rawGigaChatResponse;
                    } else {
                        cleanAssistantText = unescapeJson(cleanAssistantText);
                    }
                    
                    // Передаем очищенный текст в диспетчер для запуска Controlled Write
                    LoopbackDispatcher dispatcher = new LoopbackDispatcher();
                    dispatcher.processAndDispatch(cleanAssistantText, chatView);
                    return;
                }

                // Логика для интентов ordinary_question и read_model через единый транспорт GigaChat
                boolean continueLoop = true;
                int maxIterations = 5; // Защита от бесконечного зацикливания ИИ

                while (continueLoop && maxIterations-- > 0) {
                    // Собираем JSON-пакет (Контекст + История + Схемы инструментов)
                    String payload = ContextBuilder.buildPayload();

                    // Отправляем запрос в сеть через GigaChatAdapter
                    String rawResponse = gigaChatAdapter.sendPayload(payload);

                    // Проверяем наличие ошибок в ответе
                    String error = null;
                    if (rawResponse.contains("\"error\"")) {
                        error = extractJsonString(rawResponse, "error");
                    }

                    if (error != null) {
                        updateUiMessage(chatView, "[Ошибка API GigaChat]: " + unescapeJson(error));
                        break;
                    }

                    // Извлекаем текстовый ответ ассистента
                    String content = extractJsonString(rawResponse, "content");
                    if (content != null && !content.trim().isEmpty()) {
                        content = unescapeJson(content);
                        SessionManager.getInstance().addMessage(new ChatMessage("assistant", content));
                        updateUiMessage(chatView, "Ассистент: " + content);
                    }

                    // Извлекаем блок вызова инструментов (tool_calls)
                    String toolCalls = extractToolCallsField(rawResponse);

                    if (toolCalls != null && !toolCalls.trim().isEmpty() && !toolCalls.equals("[]")) {
                        // Сохраняем вызов со стороны ИИ в историю
                        SessionManager.getInstance().addMessage(new ChatMessage("assistant", null, toolCalls, null));

                        // Извлекаем метаданные вызова функции нативным парсером
                        String toolName = parseToolName(toolCalls);
                        String toolArgs = parseToolArgs(toolCalls);
                        String callId = parseToolCallId(toolCalls);

                        if (toolName != null && callId != null) {
                            updateUiMessage(chatView, "[ИИ активировал инструмент]: " + toolName + " с параметрами " + toolArgs);

                            // Выполняем инструмент через реестр напрямую в метамодели Capella
                            String toolResult = ToolRegistry.getInstance().executeTool(toolName, toolArgs);

                            // Кладем результат выполнения в историю сессии
                            SessionManager.getInstance().addMessage(new ChatMessage("tool", toolResult, callId, toolName));

                            // Продолжаем цикл обработки (отправляем результат выполнения обратно в модель)
                            continueLoop = true;
                        } else {
                            continueLoop = false;
                        }
                    } else {
                        continueLoop = false;
                    }
                }

            } catch (Exception e) {
                updateUiMessage(chatView, "[Критическая ошибка оркестратора]: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Безопасное обновление SWT UI-компонентов из фонового потока
     */
    private static void updateUiMessage(final ChatView view, final String message) {
        Display.getDefault().asyncExec(() -> {
            if (view != null) {
                view.appendMessage(message);
            }
        });
    }

    // =====================================================================
    // Нативные парсеры строк на регулярных выражениях (Без внешних библиотек)
    // =====================================================================
    
    private static String extractJsonString(String json, String key) {
        if (json == null || key == null || json.isEmpty()) return null;
        // Паттерн захватывает JSON-строку с учетом любых экранированных символов внутри кавычек
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String extractToolCallsField(String json) {
        if (json == null || json.isEmpty()) return null;
        int idx = json.indexOf("\"tool_calls\"");
        if (idx != -1) {
            int start = json.indexOf("[", idx);
            if (start != -1) {
                int count = 1;
                int end = start + 1;
                while (count > 0 && end < json.length()) {
                    char c = json.charAt(end);
                    if (c == '[') count++;
                    else if (c == ']') count--;
                    end++;
                }
                return json.substring(start, end);
            }
        }
        return null;
    }

    private static String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\\", "\\")
                  .replace("\\\"", "\"")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }

    private static String parseToolName(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String parseToolCallId(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String parseToolArgs(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"arguments\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        int idx = json.indexOf("\"arguments\"");
        if (idx != -1) {
            int start = json.indexOf("{", idx);
            if (start != -1) {
                int count = 1;
                int end = start + 1;
                while (count > 0 && end < json.length()) {
                    char c = json.charAt(end);
                    if (c == '{') count++;
                    else if (c == '}') count--;
                    end++;
                }
                return json.substring(start, end);
            }
        }
        return "{}";
    }
}