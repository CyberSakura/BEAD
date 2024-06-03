import component.JDKPackage;
import soot.SootMethod;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

public class AbuseAnalyzer {
    static PrintWriter reflectWriter;
    static PrintWriter compileTimeWriter;
    private static String outputReflectFileName;
    private static String outputCompileTimeFileName;

    public static void main(String[] args) {
        String userDir = System.getProperty("user.dir");
        JDKDataCombiner combiner = new JDKDataCombiner();
        long startTime, endTime;
        double reflectDuration, compileTimeDuration, reflectAbuseDuration, compileTimeAbuseDuration;
        String moduleInfoPath = Paths.get(userDir, "ModuleInfo.txt").toString();
        String pkgInfoPath = Paths.get(userDir, "PkgInfo.txt").toString();
        String classFileDir = Paths.get(userDir, "TestJar", "error_prone_check_api-2.5.1.jar").toString();    // Modify this line to the path of the input jar file directory
                                                                                                                    //  The input Jar File should be placed in the TestJar folder

        try {
            combiner.parseModuleInfoFile(moduleInfoPath);
            combiner.parsePkgInfoFile(pkgInfoPath);

            List<String> classFileDirectories = Arrays.asList(classFileDir);

            outputReflectFileName = createReflectFileName(classFileDirectories);
            outputCompileTimeFileName = createCompileTimeFileName(classFileDirectories);
            try{
                reflectWriter = new PrintWriter(new File("Result", outputReflectFileName), "UTF-8");
                compileTimeWriter = new PrintWriter(new File("Result", outputCompileTimeFileName), "UTF-8");
            } catch (Exception e){
                e.printStackTrace();
            }

            System.out.println("Start analyzing");
            System.out.println("Reading class files...");

            System.out.println("--------------------");

            System.out.println("Analyzing reflectively method invoke...");
            startTime = System.nanoTime();
            ReflectionAnalyzer reflectionAnalyzer = new ReflectionAnalyzer();
            reflectionAnalyzer.initializeAndRun(classFileDirectories);
            endTime = System.nanoTime();
            reflectDuration = (endTime - startTime) / 1e6;
            System.out.println("Analyzing reflectively method invoke done");

            System.out.println("--------------------");

            System.out.println("Analyzing compile-time method invoke...");
            startTime = System.nanoTime();
            CompileTimeAnalyzer compileTimeAnalyzer = new CompileTimeAnalyzer(classFileDirectories);
            Map<SootMethod, Set<SootMethod>> compileTimeCallMap = compileTimeAnalyzer.generateCompleteCallGraph();
            endTime = System.nanoTime();
            compileTimeDuration = (endTime - startTime) / 1e6;
            System.out.println("Analyzing compile-time method invoke done");

            System.out.println("--------------------");

            System.out.println("Checking abuses...");
            AbuseAnalyzer abuseAnalyzer = new AbuseAnalyzer();

            System.out.println("--------------------");
            System.out.println("Checking reflective abuses...");
            startTime = System.nanoTime();
            boolean reflectiveAbuse = abuseAnalyzer.checkReflectiveAbuse(combiner, reflectionAnalyzer);
            if(!reflectiveAbuse){
                System.out.println("\nNo reflective abuses found");
            }
            endTime = System.nanoTime();
            reflectAbuseDuration = (endTime - startTime) / 1e6;
            System.out.println("\nChecking reflective abuses done, result has been stored in " + outputReflectFileName);

            System.out.println("--------------------");
            System.out.println("Checking compile-time abuses...");
            startTime = System.nanoTime();
            boolean compileTimeAbuse = abuseAnalyzer.checkCompileTimeAbuse(combiner, compileTimeCallMap);
            if(!compileTimeAbuse){
                System.out.println("\nNo compile-time abuses found");
            }
            endTime = System.nanoTime();
            compileTimeAbuseDuration = (endTime - startTime) / 1e6;
            System.out.println("\nChecking compile-time abuses done, result has been stored in " + outputCompileTimeFileName);
            System.out.println("--------------------");


            System.out.println("Reflective method invoke analysis duration: " + reflectDuration + " ms");
            System.out.println("Compile-time method invoke analysis duration: " + compileTimeDuration + " ms");
            System.out.println("Reflective abuse analysis duration: " + reflectAbuseDuration + " ms");
            System.out.println("Compile-time abuse analysis duration: " + compileTimeAbuseDuration + " ms");
            System.out.println("Inconsistency analysis done");
        } catch (IOException e) {
            e.printStackTrace();
        }

        reflectWriter.close();
        compileTimeWriter.close();
    }

