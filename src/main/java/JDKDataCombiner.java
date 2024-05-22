import component.JDKClass;
import component.JDKMethod;
import component.JDKModule;
import component.JDKPackage;

import java.io.*;
import java.util.*;

public class JDKDataCombiner {

    Map<String, JDKModule> modules = new HashMap<>();

    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();
        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\ModuleInfo.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\PkgInfo.txt");
            combiner.printData();
//            combiner.printDataIntoExcel();
            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
                    int accessIndex = line.indexOf(", Access: ");
                    String methodName = line.substring(11, accessIndex).trim();
                    String accessType = line.substring(accessIndex + 9).trim();

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

    public String findCompileTimeInvokedMethod(String packageName, String className, String methodName) {
        JDKPackage pkg = this.modules.values().stream()
                .map(module -> module.getPackage(packageName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (pkg != null) {
            JDKClass cls = pkg.getClass(className);
            if (cls != null) {
                JDKMethod method = cls.getMethod(methodName);
                if (method != null) {
//                    System.out.println("Found method: " + methodName + " in package: " + packageName + " in class: " + className);
                    return method.getName();
                }else{
                    for(JDKMethod m : cls.getMethods().values()){
                        if(m.getName().substring(0, m.getName().indexOf("(")).equals(methodName.substring(0, methodName.indexOf("(")))){
                            if(isMethodMatch(m.getName(), methodName)){
                                return m.getName();
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public String findReflectiveInvokedMethod(String packageName, String className, String methodName) {
        JDKPackage pkg = this.modules.values().stream()
                .map(module -> module.getPackage(packageName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (pkg != null) {
            JDKClass cls = pkg.getClass(className);
            if (cls != null) {
                for(JDKMethod m : cls.getMethods().values()){
                    if(m.getName().contains(methodName)){
                        return m.getName();
                    }
                }
            }
        }

        return null;
    }

    public boolean isMethodMatch(String storedSignature, String querySignature) {
        String paramsStored = storedSignature.substring(storedSignature.indexOf('(') + 1, storedSignature.indexOf(')'));
        String paramsQuery = querySignature.substring(querySignature.indexOf('(') + 1, querySignature.indexOf(')'));

        String[] paramsStoredArray = paramsStored.split(", ");
        String[] paramsQueryArray = paramsQuery.split(",");

        if (paramsStoredArray.length != paramsQueryArray.length) {
            return false;
        }

        for (int i = 0; i < paramsStoredArray.length; i++) {
            if (!isTypeMatch(paramsStoredArray[i], paramsQueryArray[i])) {
                return false;
            }
        }

        return true;
    }

    public boolean isTypeMatch(String typeFromSignature, String typeFromQuery) {
        return normalizeType(typeFromSignature).equals(normalizeType(typeFromQuery));
    }

    private String normalizeType(String type) {
        String cleanType = type.replaceAll("<.*?>", "").replaceAll("\\[\\]", "");

        if (cleanType.matches("int|double|float|long|short|byte|boolean|char")) {
            return cleanType;
        }

        try {
            Class<?> clazz = Class.forName(cleanType);
            return clazz.getCanonicalName();
        } catch (ClassNotFoundException e) {
            try {
                Class<?> clazz = Class.forName("java.lang." + cleanType);
                return clazz.getCanonicalName();
            } catch (ClassNotFoundException ex) {
                return type;
            }
        }
    }
}

