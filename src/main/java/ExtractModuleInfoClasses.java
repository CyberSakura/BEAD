import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ExtractModuleInfoClasses {

    public static void main(String[] args) {
        String jmodsPath = args.length > 0 ? args[0] : defaultJmodsPath();
        run(jmodsPath);
    }

    public static void run(String jmodsPath) {
        String userDir = System.getProperty("user.dir");
        String outputPath = Paths.get(userDir, "Extracted Module Classes").toString();
        File jmodsDir = new File(jmodsPath);
        File[] jmodFiles = jmodsDir.listFiles((dir, name) -> name.endsWith(".jmod"));

        if (jmodFiles == null) {
            throw new IllegalArgumentException("No .jmod files found in: " + jmodsPath);
        }

        try {
            Files.createDirectories(Paths.get(outputPath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory: " + outputPath, e);
        }

        System.out.println("Found " + jmodFiles.length + " .jmod files.");
        for (File jmodFile : jmodFiles) {
            extractModuleInfoClass(jmodFile, outputPath);
        }
    }

    static String defaultJmodsPath() {
        String home = System.getenv("JAVA_HOME");
        if (home == null || home.isEmpty()) {
            home = System.getProperty("java.home");
        }
        return Paths.get(home, "jmods").toString();
    }

    private static void extractModuleInfoClass(File jmodFile, String outputDir) {
        try (JarFile jmodJar = new JarFile(jmodFile)) {
            ZipEntry moduleInfoEntry = jmodJar.getEntry("classes/module-info.class");
            if (moduleInfoEntry != null) {
                File outputFile = new File(outputDir, jmodFile.getName().replace(".jmod", "-module-info.class"));
                Files.copy(jmodJar.getInputStream(moduleInfoEntry), outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Extracted: " + outputFile.getPath());
            } else {
                System.out.println("module-info.class not found in " + jmodFile.getName());
            }
        } catch (IOException e) {
            System.out.println("Failed to process " + jmodFile.getName());
            e.printStackTrace();
        }
    }
}
