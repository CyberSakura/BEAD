import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class JavaSourceAnalyzer {
    private static final Map<String, Map<String, Map<String, String>>> packageClassMethods = new HashMap<>();
    private static final JavaParser parser = new JavaParser();

    public static void main(String[] args) throws Exception {
        String zipFilePath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\src.zip";
        String outputPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\ModuleInfo.txt";

        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            zipFile.stream().filter(entry -> entry.getName().endsWith(".java")).forEach(entry -> {
                try (var inputStream = zipFile.getInputStream(entry)) {
                    CompilationUnit compilationUnit = parser.parse(inputStream).getResult().orElse(null);
                    if (compilationUnit != null) {
                        // Extract package name
                        String packageName = compilationUnit.getPackageDeclaration()
                                .map(pd -> pd.getName().asString())
                                .orElse("");

                        // Process each class in the file
                        compilationUnit.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                            String className = cls.getNameAsString();
                            packageClassMethods.putIfAbsent(packageName, new HashMap<>());
                            packageClassMethods.get(packageName).putIfAbsent(className, new HashMap<>());

                            // Process methods in the class
                            cls.getMethods().forEach(method -> {
                                String methodName = method.getNameAsString();
                                String methodSignature = methodName + method.getParameters().stream()
                                        .map(p -> p.getType().asString())
                                        .collect(Collectors.joining(", ", "(", ")"));
                                String accessSpecifier = method.getAccessSpecifier().asString();

                                if (accessSpecifier.isEmpty()) {
                                    if (cls.isInterface()) {
                                        accessSpecifier = "public";
                                    } else {
                                        accessSpecifier = "package-private";
                                    }
                                }

                                packageClassMethods.get(packageName).get(className).put(methodSignature, accessSpecifier);
                            });
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Write results to a file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            int totalPackages = packageClassMethods.size();
            int totalClasses = 0;
            int totalMethods = 0;


            for (var packageEntry : packageClassMethods.entrySet()) {
                writer.write("Package: " + packageEntry.getKey() + "\n");
                totalClasses += packageEntry.getValue().size();

                for (var classEntry : packageEntry.getValue().entrySet()) {
                    writer.write("  Class: " + classEntry.getKey() + "\n");
                    totalMethods += classEntry.getValue().size();

                    for (var methodEntry : classEntry.getValue().entrySet()) {
                        writer.write("    Method: " + methodEntry.getKey() + ", Access: " + methodEntry.getValue() + "\n");
                    }
                }
                writer.write("\n");
            }

            writer.write("Summary Statistics:\n");
            writer.write("Total Packages: " + totalPackages + "\n");
            writer.write("Total Classes: " + totalClasses + "\n");
            writer.write("Total Methods: " + totalMethods + "\n");
        }

        System.out.println("Analysis complete. Results written to " + outputPath);
    }
}
