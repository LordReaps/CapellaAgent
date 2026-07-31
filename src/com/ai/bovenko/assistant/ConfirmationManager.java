package com.ai.bovenko.assistant;

import org.eclipse.swt.widgets.Display;

/**
 * Менеджер подтверждений черновиков изменений и интеграции с UI.
 */
public class ConfirmationManager {

    private static volatile ConfirmationManager instance;
    private ChatView chatView;
    private ChangeSet currentChangeSet;

    private ConfirmationManager() {}

    public static ConfirmationManager getInstance() {
        if (instance == null) {
            synchronized (ConfirmationManager.class) {
                if (instance == null) {
                    instance = new ConfirmationManager();
                }
            }
        }
        return instance;
    }

    public void registerChatView(ChatView view) {
        this.chatView = view;
    }

    /**
     * Форматирует черновик в человекочитаемый текст и выводит его в Preview Area в UI-потоке.
     */
    public void showPreview(final ChangeSet cs) {
        this.currentChangeSet = cs;
        
        // Отладочный вывод в консоль разработки Eclipse (Console View)
        System.out.println("[AI-Assistant DEBUG] showPreview вызван для черновика ID: " + cs.getId());
        System.out.println("[AI-Assistant DEBUG] Статус chatView: " + (chatView != null ? "ЗАРЕГИСТРИРОВАН" : "NULL!"));

        final StringBuilder sb = new StringBuilder();
        sb.append("=== ПРЕДПРОСМОТР ИЗМЕНЕНИЙ МОДЕЛИ ===\n");
        sb.append("ID Черновика: ").append(cs.getId()).append("\n");
        sb.append("Операция: ").append(cs.getOperation()).append("\n");
        sb.append("Элемент: ").append(cs.getPayload().get("name")).append("\n");
        sb.append("Описание: ").append(cs.getPayload().get("description")).append("\n");
        sb.append("Целевой контейнер: ").append(cs.getPayload().get("target_container_name"))
          .append(" (ID: ").append(cs.getPayload().get("target_container_id")).append(")\n");
        sb.append("--- Результаты автоматических проверок ---\n");
        
        for (java.util.Map.Entry<String, Boolean> check : cs.getChecks().entrySet()) {
            sb.append("  [")
              .append(check.getValue() ? "OK" : "ОШИБКА")
              .append("] ")
              .append(check.getKey()).append("\n");
        }
        sb.append("=====================================");

        // Используем жесткий глобальный UI-диспетчер Eclipse вместо Display.getDefault()
        try {
            org.eclipse.ui.PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    System.out.println("[AI-Assistant DEBUG] Внутри UI-потока. chatView существует? " + (chatView != null));
                    if (chatView != null && !chatView.isPreviewDisposed()) {
                        chatView.setPreviewText(sb.toString());
                        boolean allChecksPassed = !cs.getStatus().equals("failed");
                        chatView.setButtonsEnabled(allChecksPassed, true);
                        System.out.println("[AI-Assistant DEBUG] Текст успешно отправлен в SWT-виджеты.");
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[AI-Assistant ERROR] Ошибка планирования задачи в UI-поток: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Логика нажатия физической кнопки "Применить" в UI.
     */
    public void handleApplyAction() {
        if (currentChangeSet == null) return;
        
        // Изменяем статус на confirmed
        currentChangeSet.setStatus("confirmed");
        AuditLogger.log("UI_CONFIRMED_CHANGE_SET", currentChangeSet.toJson());
        
        final String csId = currentChangeSet.getId();
        
        // Программно запускаем выполнение цепочки apply_confirmed_change_set в фоновом режиме
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Вызываем инструмент через наш реестр
                String jsonArgs = "{\"change_set_id\":\"" + csId + "\"}";
                final String resultJson = ToolRegistry.getInstance().executeTool("capella.apply_confirmed_change_set", jsonArgs);
                
                // Выводим результат в чат
                org.eclipse.ui.PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        if (chatView != null) {
                            if (resultJson.contains("\"error\"")) {
                                chatView.appendMessage("[Система]: Ошибка применения черновика: " + resultJson);
                            } else {
                                chatView.appendMessage("[Система]: Элемент успешно создан и транзакционно записан в модель EMF!");
                            }
                            // Очищаем и блокируем интерфейс управления черновиком обратно
                            chatView.clearPreview();
                            chatView.setButtonsEnabled(false, false);
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * Логика нажатия физической кнопки "Отменить" в UI.
     */
    public void handleCancelAction() {
        if (currentChangeSet == null) return;
        
        currentChangeSet.setStatus("rejected");
        AuditLogger.log("UI_REJECTED_CHANGE_SET", currentChangeSet.toJson());
        
        if (chatView != null) {
            chatView.appendMessage("[Система]: Изменения отклонены пользователем.");
            chatView.clearPreview();
            chatView.setButtonsEnabled(false, false);
        }
        currentChangeSet = null;
    }
}