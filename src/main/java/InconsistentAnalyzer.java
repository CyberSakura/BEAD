import component.JDKMethod;
import component.JDKPackage;
import soot.SootMethod;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class InconsistentAnalyzer {
    static PrintWriter reflectWriter;
    static PrintWriter staticWriter;
    private static String outputReflectFileName;
    private static String outputStaticFileName;

    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();
        long startTime, endTime;
        double reflectDuration, staticDuration, reflectInconsistencyDuration, staticInconsistencyDuration;

        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\ModuleInfo.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\PkgInfo.txt");

            List<String> classFileDirectories = Arrays.asList("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\TestJar\\jide-plaf-3.6.10.jar");

            outputReflectFileName = createReflectFileName(classFileDirectories);
            outputStaticFileName = createStaticFileName(classFileDirectories);
            try{
                reflectWriter = new PrintWriter(new File("Result", outputReflectFileName), "UTF-8");
                staticWriter = new PrintWriter(new File("Result", outputStaticFileName), "UTF-8");
            } catch (Exception e){
                e.printStackTrace();
            }

            System.out.println("Start analyzing");
            System.out.println("Reading class files...");

            System.out.println("--------------------");

            System.out.println("Analyzing reflectively method invoke...");
            startTime = System.nanoTime();
            ReflectTransformer transformer = new ReflectTransformer();
            transformer.initializeAndRun(classFileDirectories);
            endTime = System.nanoTime();
            reflectDuration = (endTime - startTime) / 1e6;
            System.out.println("Analyzing reflectively method invoke done");

            System.out.println("--------------------");

            System.out.println("Analyzing static method invoke...");
            startTime = System.nanoTime();
            StaticCallAnalyzer staticCallAnalyzer = new StaticCallAnalyzer(classFileDirectories);
            Map<SootMethod, Set<SootMethod>> staticCallMap = staticCallAnalyzer.generateCompleteCallGraph();
            endTime = System.nanoTime();
            staticDuration = (endTime - startTime) / 1e6;
            System.out.println("Analyzing static method invoke done");

            System.out.println("--------------------");

            System.out.println("Checking inconsistency...");
            InconsistentAnalyzer inconsistentAnalyzer = new InconsistentAnalyzer();

            System.out.println("--------------------");
            System.out.println("Checking reflective inconsistency...");
            startTime = System.nanoTime();
            boolean reflectiveInconsistency =inconsistentAnalyzer.checkReflectiveInconsistency(combiner, transformer);
            if(!reflectiveInconsistency){
                System.out.println("\nNo reflective inconsistency found");
            }
            endTime = System.nanoTime();
            reflectInconsistencyDuration = (endTime - startTime) / 1e6;
            System.out.println("\nChecking reflective inconsistency done, result has been stored in " + outputReflectFileName);

            System.out.println("--------------------");
            System.out.println("Checking static inconsistency...");
            startTime = System.nanoTime();
            boolean staticInconsistency = inconsistentAnalyzer.checkStaticInconsistency(combiner, staticCallMap);
            if(!staticInconsistency){
                System.out.println("\nNo static inconsistency found");
            }
            endTime = System.nanoTime();
            staticInconsistencyDuration = (endTime - startTime) / 1e6;
            System.out.println("\nChecking static inconsistency done, result has been stored in " + outputStaticFileName);
            System.out.println("--------------------");


            System.out.println("Reflective method invoke analysis duration: " + reflectDuration + " ms");
            System.out.println("Static method invoke analysis duration: " + staticDuration + " ms");
            System.out.println("Reflective inconsistency analysis duration: " + reflectInconsistencyDuration + " ms");
            System.out.println("Static inconsistency analysis duration: " + staticInconsistencyDuration + " ms");
            System.out.println("Inconsistency analysis done");
        } catch (IOException e) {
            e.printStackTrace();
        }

        reflectWriter.close();
        staticWriter.close();
    }

    private static String createReflectFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Reflect_Inconsistency.txt";
    }

    private static String createStaticFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Static_Inconsistency.txt";
    }

    public boolean checkReflectiveInconsistency(JDKDataCombiner combiner, ReflectTransformer transformer) {
        boolean hasInconsistency = false;

        for (String fullMethod : transformer.getFullMethodCounts().keySet()) {
            String normalizedMethod = normalizeClassName(fullMethod);
            if (!normalizedMethod.matches("[\\w.]+\\.[\\w]+\\.[\\w]+\\.[\\w]+")) {
                continue;
            }

            String packageName = normalizedMethod.substring(0, normalizedMethod.lastIndexOf('.', normalizedMethod.lastIndexOf('.') - 1));
            String className = normalizedMethod.substring(normalizedMethod.lastIndexOf('.', normalizedMethod.lastIndexOf('.') - 1) + 1, normalizedMethod.lastIndexOf('.'));
            String methodName = normalizedMethod.substring(normalizedMethod.lastIndexOf('.') + 1);

            if(isJDKClass(normalizedMethod)){
                String retrieveMethod = combiner.findReflectiveInvokedMethod(packageName, className, methodName);
                if (retrieveMethod != null) {
                    JDKPackage pkg = findPackage(combiner, packageName);

                    if(pkg != null){
                        Set<String> accessRules = pkg.getAccessRules();
                        if (accessRules.size() == 1) {
                            if (accessRules.contains("exports")) {
                                if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                    String accessType = pkg.getClass(className).getMethod(retrieveMethod).getAccessType();
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + normalizedMethod + " is " + accessType);
                                    hasInconsistency = true;
                                }
                            } else if (accessRules.contains("opens to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is opened, while " + normalizedMethod + " is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            } else if (accessRules.contains("exports to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        reflectWriter.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is exported, while " + normalizedMethod + " is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            }
                        }else{
                            if (accessRules.contains("exports") && accessRules.contains("opens to")){
                                if(!pkg.getAllowedModules().contains(pkg.getName())){
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is exported and only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }
                }else{
                    if(isJDKClass(normalizedMethod)){
                        JDKPackage pkg = findPackage(combiner, packageName);

                        if(pkg != null){
                            Set<String> accessRules = pkg.getAccessRules();
                            if (accessRules.size() == 1) {
                                if (accessRules.contains("opens to")) {
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else if (accessRules.contains("exports to")) {
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else{
                                    reflectWriter.println("Found inconsistent reflective method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + normalizedMethod + " does not exist in JDK 17");
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }
                }
            }

        }

        return hasInconsistency;
    }

    public boolean checkStaticInconsistency(JDKDataCombiner combiner, Map<SootMethod, Set<SootMethod>> staticCallMap) {
        boolean hasInconsistency = false;

        for (Map.Entry<SootMethod, Set<SootMethod>> entry : staticCallMap.entrySet()) {
            Set<SootMethod> callees = entry.getValue();

            for(SootMethod callee: callees){
                String calleeClass = callee.getDeclaringClass().toString();

                String packageName = calleeClass.substring(0, calleeClass.lastIndexOf('.'));
                String className = calleeClass.substring(calleeClass.lastIndexOf('.') + 1);
                String methodName = callee.getSubSignature().substring(callee.getSubSignature().indexOf(" ") + 1);

                String retrieveMethod = combiner.findStaticInvokedMethod(packageName, className, methodName);
                if (retrieveMethod != null) {
                    JDKPackage pkg = findPackage(combiner, packageName);

                    if(pkg != null){
                        Set<String> accessRules = pkg.getAccessRules();
                        if (accessRules.size() == 1) {
                            if (accessRules.contains("exports")) {
                                if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public") && !pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("default")) {
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + calleeClass + "." + methodName + " is " + pkg.getClass(className).getMethod(retrieveMethod).getAccessType());
                                    hasInconsistency = true;
                                }
                            } else if (accessRules.contains("opens")) {
                                staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but current package " + pkg.getName() + " is only opened");
                                hasInconsistency = true;
                            } else if (accessRules.contains("opens to")) {
                                if(!pkg.getAllowedModules().contains(pkg.getName())) {
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }else{
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because " + pkg.getName() + "is not exported, but the project tries to statically invoke this method");
                                    hasInconsistency = true;
                                }
                            } else if (accessRules.contains("exports to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported, while " + calleeClass + "." + methodName + " is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            }
                        }else{
                            if(accessRules.contains("exports") && accessRules.contains("opens to")){
                                if(!pkg.getAllowedModules().contains(pkg.getName())){
                                    if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                        staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and only opens to " + calleeClass + "but the invoked method is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            }else if(accessRules.contains("exports") && accessRules.contains("opens")){
                                if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and opened, but the invoked method is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }else{
                        staticWriter.println("Found inconsistent static method invoke: " + retrieveMethod + ", because the project tries to statically  invoke this method, but the package " + packageName + " is not declared opened or exported in the module-info.java file, or the method " + retrieveMethod + " doesn't exist anymore in JDK 17");
                        hasInconsistency = true;
                    }
                }else{
                    if(isJDKClass(calleeClass)){
                        JDKPackage pkg = findPackage(combiner, packageName);

                        if(pkg != null){
                            Set<String> accessRules = pkg.getAccessRules();
                            if (accessRules.size() == 1) {
                                if (accessRules.contains("opens")){
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + ", because the project tries to statically invoke this method, but " + calleeClass + " is only opened");
                                    hasInconsistency = true;
                                }else if(accessRules.contains("opens to")) {
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else if (accessRules.contains("exports to")) {
                                    staticWriter.println("Found inconsistent static method invoke: " + calleeClass + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }
                }

            }

        }

        return hasInconsistency;
    }

    private JDKPackage findPackage(JDKDataCombiner combiner, String packageName) {
        return combiner.modules.values().stream()
                .map(module -> module.getPackage(packageName))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isJDKClass(String className) {
        return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.")
                || className.startsWith("sun.") || className.startsWith("com.sun.") || className.startsWith("org.ietf.")
                || className.startsWith("org.w3c.") || className.startsWith("org.xml.") || className.startsWith("netscape.");
    }

    private String normalizeClassName(String binaryName) {
        return binaryName.replace('/', '.').replaceAll("^L|;$", "").replaceAll(";\\.", ".");
    }
}
