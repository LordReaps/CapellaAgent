package com.ai.bovenko.assistant;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.emf.ecore.EPackage;

/**
 * Отказоустойчивый адаптер модели Capella.
 * Работает исключительно через EMF Reflection (EObject), обеспечивая 
 * полную совместимость без жестких импортов.
 */
public class CapellaModelAdapter {

    /**
     * Динамически извлекает имя типа элемента.
     */
    public static String getElementType(EObject eObject) {
        if (eObject == null || eObject.eClass() == null) {
            return "-";
        }
        return eObject.eClass().getName();
    }

    /**
     * Находит структурное свойство "id" и возвращает его строковое значение.
     */
    public static String getElementId(EObject eObject) {
        return getFeatureValueDynamically(eObject, "id");
    }

    /**
     * Находит структурное свойство "name" и возвращает его строковое значение.
     */
    public static String getElementName(EObject eObject) {
        return getFeatureValueDynamically(eObject, "name");
    }

    /**
     * Находит структурное свойство "description" (содержит грязный RichText HTML).
     */
    public static String getRawDescription(EObject eObject) {
        return getFeatureValueDynamically(eObject, "description");
    }

    /**
     * Универсальный внутренний метод для рефлексивного чтения свойств EMF.
     */
    private static String getFeatureValueDynamically(EObject eObject, String featureName) {
        if (eObject == null || eObject.eClass() == null) {
            return "";
        }

        // Динамически запрашиваем метасвойство по его строковому имени
        EStructuralFeature feature = eObject.eClass().getEStructuralFeature(featureName);
        
        if (feature != null) {
            // Читаем значение свойства у конкретного объекта
            Object value = eObject.eGet(feature);
            if (value != null) {
                return value.toString();
            }
        }
        
        return "";
    }
    
