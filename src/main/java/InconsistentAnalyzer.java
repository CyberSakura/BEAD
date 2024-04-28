import component.JDKPackage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class InconsistentAnalyzer {
    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();

        try{
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out1.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out.txt");

            List<String> classFileDirectories = Arrays.asList("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\TestJar\\cglib-3.3.0.jar");
            ReflectTransformer transformer = new ReflectTransformer();
            transformer.initializeAndRun(classFileDirectories);

            InconsistentAnalyzer inconsistentAnalyzer = new InconsistentAnalyzer();
            inconsistentAnalyzer.checkReflectiveInconsistentancy(combiner, transformer);

            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkReflectiveInconsistentancy(JDKDataCombiner combiner, ReflectTransformer transformer){
        for(String fullMethod: transformer.getFullMethodCounts().keySet()){
            String packageName = fullMethod.substring(0, fullMethod.lastIndexOf('.', fullMethod.lastIndexOf('.') - 1));
            String className = fullMethod.substring(fullMethod.lastIndexOf('.', fullMethod.lastIndexOf('.') - 1) + 1, fullMethod.lastIndexOf('.'));
            String methodName = fullMethod.substring(fullMethod.lastIndexOf('.') + 1);

            if(combiner.findMethod(packageName, className, methodName)){
                JDKPackage pkg = combiner.modules.values().stream()
                        .map(module -> module.getPackage(packageName))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                Set<String> accessRules = pkg.getAccessRules();
                if(accessRules.size() == 1) {
                    if (accessRules.contains("exports")) {
                        if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                            System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + fullMethod + " is not public");
                        }
                    } else if (accessRules.contains("opens to")) {
                        if (!pkg.getAllowedModules().contains(pkg.getName())) {
                            System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only opens to " + pkg.getAllowedModules());
                        }
                    } else if (accessRules.contains("exports to")) {
                        if (!pkg.getAllowedModules().contains(pkg.getName())) {
                            System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because the project tries to reflectively invoke this method, but " + pkg.getName() + " only exports to " + pkg.getAllowedModules());
                        } else {
                            if (!pkg.getClass(className).getMethod(methodName).getAccessType().equals("public")) {
                                System.out.println("Found inconsistent reflectively method invoke: " + fullMethod + ", because although " + pkg.getName() + " is exported, while " + fullMethod + " is not public");
                            }
                        }
                    }
                }

            }
        }
    }
}
