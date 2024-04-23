import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FindOpenModule {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java OpenModuleCheckerFromZip <path_to_src_zip>");
            System.exit(1);
        }

        Path zipPath = Paths.get(args[0]);
        Pattern openModulePattern = Pattern.compile("^\\s*open\\s+module\\s+.*\\{", Pattern.MULTILINE);

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            zipFile.stream()
                    .filter(e -> e.getName().endsWith("module-info.java"))
                    .forEach(e -> checkOpenModule(zipFile, e, openModulePattern));
        } catch (IOException e) {
            System.err.println("Error reading zip file: " + e.getMessage());
        }
    }

    private static void checkOpenModule(ZipFile zipFile, ZipEntry entry, Pattern pattern) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String content = reader.lines().reduce("", (acc, line) -> acc + line + "\n");
            if (pattern.matcher(content).find()) {
                System.out.println("Open module found in: " + entry.getName());
            }
        } catch (IOException e) {
            System.err.println("Error reading zip entry: " + entry.getName() + "; " + e.getMessage());
        }
    }
}
