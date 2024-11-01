/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.stackleader.netbeans.chatgpt;

import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.text.JTextComponent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;
import org.openide.util.Mutex;

/**
 *
 * @author manoj.kumar
 */
public class IDEHelper {
    
    
     private static Map<String, FunctionHandler> functionHandlers = new HashMap<>();

    public static Map<String, FunctionHandler> getFunctionHandlers() {
        return functionHandlers;
    }
     
       public static void registerFunction(FunctionHandler handler) {
        functionHandlers.put(handler.getFunctionName(), handler);
    }
    
    
    
       
  public static void listAllFiles(StringBuilder output) {
    Mutex.EVENT.readAccess(() -> {
        // Get the currently opened projects
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();

        if (openProjects.length > 0) {
            Project activeProject = openProjects[0];  // Assuming you want the first active project

            // Get the root folder of the active project
            FileObject projectDirectory = activeProject.getProjectDirectory();

            // Recursively list all files
            listFilesRecursively(projectDirectory, output);
        } else {
            System.out.println("No active projects found.");
        }
        return null;  // Return value is required for readAccess
    });
}

    private static void listFilesRecursively(FileObject folder,StringBuilder output) {
        for (FileObject file : folder.getChildren()) {
            if (file.isFolder()) {
                listFilesRecursively(file,output);  // Recursively list files in subdirectories
            } else {
                //System.out.println("File: " + file.getPath());
                output.append(file.getPath()).append("\n");
            }
        }
    }
    
        public void detectProjectLanguage() {
        // Get the currently opened projects
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();

        if (openProjects.length > 0) {
            Project activeProject = openProjects[0];  // Assuming you want the first active project
            FileObject projectDirectory = activeProject.getProjectDirectory();
            
            // Scan the project files to detect the language
            String language = detectLanguageByFileExtension(projectDirectory);
            if (language != null) {
                System.out.println("Detected Project Language: " + language);
            } else {
                System.out.println("Language could not be determined.");
            }
        } else {
            System.out.println("No active projects found.");
        }
    }

    private String detectLanguageByFileExtension(FileObject folder) {
        for (FileObject file : folder.getChildren()) {
            if (file.isFolder()) {
                // Recursively check subdirectories
                String language = detectLanguageByFileExtension(file);
                if (language != null) {
                    return language;
                }
            } else {
                // Check file extension to determine language
                String ext = file.getExt();
                switch (ext) {
                    case "java":
                        return "Java";
                    case "cpp":
                    case "h":
                        return "C++";
                    case "py":
                        return "Python";
                    case "js":
                        return "JavaScript";
                    case "html":
                        return "HTML";
                    case "php":
                        return "PHP";
                    // Add more file extensions for other languages as needed
                    default:
                        break;
                }
            }
        }
        return null;
    }
    
    //TEST THE METHODS
        public static void main(String[] args) {
        // Step 1: Register the FilesList handler
        FilesList filesListHandler = new FilesList();
        IDEHelper.registerFunction(filesListHandler);

        // Step 2: Test the listAllFiles functionality through the FilesList handler
        System.out.println("=== Testing listAllFiles ===");
        FunctionHandler filesHandler = IDEHelper.getFunctionHandlers().get("projetc_files_list");
        
        if (filesHandler != null) {
            // Simulate passing empty JSON arguments since no variables are used
            JSONObject arguments = new JSONObject();
            String result = filesHandler.execute(arguments);
            System.out.println(result);  // Output the list of files
        } else {
            System.out.println("FilesList handler not found.");
        }

        // Step 3: Test the detectProjectLanguage method
        System.out.println("=== Testing detectProjectLanguage ===");
        IDEHelper ideHelperInstance = new IDEHelper();
        ideHelperInstance.detectProjectLanguage();  // Detect and print the language
        
        System.out.println("=== This Project ===");
        JTextComponent editorPane = EditorRegistry.lastFocusedComponent();  
        System.out.println(editorPane.getDocument().getLength());
       
        Project thisProject=OpenProjects.getDefault().getOpenProjects()[0];
        System.out.println(thisProject.getProjectDirectory().getPath());
    }
    
    /**
     * Displays a dialog with a JComboBox of open projects, allowing the user to select one.
     * 
     * @return The selected Project, or null if no project was selected.
     */
    public static Project selectProjectFromOpenProjects() {
        // Retrieve the open projects
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();

        // Check if there are any open projects
        if (openProjects.length == 0) {
            JOptionPane.showMessageDialog(null, "No open projects found.");
            return null;
        }

        // Create a JComboBox with the project names as options
        JComboBox<Project> projectComboBox = new JComboBox<>(openProjects);

        // Set a custom renderer to display project names instead of object references
        projectComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                // Assuming each Project object has a method getDisplayName() to show project name
                if (value instanceof Project) {
                    Project project = (Project) value;
                    setText(project.getProjectDirectory().getName()); // Adjust as per Project API
                }
                return this;
            }
        });

        // Show the JComboBox in a JOptionPane dialog
        int result = JOptionPane.showConfirmDialog(
                null, projectComboBox, "Select a Project", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            return (Project) projectComboBox.getSelectedItem();
        } else {
            return null;
        }
    }

}

class FilesList implements FunctionHandler {

    @Override
    public String getFunctionName() {
        return "projetc_files_list";
    }

    @Override
    public String getDescription() {
        return "List all the files in the provided project.";
    }

    @Override
    public JSONObject getFunctionJSON() {
        JSONObject functionObject = new JSONObject();
        functionObject.put("name", getFunctionName());
        functionObject.put("description", getDescription());

        JSONObject parametersObject = new JSONObject();
        parametersObject.put("type", "object");

        JSONObject propertiesObject = new JSONObject();

        for (VariableDefinition variable : getVariables().values()) {
            propertiesObject.put(variable.getName(), variable.toJSON());
        }

        parametersObject.put("properties", propertiesObject);
        parametersObject.put("required", new JSONArray(getVariables().keySet()));

        functionObject.put("parameters", parametersObject);

        return functionObject;
    }

    @Override
    public Map<String, VariableDefinition> getVariables() {
        Map<String, VariableDefinition> variables = new HashMap<>();
//        variables.put("length", new VariableDefinition("length", "number", "The length of the cuboid.", null));
//        variables.put("width", new VariableDefinition("width", "number", "The width of the cuboid.", null));
//        variables.put("height", new VariableDefinition("height", "number", "The height of the cuboid.", null));
        return variables;
    }

    @Override
    public String execute(JSONObject arguments) {
//        double length = arguments.getDouble("length");
//        double width = arguments.getDouble("width");
//        double height = arguments.getDouble("height");
//
//        double volume = length * width * height;
        StringBuilder output = new StringBuilder();
        output.append("** List of files ***").append("\n");

        IDEHelper.listAllFiles(output);
        output.append("\n*****").append("\n");

        return output.toString();
    }
}
