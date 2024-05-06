import component.JDKMethod;
import component.JDKPackage;
import soot.SootMethod;

import java.io.IOException;
import java.util.*;

public class InconsistentAnalyzer {
    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();

        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\PkgInfo.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\ModuleInfo.txt");

            List<String> classFileDirectories = Arrays.asList("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\TestJar\\amidst-v4-6.jar");

            System.out.println("Start analyzing");
            System.out.println("Reading class files...");

            System.out.println("--------------------");

            System.out.println("Analyzing reflectively method invoke...");
            ReflectTransformer transformer = new ReflectTransformer();
            transformer.initializeAndRun(classFileDirectories);
            System.out.println("Analyzing reflectively method invoke done");

            System.out.println("--------------------");

            System.out.println("Analyzing static method invoke...");
            StaticCallAnalyzer staticCallAnalyzer = new StaticCallAnalyzer(classFileDirectories);
            Map<SootMethod, SootMethod> staticCallMap = staticCallAnalyzer.generateCompleteCallGraph();
            System.out.println("Analyzing static method invoke done");

            System.out.println("--------------------");

            System.out.println("Checking inconsistency...");
            InconsistentAnalyzer inconsistentAnalyzer = new InconsistentAnalyzer();

            System.out.println("--------------------");
            System.out.println("Checking reflective inconsistency...");
            boolean reflectiveInconsistency =inconsistentAnalyzer.checkReflectiveInconsistency(combiner, transformer);
            if(!reflectiveInconsistency){
                System.out.println("No reflective inconsistency found");
            }
            System.out.println("\nChecking reflective inconsistency done");

            System.out.println("--------------------");
            System.out.println("Checking static inconsistency...");
            boolean staticInconsistency = inconsistentAnalyzer.checkStaticInconsistency(combiner, staticCallMap);
            if(!staticInconsistency){
                System.out.println("No static inconsistency found");
            }
            System.out.println("\nChecking static inconsistency done");
            System.out.println("--------------------");

            System.out.println("Inconsistency analysis done");
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                                    System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + normalizedMethod + " is " + accessType);
                                    hasInconsistency = true;
                                }
                            } else if (accessRules.contains("opens to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is opened, while " + normalizedMethod + " is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            } else if (accessRules.contains("exports to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                } else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is exported, while " + normalizedMethod + " is not public");
                                        hasInconsistency = true;
                                    }
                                }
                            }
                        }else{
                            if (accessRules.contains("exports") && accessRules.contains("opens to")){
                                if(!pkg.getAllowedModules().contains(pkg.getName())){
                                    System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because although " + pkg.getName() + " is exported and only opens to " + pkg.getAllowedModules());
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }
                }else{
                    System.out.println("Found inconsistent reflectively method invoke: " + normalizedMethod + ", because the project tries to reflectively invoke this method, but the method " + normalizedMethod + " doesn't exist anymore in JDK 17");
                    hasInconsistency = true;
                }
            }

        }

        return hasInconsistency;
    }

    public boolean checkStaticInconsistency(JDKDataCombiner combiner, Map<SootMethod, SootMethod> staticCallMap) {
        boolean hasInconsistency = false;

        for (SootMethod caller : staticCallMap.keySet()) {
            SootMethod callee = staticCallMap.get(caller);
            String calleeClass = callee.getDeclaringClass().toString();

            String packageName = calleeClass.substring(0, calleeClass.lastIndexOf('.'));
            String className = calleeClass.substring(calleeClass.lastIndexOf('.') + 1);
            String methodName = staticCallMap.get(caller).getSubSignature().substring(staticCallMap.get(caller).getSubSignature().indexOf(" ") + 1);

            String retrieveMethod = combiner.findStaticInvokedMethod(packageName, className, methodName);
            if (retrieveMethod != null) {
                JDKPackage pkg = findPackage(combiner, packageName);

                if(pkg != null){
                    Set<String> accessRules = pkg.getAccessRules();
                    if (accessRules.size() == 1) {
                        if (accessRules.contains("exports")) {
                            if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public") && !pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("default")) {
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + calleeClass + "." + methodName + " is " + pkg.getClass(className).getMethod(retrieveMethod).getAccessType());
                                hasInconsistency = true;
                            }
                        } else if (accessRules.contains("opens")) {
                            System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but current package " + pkg.getName() + " is only opened");
                            hasInconsistency = true;
                        } else if (accessRules.contains("opens to")) {
                            if(!pkg.getAllowedModules().contains(pkg.getName())) {
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                hasInconsistency = true;
                            }else{
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because " + pkg.getName() + "is not exported, but the project tries to statically invoke this method");
                                hasInconsistency = true;
                            }
                        } else if (accessRules.contains("exports to")) {
                            if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                hasInconsistency = true;
                            } else {
                                if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                    System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported, while " + calleeClass + "." + methodName + " is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }else{
                        if(accessRules.contains("exports") && accessRules.contains("opens to")){
                            if(!pkg.getAllowedModules().contains(pkg.getName())){
                                if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                    System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and only opens to " + calleeClass + "but the invoked method is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }else if(accessRules.contains("exports") && accessRules.contains("opens")){
                            if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and opened, but the invoked method is not public");
                                hasInconsistency = true;
                            }
                        }
                    }
                }else{
                    System.out.println("Found inconsistent reflectively method invoke: " + retrieveMethod + ", because the project tries to reflectively invoke this method, but the package " + packageName + " is not declared opened or exported in the module-info.java file, or the method " + retrieveMethod + " doesn't exist anymore in JDK 17");
                    hasInconsistency = true;
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