    private static String createReflectFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Reflect_Abuse.txt";
    }

    private static String createCompileTimeFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Compile_Time_Abuse.txt";
    }

    public boolean checkReflectiveAbuse(JDKDataCombiner combiner, ReflectionAnalyzer transformer) {
        boolean hasInconsistency = false;
        int reflectAbuseCount = 0;

        for (SootMethod sourceMethod : transformer.getFullMethodCounts().keySet()) {
            String sourceMethodClass = sourceMethod.getDeclaringClass().toString();
            String sourceMethodSignature = sourceMethod.getSignature();
            Map<String, Integer> methods = transformer.getFullMethodCounts().get(sourceMethod);
            if(methods != null){
                for (String fullMethod : methods.keySet()) {
                    String normalizedMethod = normalizeClassName(fullMethod);
                    if (!normalizedMethod.matches("[\\w.]+\\.[\\w]+\\.[\\w]+\\.[\\w]+")) {
                        continue;
                    }

                    String packageName = normalizedMethod.substring(0, normalizedMethod.lastIndexOf('.', normalizedMethod.lastIndexOf('.') - 1));
                    String className = normalizedMethod.substring(normalizedMethod.lastIndexOf('.', normalizedMethod.lastIndexOf('.') - 1) + 1, normalizedMethod.lastIndexOf('.'));
                    String methodName = normalizedMethod.substring(normalizedMethod.lastIndexOf('.') + 1);

                    if (isJDKClass(normalizedMethod)) {
                        String retrieveMethod = combiner.findReflectiveInvokedMethod(packageName, className, methodName);
                        if (retrieveMethod != null) {
                            JDKPackage pkg = findPackage(combiner, packageName);
                            String currentModule = combiner.returnCurrentModule(packageName);

                            if (pkg != null) {
                                Set<String> accessRules = pkg.getAccessRules();
                                if (accessRules.size() == 1) {
                                    if (accessRules.contains("exports")) {
                                        if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                            String accessType = pkg.getClass(className).getMethod(retrieveMethod).getAccessType();
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + normalizedMethod + " is " + accessType);
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        }
                                    } else if (accessRules.contains("opens to")) {
                                        if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + packageName + " only opens to " + pkg.getAllowedModules());
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        } else {
                                            if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                                reflectWriter.println("Detected abuse under module " + currentModule);
                                                reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                                reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                                reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, and although " + pkg.getName() + " is opened, but " + normalizedMethod + " is not public");
                                                reflectWriter.println("-------------------------------------------------");
                                                hasInconsistency = true;
                                                reflectAbuseCount++;
                                            }
                                        }
                                    } else if (accessRules.contains("exports to")) {
                                        if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + packageName + " only exports to " + pkg.getAllowedModules());
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        } else {
                                            if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                                reflectWriter.println("Detected abuse under module " + currentModule);
                                                reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                                reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                                reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, and although " + pkg.getName() + " is exported, but " + normalizedMethod + " is not public");
                                                reflectWriter.println("-------------------------------------------------");
                                                hasInconsistency = true;
                                                reflectAbuseCount++;
                                            }
                                        }
                                    }
                                } else {
                                    if (accessRules.contains("exports") && accessRules.contains("opens to")) {
                                        if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + packageName + " is exported and only exports to " + pkg.getAllowedModules());
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isJDKClass(normalizedMethod)) {
                                JDKPackage pkg = findPackage(combiner, packageName);
                                String currentModule = combiner.returnCurrentModule(packageName);

                                if (pkg != null) {
                                    Set<String> accessRules = pkg.getAccessRules();
                                    if (accessRules.size() == 1) {
                                        if (accessRules.contains("opens to")) {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + packageName + " only opens to " + pkg.getAllowedModules());
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        } else if (accessRules.contains("exports to")) {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + packageName + " only exports to " + pkg.getAllowedModules());
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        } else {
                                            reflectWriter.println("Detected abuse under module " + currentModule);
                                            reflectWriter.println("Source method: " + sourceMethodSignature +" from class: " + sourceMethodClass);
                                            reflectWriter.println("Involved Method: " + normalizedMethod + " in target class: " + className + " from package " + packageName);
                                            reflectWriter.println("Abuse Reason: The project tries to reflectively invoke this method, but " + normalizedMethod + " does not exist in current JDK version");
                                            reflectWriter.println("-------------------------------------------------");
                                            hasInconsistency = true;
                                            reflectAbuseCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        reflectWriter.println("Total Reflective Abuses: " + reflectAbuseCount);
        return hasInconsistency;
    }

    public boolean checkCompileTimeAbuse(JDKDataCombiner combiner, Map<SootMethod, Set<SootMethod>> compileTimeCallMap) {
        boolean hasInconsistency = false;
        int compileTimeAbuseCount = 0;

        for (Map.Entry<SootMethod, Set<SootMethod>> entry : compileTimeCallMap.entrySet()) {
            Set<SootMethod> callees = entry.getValue();

            for(SootMethod callee: callees){
                String calleeClass = callee.getDeclaringClass().toString();

                String packageName = calleeClass.substring(0, calleeClass.lastIndexOf('.'));
                String className = calleeClass.substring(calleeClass.lastIndexOf('.') + 1);
                String methodName = callee.getSubSignature().substring(callee.getSubSignature().indexOf(" ") + 1);

                String retrieveMethod = combiner.findCompileTimeInvokedMethod(packageName, className, methodName);
                String currentModule = combiner.returnCurrentModule(packageName);
                if (retrieveMethod != null) {
                    JDKPackage pkg = findPackage(combiner, packageName);

                    if(pkg != null){
                        Set<String> accessRules = pkg.getAccessRules();
                        if (accessRules.size() == 1) {
                            if (accessRules.contains("exports")) {
                                if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public") && !pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("default")) {
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time,  but " + calleeClass + "." + methodName + " is " + pkg.getClass(className).getMethod(retrieveMethod).getAccessType());
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }
                            } else if (accessRules.contains("opens")) {
                                compileTimeWriter.println("Detected abuse under module " + currentModule);
                                compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + packageName + " is only opened");
                                compileTimeWriter.println("-------------------------------------------------");
                                hasInconsistency = true;
                                compileTimeAbuseCount++;
                            } else if (accessRules.contains("opens to")) {
                                if(!pkg.getAllowedModules().contains(pkg.getName())) {
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + packageName + " only opens to " + pkg.getAllowedModules());
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }else{
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, because " + pkg.getName() + "is not exported, but the project tries to invoke this method during compile time");
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }
                            } else if (accessRules.contains("exports to")) {
                                if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time,  but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                } else {
                                    if (!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")) {
                                        compileTimeWriter.println("Detected abuse under module " + currentModule);
                                        compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                        compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but although " + pkg.getName() + " is exported, while " + calleeClass + "." + methodName + " is not public");
                                        hasInconsistency = true;
                                        compileTimeAbuseCount++;
                                    }
                                }
                            }
                        }else{
                            if(accessRules.contains("exports") && accessRules.contains("opens to")){
                                if(!pkg.getAllowedModules().contains(pkg.getName())){
                                    if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                        compileTimeWriter.println("Detected abuse under module " + currentModule);
                                        compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                        compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + packageName + " only opens to " + pkg.getAllowedModules() + " and the invoked method is not public");
                                        compileTimeWriter.println("-------------------------------------------------");
                                        hasInconsistency = true;
                                        compileTimeAbuseCount++;
                                    }
                                }
                            }else if(accessRules.contains("exports") && accessRules.contains("opens")){
                                if(!pkg.getClass(className).getMethod(retrieveMethod).getAccessType().equals("public")){
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + packageName + " is only exported and opened, but the invoked method is not public");
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }
                            }
                        }
                    }else{
                        compileTimeWriter.println("Detected abuse under module " + currentModule);
                        compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                        compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + retrieveMethod + " at compile time, but " + packageName + " is not declared opened or exported in the module-info.java file");
                        compileTimeWriter.println("-------------------------------------------------");
                        hasInconsistency = true;
                        compileTimeAbuseCount++;
                    }
                }else{
                    if(isJDKClass(calleeClass)){
                        JDKPackage pkg = findPackage(combiner, packageName);

                        if(pkg != null){
                            Set<String> accessRules = pkg.getAccessRules();
                            if (accessRules.size() == 1) {
                                if (accessRules.contains("opens")){
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + calleeClass + " is only opened");
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }else if(accessRules.contains("opens to")) {
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + calleeClass + " only opens to " + pkg.getAllowedModules());
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                } else if (accessRules.contains("exports to")) {
                                    compileTimeWriter.println("Detected abuse under module " + currentModule);
                                    compileTimeWriter.println("Involved Source Method: " + entry.getKey().getSignature() + "; Involved Target Method: " +  methodName + " in target class: " + calleeClass + " from package " + packageName);
                                    compileTimeWriter.println("Abuse Reason: The project tries to invoke target method " + methodName + " at compile time, but " + calleeClass + " only exports to " + pkg.getAllowedModules());
                                    compileTimeWriter.println("-------------------------------------------------");
                                    hasInconsistency = true;
                                    compileTimeAbuseCount++;
                                }
                            }
                        }
                    }
                }

            }

        }

        compileTimeWriter.println("Total Compile-Time Abuses: " + compileTimeAbuseCount);
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
