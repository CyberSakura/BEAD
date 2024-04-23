import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModuleInfoExtractSrc {
    public static void main(String[] args) {
        String srcZipPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\src.zip";
        String moduleInfoOutputPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\Extracted Module Info";
        String outputPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\directives";

        Map<String, Set<String>> moduleStatements = new HashMap<>();
        Pattern directivePattern = Pattern.compile("^(exports|uses|provides|opens|open)(\\s+|\\.{3}\\s*)");

        try {
            Files.createDirectories(Paths.get(moduleInfoOutputPath));
        } catch (IOException e) {
            System.out.println("Failed to create output directory.");
            e.printStackTrace();
            return;
        }

        try (ZipFile zipFile = new ZipFile(srcZipPath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("module-info.java")) {
                    String moduleName = entry.getName().substring(0, entry.getName().indexOf('/'));
                    Set<String> statements = new LinkedHashSet<>();
                    boolean recording = false;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                        String line;
                        StringBuilder directiveBuilder = new StringBuilder(); // Buffer to hold ongoing directive lines
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) {
                                continue; // Skip empty or whitespace-only lines
                            }
                            if (line.startsWith("module")) {
                                recording = true;
                            } else if (recording && line.equals("}")) {
                                recording = false;
                            } else if (recording) {
                                if (directivePattern.matcher(line).find()) { // Start of a new directive
                                    if (directiveBuilder.length() > 0) { // There's a buffered directive
                                        statements.add(directiveBuilder.toString());
                                        directiveBuilder.setLength(0); // Clear the buffer
                                    }
                                    directiveBuilder.append(line); // Start buffering the new directive
                                } else if (directiveBuilder.length() > 0) { // Continuing an existing directive
                                    directiveBuilder.append(" ");
                                    directiveBuilder.append(line);
                                }

                                if (line.endsWith(";")) {
                                    if (directiveBuilder.length() > 0) { // Complete directive ending with a semicolon
                                        statements.add(directiveBuilder.toString());
                                        directiveBuilder.setLength(0); // Clear the buffer
                                    }
                                }
                            }
                        }
                        // Check if there's any buffered directive left to add after reading all lines
                        if (directiveBuilder.length() > 0) {
                            statements.add(directiveBuilder.toString());
                        }
                    }

                    moduleStatements.put(moduleName, statements);
                    Path filePath = Paths.get(moduleInfoOutputPath, entry.getName());
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipFile.getInputStream(entry), filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred while processing the src.zip file.");
            e.printStackTrace();
        }

        moduleStatements.forEach((module, lines) -> {
            try {
                Path moduleDirectory = Paths.get(outputPath, module);
                Files.createDirectories(moduleDirectory); // Ensure the directory exists

                Path outputFile = moduleDirectory.resolve("directives.txt");
                try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } catch (IOException e) {
                System.out.println("Failed to write output for module: " + module);
                e.printStackTrace();
            }
        });

        System.out.println("Finished");
    }
}
