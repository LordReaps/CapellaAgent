package com.ai.bovenko.assistant;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Локальный HTTP-эндпоинт
 * Обеспечивает изолированный фоновый запуск встроенного сервера
 * для интеграции с внешними инструментами тестирования и MCP.
 */
public class LocalToolEndpoint {
    private static HttpServer server;
    private static final int PORT = 8080;

    /**
     * Запуск сервера в отдельном потоке (Thread), предотвращающий зависание UI Eclipse.
     */
    public static synchronized void startServer() {
        if (server != null) return;
        try {
            // Слушаем исключительно локальный интерфейс loopback (127.0.0.1) в целях безопасности
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            
            // =========================================================================
            // 1. Эндпоинт GET /tools/list — получение метаданных всех инструментов
            // =========================================================================
            server.createContext("/tools/list", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    boolean first = true;
                    for (CapellaTool tool : ToolRegistry.getInstance().getTools().values()) {
                        if (!first) sb.append(",");
                        first = false;
                        sb.append("{")
                          .append("\"name\":\"").append(escapeJson(tool.getName())).append("\",")
                          .append("\"description\":\"").append(escapeJson(tool.getDescription())).append("\",")
                          .append("\"inputSchema\":").append(tool.getInputSchema()) // Схема уже содержит валидный JSON
                          .append("}");
                    }
                    sb.append("]");

                    sendJsonResponse(exchange, 200, sb.toString());
                }
            });

            // =========================================================================
            // 2. Эндпоинт POST /tools/call — выполнение инструмента по его имени
            // =========================================================================
            server.createContext("/tools/call", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }

                    // Читаем входящий поток байт в UTF-8
                    String body;
                    try (InputStream is = exchange.getRequestBody()) {
                        body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    
                    System.out.println("[DEBUG-ENDPOINT] Получен JSON: " + body);

                    // Извлекаем параметры "tool" и "arguments" с помощью надежного токенизатора
                    String toolName = extractJsonKey(body, "tool");
                    String rawArguments = extractJsonKey(body, "arguments");

                    if (toolName == null || toolName.isEmpty()) {
                        sendJsonResponse(exchange, 400, "{\"error\":\"Missing or invalid 'tool' field in request body\"}");
                        return;
                    }

                    // Нормализуем аргументы (деэкранируем, если они пришли в виде строки)
                    String jsonArguments = normalizeArguments(rawArguments);

                    try {
                        // Вызываем логику выполнения внутри монолитного плагина через ToolRegistry
                        String resultJson = ToolRegistry.getInstance().executeTool(toolName, jsonArguments);
                        sendJsonResponse(exchange, 200, resultJson);
                    } catch (Exception e) {
                        sendJsonResponse(exchange, 500, "{\"error\":\"Tool execution internal error: " + escapeJson(e.getMessage()) + "\"}");
                    }
                }
            });

            // Настройка стандартного исполнителя и запуск в фоновом режиме
            server.setExecutor(null); 
            new Thread(() -> {
                try {
                    server.start();
                    System.out.println("[Capella AI] Local HTTP Server successfully started on http://127.0.0.1:" + PORT);
                } catch (Exception e) {
                    System.err.println("[Capella AI] Critical error during server startup: " + e.getMessage());
                }
            }, "Capella-AI-Server-Thread").start();

        } catch (IOException e) {
            System.err.println("[Capella AI] Failed to bind HttpServer: " + e.getMessage());
        }
    }

    /**
     * Остановка сервера при уничтожении ViewPart или деактивации плагина.
     */
    public static synchronized void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("[Capella AI] Local HTTP Server stopped.");
        }
    }

    /**
     * Утилитарный метод отправки JSON-ответа.
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Экранирование спецсимволов для формирования корректного JSON-вывода.
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Высокоточный отказоустойчивый метод для ручного извлечения значения ключа из JSON-строки.
     * Заменяет собой тяжелые сторонние библиотеки и регулярные выражения, чувствительные к структуре.
     */
    private static String extractJsonKey(String json, String key) {
        if (json == null || key == null) return null;
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;

        // Ищем двоеточие после ключа
        int colonIdx = json.indexOf(":", keyIdx + searchKey.length());
        if (colonIdx == -1) return null;

        // Определяем, где начинается значение
        int startIdx = colonIdx + 1;
        while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) {
            startIdx++;
        }
        if (startIdx >= json.length()) return null;

        char firstChar = json.charAt(startIdx);

        // Сценарий 1: Значение является строкой (начинается с кавычки)
        if (firstChar == '"') {
            int endIdx = startIdx + 1;
            boolean escaped = false;
            while (endIdx < json.length()) {
                char c = json.charAt(endIdx);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // Нашли закрывающую кавычку строки
                    return json.substring(startIdx + 1, endIdx);
                }
                endIdx++;
            }
        } 
        // Сценарий 2: Значение является JSON-объектом (начинается с '{')
        else if (firstChar == '{') {
            int bracketCount = 1;
            int endIdx = startIdx + 1;
            while (endIdx < json.length() && bracketCount > 0) {
                char c = json.charAt(endIdx);
                if (c == '{') bracketCount++;
                else if (c == '}') bracketCount--;
                endIdx++;
            }
            if (bracketCount == 0) {
                return json.substring(startIdx, endIdx);
            }
        }

        return null;
    }

    /**
     * Нормализует строку аргументов, обрабатывая как чистые JSON-объекты, 
     * так и принудительно экранированные строки, приходящие от LLM.
     */
    private static String normalizeArguments(String rawArgs) {
        if (rawArgs == null) return "{}";
        String trimmed = rawArgs.trim();
        if (trimmed.isEmpty()) return "{}";

        // Если это уже готовый объект, возвращаем как есть
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        // Если это строка, содержащая экранированный JSON, производим деэкранирование
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}