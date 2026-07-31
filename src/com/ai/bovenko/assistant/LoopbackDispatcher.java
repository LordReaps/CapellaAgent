package com.ai.bovenko.assistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.swt.widgets.Display;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Диспетчер петлевого вызова (Loopback).
 * Перехватывает текстовые ответы GigaChat, извлекает из них эмулированные 
 * вызовы инструментов (даже с одинарными кавычками) и активирует Controlled Write.
 */
public class LoopbackDispatcher {

    /**
     * Анализирует сырой ответ от GigaChat API и направляет его либо в интерфейс черновиков, 
     * либо выводит как обычный текст ассистента.
     */
	public void processAndDispatch(String response, ChatView chatView) {
	    // 1. Ищем начало и конец JSON-блока по маркерным символам
	    int start = response.indexOf("{");
	    int end = response.lastIndexOf("}");

	    if (start != -1 && end != -1 && end > start) {
	        String json = response.substring(start, end + 1);

	        // 2. Проверяем наличие нужного инструмента
	        if (json.contains("capella.propose_create_operational_activity")) {
	            
	            // 3. Извлекаем данные через поиск подстрок
	            String name = extractValueManual(json, "name");
	            String description = extractValueManual(json, "description");
	            String targetContainerId = extractValueManual(json, "target_container_id");
	            
	            // 4. Отправляем на локальный сервер
	            System.out.println("[DEBUG-DISPATCHER] Отправляю: Name=" + name + ", Desc=" + description);
	            sendToLocalToolEndpoint("capella.propose_create_operational_activity", name, description, targetContainerId);
	            
	            Display.getDefault().asyncExec(() -> 
	                chatView.appendMessage("[Система]: Активирована операция: " + name));
	            return;
	        }
	        else if (json.contains("capella.propose_create_operational_entity")) {
	        	
	        	// 3. Извлекаем данные через поиск подстрок
	            String name = extractValueManual(json, "name");
	            String description = extractValueManual(json, "description");
	            String targetContainerId = extractValueManual(json, "target_container_id");


	            // 4. Отправляем на локальный сервер
	            System.out.println("[DEBUG-DISPATCHER] Отправляю: Name=" + name + ", Desc=" + description);
	            sendToLocalToolEndpoint("capella.propose_create_operational_entity", name, description, targetContainerId);
	            
	            Display.getDefault().asyncExec(() -> 
	                chatView.appendMessage("[Система]: Активирована операция: " + name));
	            return;
	        }
	    }

	    // Фолбэк: обычный текст
	    Display.getDefault().asyncExec(() -> chatView.appendMessage("Ассистент: " + response));
	}

	/**
	 * Метод без регулярных выражений: ищет ключ и берет значение до следующей кавычки
	 */
	private String extractValueManual(String json, String key) {
	    // Ищем ключ, не опираясь на кавычки (GigaChat может их пропустить)
	    // Ищем вхождение ключа
	    String searchKey = "\"" + key + "\"";
	    int keyIdx = json.indexOf(searchKey);
	    
	    // Если не нашли с кавычками, ищем без них
	    if (keyIdx == -1) {
	        searchKey = key; 
	        keyIdx = json.indexOf(searchKey);
	    }
	    
	    if (keyIdx == -1) return "ERR_KEY_NOT_FOUND_" + key;
	    
	    // Ищем двоеточие после ключа
	    int colonIdx = json.indexOf(":", keyIdx + searchKey.length());
	    if (colonIdx == -1) return "ERR_NO_COLON_" + key;
	    
	    // Ищем начало значения (пропускаем пробелы)
	    int start = -1;
	    for (int i = colonIdx + 1; i < json.length(); i++) {
	        char c = json.charAt(i);
	        if (c == '"' || c == '\'') {
	            start = i + 1; // Начало текста после кавычки
	            break;
	        } else if (Character.isLetterOrDigit(c)) {
	            start = i; // Начало текста без кавычек
	            break;
	        }
	    }
	    
	    if (start == -1) return "ERR_START_NOT_FOUND_" + key;
	    
	    // Ищем конец значения (кавычка или запятая/фигурная скобка)
	    int end = -1;
	    char quote = (start > 0 && json.charAt(start-1) == '\'') ? '\'' : '"';
	    
	    // Если начали с кавычки, ищем такую же
	    if (start > 0 && (json.charAt(start-1) == '"' || json.charAt(start-1) == '\'')) {
	        end = json.indexOf(json.charAt(start-1), start);
	    } else {
	        // Если без кавычек, ищем до конца объекта или до запятой
	        int commaIdx = json.indexOf(",", start);
	        int bracketIdx = json.indexOf("}", start);
	        end = Math.min(commaIdx != -1 ? commaIdx : 999999, bracketIdx != -1 ? bracketIdx : 999999);
	    }
	    
	    return (end != -1) ? json.substring(start, end).trim() : "ERR_VAL_END_NOT_FOUND_" + key;
	}

	
	
    /**
     * Отказоустойчивый метод вытаскивания поля "content" из общего JSON-пакета Сбера,
     * если ИИ решил просто поговорить с инженером текстовыми сообщениями.
     */
    private String extractContentFieldResilient(String json) {
        if (json == null || json.isEmpty()) return "";
        
        Pattern contentPattern = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"\\s*(\\s*},|\\s*})", Pattern.DOTALL);
        Matcher matcher = contentPattern.matcher(json);
        if (matcher.find()) {
            String clean = matcher.group(1);
            // Деэкранируем управляющие спецсимволы, чтобы текст в SWT-чате выглядел красиво
            return clean.replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
        }
        
        // Крайний случай: если JSON совсем поврежден, возвращаем очищенную от скобок строку
        return json.replaceAll("[\"{}]+", "").trim();
    }

	/**
	 * Метод отправки, который вы не могли вызвать
	 */
    private void sendToLocalToolEndpoint(String tool, String name, String description, String targetContainerId) {
    	try {
            // Динамически формируем аргументы в виде валидного JSON-объекта
            StringBuilder argsBuilder = new StringBuilder();
            argsBuilder.append("{");
            argsBuilder.append("\"name\":\"").append(escapeJson(name)).append("\"");
            
            if (description != null && !description.isEmpty()) {
                argsBuilder.append(",\"description\":\"").append(escapeJson(description)).append("\"");
            }
            if (targetContainerId != null && !targetContainerId.isEmpty()) {
                argsBuilder.append(",\"target_container_id\":\"").append(escapeJson(targetContainerId)).append("\"");
            }
            argsBuilder.append("}");

            String jsonPayload = String.format(
                "{\"tool\": \"%s\", \"arguments\": %s}",
                tool, argsBuilder.toString()
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8080/tools/call"))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[LoopbackDispatcher] Ответ локального эндпоинта (код): " + response.statusCode());
            
        } catch (Exception e) {
            System.err.println("[LoopbackDispatcher] Ошибка отправки запроса на локальный эндпоинт: " + e.getMessage());
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}