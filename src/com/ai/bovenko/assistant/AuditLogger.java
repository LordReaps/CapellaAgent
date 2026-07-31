package com.ai.bovenko.assistant;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

/**
 * Персистентный журнал аудита (Audit Log).
 * Записывает все значимые операции системы в формате JSONL.
 */
public class AuditLogger {

    private static File logFile = null;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * Инициализирует файл логирования в директории метаданных плагина.
     */
    private static synchronized File getLogFile() {
        if (logFile == null) {
            try {
                // Пытаемся получить bundle плагина динамически, чтобы не завязываться на жесткие константы
                Bundle bundle = Platform.getBundle("com.ai.bovenko.assistant");
                if (bundle == null) {
                    // Фолбэк, если символическое имя бандла отличается
                    bundle = Platform.getBundle(Platform.getProduct().getDefiningBundle().getSymbolicName());
                }
                
                IPath stateLocation = Platform.getStateLocation(bundle);
                File stateDir = stateLocation.toFile();
                if (!stateDir.exists()) {
                    stateDir.mkdirs();
                }
                logFile = new File(stateDir, "audit.jsonl");
            } catch (Exception e) {
                // Если мы находимся вне OSGi-контекста (например, при юнит-тестах), сохраняем в temp
                logFile = new File(System.getProperty("java.io.tmpdir"), "audit.jsonl");
            }
        }
        return logFile;
    }

    /**
     * Записывает структурированное событие в лог в формате JSONL.
     * * @param action Тип действия (вызов инструмента, смена статуса черновика и т.д.)
     * @param details Свободная JSON-строка или плоский список параметров для детализации
     */
    public static synchronized void log(String action, String details) {
        File file = getLogFile();
        String timestamp = dateFormat.format(new Date());
        
        // Формируем плоскую строку JSONL вручную
        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"timestamp\":\"").append(timestamp).append("\",")
          .append("\"action\":\"").append(escapeJson(action)).append("\",")
          .append("\"details\":").append(details != null && details.startsWith("{") ? details : "\"" + escapeJson(details) + "\"")
          .append("}");

        try (FileWriter fw = new FileWriter(file, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(sb.toString());
        } catch (IOException e) {
            // Запись в стандартный лог ошибок Eclipse, если файл недоступен
            System.err.println("[AuditLogger Error] Не удалось записать лог аудита: " + e.getMessage());
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}