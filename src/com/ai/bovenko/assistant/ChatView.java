package com.ai.bovenko.assistant;

import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

/**
 * Реализация панели ИИ-ассистента.
 * Интегрирована с глобальной службой выделения Eclipse RCP для перехвата элементов модели.
 */
public class ChatView extends ViewPart implements ISelectionListener {

    // Элементы верхней панели (Контекст элемента)
    private Text idText;
    private Text typeText;
    private Text nameText;

    // Элементы центральной и нижней панелей (Чат)
    private Text chatHistoryText;
    private Text inputField;
    private Button sendButton;

    // Элементы управления черновиками Шага 3
    private Text previewText;
    private Button applyButton;
    private Button cancelButton;
    
    public ChatView() {
        super();
    }

    @Override
    public void createPartControl(Composite parent) {
        // Отрисовка UI
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginWidth = 8;
        mainLayout.marginHeight = 8;
        mainLayout.verticalSpacing = 8;
        parent.setLayout(mainLayout);

        // Блок контекста
        Group contextGroup = new Group(parent, SWT.NONE);
        contextGroup.setText(" Контекст выделенного элемента ");
        contextGroup.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        contextGroup.setLayout(new GridLayout(2, false));

        new Label(contextGroup, SWT.NONE).setText("ID:");
        idText = new Text(contextGroup, SWT.BORDER | SWT.READ_ONLY);
        idText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        idText.setText("-");

        new Label(contextGroup, SWT.NONE).setText("Тип:");
        typeText = new Text(contextGroup, SWT.BORDER | SWT.READ_ONLY);
        typeText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        typeText.setText("-");

        new Label(contextGroup, SWT.NONE).setText("Имя:");
        nameText = new Text(contextGroup, SWT.BORDER | SWT.READ_ONLY);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        nameText.setText("-");

        // История чата
        new Label(parent, SWT.NONE).setText("История сообщений:");
        chatHistoryText = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.READ_ONLY | SWT.WRAP | SWT.BORDER);

        // ИСПРАВЛЕНИЕ: Запрещаем истории занимать всё лишнее место (false) и задаем фиксированную высоту
        GridData gdHistory = new GridData(SWT.FILL, SWT.FILL, true, false);
        gdHistory.heightHint = 120; // Высота истории в пикселях (подстройте под себя)
        chatHistoryText.setLayoutData(gdHistory);
        chatHistoryText.setText("[Система]: Панель активирована. Служба отслеживания модели запущена...\n");
        
        
        // =====================================================================
        // ГРУППА ПРЕДПРОСМОТРА ИЗМЕНЕНИЙ
        // =====================================================================
        
        System.out.println("[AI-TRACE 1] ChatView.createPartControl() начал работу.");
        Group previewGroup = new Group(parent, SWT.NONE);
        previewGroup.setText("Управление изменениями (Controlled Write)");
        
        // Так как у родительского layout всего 1 колонка, span по горизонтали ставим в 1
        previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewGroup.setLayout(new GridLayout(2, false));

