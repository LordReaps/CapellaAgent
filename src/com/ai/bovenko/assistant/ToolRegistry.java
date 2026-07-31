package com.ai.bovenko.assistant;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Реестр-синглтон инструментов ИИ. Работает на чистом Java без использования org.json.
 */
public class ToolRegistry {
    private static volatile ToolRegistry instance;
    private final Map<String, CapellaTool> tools = new HashMap<>();

    private ToolRegistry() {
        registerTool(new GetCurrentElementTool());
        registerTool(new FindOperationalElementsTool());
        registerTool(new ProposeCreateOperationalActivityTool());
        registerTool(new ProposeCreateOperationalEntityTool());
        registerTool(new GetChangeSetTool());
        registerTool(new ApplyConfirmedChangeSetTool());
    }

    public static ToolRegistry getInstance() {
        if (instance == null) {
            synchronized (ToolRegistry.class) {
                if (instance == null) {
                    instance = new ToolRegistry();
                }
            }
        }
        return instance;
    }

    public void registerTool(CapellaTool tool) {
        tools.put(tool.getName(), tool);
    }

    public Map<String, CapellaTool> getTools() {
        return tools;
    }

    public String executeTool(String name, String jsonArguments) {
        CapellaTool tool = tools.get(name);
        if (tool == null) {
            return "{\"error\":\"Инструмент не найден: " + escapeJson(name) + "\"}";
        }
        try {
            return tool.execute(jsonArguments);
        } catch (Exception e) {
            return "{\"error\":\"Ошибка выполнения: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * Утилита для безопасного экранирования строк под формат JSON
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // =====================================================================
    // ИНСТРУМЕНТ 1: capella.get_current_element
    // =====================================================================
    private static class GetCurrentElementTool implements CapellaTool {
        @Override
        public String getName() { return "capella.get_current_element"; }

        @Override
        public String getDescription() {
            return "Возвращает ID, тип, имя и очищенное описание текущего выделенного элемента в UI Capella.";
        }

        @Override
        public String getInputSchema() {
            return "{\"type\":\"object\",\"properties\":{}}";
        }

        @Override
        public String execute(String jsonArguments) {
            // Чистая операция чтения из памяти — никакого UI-потока!
            EObject element = SessionManager.getInstance().getCurrentSelectedElement();
            
            if (element == null) {
                return "{\"status\":\"warning\",\"message\":\"В интерфейсе Capella ничего не выбрано.\"}";
            }
            
            String id = CapellaModelAdapter.getElementId(element);
            String type = CapellaModelAdapter.getElementType(element);
            String name = CapellaModelAdapter.getElementName(element);
            String desc = RichTextSanitizer.sanitize(CapellaModelAdapter.getRawDescription(element));

            return "{"
                + "\"id\":\"" + escapeJson(id) + "\","
                + "\"type\":\"" + escapeJson(type) + "\","
                + "\"name\":\"" + escapeJson(name) + "\","
                + "\"description\":\"" + escapeJson(desc) + "\""
                + "}";
        }
    }

    // =====================================================================
    // ИНСТРУМЕНТ 2: capella.find_operational_elements
    // =====================================================================
    private static class FindOperationalElementsTool implements CapellaTool {
        @Override
        public String getName() { return "capella.find_operational_elements"; }

        @Override
        public String getDescription() {
            return "Рефлексивно находит все элементы выбранного типа на операционном уровне модели. " +
                   "Аргумент elementType принимает: 'Activity', 'Entity' или 'Actor'.";
        }

        @Override
        public String getInputSchema() {
            return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"elementType\":{\"type\":\"string\",\"enum\":[\"Activity\",\"Entity\",\"Actor\"]}"
                + "},"
                + "\"required\":[\"elementType\"]"
                + "}";
        }

        @Override
        public String execute(String jsonArguments) {
            String targetType = parseTargetType(jsonArguments);
            
            // Берем точку опоры для поиска из кэша памяти
            EObject anchor = SessionManager.getInstance().getCurrentSelectedElement();
            if (anchor == null) {
                return "{\"error\":\"Нет активного элемента для определения контекста поиска.\"}";
            }
            
            // Получаем корневой контейнер модели для этого элемента
            EObject root = EcoreUtil.getRootContainer(anchor);
            
            StringBuilder jsonList = new StringBuilder();
            jsonList.append("[");
            
            TreeIterator<EObject> it = EcoreUtil.getAllContents(root, true);
            boolean isFirst = true;
            
            while (it.hasNext()) {
                EObject obj = it.next();
                String metaTypeName = obj.eClass().getName();
                
                boolean isMatch = false;
                if ("Activity".equals(targetType) && metaTypeName.contains("OperationalActivity")) isMatch = true;
                if ("Entity".equals(targetType) && (metaTypeName.equals("OperationalEntity") || metaTypeName.equals("Entity"))) isMatch = true;
                if ("Actor".equals(targetType) && metaTypeName.contains("Actor")) isMatch = true;

                if (isMatch) {
                    if (!isFirst) jsonList.append(",");
                    isFirst = false;
                    
                    jsonList.append("{")
                            .append("\"id\":\"").append(escapeJson(CapellaModelAdapter.getElementId(obj))).append("\",")
                            .append("\"type\":\"").append(escapeJson(metaTypeName)).append("\",")
                            .append("\"name\":\"").append(escapeJson(CapellaModelAdapter.getElementName(obj))).append("\"")
                            .append("}");
                }
            }
            jsonList.append("]");

            return "{"
                + "\"requestedType\":\"" + escapeJson(targetType) + "\","
                + "\"elements\":" + jsonList.toString()
                + "}";
        }
        
        private String parseTargetType(String jsonArguments) {
            if (jsonArguments == null || jsonArguments.isEmpty()) {
                return "";
            }
            // Ищем паттерн "elementType" : "значение"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"elementType\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(jsonArguments);
            if (matcher.find()) {
                return matcher.group(1); // Возвращает Activity, Entity или Actor
            }
            return "";
        }
    }
    
    
    
    /**
     * Инструмент: Предложить создание Operational Activity
     */
    public static class ProposeCreateOperationalActivityTool implements CapellaTool {
        @Override
        public String getName() {
            return "capella.propose_create_operational_activity";
        }
        
        @Override
        public String getInputSchema() {
            return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"description\":\"Имя создаваемой активности\"},"
                + "\"description\":{\"type\":\"string\",\"description\":\"Описание активности\"},"
                + "\"target_container_id\":{\"type\":\"string\",\"description\":\"ID целевого контейнера (опционально)\"}"
                + "},"
                + "\"required\":[\"name\",\"description\"]"
                + "}";
        }

        @Override
        public String execute(String jsonArguments) {
        	System.out.println("[AI-TRACE 4] Инструмент propose_create_operational_activity запущен.");
            String name = parseJsonField(jsonArguments, "name");
            String description = parseJsonField(jsonArguments, "description");
            String targetContainerId = parseJsonField(jsonArguments, "target_container_id");

            ChangeSet cs = ChangeSetManager.proposeCreateOperationalActivity(name, description, targetContainerId);
            System.out.println("[AI-TRACE 5] Черновик создан. ID: " + cs.getId() + ", Статус: " + cs.getStatus());
            
            // триггер UI 
            System.out.println("[AI-TRACE 6] Передаем черновик в ConfirmationManager. Хэш-код менеджера: " + ConfirmationManager.getInstance().hashCode());
            ConfirmationManager.getInstance().showPreview(cs);
            System.out.println("[AI-TRACE 7] Метод showPreview(cs) успешно вызван и завершен в потоке инструмента.");
            
            return cs.toJson();
        }
        
        @Override
        public String getDescription() {
            return "Инициализирует процесс создания OperationalActivity, запускает валидацию и создает черновик изменений (Change Set).";
        }
    }
    
    public class ProposeCreateOperationalEntityTool implements CapellaTool {

        @Override
        public String getName() {
            return "capella.propose_create_operational_entity";
        }

        @Override
        public String getDescription() {
            return "Предлагает создать новую операционную сущность (OperationalEntity / Актора) в указанном контейнере (OperationalEntityPkg). Возвращает созданный черновик изменений (Change Set).";
        }

        @Override
        public String getInputSchema() {
            return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "  \"name\":{\"type\":\"string\",\"description\":\"Название операционной сущности\"},"
                + "  \"description\":{\"type\":\"string\",\"description\":\"Описание (summary/description) сущности\"},"
                + "  \"target_container_id\":{\"type\":\"string\",\"description\":\"ID родительского пакета (OperationalEntityPkg). Если не передан, будет осуществлен автопоиск дефолтного контейнера.\"}"
                + "},"
                + "\"required\":[\"name\"]"
                + "}";
        }

        @Override
        public String execute(String jsonArguments) {
            System.out.println("[DEBUG-TOOL] Вызов инструмента propose_create_operational_entity с аргументами: " + jsonArguments);
            
            // Используем общий парсер из реестра
            String name = ToolRegistry.parseJsonField(jsonArguments, "name");
            String description = ToolRegistry.parseJsonField(jsonArguments, "description");
            String targetContainerId = ToolRegistry.parseJsonField(jsonArguments, "target_container_id");

            // 1. Инициализируем и валидируем черновик изменений
            ChangeSet cs = ChangeSetManager.proposeCreateOperationalEntity(name, description, targetContainerId);

            // 2. Отправляем черновик в менеджер подтверждений для отрисовки в UI Eclipse!
            ConfirmationManager.getInstance().showPreview(cs);

            // Возвращаем результат выполнения для GigaChat
            return cs.toJson();
        }
    }

    /**
     * Инструмент: Получить текущий статус черновика по ID
     */
    public static class GetChangeSetTool implements CapellaTool {
        @Override
        public String getName() {
            return "capella.get_change_set";
        }
        
        @Override
        public String getInputSchema() {
            return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"change_set_id\":{\"type\":\"string\",\"description\":\"Идентификатор черновика изменений\"}"
                + "},"
                + "\"required\":[\"change_set_id\"]"
                + "}";
        }
        
        @Override
        public String getDescription() {
            return "Возвращает текущую структуру и статус указанного черновика (Change Set) из текущей сессии.";
        }

        @Override
        public String execute(String jsonArguments) {
            String csId = parseJsonField(jsonArguments, "change_set_id");
            ChangeSet cs = SessionManager.getInstance().getChangeSetById(csId);
            
            if (cs == null) {
                return "{\"error\":\"NOT_FOUND\", \"message\":\"Черновик " + csId + " не найден.\"}";
            }
            return cs.toJson();
        }
    }

    
    /**
     * Инструмент: Применить подтвержденный черновик изменений
     */
    public static class ApplyConfirmedChangeSetTool implements CapellaTool {
        @Override
        public String getName() {
            return "capella.apply_confirmed_change_set";
        }
        
        @Override
        public String getInputSchema() {
            return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"change_set_id\":{\"type\":\"string\",\"description\":\"Идентификатор подтвержденного черновика для записи в модель\"}"
                + "},"
                + "\"required\":[\"change_set_id\"]"
                + "}";
        }
        
        @Override
        public String getDescription() {
            return "Выполняет транзакционную запись изменений в модель EMF Capella при условии, что черновик был подтвержден пользователем.";
        }

        @Override
        public String execute(String jsonArguments) {
            String csId = parseJsonField(jsonArguments, "change_set_id");
            ChangeSet cs = SessionManager.getInstance().getChangeSetById(csId);

            if (cs == null) {
                return "{\"error\":\"NOT_FOUND\", \"message\":\"Черновик не найден.\"}";
            }

            // Запрещаем запись, если пользователь не нажал физическую кнопку "Применить" в UI
            if (!"confirmed".equals(cs.getStatus())) {
                AuditLogger.log("CHANGE_SET_APPLY_REJECTED", "{\"change_set_id\":\"" + csId + "\",\"reason\":\"INVALID_CHANGE_SET_STATUS\"}");
                return "{\"error\":\"INVALID_CHANGE_SET_STATUS\", \"message\":\"Черновик должен иметь статус confirmed. Текущий статус: " + cs.getStatus() + "\"}";
            }

            try {
            	String containerId = cs.getPayload().get("target_container_id");
                String name = cs.getPayload().get("name");
                String description = cs.getPayload().get("description");

                // 1. Вызываем безопасный метод записи в CapellaModelAdapter
                String realGeneratedId;

                // РАЗВЕТВЛЕНИЕ ЛОГИКИ: Проверяем, какую операцию мы подтверждаем
                if ("create_operational_entity".equals(cs.getOperation())) {
                    System.out.println("[ToolRegistry] Выполняется транзакционное создание Сущности...");
                    realGeneratedId = CapellaModelAdapter.createOperationalEntity(containerId, name, description);
                } else {
                    System.out.println("[ToolRegistry] Выполняется транзакционное создание Активности...");
                    realGeneratedId = CapellaModelAdapter.createOperationalActivity(containerId, name, description);
                }
                
                // 2. Читаем созданный элемент обратно из модели для верификации
                EObject context = SessionManager.getInstance().getCurrentSelectedElement();
                EObject createdElement = CapellaModelAdapter.findElementById(context, realGeneratedId);
                
                String verifiedName = "-";
                String verifiedType = "-";
                if (createdElement != null) {
                    verifiedName = CapellaModelAdapter.getElementName(createdElement);
                    verifiedType = CapellaModelAdapter.getElementType(createdElement);
                } else {
                    throw new IllegalStateException("Элемент был записан, но не найден при обратном чтении из модели.");
                }

                cs.setStatus("applied");
                AuditLogger.log("CHANGE_SET_APPLIED", cs.toJson());

                return "{"
                    + "\"status\":\"applied\","
                    + "\"change_set_id\":\"" + csId + "\","
                    + "\"created_element\":{"
                    + "\"id\":\"" + realGeneratedId + "\","
                    + "\"type\":\"" + escapeJson(verifiedType) + "\","
                    + "\"name\":\"" + escapeJson(verifiedName) + "\""
                    + "}"
                    + "}";
            } catch (Exception e) {
                cs.setStatus("failed");
                AuditLogger.log("CHANGE_SET_FAILED", "{\"change_set_id\":\"" + csId + "\",\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                return "{\"error\":\"WRITE_FAILED\", \"message\":\"" + escapeJson(e.getMessage()) + "\"}";
            }
        }
    }

    /**
     * Универсальный утилитарный метод парсинга JSON-полей регулярками (без внешних либ)
     */
    public static String parseJsonField(String json, String fieldName) {
        if (json == null || json.isEmpty()) return "";
        // Ищем паттерн поля
        String regex = "\\\\?\"" + fieldName + "\\\\?\"\\s*:\\s*\\\\?\"([^\\\"]*)\\\\?\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String val = matcher.group(1);
            if (val == null) return "";
            // Очищаем от случайных концевых слэшей, оставшихся от экранирования JSON в консоли
            if (val.endsWith("\\")) {
                val = val.substring(0, val.length() - 1);
            }
            if (val.startsWith("\\")) {
                val = val.substring(1);
            }
            return val.trim();
        }
        return "";
    }
}