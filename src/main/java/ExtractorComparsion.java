import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ExtractorComparsion {
    private static final String DIRECTORY1 = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\directives";
    private static final String DIRECTORY2 = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\directives1";

    public static void main(String[] args) {
        Path path1 = Paths.get(DIRECTORY1);
        Path path2 = Paths.get(DIRECTORY2);

        if (!Files.exists(path1) || !Files.exists(path2)) {
            System.out.println("One or both directories do not exist.");
            return;
        }

        try {
            compareDirectories(path1, path2);
        } catch (IOException e) {
            System.out.println("An error occurred during comparison.");
            e.printStackTrace();
        }
    }

    private static void compareDirectories(Path dir1, Path dir2) throws IOException {
        Files.walk(dir1).forEach(path -> {
            try {
                Path relative = dir1.relativize(path);
                Path otherPath = dir2.resolve(relative);

                if (!Files.exists(otherPath)) {
                    System.out.println("Missing in second directory: " + relative);
                } else if (!Files.isDirectory(path)) {
                    compareFiles(path, otherPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Check for extra files in the second directory not present in the first
        Files.walk(dir2).forEach(path -> {
            try {
                Path relative = dir2.relativize(path);
                if (!Files.exists(dir1.resolve(relative))) {
                    System.out.println("Extra in second directory: " + relative);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void compareFiles(Path file1, Path file2) throws IOException {
        if (Files.isDirectory(file1) || Files.isDirectory(file2)) {
            return; // Ignore directories for file comparison
        }

        byte[] content1 = Files.readAllBytes(file1);
        byte[] content2 = Files.readAllBytes(file2);

        if (!Arrays.equals(content1, content2)) {
            Path relativePath1 = Paths.get(DIRECTORY1).relativize(file1);
            Path relativePath2 = Paths.get(DIRECTORY2).relativize(file2);
            System.out.println("File content differs: " + relativePath1 + " <> " + relativePath2);
        }
    }

}
