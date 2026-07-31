package com.ai.bovenko.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сетевой адаптер ИИ и нативный парсер ответов без внешних зависимостей.
 */
public class LLMAdapter {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions"; 
    private static final String API_KEY = "sk-placeholder-your-key-here"; 

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Контейнер для хранения распарсенного ответа от сервера шлюза.
     */
    public static class LLMResponse {
        public String content;   // Текстовый ответ (если есть)
        public String toolCalls; // JSON строка массива tool_calls (если есть)
        public String error;     // Описание ошибки рантайма
    }

    public static LLMResponse post(String jsonPayload) {
        LLMResponse result = new LLMResponse();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                result.error = "HTTP " + response.statusCode() + ": " + response.body();
                return result;
            }

            String body = response.body();

            // 1. Извлекаем текстовый контент через регулярное выражение
            Pattern contentPattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
            Matcher contentMatcher = contentPattern.matcher(body);
            if (contentMatcher.find()) {
                result.content = unescapeJson(contentMatcher.group(1));
            }

            // 2. Ищем массив вызовов функций (tool_calls) через алгоритм баланса скобок
            int toolCallsIdx = body.indexOf("\"tool_calls\"");
            if (toolCallsIdx != -1) {
                int startBracket = body.indexOf("[", toolCallsIdx);
                if (startBracket != -1) {
                    int bracketCount = 1;
                    int endBracket = startBracket + 1;
                    while (bracketCount > 0 && endBracket < body.length()) {
                        char ch = body.charAt(endBracket);
                        if (ch == '[') bracketCount++;
                        else if (ch == ']') bracketCount--;
                        endBracket++;
                    }
                    result.toolCalls = body.substring(startBracket, endBracket);
                }
            }

        } catch (Exception e) {
            result.error = "Сбой сети: " + e.getMessage();
        }
        return result;
    }

    private static String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\\", "\\")
                  .replace("\\\"", "\"")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }
}