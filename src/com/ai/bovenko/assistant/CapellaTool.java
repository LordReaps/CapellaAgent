package com.ai.bovenko.assistant;

/**
 * Универсальный интерфейс исполняемого инструмента модели.
 * Соответствует спецификации Function Calling (OpenAI / MCP).
 */
public interface CapellaTool {
    /** Уникальное имя инструмента, например: capella.get_current_element */
    String getName();
    
    /** Описание назначения инструмента для системного промпта LLM */
    String getDescription();
    
    /** Строка JSON-схемы (JSON Schema), описывающая ожидаемые аргументы */
    String getInputSchema();
    
    /** Тело выполнения инструмента. Принимает аргументы в JSON, возвращает результат в JSON */
    String execute(String jsonArguments);
}