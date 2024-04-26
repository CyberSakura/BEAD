import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class JavaSourceAnalyzer {
    private static final Map<String, Map<String, Map<String, String>>> packageClassMethods = new HashMap<>();
    private static final JavaParser parser = new JavaParser();

    public static void main(String[] args) throws Exception {
        String zipFilePath = "E:\\Thesis\\AbuseDetection\\src.zip";
        String outputPath = "E:\\Thesis\\AbuseDetection\\out.txt";

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
                                String accessSpecifier = method.getAccessSpecifier().asString();
                                packageClassMethods.get(packageName).get(className).put(methodName, accessSpecifier);
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
            for (var packageEntry : packageClassMethods.entrySet()) {
                writer.write("Package: " + packageEntry.getKey() + "\n");
                for (var classEntry : packageEntry.getValue().entrySet()) {
                    writer.write("  Class: " + classEntry.getKey() + "\n");
                    for (var methodEntry : classEntry.getValue().entrySet()) {
                        writer.write("    Method: " + methodEntry.getKey() + ", Access: " + methodEntry.getValue() + "\n");
                    }
                }
                writer.write("\n");
            }
        }
    }
}
