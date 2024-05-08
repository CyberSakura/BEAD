import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class StaticCallAnalyzer {
    private static List<String> paths = new ArrayList<>();
    private String outputFileName;
    private int staticInvokeCount = 0;

//    public static void main(String[] args) {
//        String jarPath = "C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\TestJar\\cglib-3.3.0.jar";
//        setupSoot(jarPath);
//        Map<SootMethod, SootMethod> callMap = generateCompleteCallGraph();
//        callMap.forEach((source, target) -> System.out.println(source + " => " + target));
//    }

    public StaticCallAnalyzer(List<String> classPaths){
        paths.addAll(classPaths);
        outputFileName = createFileName(classPaths);
        for(String path: paths){
            setupSoot(path);
        }
    }

    private static void setupSoot(String jarPath) {
        G.reset();

        Options.v().set_prepend_classpath(true);
        Options.v().set_verbose(true);
        Options.v().set_whole_program(true);
        Options.v().set_app(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_exclude(Collections.singletonList("java.*"));
        Options.v().set_process_dir(Collections.singletonList(jarPath));
        Options.v().set_src_prec(Options.src_prec_class);

        Scene.v().loadNecessaryClasses();
        System.out.println("Loaded necessary classes for " + jarPath);
    }

    private String createFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Static_Invoke.txt";
    }

    public Map<SootMethod, Set<SootMethod>> generateCompleteCallGraph() {
        List<SootMethod> entryPoints = new ArrayList<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (sm.isPublic() && sm.isConcrete()) {
                    entryPoints.add(sm);
                }
            }
        }
        Scene.v().setEntryPoints(entryPoints);
        PackManager.v().runPacks();

        Map<SootMethod, Set<SootMethod>> callMap = new HashMap<>();
        CallGraph cg = Scene.v().getCallGraph();
        try (PrintWriter writer = new PrintWriter(new File("Result", outputFileName), "UTF-8")) {
            writer.println("All Static Calls invokes JDK:");
            for (Edge e : cg) {

                if (e.getSrc() == null || e.getSrc().method() == null) {
                    continue;
                }

                SootMethod srcMethod = e.getSrc().method();
                SootMethod tgtMethod = e.tgt();

                if (tgtMethod != null && tgtMethod.isStatic() && isJDKClass(tgtMethod.getDeclaringClass().toString())) {
                    if (!isJDKClass(srcMethod.getDeclaringClass().toString())) {
                        writer.println("Found static invoke: " + srcMethod + " => " + tgtMethod);
                        callMap.computeIfAbsent(srcMethod, k -> new HashSet<>()).add(tgtMethod);
                        staticInvokeCount++;
                    }
                }

            }

            writer.println("\nTotal static calls: " + staticInvokeCount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Static call invoke result has stored in " + outputFileName);
        return callMap;
    }

    private boolean isJDKClass(String className) {
        return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.")
                || className.startsWith("sun.") || className.startsWith("com.sun.") || className.startsWith("org.ietf.")
                || className.startsWith("org.w3c.") || className.startsWith("org.xml.") || className.startsWith("netscape.");
    }
}
