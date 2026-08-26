import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Bead {

    public static void main(String[] args) {
        Config config = Config.parse(args);
        if (config == null) {
            System.exit(1);
            return;
        }
        if (config.helpOnly) {
            return;
        }

        try {
            new File("Result").mkdirs();

            Path moduleInfo = Paths.get("ModuleInfo.txt");
            Path pkgInfo = Paths.get("PkgInfo.txt");
            boolean haveIndex = Files.isRegularFile(moduleInfo) && Files.isRegularFile(pkgInfo);

            if (config.rebuildJdkIndex || !haveIndex) {
                System.out.println("Building JDK module/package index...");
                System.out.println("  jmods:   " + config.jmods);
                System.out.println("  src.zip: " + config.srcZip);
                ExtractModuleInfoClasses.run(config.jmods);
                ModuleInfoExtractJavap.run();
                ModuleAccessParser.run(Paths.get(System.getProperty("user.dir"), "directives").toString());
                JavaSourceAnalyzer.run(config.srcZip);
            } else {
                System.out.println("Using existing ModuleInfo.txt and PkgInfo.txt (pass --rebuild-jdk-index to regenerate).");
            }

            System.out.println("Analyzing JAR: " + config.jar);
            warnIfUnsupportedJdk();
            AbuseAnalyzer.run(config.jar);
        } catch (Exception e) {
            System.err.println("BEAD failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void warnIfUnsupportedJdk() {
        int feature = runtimeFeatureVersion();
        if (feature == 17) {
            return;
        }
        System.err.println("Warning: BEAD was evaluated on JDK 17 (you are on " + feature + ").");
        if (feature > 21) {
            System.err.println("Soot 4.5.0 cannot load class files from this JDK. Aborting.");
            System.err.println("Point JAVA_HOME at JDK 17 and rerun, for example:");
            System.err.println("  macOS:  export JAVA_HOME=$(/usr/libexec/java_home -v 17)");
            System.err.println("  Linux:  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk");
            System.exit(1);
        }
    }

    static int runtimeFeatureVersion() {
        String spec = System.getProperty("java.specification.version", "0");
        if (spec.startsWith("1.")) {
            spec = spec.substring(2);
        }
        int dot = spec.indexOf('.');
        if (dot > 0) {
            spec = spec.substring(0, dot);
        }
        try {
            return Integer.parseInt(spec);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static final class Config {
        String jar;
        String jmods;
        String srcZip;
        boolean rebuildJdkIndex;
        boolean helpOnly;

        static Config parse(String[] args) {
            Config config = new Config();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help":
                    case "-h":
                        printUsage();
                        config.helpOnly = true;
                        return config;
                    case "--jar":
                        config.jar = nextArg(args, ++i, "--jar");
                        if (config.jar == null) {
                            return null;
                        }
                        break;
                    case "--jmods":
                        config.jmods = nextArg(args, ++i, "--jmods");
                        if (config.jmods == null) {
                            return null;
                        }
                        break;
                    case "--src-zip":
                        config.srcZip = nextArg(args, ++i, "--src-zip");
                        if (config.srcZip == null) {
                            return null;
                        }
                        break;
                    case "--rebuild-jdk-index":
                        config.rebuildJdkIndex = true;
                        break;
                    default:
                        System.err.println("Unknown argument: " + args[i]);
                        printUsage();
                        return null;
                }
            }

            if (config.jar == null || config.jar.isEmpty()) {
                System.err.println("Missing required argument: --jar <path>");
                printUsage();
                return null;
            }
            if (!new File(config.jar).isFile()) {
                System.err.println("JAR not found: " + config.jar);
                return null;
            }

            if (config.jmods == null) {
                config.jmods = Paths.get(jdkHome(), "jmods").toString();
            }
            if (config.srcZip == null) {
                Path localSrc = Paths.get("src.zip");
                config.srcZip = Files.isRegularFile(localSrc)
                        ? localSrc.toString()
                        : Paths.get(jdkHome(), "lib", "src.zip").toString();
            }
            return config;
        }

        private static String nextArg(String[] args, int index, String flag) {
            if (index >= args.length) {
                System.err.println(flag + " requires a value");
                printUsage();
                return null;
            }
            return args[index];
        }

        private static String jdkHome() {
            String home = System.getenv("JAVA_HOME");
            if (home == null || home.isEmpty()) {
                home = System.getProperty("java.home");
            }
            return home;
        }

        private static void printUsage() {
            System.out.println("BEAD — Breaking Encapsulation Abuse Detector");
            System.out.println();
            System.out.println("Usage:");
            System.out.println("  mvn compile exec:java -Dexec.args=\"--jar TestJar/lombok-1.18.6.jar\"");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --jar <path>            JAR to analyze (required)");
            System.out.println("  --jmods <dir>           JDK jmods directory (default: $JAVA_HOME/jmods)");
            System.out.println("  --src-zip <path>        JDK src.zip (default: ./src.zip or $JAVA_HOME/lib/src.zip)");
            System.out.println("  --rebuild-jdk-index     Rebuild ModuleInfo.txt and PkgInfo.txt from the JDK");
            System.out.println("  --help                  Show this help");
        }
    }
}
