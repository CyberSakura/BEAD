import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import component.AccessRule;
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
            validateAccessRules(accessRecorder);
            writeAccessRecorderDataToFile(accessRecorder);
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
        String[] initialParts = line.split("\\s+");
        String directiveType = initialParts[0];
        String packageName = initialParts[1].endsWith(";") ? initialParts[1].substring(0, initialParts[1].length() - 1) : initialParts[1];
        Set<String> allowedModules = null;

        if (line.contains(" to ")) {
            directiveType += " to";
            String modulesPart = line.substring(line.indexOf(" to ") + 4);
            String[] modules = modulesPart.split(",\\s*");
            allowedModules = new HashSet<>();
            for (String module : modules) {
                String cleanedModule = module.endsWith(";") ? module.substring(0, module.length() - 1) : module;
                allowedModules.add(cleanedModule.trim());
            }
        }

        accessRecorder.addAccessRule(moduleName, packageName, directiveType, allowedModules);


    }

    private static void validateAccessRules(ModuleAccessRecorder moduleAccessRecorder) {
        HashMap<String, HashMap<String, AccessRule>> allRules = moduleAccessRecorder.getAccessRules();
        for (Map.Entry<String, HashMap<String, AccessRule>> moduleEntry : allRules.entrySet()) {
            String moduleName = moduleEntry.getKey();
            for (Map.Entry<String, AccessRule> packageEntry : moduleEntry.getValue().entrySet()) {
                String packageName = packageEntry.getKey();
                AccessRule rule = packageEntry.getValue();
                Set<String> types = rule.getTypes();
                Set<String> allowedModules = rule.getAllowedModules();

                // Check for each case:
                if (types.contains("exports") && allowedModules != null && !allowedModules.isEmpty()) {
                    System.out.println("Error: Module '" + moduleName + "', Package '" + packageName + "' - 'exports' should not have allowed modules but found: " + allowedModules);
                } else if (types.contains("exports to")&& (allowedModules == null || allowedModules.isEmpty())) {
                    System.out.println("Error: Module '" + moduleName + "', Package '" + packageName + "' - 'exports to' requires allowed modules but none found.");
                } else if (types.contains("opens") && allowedModules != null && !allowedModules.isEmpty()) {
                    System.out.println("Error: Module '" + moduleName + "', Package '" + packageName + "' - 'opens' should not have allowed modules but found: " + allowedModules);
                } else if (types.contains("opens to") && (allowedModules == null || allowedModules.isEmpty())) {
                    System.out.println("Error: Module '" + moduleName + "', Package '" + packageName + "' - 'opens to' requires allowed modules but none found.");
                }
            }
        }

        System.out.println("Validation complete. No errors found.");
    }

    private static void writeAccessRecorderDataToFile(ModuleAccessRecorder accessRecorder) throws IOException {
        String content = accessRecorder.formatAccessRules();
        Path outputPath = Paths.get("ModuleInfo.txt");
        Files.write(outputPath, content.getBytes());
        System.out.println("Access rules written to " + outputPath);
    }
}

