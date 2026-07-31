package com.ai.bovenko.assistant;

/**
 * Рулевой rule-based классификатор запросов пользователя.
 * Разделяет интенты без привлечения тяжелых нейросетевых библиотек.
 */
public class Router {

    public static String classify(String text) {
        if (text == null) return "ordinary_question";
        
        String input = text.toLowerCase().trim();

        // Проверяем триггеры на контролируемую запись данных в модель
        if (input.contains("создай") || input.contains("добавь") || input.contains("сгенерируй")) {
            return "controlled_write";
        }
        
        // Проверяем триггеры на чтение/анализ структуры
        if (input.contains("покажи") || input.contains("найди") || input.contains("какие есть") || input.contains("список")) {
            return "read_model";
        }

        // По умолчанию считаем обычным вопросом об архитектуре систем
        return "ordinary_question";
    }
}