import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class ExtractModuleInfoClasses {
    private static final String JDK_JMODS_PATH = "C:\\Program Files\\Java\\jdk-17\\jmods";
    private static final String OUTPUT_DIR = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\Extracted Module Classes";

    public static void main(String[] args) {
        File jmodsDir = new File(JDK_JMODS_PATH);
        File[] jmodFiles = jmodsDir.listFiles((dir, name) -> name.endsWith(".jmod"));

        if (jmodFiles == null) {
            System.out.println("No .jmod files found in the directory.");
            return;
        }

        System.out.println("Found " + jmodFiles.length + " .jmod files.");
        for (File jmodFile : jmodFiles) {
            extractModuleInfoClass(jmodFile, OUTPUT_DIR);
        }
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
