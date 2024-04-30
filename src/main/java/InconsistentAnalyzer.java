import component.JDKPackage;
import soot.SootMethod;

import java.io.IOException;
import java.util.*;

public class InconsistentAnalyzer {
    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();

        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out1.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out.txt");

            List<String> classFileDirectories = Arrays.asList("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\TestJar\\cglib-3.3.0.jar");

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
            inconsistentAnalyzer.checkReflectiveInconsistency(combiner, transformer);
            if(inconsistentAnalyzer.checkReflectiveInconsistency(combiner, transformer)){
                System.out.println("Found reflective inconsistency");
            } else {
                System.out.println("No reflective inconsistency found");
            }
            System.out.println("Checking reflective inconsistency done");

            System.out.println("--------------------");
            System.out.println("Checking static inconsistency...");
            inconsistentAnalyzer.checkStaticInconsistency(combiner, staticCallMap);
            if(inconsistentAnalyzer.checkStaticInconsistency(combiner, staticCallMap)){
                System.out.println("Found static inconsistency");
            } else {
                System.out.println("No static inconsistency found");
            }
            System.out.println("Checking static inconsistency done");

            System.out.println("\nChecking inconsistency done");


            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public boolean checkReflectiveInconsistency(JDKDataCombiner combiner, ReflectTransformer transformer) {
        boolean hasInconsistency = false;

        for (String fullMethod : transformer.getFullMethodCounts().keySet()) {
            String packageName = fullMethod.substring(0, fullMethod.lastIndexOf('.', fullMethod.lastIndexOf('.') - 1));
            String className = fullMethod.substring(fullMethod.lastIndexOf('.', fullMethod.lastIndexOf('.') - 1) + 1, fullMethod.lastIndexOf('.'));
            String methodName = fullMethod.substring(fullMethod.lastIndexOf('.') + 1);

            if (combiner.findMethod(packageName, className, methodName)) {
                JDKPackage pkg = combiner.modules.values().stream()
                        .map(module -> module.getPackage(packageName))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                if(pkg != null){
                    Set<String> accessRules = pkg.getAccessRules();
                    if (accessRules.size() == 1) {
                        if (accessRules.contains("exports")) {
                            if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + fullMethod + " is not public");
                                hasInconsistency = true;
                            }
                        } else if (accessRules.contains("opens to")) {
                            if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                                hasInconsistency = true;
                            }else {
                                if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                    System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because although " + pkg.getName() + " is opened, while " + fullMethod + " is not public");
                                    hasInconsistency = true;
                                }
                            }
                        } else if (accessRules.contains("exports to")) {
                            if (!pkg.getAllowedModules().contains(pkg.getName())) {
                                System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                                hasInconsistency = true;
                            } else {
                                if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                    System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because although " + pkg.getName() + " is exported, while " + fullMethod + " is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }else{
                        if (accessRules.contains("exports") && accessRules.contains("opens to")){
                            if(!pkg.getAllowedModules().contains(pkg.getName())){
                                System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because although " + pkg.getName() + " is exported and only opens to " + pkg.getAllowedModules());
                                hasInconsistency = true;
                            }
                        }
                    }
                }
            }
        }

        return hasInconsistency;
    }

    public boolean checkStaticInconsistency(JDKDataCombiner combiner, Map<SootMethod, SootMethod> staticCallMap) {
        boolean hasInconsistency = false;

        for (SootMethod caller : staticCallMap.keySet()) {
            String calleeClass = staticCallMap.get(caller).getDeclaringClass().toString();

            String packageName = calleeClass.substring(0, calleeClass.lastIndexOf('.'));
            String className = calleeClass.substring(calleeClass.lastIndexOf('.') + 1);
            String methodName = staticCallMap.get(caller).getName();

            if (combiner.findMethod(packageName, className, methodName)) {
                JDKPackage pkg = combiner.modules.values().stream()
                        .map(module -> module.getPackage(packageName))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                if(pkg != null){
                    Set<String> accessRules = pkg.getAccessRules();
                    if (accessRules.size() == 1) {
                        if (accessRules.contains("exports")) {
                            if(!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because the project tries to statically invoke this method, but " + calleeClass + "." + methodName + " is not public");
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
                                if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                    System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported, while " + calleeClass + "." + methodName + " is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }
                    }else{
                        if(accessRules.contains("exports") && accessRules.contains("opens to")){
                            if(!pkg.getAllowedModules().contains(pkg.getName())){
                                if(!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")){
                                    System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and only opens to " + calleeClass + "but the invoked method is not public");
                                    hasInconsistency = true;
                                }
                            }
                        }else if(accessRules.contains("exports") && accessRules.contains("opens")){
                            if(!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")){
                                System.out.println("Found inconsistent static method invoke: " + calleeClass + "." + methodName + ", because although " + pkg.getName() + " is exported and opened, but the invoked method is not public");
                                hasInconsistency = true;
                            }
                        }
                    }
                }
            }
        }

        return hasInconsistency;
    }
}
