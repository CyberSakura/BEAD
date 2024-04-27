import java.io.*;
import java.util.*;

public class JDKDataCombiner {

    Map<String, JDKModule> modules = new HashMap<>();

    public void parseModuleInfoFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            JDKModule currentModule = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Module:")) {
                    String moduleName = line.substring(7).trim();
                    currentModule = new JDKModule(moduleName);
                    modules.put(moduleName, currentModule);
                } else if (line.contains("Package:")) {
                    String packageName = line.substring(line.indexOf("Package:") + 9, line.indexOf("->")).trim();
                    String accessInfo = line.substring(line.indexOf("->") + 2).trim();
                    JDKPackage pkg = new JDKPackage(packageName);

                    // Extract the types from the string
                    String typesStr = accessInfo.substring(accessInfo.indexOf("[") + 1, accessInfo.indexOf("]"));
                    Arrays.stream(typesStr.split(",")).forEach(type -> pkg.addAccessRule(type.trim()));

                    // Extract allowed modules if present
                    if (accessInfo.contains("AllowedModules:")) {
                        String modulesStr = accessInfo.substring(accessInfo.lastIndexOf("[") + 1, accessInfo.lastIndexOf("]"));
                        pkg.allowedModules = Arrays.asList(modulesStr.split(",\\s*"));
                    }
                    currentModule.addPackage(pkg);
                }
            }
        }
    }

    public void parsePkgInfoFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            JDKPackage currentPackage = null;
            JDKClass currentClass = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Package:")) {
                    String packageName = line.substring(8).trim();
                    for (JDKModule module : modules.values()) {
                        if (module.packages.containsKey(packageName)) {
                            currentPackage = module.packages.get(packageName);
                            break;
                        }
                    }
                } else if (line.contains("Class:")) {
                    currentClass = new JDKClass(line.substring(8).trim());
                    currentPackage.addClass(currentClass);
                } else if (line.contains("Method:")) {
                    String[] parts = line.substring(11).trim().split(",");
                    String methodName = parts[0].trim();
                    String accessType = (parts.length > 1 && parts[1].length() > "Access: ".length())
                            ? parts[1].substring("Access: ".length()).trim()
                            : "default";

                    JDKMethod method = new JDKMethod(methodName, accessType);
                    currentClass.addMethod(method);
                }
            }
        }
    }

    public void printData() {
        File outputFile = new File("data.txt");
        try (PrintWriter out = new PrintWriter(outputFile)) {
            for (JDKModule mod : modules.values()) {
                out.println("Module " + mod.name);
                for (JDKPackage pkg : mod.packages.values()) {
                    out.print("----Package " + pkg.name + "; AccessRules: " + String.join(", ", pkg.accessRules));
                    if (!pkg.allowedModules.isEmpty() && !pkg.allowedModules.get(0).isEmpty()) {
                        out.print("; Allowed Modules: " + String.join(", ", pkg.allowedModules));
                    }
                    out.println();  // Finish the line after printing the package details
                    for (JDKClass cls : pkg.classes.values()) {
                        out.println("       ---- Class " + cls.name);
                        for (JDKMethod meth : cls.methods.values()) {
                            out.println("               ---- Method " + meth.name + "; AccessType: " + meth.accessType);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error: Unable to create or write to the file 'data.txt'.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();
        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out1.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out.txt");
            combiner.printData();
//            combiner.printDataIntoExcel();
            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class JDKModule {
    String name;
    Map<String, JDKPackage> packages = new HashMap<>();

    public JDKModule(String name) {
        this.name = name;
    }

    void addPackage(JDKPackage pkg) {
        packages.put(pkg.name, pkg);
    }
}

class JDKPackage {
    String name;
    Set<String> accessRules = new HashSet<>();
    List<String> allowedModules = new ArrayList<>();
    Map<String, JDKClass> classes = new HashMap<>();

    public JDKPackage(String name) {
        this.name = name;
    }

    void addClass(JDKClass cls) {
        classes.put(cls.name, cls);
    }

    void addAccessRule(String rule) {
        accessRules.add(rule);
    }
}

class JDKClass {
    String name;
    Map<String, JDKMethod> methods = new HashMap<>();

    public JDKClass(String name) {
        this.name = name;
    }

    void addMethod(JDKMethod method) {
        methods.put(method.name, method);
    }
}

class JDKMethod {
    String name;
    String accessType;

    public JDKMethod(String name, String accessType) {
        this.name = name;
        this.accessType = accessType;
    }
}
