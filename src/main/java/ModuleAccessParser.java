import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import component.ModuleAccessRecorder;

public class ModuleAccessParser {
    private static final String DIRECTIVES_FILE_NAME = "directives.txt";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java ModuleAccessParser <path_to_directives_folder>");
            return;
        }

        String rootDirectoryPath = args[0];
        ModuleAccessRecorder accessRecorder = new ModuleAccessRecorder();
        try {
            parseDirectives(rootDirectoryPath, accessRecorder);
            System.out.println("Parsing complete.");
        } catch (IOException e) {
            System.out.println("Error reading files: " + e.getMessage());
        }
    }

    private static void parseDirectives(String rootDirectoryPath, ModuleAccessRecorder accessRecorder) throws IOException {
        Files.walk(Paths.get(rootDirectoryPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(DIRECTIVES_FILE_NAME))
                .forEach(path -> processDirectiveFile(path, accessRecorder));
    }

    private static void processDirectiveFile(Path directiveFilePath, ModuleAccessRecorder accessRecorder) {
        try {
            String moduleName = directiveFilePath.getParent().getFileName().toString();
            Files.lines(directiveFilePath).forEach(line -> {
                if (line.startsWith("exports") || line.startsWith("opens")) {
                    parseLine(line, moduleName, accessRecorder);
                }
            });
        } catch (IOException e) {
            System.out.println("Failed to read file: " + directiveFilePath + " due to " + e.getMessage());
        }
    }

    private static void parseLine(String line, String moduleName, ModuleAccessRecorder accessRecorder) {
        String[] parts = line.split(" ");
        String directiveType = parts[0];
        String packageName = parts[1];
        Set<String> allowedModules = null;

        if (line.contains("to")) {
            allowedModules = new HashSet<>(Arrays.asList(line.substring(line.indexOf("to") + 3).split(", ")));
        }

        accessRecorder.addAccessRule(moduleName, packageName, directiveType, allowedModules);
    }
}