        previewText = new Text(previewGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        previewText.setEditable(false);

        // Разрешаем самому текстовому полю растягиваться внутри группы (true)
        GridData gdPreview = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        gdPreview.heightHint = 250; // Увеличиваем минимальную/начальную высоту черновика
        previewText.setLayoutData(gdPreview);
        previewText.setText("Нет активных предложений для изменения модели.");
        
        
        applyButton = new Button(previewGroup, SWT.PUSH);
        applyButton.setText("Применить изменения");
        applyButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        applyButton.setEnabled(false);

        cancelButton = new Button(previewGroup, SWT.PUSH);
        cancelButton.setText("Отменить");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancelButton.setEnabled(false);

        // Вешаем SWT-слушатели событий нажатия кнопок на ConfirmationManager
        applyButton.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                ConfirmationManager.getInstance().handleApplyAction();
            }
        });

        cancelButton.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                ConfirmationManager.getInstance().handleCancelAction();
            }
        });
        
        System.out.println("[AI-TRACE 2] SWT-виджеты черновиков успешно созданы. Ссылка на previewText: " + previewText);

        // Регистрируем эту панель в менеджере подтверждений
        ConfirmationManager.getInstance().registerChatView(this);
        System.out.println("[AI-TRACE 3] Вызван registerChatView(this). Хэш-код ConfirmationManager: " + ConfirmationManager.getInstance().hashCode());
        
        // Панель ввода
        Composite bottomComposite = new Composite(parent, SWT.NONE);
        bottomComposite.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false));
        bottomComposite.setLayout(new GridLayout(2, false));

        inputField = new Text(bottomComposite, SWT.BORDER | SWT.SINGLE);
        inputField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputField.setMessage("Введите ваш запрос к ИИ...");

        sendButton = new Button(bottomComposite, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        

     // Обработчик нажатия Enter в поле ввода
        inputField.addListener(SWT.DefaultSelection, event -> {
            String userText = inputField.getText();
            if (!userText.trim().isEmpty()) {
                appendMessage("Вы: " + userText);
                inputField.setText(""); // Очищаем поле ввода
                
                // Передаем текст оркестратору диалога
                DialogController.processUserMessage(userText, ChatView.this);
            }
        });

        // Обработчик клика по кнопке "Send"
        sendButton.addListener(SWT.Selection, event -> {
            String userText = inputField.getText();
            if (!userText.trim().isEmpty()) {
                appendMessage("Вы: " + userText);
                inputField.setText(""); // Очищаем поле ввода
                
                // Передаем текст оркестратору диалога
                DialogController.processUserMessage(userText, ChatView.this);
            }
        });
        
        // Регистрируем слушатель выделения
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);

        // Сразу записываем текущее выделение в память при старте
        ISelection initialSelection = getSite().getWorkbenchWindow().getSelectionService().getSelection();
        if (initialSelection != null) {
            selectionChanged(null, initialSelection);
        }
        
        
        // =====================================================================
        // РЕГИСТРАЦИЯ СЛУШАТЕЛЯ
        // =====================================================================
        getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(this);
     
        // Автоматический запуск локального сервера при открытии панели чата ассистента
        LocalToolEndpoint.startServer();
    }

    /**
     * Метод срабатывает автоматически при любом клике в интерфейсе Capella.
     */
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection ss = (IStructuredSelection) selection;
            Object first = ss.getFirstElement();
            
            if (first instanceof EObject) {
                EObject element = (EObject) first;
                
                // 1. Твой существующий код обновления текстовых полей в UI панели
                if (idText != null && !idText.isDisposed()) {
                    idText.setText(CapellaModelAdapter.getElementId(element));
                }
                if (nameText != null && !nameText.isDisposed()) {
                    nameText.setText(CapellaModelAdapter.getElementName(element));
                }
                if (typeText != null && !typeText.isDisposed()) {
                    typeText.setText(CapellaModelAdapter.getElementType(element));
                }
                
                // 2. ИСПРАВЛЕНИЕ: Обязательно сохраняем элемент в кэш сессии!
                SessionManager.getInstance().setCurrentSelectedElement(element);
            }
        }
    }

    
    /**
     * Безопасное извлечение EObject, сбор метаданных и их санитария
     */
    private void processSelection(Object element) {
        // Заворачиваем весь процесс в try-catch для обеспечения отказоустойчивости панели
        try {
            EObject eObject = null;

            if (element instanceof EObject) {
                eObject = (EObject) element;
            } else {
                eObject = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(element, EObject.class);
            }

            if (eObject != null) {
                String type = CapellaModelAdapter.getElementType(eObject);
                String id = CapellaModelAdapter.getElementId(eObject);
                String name = CapellaModelAdapter.getElementName(eObject);
                String rawDescription = CapellaModelAdapter.getRawDescription(eObject);

                // Применяем модуль санитарии текста к описанию
                String cleanDescription = RichTextSanitizer.sanitize(rawDescription);

                // Обновляем поля UI
                idText.setText(id.isEmpty() ? "[Нет ID]" : id);
                typeText.setText(type.isEmpty() ? "[Нет Типа]" : type);
                nameText.setText(name.isEmpty() ? "[Нет Имени]" : name);

                // Выводим в историю чата аккуратный очищенный текст
                if (!cleanDescription.isEmpty()) {
                    chatHistoryText.append("[Контекст]: Описание элемента:\n" + cleanDescription + "\n");
                } else {
                    chatHistoryText.append("[Контекст]: Выбран " + type + " '" + name + "' (Описание отсутствует).\n");
                }
            }
        } catch (Exception e) {
            // Ошибки рантайма логируются через org.eclipse.core.runtime.ILog
            org.eclipse.core.runtime.ILog log = org.eclipse.core.runtime.Platform.getLog(ChatView.class);
            log.log(new org.eclipse.core.runtime.Status(
                org.eclipse.core.runtime.IStatus.ERROR, 
                "com.ai.bovenko.assistant", 
                "Ошибка при обработке контекста выделенного элемента модели", 
                e
            ));
        }
    }

    private void handleSendAction() {
        String userText = inputField.getText().trim();
        if (!userText.isEmpty()) {
            chatHistoryText.append("[Вы]: " + userText + "\n");
            inputField.setText("");
            chatHistoryText.append("[ИИ]: Работаю с контекстом текущего элемента...\n");
        }
    }

    @Override
    public void setFocus() {
        if (inputField != null && !inputField.isDisposed()) {
            inputField.setFocus();
        }
    }

    @Override
    public void dispose() {
        // =====================================================================
        // УДАЛЕНИЕ СЛУШАТЕЛЯ И ОЧИСТКА (Защита от утечек памяти)
        // =====================================================================
        if (getSite() != null && getSite().getWorkbenchWindow() != null && getSite().getWorkbenchWindow().getSelectionService() != null) {
            getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(this);
        }
        
        idText = null;
        typeText = null;
        nameText = null;
        chatHistoryText = null;
        inputField = null;
        sendButton = null;
        super.dispose();
        
        LocalToolEndpoint.stopServer();
    }
    
    /**
     * Добавляет новую строку текста в лог чата.
     * Безопасно работает с потоками благодаря проверке UI-диспетчера.
     */
    public void appendMessage(final String message) {
        if (chatHistoryText != null && !chatHistoryText.isDisposed()) {
        	chatHistoryText.append(message + "\n");
        }
    }
    
    
    
    
    
    
    public boolean isPreviewDisposed() {
        return previewText == null || previewText.isDisposed();
    }

    public void setPreviewText(String text) {
        if (previewText != null && !previewText.isDisposed()) {
            previewText.setText(text);
        }
    }

    public void setButtonsEnabled(boolean applyEnabled, boolean cancelEnabled) {
        if (applyButton != null && !applyButton.isDisposed()) {
            applyButton.setEnabled(applyEnabled);
        }
        if (cancelButton != null && !cancelButton.isDisposed()) {
            cancelButton.setEnabled(cancelEnabled);
        }
    }

    public void clearPreview() {
        if (previewText != null && !previewText.isDisposed()) {
            previewText.setText("Нет активных предложений для изменения модели.");
        }
    }
    
}