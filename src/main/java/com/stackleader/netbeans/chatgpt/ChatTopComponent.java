package com.stackleader.netbeans.chatgpt;

import com.redbus.store.ChatSimilarityResult;
import com.redbus.store.MapDBVectorStore;
import static com.stackleader.netbeans.chatgpt.JavaFileDependencyScanner.scanJavaFiles;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import io.reactivex.functions.Consumer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import javax.swing.text.JTextComponent;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.json.JSONObject;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.cookies.EditorCookie;
import org.openide.explorer.ExplorerManager;
import org.openide.util.Utilities;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author dcnorris
 */
@TopComponent.Description(
        preferredID = ChatTopComponent.PREFERRED_ID,
        iconBase = "icons/chat.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "output", openAtStartup = true, position = 500)
@ActionID(category = "Window", id = "com.stackleader.netbeans.chatgpt.ChatTopComponent")
@ActionReference(path = "Menu/Window", position = 333)
@TopComponent.OpenActionRegistration(
        displayName = "#Chat_TopComponent_Title",
        preferredID = ChatTopComponent.PREFERRED_ID)
public class ChatTopComponent extends TopComponent {

    System.Logger LOG = System.getLogger("ChatTopComponent");

    public static final String PREFERRED_ID = "ChatTopComponent";
    private static final int BOTTOM_PANEL_HEIGHT = 140;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 25;
    private static final int ACTIONS_PANEL_WIDTH = 20;
    public static final String QUICK_COPY_TEXT = "(Quick Copy: Ctrl+Click here)";

    private RSyntaxTextArea outputTextArea;
    private final static List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final static Parser parser = Parser.builder().build();
    private final static HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    private Gutter gutter;
    private boolean shouldAnnotateCodeBlock = true;
    private JComboBox<String> modelSelection;
    private OpenAiService service;
    MapDBVectorStore store;
    private static final RequestProcessor RP = new RequestProcessor(ChatTopComponent.class);

    public ChatTopComponent() {
        setName(NbBundle.getMessage(ChatTopComponent.class, "Chat_TopComponent_Title")); // NOI18N
        setLayout(new BorderLayout());
        Configuration config = Configuration.getInstance();
        Preferences prefs = config.getPreferences();
        String token = prefs.get(Configuration.OPENAI_TOKEN_KEY, null);
        if (token == null || token.isBlank()) {
            token = promptForToken();
            if (token != null && !token.isBlank()) {
                prefs.put(Configuration.OPENAI_TOKEN_KEY, token);
            } else {
                add(createMissingTokenBanner(), BorderLayout.CENTER);
                return;
            }
        }
        addComponentsToFrame();
        service = new OpenAiService(token);
        
        //Function calls
        IDEHelper.registerFunction(new FilesList());
        
        //Initiate Vector Store
        store = new MapDBVectorStore("MKVECOLLAMA.db");
        
        String currentDirectory = System.getProperty("user.dir");
        appendText("Plugin working directory: "+currentDirectory+"\n");
        appendText("Vector store : MKVECOLLAMA.db \n");
        
        
         
        //Get Project Info
        // Get the currently opened projects
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        appendText("Open Projects that I can access:\n");
        for(Project project:openProjects){
           // JOptionPane.showConfirmDialog(outputTextArea,"Open Projects: "+ OpenProjects.getDefault().getOpenProjects()[0].getProjectDirectory().getPath());//Testing here
        appendText(project.getProjectDirectory().getName()+":"+project.getProjectDirectory().getPath()+"\n");
        }

    }
    
    private String promptForToken() {
        NotifyDescriptor.InputLine inputLine = new NotifyDescriptor.InputLine(
                "Enter OpenAI API Token:",
                "API Token Required"
        );
        if (DialogDisplayer.getDefault().notify(inputLine) == NotifyDescriptor.OK_OPTION) {
            return inputLine.getInputText();
        }
        return null;
    }

    @Override
    protected void componentOpened() {
        super.componentOpened();
        setName(NbBundle.getMessage(ChatTopComponent.class,
                "Chat_TopComponent_Title")); // NOI18N
    }

    private void addComponentsToFrame() {
        add(createActionsPanel(), BorderLayout.WEST);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createOutputScrollPane(), BorderLayout.CENTER);
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createMissingTokenBanner() {
        JPanel missingTokenBanner = new JPanel();
        JLabel messageLabel = new JLabel("<html><center>OPENAI_TOKEN environment variable is not defined.<br>Please restart NetBeans after defining this variable.</center></html>");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        missingTokenBanner.setLayout(new BorderLayout());
        missingTokenBanner.add(messageLabel, BorderLayout.CENTER);

        return missingTokenBanner;
    }

    private JPanel createActionsPanel() {
        JPanel actionsPanel = new JPanel();
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setPreferredSize(new Dimension(ACTIONS_PANEL_WIDTH, 500));
        JButton optionsButton = new JButton(ImageUtilities.loadImageIcon("icons/options.png", true));
        optionsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleOptionsButtonClick();
            }
        });
        actionsPanel.add(optionsButton);
        return actionsPanel;
    }

    private void handleOptionsButtonClick() {
        Configuration config = Configuration.getInstance();
        Preferences prefs = config.getPreferences();
        String currentToken = prefs.get(Configuration.OPENAI_TOKEN_KEY, "");

        NotifyDescriptor.InputLine inputLine = new NotifyDescriptor.InputLine(
                "OpenAI API Token:",
                "Edit API Token"
        );
        inputLine.setInputText(currentToken);

        if (DialogDisplayer.getDefault().notify(inputLine) == NotifyDescriptor.OK_OPTION) {
            String newToken = inputLine.getInputText();
            prefs.put(Configuration.OPENAI_TOKEN_KEY, newToken);
            service = new OpenAiService(newToken);
        }
    }

    private JPanel createOutputScrollPane() {
        JPanel cp = new JPanel(new BorderLayout());
        outputTextArea = createOutputTextArea();
        RTextScrollPane outputScrollPane = new RTextScrollPane(outputTextArea);
        outputScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outputScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        outputScrollPane.setBorder(new TitledBorder("Output"));
        gutter = outputScrollPane.getGutter();
        cp.add(outputScrollPane);
        return cp;
    }

    private RSyntaxTextArea createOutputTextArea() {
        RSyntaxTextArea outputTextArea = new RSyntaxTextArea();
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        Color bg = defaults.getColor("EditorPane.background");
        Color fg = defaults.getColor("EditorPane.foreground");
        Color menuBackground = defaults.getColor("Menu.background");
        Color selectedTextColor = new Color(100, 149, 237);
        outputTextArea.setForeground(fg);
        outputTextArea.setBackground(menuBackground);
        outputTextArea.setSelectedTextColor(selectedTextColor);
        outputTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        outputTextArea.setEditable(false);
        outputTextArea.setLineWrap(true);
        outputTextArea.setWrapStyleWord(true);
        outputTextArea.setLinkGenerator(new CodeBlockLinkGenerator());
        return outputTextArea;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createButtonPanel(), BorderLayout.WEST);
        bottomPanel.add(createInputScrollPane(), BorderLayout.CENTER);
        bottomPanel.setPreferredSize(new Dimension(0, BOTTOM_PANEL_HEIGHT));
        return bottomPanel;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 5, 5, 5);
        String[] models =OllamaHelpers.mergeArrays(OllamaHelpers.fetchModelNames(),new String[]{"gpt-3.5-turbo-1106","gpt-3.5-turbo-16k-0613", "gpt-4o"});
        modelSelection = new JComboBox<>(models);
        modelSelection.setSelectedItem(models[0]);
        buttonPanel.add(modelSelection, gbc);
        gbc.gridy++;
        JButton resetButton = createResetButton();
        buttonPanel.add(resetButton, gbc);
