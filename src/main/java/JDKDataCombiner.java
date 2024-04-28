import component.JDKClass;
import component.JDKMethod;
import component.JDKModule;
import component.JDKPackage;

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
                        Arrays.stream(modulesStr.split(",\\s*")).forEach(pkg::addAllowedModule);
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
                        if (module.getPackages().containsKey(packageName)) {
                            currentPackage = module.getPackages().get(packageName);
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
                out.println("Module " + mod.getName());
                for (JDKPackage pkg : mod.getPackages().values()) {
                    out.print("----Package " + pkg.getName() + "; AccessRules: " + String.join(", ", pkg.getAccessRules()));
                    if (!pkg.getAllowedModules().isEmpty() && !pkg.getAllowedModules().get(0).isEmpty()) {
                        out.print("; Allowed Modules: " + String.join(", ", pkg.getAllowedModules()));
                    }
                    out.println();  // Finish the line after printing the package details
                    for (JDKClass cls : pkg.getClasses().values()) {
                        out.println("       ---- Class " + cls.getName());
                        for (JDKMethod meth : cls.getMethods().values()) {
                            out.println("               ---- Method " + meth.getName() + "; AccessType: " + meth.getAccessType());
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

