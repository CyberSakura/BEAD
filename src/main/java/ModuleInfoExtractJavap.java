import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ModuleInfoExtractJavap {
    private static final String MODULES_DIR = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\Extracted Module Classes";  // module-info.class文件所在目录

    public static void main(String[] args) {
        File modulesDir = new File(MODULES_DIR);
        File[] moduleFiles = modulesDir.listFiles((dir, name) -> name.endsWith("module-info.class"));
        String outputPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\directives1";

        if (moduleFiles == null) {
            System.out.println("No module-info.class files found.");
            return;
        }

        Map<String, List<String>> moduleContents = new HashMap<>();
        Arrays.stream(moduleFiles).forEach(file -> {
            List<String> decompiledContent = decompileModuleInfo(file);
            if (decompiledContent != null) {
                moduleContents.put(file.getName().replaceAll("-module-info.class$", ""), decompiledContent);
            }
        });

        // 输出或处理Map中的数据
        moduleContents.forEach((module, lines) -> {
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

    private static List<String> decompileModuleInfo(File moduleFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "javap", "-p", moduleFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process p = pb.start();
            List<String> outputLines = new ArrayList<>();
            Pattern pattern = Pattern.compile("^(exports|uses|provides|opens|open)(\\s+)", Pattern.DOTALL);
            StringBuilder currentDeclaration = new StringBuilder();

            boolean insideModule = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim().replaceAll("\\s+", " ");
                    line = line.trim().replace('$', '.');
                    if (line.isEmpty()) {
                        continue; // Skip empty or whitespace-only lines
                    }
                    if (line.startsWith("module")) {
                        insideModule = true;
                    } else if (insideModule && line.equals("}")) {
                        insideModule = false;
                    } else if (insideModule) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) { // Start of a new directive
                            if (currentDeclaration.length() > 0) { // There's a buffered directive
                                outputLines.add(currentDeclaration.toString().trim()); // Add buffered directive
                                currentDeclaration.setLength(0); // Clear the buffer
                            }
                            currentDeclaration.append(line.trim()); // Start buffering the new directive
                        } else if (currentDeclaration.length() > 0) { // Continuing an existing directive
                            if (line.startsWith("with ")) {
                                currentDeclaration.append(" ").append(line.trim()); // Ensure no extra space before 'with'
                            } else {
                                currentDeclaration.append(" ").append(line.trim());
                            }
                        }

                        if (line.endsWith(";")) {
                            if (currentDeclaration.length() > 0) { // Complete directive ending with a semicolon
                                outputLines.add(currentDeclaration.toString().trim()); // Add complete directive
                                currentDeclaration.setLength(0); // Clear the buffer
                            }
                        }
                    }
                }
            }

            p.waitFor();
            if (p.exitValue() == 0) {
                return outputLines;
            } else {
                System.out.println("Failed to decompile " + moduleFile.getName() + ", exit code: " + p.exitValue());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error decompiling " + moduleFile.getName());
            e.printStackTrace();
        }
        return null;
    }

}
