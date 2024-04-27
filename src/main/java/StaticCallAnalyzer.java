import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StaticCallAnalyzer {

    public static void main(String[] args) {
        String jarPath = "E:\\Thesis\\AbuseDetection\\TestJar\\guava-33.1.0-jre.jar";
        setupSoot(jarPath);
        generateCompleteCallGraph();
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
        System.out.println("Loaded necessary classes");
    }

    private static void generateCompleteCallGraph() {
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

        CallGraph cg = Scene.v().getCallGraph();
        try (PrintWriter writer = new PrintWriter("output.txt", "UTF-8")) {
            writer.println("All Static Calls invokes JDK:");
            for (Edge e : cg) {
                SootMethod targetMethod = e.tgt();
                if (targetMethod != null && targetMethod.isStatic()) {  // Check for null to prevent NullPointerException
                    if ((!e.getSrc().method().getDeclaringClass().toString().startsWith("jdk") && !e.getSrc().method().getDeclaringClass().toString().startsWith("java"))) {
                        writer.println(e.getSrc().method() + " => " + targetMethod);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