    /**
     * Ищет EObject по его строковому ID, обходя всю модель вверх до корня, а затем вниз по всему дереву объектов.
     */
    public static EObject findElementById(EObject context, String targetId) {
        if (context == null || targetId == null || targetId.isEmpty()) {
            return null;
        }
        // Поднимаемся к самому корню модели (Project / SystemEngineering)
        EObject root = context;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }
        // Обходим дерево сверху вниз в поисках совпадения по ID
        if (targetId.equals(getElementId(root))) {
            return root;
        }
        org.eclipse.emf.common.util.TreeIterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            if (targetId.equals(getElementId(obj))) {
                return obj;
            }
        }
        return null;
    }

    /**
     * Автоматически находит первый попавшийся контейнер типа "OperationalActivityPkg"
     * или "OperationalAnalysis" в текущей модели для фолбэка, если ID не передан.
     */
    public static EObject findRootOperationalContainer(EObject context) {
        if (context == null) return null;
        EObject root = context;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }
        
        if (root.eClass().getName().contains("Operational")) {
            return root;
        }
        
        org.eclipse.emf.common.util.TreeIterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            String typeName = obj.eClass().getName();
            // Ищем пакет активностей или корень анализа
            if ("OperationalActivityPkg".equals(typeName) || "OperationalAnalysis".equals(typeName)) {
                return obj;
            }
        }
        return null;
    }
    
    
    
    /**
     * Транзакционное рефлексивное создание OperationalActivity внутри RecordingCommand.
     * Прямой вызов из внешних HTTP-потоков запрещен, вызывается строго в цепочке подтверждения UI.
     */
    public static String createOperationalActivity(String containerId, final String name, final String description) throws Exception {
        // Берем контекстный элемент из сессии, чтобы от него построить поиск корня модели
        EObject context = SessionManager.getInstance().getCurrentSelectedElement();
        final EObject container = findElementById(context, containerId);
        
        if (container == null) {
            throw new IllegalArgumentException("Целевой контейнер с ID " + containerId + " не найден в модели.");
        }

        // Находим транзакционный Editing Domain для работы с моделью в потоке Eclipse
        final TransactionalEditingDomain editingDomain = TransactionUtil.getEditingDomain(container);
        if (editingDomain == null) {
            throw new IllegalStateException("Не удалось получить TransactionalEditingDomain для контейнера. Модификация невозможна.");
        }

        // Массив из одного элемента для передачи сгенерированного ID из транзакционной лямбды наружу
        final String[] resultId = new String[1];
        final Exception[] throwables = new Exception[1];

        // Запускаем безопасную команду записи изменений
        editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain, "Create Operational Activity via AI Assistant") {
            @Override
            protected void doExecute() {
                try {
                    // 1. Динамически определяем EClass для новой OperationalActivity.
                    // Пытаемся найти его мета-описание (EClass) в том же EPackage, где находится контейнер, 
                    // либо через рефлексивный обход пакетов Capella.
                    EClass targetEClass = null;
                    
                    // Обходим зарегистрированные классификаторы пакета контейнера в поисках "OperationalActivity"
                    for (org.eclipse.emf.ecore.EClassifier classifier : container.eClass().getEPackage().getEClassifiers()) {
                        if (classifier instanceof EClass && "OperationalActivity".equals(classifier.getName())) {
                            targetEClass = (EClass) classifier;
                            break;
                        }
                    }

                    // Если в текущем пакете не нашли (например, контейнер — это базовый элемент), ищем в соседних пакетах модели
                 // Если в текущем пакете не нашли (например, контейнер — это базовый элемент), ищем в соседних пакетах модели
                    if (targetEClass == null) {
                        for (Object pkgUri : org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.keySet()) {
                            org.eclipse.emf.ecore.EPackage pkg = org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.getEPackage((String) pkgUri);
                            if (pkg != null && pkg.getName().contains("oa")) { // oa = Operational Analysis
                                for (org.eclipse.emf.ecore.EClassifier classifier : pkg.getEClassifiers()) {
                                    if (classifier instanceof EClass && "OperationalActivity".equals(classifier.getName())) {
                                        targetEClass = (EClass) classifier;
                                        break;
                                    }
                                }
                            }
                            if (targetEClass != null) break;
                        }
                    }

                    if (targetEClass == null) {
                        throw new ClassNotFoundException("Метакласс OperationalActivity не найден в зарегистрированных EMF пакетах Capella.");
                    }

                    // 2. Создаем экземпляр через рефлексивный EFactory пакета метамодели
                    EFactory factory = targetEClass.getEPackage().getEFactoryInstance();
                    EObject newActivity = factory.create(targetEClass);

                    // 3. Назначаем уникальный ID (используем стандартный генератор EMF Capella)
                    String generatedId = EcoreUtil.generateUUID();
                    EStructuralFeature idFeature = targetEClass.getEStructuralFeature("id");
                    if (idFeature != null) {
                        newActivity.eSet(idFeature, generatedId);
                    }
                    resultId[0] = generatedId;

                    // 4. Назначаем свойства name и description рефлексивно
                    EStructuralFeature nameFeature = targetEClass.getEStructuralFeature("name");
                    if (nameFeature != null && name != null) {
                        newActivity.eSet(nameFeature, name);
                    }

                    EStructuralFeature summaryFeature = targetEClass.getEStructuralFeature("summary");
                    if (summaryFeature != null && description != null) {
                        newActivity.eSet(summaryFeature, description);
                    }

                    EStructuralFeature descFeature = targetEClass.getEStructuralFeature("description");
                    if (descFeature != null && description != null) {
                        newActivity.eSet(descFeature, description);
                    }
                    
                    // 5. Ищем подходящую структурную фичу для вложения активности в контейнер
                    EObject realContainer = container;
                    
                    // Если контейнер — это корневой блок анализа, ищем Root Operational Activity
                    if ("OperationalAnalysis".equals(realContainer.eClass().getName())) {
                        System.out.println("[AI-Adapter] Контейнер — OperationalAnalysis. Ищем Root Operational Activity...");
                        org.eclipse.emf.common.util.TreeIterator<EObject> it = realContainer.eAllContents();
                        EObject rootActivity = null;
                        EObject activityPkg = null;
                        
                        while (it.hasNext()) {
                            EObject child = it.next();
                            String className = child.eClass().getName();
                            
                            if ("OperationalActivity".equals(className) && rootActivity == null) {
                                rootActivity = child; // Нашли нашу Root Operational Activity
                            }
                            if ("OperationalActivityPkg".equals(className) && activityPkg == null) {
                                activityPkg = child; // Фолбэк на папку
                            }
                        }
                        
                        // Меняем целевой контейнер на найденную корневую активность
                        if (rootActivity != null) {
                            realContainer = rootActivity;
                            System.out.println("[AI-Adapter] Целевой контейнер изменен на Root Activity: " + getElementName(realContainer));
                        } else if (activityPkg != null) {
                            realContainer = activityPkg;
                            System.out.println("[AI-Adapter] Root Activity не найдена, фолбэк на OperationalActivityPkg");
                        }
                    }

                    // Ищем фичу вложения бизнес-уровня (декомпозиция функций/активностей)
                    EStructuralFeature containmentFeature = null;
                    
                    // Приоритет 1: Проверяем прямые стандартные фичи декомпозиции Capella
                    String[] businessFeatures = {"ownedFunctions", "ownedOperationalActivities", "ownedActivities"};
                    for (String fName : businessFeatures) {
                        EStructuralFeature f = realContainer.eClass().getEStructuralFeature(fName);
                        if (f instanceof org.eclipse.emf.ecore.EReference && f.isMany()) {
                            if (((org.eclipse.emf.ecore.EReference) f).isContainment()) {
                                containmentFeature = f;
                                break;
                            }
                        }
                    }
                    
                    // Приоритет 2: Если стандартные не найдены, ищем любую контейнмент-коллекцию, кроме технической 'migrated'
                    if (containmentFeature == null) {
                        for (EStructuralFeature feature : realContainer.eClass().getEAllStructuralFeatures()) {
                            if (feature instanceof org.eclipse.emf.ecore.EReference && feature.isMany()) {
                                org.eclipse.emf.ecore.EReference reference = (org.eclipse.emf.ecore.EReference) feature;
                                if (reference.isContainment() && !reference.getName().contains("migrated")) {
                                    if (reference.getEType() instanceof EClass) {
                                        EClass featureEClass = (EClass) reference.getEType();
                                        if (featureEClass.isSuperTypeOf(targetEClass) || "OperationalActivity".equals(featureEClass.getName())) {
                                            containmentFeature = reference;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (containmentFeature != null && containmentFeature.isMany()) {
                        // Добавляем созданный EObject в список дочерних элементов правильного контейнера
                        @SuppressWarnings("unchecked")
                        java.util.List<EObject> list = (java.util.List<EObject>) realContainer.eGet(containmentFeature);
                        list.add(newActivity);
                        System.out.println("[AI-Adapter] Элемент успешно добавлен в контейнер типа: " + realContainer.eClass().getName());
                    } else {
                        throw new IllegalStateException("Не найдена коллекция ownedOperationalActivities в контейнере типа " + realContainer.eClass().getName());
                    }

                } catch (Exception e) {
                    throwables[0] = e;
                }
            }
        });

        // Если внутри транзакции произошло исключение, пробрасываем его выше
        if (throwables[0] != null) {
            throw throwables[0];
        }

        return resultId[0];
    }
    
    /**
     * Автоматически находит первый попавшийся контейнер типа "OperationalEntityPkg"
     * в текущей модели для фолбэка, если ID не передан.
     */
    public static EObject findRootOperationalEntityContainer(EObject context) {
        if (context == null) return null;
        EObject root = context;
        while (root.eContainer() != null) {
            root = root.eContainer();
        }
        
        org.eclipse.emf.common.util.TreeIterator<EObject> it = root.eAllContents();
        while (it.hasNext()) {
            EObject obj = it.next();
            String typeName = obj.eClass().getName();
            if ("OperationalEntityPkg".equals(typeName)) {
                return obj;
            }
        }
        return null;
    }
    
    /**
     * Транзакционное создание OperationalEntity (Операционной сущности / актора)
     */
    public static String createOperationalEntity(String containerId, final String name, final String description) throws Exception {
        EObject context = SessionManager.getInstance().getCurrentSelectedElement();
        final EObject container = findElementById(context, containerId);
        
        if (container == null) {
            throw new IllegalArgumentException("Целевой контейнер с ID " + containerId + " не найден в модели.");
        }

        final TransactionalEditingDomain editingDomain = TransactionUtil.getEditingDomain(container);
        if (editingDomain == null) {
            throw new IllegalStateException("Не удалось получить TransactionalEditingDomain для контейнера.");
        }

        final String[] resultId = new String[1];
        final Exception[] throwables = new Exception[1];

        editingDomain.getCommandStack().execute(new RecordingCommand(editingDomain, "Create Operational Entity via AI Assistant") {
            @Override
            protected void doExecute() {
                try {
                    // 1. Поиск метакласса OperationalEntity
                    EClass targetEClass = null;
                    for (org.eclipse.emf.ecore.EClassifier classifier : container.eClass().getEPackage().getEClassifiers()) {
                        if (classifier instanceof EClass && "Entity".equals(classifier.getName())) {
                            targetEClass = (EClass) classifier;
                            break;
                        }
                    }

                    if (targetEClass == null) {
                        for (Object pkgUri : org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.keySet()) {
                            org.eclipse.emf.ecore.EPackage pkg = org.eclipse.emf.ecore.EPackage.Registry.INSTANCE.getEPackage((String) pkgUri);
                            if (pkg != null && pkg.getName().contains("oa")) { 
                                for (org.eclipse.emf.ecore.EClassifier classifier : pkg.getEClassifiers()) {
                                    if (classifier instanceof EClass && "Entity".equals(classifier.getName())) {
                                        targetEClass = (EClass) classifier;
                                        break;
                                    }
                                }
                            }
                            if (targetEClass != null) break;
                        }
                    }

                    if (targetEClass == null) {
                        throw new ClassNotFoundException("Метакласс OperationalEntity не найден в пакетах EMF.");
                    }

                    // 2. Создание объекта
                    EFactory factory = targetEClass.getEPackage().getEFactoryInstance();
                    EObject newEntity = factory.create(targetEClass);

                    // 3. Генерация UUID
                    String generatedId = EcoreUtil.generateUUID();
                    EStructuralFeature idFeature = targetEClass.getEStructuralFeature("id");
                    if (idFeature != null) {
                        newEntity.eSet(idFeature, generatedId);
                    }
                    resultId[0] = generatedId;

                    // 4. Заполнение атрибутов
                    EStructuralFeature nameFeature = targetEClass.getEStructuralFeature("name");
                    if (nameFeature != null && name != null) {
                        newEntity.eSet(nameFeature, name);
                    }

                    EStructuralFeature summaryFeature = targetEClass.getEStructuralFeature("summary");
                    if (summaryFeature != null && description != null) {
                        newEntity.eSet(summaryFeature, description);
                    }

                    EStructuralFeature descFeature = targetEClass.getEStructuralFeature("description");
                    if (descFeature != null && description != null) {
                        newEntity.eSet(descFeature, description);
                    }
                    
                    EObject realContainer = container;
                    // Автоматическое перенаправление из корня в пакет сущностей
                    if ("OperationalAnalysis".equals(realContainer.eClass().getName())) {
                        org.eclipse.emf.common.util.TreeIterator<EObject> it = realContainer.eAllContents();
                        EObject entityPkg = null;
                        while (it.hasNext()) {
                            EObject child = it.next();
                            String className = child.eClass().getName();
                            if ("OperationalEntityPkg".equals(className)) {
                                entityPkg = child;
                                break;
                            }
                        }
                        if (entityPkg != null) {
                            realContainer = entityPkg;
                        }
                    }

                    // 5. Разрешение контейнмента
                    EStructuralFeature containmentFeature = null;
                    String[] businessFeatures = {"ownedOperationalEntities", "ownedEntities"};
                    for (String fName : businessFeatures) {
                        EStructuralFeature f = realContainer.eClass().getEStructuralFeature(fName);
                        if (f instanceof org.eclipse.emf.ecore.EReference && f.isMany()) {
                            if (((org.eclipse.emf.ecore.EReference) f).isContainment()) {
                                containmentFeature = f;
                                break;
                            }
                        }
                    }
                    
                    if (containmentFeature == null) {
                        for (EStructuralFeature feature : realContainer.eClass().getEAllStructuralFeatures()) {
                            if (feature instanceof org.eclipse.emf.ecore.EReference && feature.isMany()) {
                                org.eclipse.emf.ecore.EReference reference = (org.eclipse.emf.ecore.EReference) feature;
                                if (reference.isContainment() && !reference.getName().contains("migrated")) {
                                    if (reference.getEType() instanceof EClass) {
                                        EClass featureEClass = (EClass) reference.getEType();
                                        if (featureEClass.isSuperTypeOf(targetEClass) || "OperationalEntity".equals(featureEClass.getName())) {
                                            containmentFeature = reference;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (containmentFeature != null && containmentFeature.isMany()) {
                        @SuppressWarnings("unchecked")
                        java.util.List<EObject> list = (java.util.List<EObject>) realContainer.eGet(containmentFeature);
                        list.add(newEntity);
                        System.out.println("[AI-Adapter] Сущность (Entity) успешно добавлена в контейнер типа: " + realContainer.eClass().getName());
                    } else {
                        throw new IllegalStateException("Не найдена коллекция для размещения сущностей в контейнере типа " + realContainer.eClass().getName());
                    }

                } catch (Exception e) {
                    throwables[0] = e;
                }
            }
        });

        if (throwables[0] != null) {
            throw throwables[0];
        }
        return resultId[0];
    }
    
    
}