package com.ai.bovenko.assistant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Транспортный адаптер для прямой работы с GigaChat API Сбера.
 * Работает на чистой Java, кэширует сессионные токены и обходит SSL-проверки Минцифры.
 */
public class GigaChatAdapter {

    // Авторизационные данные из вашего .env
    private static final String AUTH_DATA = "";
    private static final String SCOPE = "GIGACHAT_API_PERS";
    
    // Эндпоинты шлюзов Сбера и имя рабочей модели
    private static final String AUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String COMPLETIONS_URL = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";
    private static final String MODEL_NAME = "GigaChat";

    // Статическое кэширование токена авторизации (работает по логике вашего Python TokenManager)
    private static String cachedToken = null;
    private static long tokenExpiryTime = 0;

    private final HttpClient httpClient;

    public GigaChatAdapter() {
        // Создаем HttpClient, игнорирующий строгие SSL-проверки, чтобы плагин не падал в Capella
        this.httpClient = createUnsafeHttpClient();
    }
    
    
    
    /**
     * Отправляет готовую сформированную JSON-нагрузку (включая историю и инструменты) в GigaChat API.
     * @param jsonPayload Полный сформированный JSON-пакет от ContextBuilder
     * @return Сырой JSON-ответ 
     */
    public String sendPayload(String jsonPayload) {
        try {
            String token = getAccessToken();
            String rqUid = UUID.randomUUID().toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COMPLETIONS_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("RqUID", rqUid)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            System.out.println("[GigaChatAdapter] Отправка сформированного пакета контекста в GigaChat API...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                return "{\"error\": \"Ошибка API GigaChat: " + response.statusCode() + " - " + escapeJson(response.body()) + "\"}";
            }
        } catch (Exception e) {
            System.err.println("[GigaChatAdapter] Исключение при отправке сырого пакета: " + e.getMessage());
            e.printStackTrace();
            return "{\"error\": \"Критическое исключение сети/транспорта: " + escapeJson(e.getMessage()) + "\"}";
        }
    }
    

    /**
     * Отправляет текстовый запрос пользователя в GigaChat API и возвращает сырой JSON-ответ Сбера.
     * @param userText Текст запроса от инженера Capella
     * @param systemPrompt Системная инструкция (интент) для управления поведением модели
     * @return JSON-строка ответа от GigaChat или JSON с описанием ошибки
     */
    public String sendChatCompletion(String userText, String systemPrompt) {
        try {
            // Шаг 1. Получаем действующий Bearer-токен (из кэша или запрашиваем новый)
            String token = getAccessToken();
            String rqUid = UUID.randomUUID().toString();

            // Сборка JSON-нагрузки запроса генерации текста с динамическими промптами
            String jsonPayload = "{"
                    + "\"model\":\"" + MODEL_NAME + "\","
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},"
                    + "{\"role\":\"user\",\"content\":\"" + escapeJson(userText) + "\"}"
                    + "],"
                    + "\"temperature\":0.1," // Низкая температура гарантирует строгое следование JSON-формату
                    + "\"max_tokens\":2048"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COMPLETIONS_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("RqUID", rqUid)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            System.out.println("[GigaChatAdapter] Отправка запроса в Chat Completions API...");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Возвращаем полноценный JSON-ответ. Разбор структуры tool/text сделает LoopbackDispatcher
                return response.body();
            } else {
                // Возвращаем ошибку в JSON-формате во избежание падения по NullPointerException выше по стеку
                return "{\"error\": \"Ошибка API GigaChat: " + response.statusCode() + " - " + escapeJson(response.body()) + "\"}";
            }

        } catch (Exception e) {
            System.err.println("[GigaChatAdapter] Исключение при выполнении запроса: " + e.getMessage());
            e.printStackTrace();
            return "{\"error\": \"Критическое исключение сети/транспорта: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * Поток-безопасный метод извлечения токена. Если старый токен еще "жив", 
     * отдает значение из оперативной памяти без повторного сетевого вызова.
     */
    private synchronized String getAccessToken() throws Exception {
        // Если токен есть в кэше и время жизни не истекло — отдаем его (экономим время на запросах)
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedToken;
        }

        System.out.println("[GigaChatAdapter] Запрос нового OAuth-токена (кэш пуст или устарел)...");
        String rqUid = UUID.randomUUID().toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("RqUID", rqUid)
                .header("Authorization", "Basic " + AUTH_DATA) // "Basic" прибавляется автоматически к вашему токену
                .POST(HttpRequest.BodyPublishers.ofString("scope=" + SCOPE))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String responseBody = response.body();
            
            // Нативный разбор полей без привлечения тяжелых сторонних библиотек (Jackson/Gson)
            String token = extractJsonValue(responseBody, "access_token");
            String expiresAtStr = extractJsonValue(responseBody, "expires_at");

            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Поле access_token отсутствует в ответе Сбера: " + responseBody);
            }

            cachedToken = token;
            try {
                // Сбер возвращает Unix Timestamp окончания действия токена в миллисекундах
                long expiresAt = Long.parseLong(expiresAtStr);
                // Отнимаем 5 минут (300 000 мс) резервного времени на сетевые задержки
                tokenExpiryTime = expiresAt - (5 * 60 * 1000);
            } catch (Exception e) {
                // Фолбэк-стратегия: если таймштамп не распарсился, жестко выставляем 25 минут жизни токена
                tokenExpiryTime = System.currentTimeMillis() + (25 * 60 * 1000);
            }

            System.out.println("[GigaChatAdapter] Новый OAuth-токен успешно получен и сохранен в кэш.");
            return cachedToken;
        } else {
            throw new RuntimeException("Ошибка авторизации в GigaChat OAuth. Статус-код: " + response.statusCode() + " -> " + response.body());
        }
    }

    /**
     * Универсальный отказоустойчивый метод поиска и извлечения параметров из строки JSON.
     * Корректно забирает как строковые значения в кавычках, так и чистые числа (timestamp).
     */
    public static String extractJsonValue(String json, String key) {
        if (json == null || key == null || json.isEmpty()) return "";
        
        // 1. Поиск текстовых значений: "key" : "value"
        Pattern stringPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher stringMatcher = stringPattern.matcher(json);
        if (stringMatcher.find()) {
            return stringMatcher.group(1);
        }
        
        // 2. Поиск числовых значений (без кавычек, актуально для поля expires_at): "key" : 12345678
        Pattern numberPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher numberMatcher = numberPattern.matcher(json);
        if (numberMatcher.find()) {
            return numberMatcher.group(1);
        }
        
        return "";
    }

    /**
     * Экранирует управляющие символы строки перед помещением её внутрь JSON-пакета
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * Инициализирует доверительный SSL-контекст, который безоговорочно принимает любые 
     * SSL-сертификаты. Заменяет логику "verify=True" из Python, учитывая особенности Java JRE в Eclipse.
     */
    private HttpClient createUnsafeHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Критический сбой инициализации небезопасного HttpClient", e);
        }
    }
}