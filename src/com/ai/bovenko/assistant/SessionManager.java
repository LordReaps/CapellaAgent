package com.ai.bovenko.assistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.emf.ecore.EObject;

/**
 * Синглтон для управления состоянием сессии чата и кэширования контекста модели в памяти.
 */
public class SessionManager {
    private static volatile SessionManager instance;
    
    private final List<ChatMessage> chatHistory = Collections.synchronizedList(new ArrayList<>());
    private volatile EObject currentSelectedElement = null;

    private SessionManager() {
        // Инициализируем базовый системный промпт
        chatHistory.add(new ChatMessage("system", 
            "Вы — продвинутый ИИ-ассистент, интегрированный в среду Thales Capella. " +
            "Вам доступны инструменты чтения операционного уровня модели (Operational Analysis). " +
            "Отвечайте емко и технически грамотно. Если вам не хватает данных, используйте доступные инструменты."));
    }
    
    // Хранилище активных черновиков изменений в текущей сессии
    private final List<ChangeSet> changeSets = Collections.synchronizedList(new ArrayList<>());

    public void addChangeSet(ChangeSet changeSet) {
        this.changeSets.add(changeSet);
    }

    public ChangeSet getChangeSetById(String changeSetId) {
        synchronized (changeSets) {
            for (ChangeSet cs : changeSets) {
                if (cs.getId().equals(changeSetId)) {
                    return cs;
                }
            }
        }
        return null;
    }
    
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    public List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    public void addMessage(ChatMessage message) {
        chatHistory.add(message);
    }

    public void clearHistory() {
        chatHistory.clear();
    }

    public EObject getCurrentSelectedElement() {
        return currentSelectedElement;
    }

    public void setCurrentSelectedElement(EObject element) {
        this.currentSelectedElement = element;
    }
}