//        gbc.gridy++;
//        JButton submitButton = createSubmitButton();
//        buttonPanel.add(submitButton, gbc);
        gbc.gridy++;
        buttonPanel.add(createChatHistoryButton(),gbc);//To load history
        //Index codebase
        gbc.gridy++;
        buttonPanel.add(createIndexButton(),gbc);//To load history
        return buttonPanel;
    }

    private JButton createResetButton() {
        final JButton resetButton = createButton("Reset");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reset();
            }
        });
        return resetButton;
    }

    private JButton createSubmitButton() {
        final JButton submitButton = createButton("Submit");
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        return submitButton;
    }
    
    
     
private JButton createIndexButton() {
    final JButton submitButton = createButton("Index Project");

    submitButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            Project selectedProject = IDEHelper.selectProjectFromOpenProjects();
            if (selectedProject == null) {
                System.out.println("No project selected.");
                return;
            }

            System.out.println("Selected project: " + selectedProject.getProjectDirectory().getName());

            // Start a background task using RequestProcessor
            RequestProcessor.getDefault().post(new Runnable() {
                @Override
                public void run() {
                    // Initialize ProgressHandle
                    ProgressHandle progressHandle = ProgressHandle.createHandle("Indexing Project: " + selectedProject.getProjectDirectory().getName());
                    progressHandle.start();  // Start the progress bar

                    try {
                        // Get the file dependencies
                        Map<String, List<String>> result = scanJavaFiles(new File(selectedProject.getProjectDirectory().getPath()));
                        appendText("==== Generating Data for Code Search ====\n");

                        int totalFiles = result.size();
                        int currentFile = 0;

                        for (String key : result.keySet()) {
                            appendText(key + "\n");
                            progressHandle.progress("Processing file: " + key, currentFile * 100 / totalFiles);
                            currentFile++;

                            Path path = Paths.get(key);
                            try {
                                String fileContent = Files.readString(path);
                                String userInput = inputTextArea.getText();
                                String selectedModel = (String) modelSelection.getSelectedItem();
                                appendText(selectedModel + " is being used ... \n");

                                String prompt = (userInput.isBlank() ? "Describe the file content given below in just one sentence.\n" : userInput) + fileContent;
                                JSONObject codeSummary = OllamaHelpers.makeNonStreamedRequest(selectedModel, prompt);

                                if (codeSummary != null) {
                                    String llmresp=codeSummary.getString("response") ;
                                    appendText(llmresp + "\n");
                                    
                                    List<String> chat1 = Arrays.asList( llmresp);
                                    double[] embedding1 = OllamaHelpers.getChatEmbedding(chat1); // Similar chat to query
                                    chat1 = Arrays.asList( llmresp,path.toFile().getAbsolutePath());
                                    //The key for storage is Project-name and the sys-millisec
                                    store.storeChat(selectedProject.getProjectDirectory().getName()+"-"+System.currentTimeMillis(), chat1, embedding1);
                                }
                            } catch (Exception ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }

                        appendText("==== Completed the Indexing ====\n");
                        appendText("Now your questions will be answered by looking at the project code. Also, regularly re-index for updated results.\n");
                    } finally {
                        // Complete the progress handle to stop the progress bar
                        progressHandle.finish();
                    }
                }
            });
        }
    });

    return submitButton;
}
     
     private JButton createChatHistoryButton() {
        final JButton submitButton = createButton("Search History");
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userInput = inputTextArea.getText();
                
                if(!userInput.isBlank()){
                 List<String> chat1 = Arrays.asList(userInput);
                double[] embedding1 = OllamaHelpers.getChatEmbedding(chat1); // Similar chat to query
                List<ChatSimilarityResult> chats=store.findSimilarChats(embedding1, 0.4, 3);
                chats.forEach(chatresult->{
                    List<String> kvChats=store.getChat(chatresult.getChatId());
                    kvChats.forEach(chat->{
                        appendToOutputDocumentOllama(chat+"\n" );
                    });
                    
                });
                } else {
                    JOptionPane.showMessageDialog(inputTextArea, "Please put question in input box, for this search.");
                }
            }
        });
        return submitButton;
    }

    private JButton createButton(String buttonText) {
        JButton button = new JButton(buttonText);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        return button;
    }

    private JScrollPane createInputScrollPane() {
        inputTextArea = new JTextArea();
        inputTextArea.setWrapStyleWord(true);
        inputTextArea.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        int caret = inputTextArea.getCaretPosition();
                        inputTextArea.insert("\n", caret);
                        inputTextArea.setCaretPosition(caret + 1);
                    } else {
                        e.consume(); // to prevent newline being added by default behavior
                        submit();
                    }
                }
            }
        });
        return new JScrollPane(inputTextArea);
    }
    private JTextArea inputTextArea;

    private void submit() {
        String userInput = inputTextArea.getText();
        String selectedModel = (String) modelSelection.getSelectedItem(); // Get the selected model
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
               
                
                
                //Get Editior
                JTextComponent editorPane = EditorRegistry.lastFocusedComponent();  
                
                String selectedText=editorPane.getSelectedText();
                if(selectedText!=null && (!selectedText.isBlank())){
                    final ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userInput+"( in context of "+selectedText+")");
                    messages.add(userMessage);
                }
                else{
                    final ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userInput);
                    messages.add(userMessage);
                }
                
                 appendToOutputDocument("User: ");
                appendToOutputDocument(System.lineSeparator());
                appendToOutputDocument(userInput);
                appendToOutputDocument(System.lineSeparator());
                if (OllamaHelpers.OLLAMA_MODELS.contains(selectedModel)) {
                    
                    appendToOutputDocument("Ollama("+selectedModel+"): responding...");

                    String llmResp=OllamaHelpers.callLLMChat(null, selectedModel, messages, null).getJSONObject("message").getString("content");
                    appendToOutputDocumentOllama(llmResp);
                    //
                    //Store chat 
                     List<String> chat1 = Arrays.asList(userInput, llmResp);
                    double[] embedding1 = OllamaHelpers.getChatEmbedding(chat1); // Similar chat to query
                    store.storeChat("NBCHAT-"+System.currentTimeMillis(), chat1, embedding1);
                    appendText("[+m]\n");                   
                    return null;
                } else {
                    callChatGPT(userInput);

                }
                return null;
            }

            private void callChatGPT(String userInput) {
                // Create chat completion request
                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                        .builder()
                        .model(selectedModel)
                        .messages(messages)
                        .n(1)
                        .maxTokens(1000)
                        .logitBias(new HashMap<>())
                        .build();
                try {
                    appendToOutputDocument("ChatGPT: ");
                    appendToOutputDocument(System.lineSeparator());
                    StringBuilder gptResponse = new StringBuilder();
                    service.streamChatCompletion(chatCompletionRequest)
                            .doOnError(throwable -> {
                                String errorMessage = "Error calling OpenAI API: " + throwable.getMessage();
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(ChatTopComponent.this, errorMessage, "API Error", JOptionPane.ERROR_MESSAGE);
                                });
                            })
                            .blockingForEach(new Consumer<ChatCompletionChunk>() {
                                StringBuilder codeBlockIndicatorBuffer = new StringBuilder();

                                @Override
                                public void accept(ChatCompletionChunk chunk) throws Exception {
                                    for (ChatCompletionChoice choice : chunk.getChoices()) {
                                        if (choice.getMessage().getContent() != null) {
                                            String content = choice.getMessage().getContent();
                                            gptResponse.append(content);
                                            if (!codeBlockIndicatorBuffer.isEmpty()) {
                                                codeBlockIndicatorBuffer.append(content);
                                                if (content.contains(System.lineSeparator())) {
                                                    //flush buffer
                                                    appendToOutputDocument(codeBlockIndicatorBuffer.toString());
                                                    codeBlockIndicatorBuffer.setLength(0);
                                                }
                                            } else {
                                                if (content.startsWith("`")) {
                                                    codeBlockIndicatorBuffer.append(content);
                                                } else {
                                                    appendToOutputDocument(content);
                                                }
                                            }
                                        }
                                    }
                                }
                            });
                    final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), gptResponse.toString());
                    messages.add(systemMessage);
                    appendToOutputDocument(System.lineSeparator());
                } catch (OpenAiHttpException ex) {
                    LOG.log(System.Logger.Level.ERROR, "Error calling OpenAI API", ex);
                }
            }

        };
        worker.execute();
        inputTextArea.setText("");
    }
    
     // Method to append content to outputTextArea and handle code annotations
     // Method to append content to outputTextArea and show code blocks in pop-ups
    private void appendToOutputDocumentOllama(String content) {
        // Split the content by code block marker (```)
        String[] parts = content.split("```");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (i % 2 == 0) {
                // Even index: regular text (outside code blocks)
                appendText(part);
            } else {
                String language=part.split("\\r?\\n")[0];
                part=part.substring(language.length());
                // Odd index: code block, show in RSyntaxTextArea with syntax highlighting
                showCodeInPopup(part.trim(), language);  // Assuming Java for this example
                appendText(part);
            }
        }
    }

    // Method to append text to the outputTextArea
    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            outputTextArea.append(text);
        });
    }

    // Method to show the identified code in RSyntaxTextArea with "Copy to Clipboard" option
    private void showCodeInPopup(String code, String language) {
        SwingUtilities.invokeLater(() -> {
            // Create RSyntaxTextArea with appropriate syntax style
            RSyntaxTextArea textArea = new RSyntaxTextArea(30, 120);
            textArea.setText(code);
            textArea.setEditable(false);
            textArea.setSyntaxEditingStyle(getSyntaxStyle(language));
            textArea.setCodeFoldingEnabled(true);

            // Create a scroll pane for the text area
            RTextScrollPane scrollPane = new RTextScrollPane(textArea);
            scrollPane.setFoldIndicatorEnabled(true);

            // Create custom buttons for the dialog
            Object[] options = {"Copy", "Close"};

            // Show the dialog with RSyntaxTextArea embedded
            int result = JOptionPane.showOptionDialog(
                    null, scrollPane, "Code Block Detected ("+language+")",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]
            );

            // Handle the user's selection
            if (result == JOptionPane.YES_OPTION) {
                copyToClipboard(code);
                JOptionPane.showMessageDialog(null, "Code copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    // Method to copy code to the system clipboard
    private void copyToClipboard(String code) {
        StringSelection stringSelection = new StringSelection(code);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    // Method to determine syntax style based on language string
    private String getSyntaxStyle(String language) {
        switch (language.toLowerCase()) {
            case "java":
                return SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "python":
                return SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "javascript":
                return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "xml":
                return SyntaxConstants.SYNTAX_STYLE_XML;
            case "html":
                return SyntaxConstants.SYNTAX_STYLE_HTML;
            case "c":
            case "cpp":
                return SyntaxConstants.SYNTAX_STYLE_C;
            // Add more cases as needed for other languages
            default:
                return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
    }
    
    private void appendToOutputDocument(String content) {
        if (content.startsWith("```")) {
            if (shouldAnnotateCodeBlock) {
                int newlinePos = content.indexOf("\n");

                // If a newline character is found, insert the annotation before it
                if (newlinePos != -1) {
                    String beforeNewline = content.substring(0, newlinePos);
                    String afterNewline = content.substring(newlinePos);
                    SwingUtilities.invokeLater(() -> {
                        outputTextArea.append(beforeNewline + " " + QUICK_COPY_TEXT + afterNewline);
                    });
                } else {
                    // If no newline character is found, append the content as is
                    SwingUtilities.invokeLater(() -> {
                        outputTextArea.append(content);
                    });
                }
                shouldAnnotateCodeBlock = false;
            } else {
                SwingUtilities.invokeLater(() -> {
                    outputTextArea.append(content);
                });
                shouldAnnotateCodeBlock = true;
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                outputTextArea.append(content);
            });
        }

    }

    private void reset() {
        messages.clear();
        shouldAnnotateCodeBlock = true;
        SwingUtilities.invokeLater(() -> {
            outputTextArea.setText("");
        });
    }


    /**
     * 1. Get path to the folder,
     * 2. Scan all the Java files
     * 3. Create chunks of 256 characters,
     * 4. Call embeddings,
     * 5. Store them into MapDB vector store,
     * 6. The vector db should be created with name of the project.
     */
    private void indexProject(){
    
    
    }

}
