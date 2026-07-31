package com.ai.bovenko.assistant;

/**
 * Утилитарный класс для очистки грязного HTML/XML кода из полей описания Capella
 */
public class RichTextSanitizer {

    /**
     * Очищает входящую строку от тегов, декодирует спецсимволы и нормализует кавычки.
     */
    public static String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "";
        }

        // 1. Удаляем блочные и строчные теги.
        // Флаг (?s) (Pattern.DOTALL) критически важен: он заставляет точку включать символы переноса строки (\n),
        // что позволяет корректно удалять теги, если они разбиты на несколько строк.
        String cleanText = rawHtml.replaceAll("(?s)<[^>]*>", "");

        // 2. Декодируем стандартные HTML-сущности
        cleanText = cleanText.replace("&quot;", "\"")
                             .replace("&nbsp;", " ")
                             .replace("&amp;", "&")
                             .replace("&lt;", "<")
                             .replace("&gt;", ">");

        // 3. Нормализуем "умные" кавычки к стандартному виду
        cleanText = cleanText.replace("“", "\"")
                             .replace("”", "\"")
                             .replace("‘", "'")
                             .replace("’", "'");

        // Возвращаем очищенный текст без лишних пробелов по краям
        return cleanText.trim();
    }
}