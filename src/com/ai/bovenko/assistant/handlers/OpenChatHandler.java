package com.ai.bovenko.assistant.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Обработчик команды для открытия чат-панели AI Assistant.
 * Располагается в пакете com.ai.bovenko.assistant.handlers в соответствии с деревом проекта.
 */
public class OpenChatHandler extends AbstractHandler {

    // Обязательно замените этот плейсхолдер на реальный ID вашего ChatView из plugin.xml
    private static final String CHAT_VIEW_ID = "com.ai.bovenko.assistant.ChatView";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // Безопасно извлекаем текущее активное окно Eclipse Workbench
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        
        if (window != null) {
            // Получаем активную страницу (рабочую область) текущего окна
            IWorkbenchPage page = window.getActivePage();
            
            if (page != null) {
                try {
                    /*
                     * Показываем View чата. 
                     * Если оно не было создано — инициализирует, 
                     * если было скрыто за другими вкладками — выводит на передний план (Focus).
                     */
                    page.showView(CHAT_VIEW_ID);
                } catch (PartInitException e) {
                    throw new ExecutionException("Критическая ошибка при открытии панели ChatView [ID: " 
                            + CHAT_VIEW_ID + "]", e);
                }
            } else {
                System.err.println("[AI Assistant] Не удалось получить активную страницу WorkbenchPage.");
            }
        } else {
            System.err.println("[AI Assistant] Не удалось получить активное окно WorkbenchWindow.");
        }
        
        // В механизме Eclipse Commands обработчики обязаны возвращать null
        return null;
    }
}