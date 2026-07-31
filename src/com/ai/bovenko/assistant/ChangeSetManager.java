package com.ai.bovenko.assistant;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import java.util.UUID;

/**
 * Слой валидации и управления жизненным циклом черновиков.
 */
public class ChangeSetManager {

    /**
     * Создает и валидирует черновик для создания OperationalActivity.
     */
    public static ChangeSet proposeCreateOperationalActivity(String name, String description, String targetContainerId) {
        System.out.println("[DEBUG-MANAGER] Получены данные: name='" + name + "', description='" + description + "'");
        String changeSetId = "cs_" + UUID.randomUUID().toString().substring(0, 8);
        ChangeSet cs = new ChangeSet(changeSetId, "create_operational_activity");
        
        cs.getPayload().put("name", name != null ? name : "");
        cs.getPayload().put("description", description != null ? description : "");

        EObject contextElement = SessionManager.getInstance().getCurrentSelectedElement();
        EObject container = null;

        // Безопасная проверка переданного ID
        boolean isIdValid = targetContainerId != null 
                         && !targetContainerId.trim().isEmpty() 
                         && !targetContainerId.contains("ERR_KEY_NOT_FOUND");

        if (isIdValid) {
            container = CapellaModelAdapter.findElementById(contextElement, targetContainerId);
        }

        // Если ID невалиден ИЛИ элемент по ID не был найден в модели
        if (container == null) {
            System.out.println("[DEBUG-MANAGER] ID пуст или не найден. Откат на корневой операционный контейнер активностей.");
            container = CapellaModelAdapter.findRootOperationalContainer(contextElement);
        }

        // 1. Проверка CONTAINER_EXISTS
        boolean containerExists = (container != null);
        cs.getChecks().put("CONTAINER_EXISTS", containerExists);

        if (containerExists) {
            String finalContainerId = CapellaModelAdapter.getElementId(container);
            cs.getPayload().put("target_container_id", finalContainerId);
            cs.getPayload().put("target_container_name", CapellaModelAdapter.getElementName(container));

            // 2. Проверка NO_DUPLICATE_NAME
            boolean hasDuplicate = false;
            TreeIterator<EObject> children = container.eAllContents();
            while (children.hasNext()) {
                EObject child = children.next();
                if ("OperationalActivity".equals(child.eClass().getName())) {
                    if (name != null && name.trim().equalsIgnoreCase(CapellaModelAdapter.getElementName(child).trim())) {
                        hasDuplicate = true;
                        break;
                    }
                }
            }
            cs.getChecks().put("NO_DUPLICATE_NAME", !hasDuplicate);

            // 3. Проверка TYPE_ALLOWED_IN_CONTAINER
            String containerType = container.eClass().getName();
            boolean typeAllowed = containerType.equals("OperationalActivityPkg") 
                              || containerType.equals("OperationalActivity") 
                              || containerType.equals("OperationalAnalysis");
            cs.getChecks().put("TYPE_ALLOWED_IN_CONTAINER", typeAllowed);
            
            if (hasDuplicate || !typeAllowed) {
                cs.setStatus("failed");
            }
        } else {
            cs.getPayload().put("target_container_id", "UNKNOWN");
            cs.getChecks().put("NO_DUPLICATE_NAME", false);
            cs.getChecks().put("TYPE_ALLOWED_IN_CONTAINER", false);
            cs.setStatus("failed");
        }

        SessionManager.getInstance().addChangeSet(cs);
        AuditLogger.log("CHANGE_SET_PROPOSED", cs.toJson());
        
        return cs;
    }
    
    /**
     * Создает и валидирует черновик для создания OperationalEntity.
     */
    public static ChangeSet proposeCreateOperationalEntity(String name, String description, String targetContainerId) {
        System.out.println("[DEBUG-MANAGER] Получены данные для сущности: name='" + name + "', description='" + description + "'");
        String changeSetId = "cs_" + UUID.randomUUID().toString().substring(0, 8);
        ChangeSet cs = new ChangeSet(changeSetId, "create_operational_entity");
        
        cs.getPayload().put("name", name != null ? name : "");
        cs.getPayload().put("description", description != null ? description : "");

        EObject contextElement = SessionManager.getInstance().getCurrentSelectedElement();
        EObject container = null;

        // Безопасная проверка переданного ID
        boolean isIdValid = targetContainerId != null 
                         && !targetContainerId.trim().isEmpty() 
                         && !targetContainerId.contains("ERR_KEY_NOT_FOUND");

        if (isIdValid) {
            container = CapellaModelAdapter.findElementById(contextElement, targetContainerId);
        } 
        
        // ДВОЙНОЙ ОТКАТ: Если ID невалиден ИЛИ элемент по ID не найден, ищем пакет сущностей
        if (container == null) {
            System.out.println("[DEBUG-MANAGER] ID пуст или не найден. Откат на автопоиск OperationalEntityPkg.");
            container = findDefaultContainer(contextElement, "EntityPkg");
        }

        if (container != null) {
            cs.getPayload().put("target_container_id", CapellaModelAdapter.getElementId(container));
            cs.getPayload().put("target_container_name", CapellaModelAdapter.getElementName(container));
            
            // 1. Проверяем дубликаты имен среди сущностей и акторов
            boolean hasDuplicate = false;
            for (EObject child : container.eContents()) {
                String childType = child.eClass().getName();
                if ("OperationalEntity".equals(childType) || "OperationalActor".equals(childType)) {
                    if (name != null && name.trim().equalsIgnoreCase(CapellaModelAdapter.getElementName(child).trim())) {
                        hasDuplicate = true;
                        break;
                    }
                }
            }
            cs.getChecks().put("NO_DUPLICATE_NAME", !hasDuplicate);

            // 2. Проверяем валидность контейнера
            String containerType = container.eClass().getName();
            boolean typeAllowed = containerType.equals("EntityPkg") 
                              || containerType.equals("Entity") 
                              || containerType.equals("OperationalAnalysis");
            cs.getChecks().put("TYPE_ALLOWED_IN_CONTAINER", typeAllowed);
            
            if (hasDuplicate || !typeAllowed) {
                cs.setStatus("failed");
            }
        } else {
            cs.getPayload().put("target_container_id", "UNKNOWN");
            cs.getChecks().put("NO_DUPLICATE_NAME", false);
            cs.getChecks().put("TYPE_ALLOWED_IN_CONTAINER", false);
            cs.setStatus("failed");
        }

        SessionManager.getInstance().addChangeSet(cs);
        AuditLogger.log("CHANGE_SET_PROPOSED", cs.toJson());
        
        return cs;
    }

    private static EObject findDefaultContainer(EObject context, String targetType) {
        if (context == null) return null;
        EObject root = context;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }
        TreeIterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (targetType.equals(obj.eClass().getName())) {
                return obj;
            }
        }
        return null;
    }
}