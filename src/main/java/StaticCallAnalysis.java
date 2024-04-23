import component.Utils;
import soot.*;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class StaticCallAnalysis extends SceneTransformer{
    private static final String CLASS_FILE_LIST = "class_file_directory_list.txt";
    private static List<String> paths = new ArrayList<>();
    
    
    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader(CLASS_FILE_LIST))) {
            String path;
            while ((path = reader.readLine()) != null) {
                paths.add(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        StaticCallAnalysis staticCallAnalysis = new StaticCallAnalysis();

        staticCallAnalysis.run();
    }

    public void run() {
        Options.v().set_whole_program(true);
        Options.v().set_debug(true);
        Options.v().set_allow_phantom_refs(true);

        Options.v().set_output_format(Options.output_format_none);
        Options.v().no_writeout_body_releasing();

        PackManager.v().getPack("wjtp")
                .add(new Transform("wjtp.ru", this));
        PackManager.v().getPack("wjtp").apply();
        PackManager.v().runPacks();
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map) {


        Options.v().set_process_dir(new ArrayList<>(paths));
        Scene.v().loadNecessaryClasses();

        CallGraph cg = null;
        try {
            cg = Scene.v().getCallGraph();
        } catch (RuntimeException e) {
            System.err.println("Call graph is not available: " + e.getMessage());
            return;
        }

        Chain<SootClass> appClasses = Scene.v().getApplicationClasses();
        List<SootMethod> appMethods = new ArrayList<>();
        for (SootClass clazz : appClasses) {
            appMethods.addAll( clazz.getMethods() );
        }

        Scene.v().setEntryPoints(appMethods);

        System.out.println("Total application methods: " + appMethods.size());

    }
}

