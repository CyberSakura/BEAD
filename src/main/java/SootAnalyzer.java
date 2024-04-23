import soot.*;
import soot.jimple.JimpleBody;
import soot.options.Options;
import soot.util.Chain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SootAnalyzer {
    private static String[] paths;

    public static String sourceDirectory = System.getProperty("user.dir") + File.separator + "target" + File.separator + "classes";
    public static String clsName = "ReflectionExample";
    public static String methodName = "method1";

    public static void setupSoot() {
        G.reset();
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_soot_classpath(sourceDirectory);
        Options.v().set_whole_program(true);

        List<String> processDirs = new ArrayList<String>();
        for (String path : paths) {
            processDirs.add(path);
        }

        Scene.v().addBasicClass(clsName, SootClass.SIGNATURES);
        Options.v().set_process_dir(processDirs);
        Scene.v().loadNecessaryClasses();

        Chain<SootClass> classes = Scene.v().getClasses();
        for (SootClass cls : classes) {
            System.out.println("Loaded class: " + cls.getName());
        }
    }
    public static void main(String[] args) {
        // specify the class file to analyze
        List<String> pathsList = new ArrayList<>();
        File inputFile = new File("class_file_directory_list.txt");
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String path;
            while ((path = reader.readLine()) != null) {
                pathsList.add(path);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        paths = pathsList.toArray(new String[0]);
        setupSoot();

        SootClass mainClass = Scene.v().getSootClass(clsName);
        SootMethod sm = mainClass.getMethodByName(methodName);
        JimpleBody body = (JimpleBody) sm.retrieveActiveBody();

        // Print some information about printFizzBuzz
        System.out.println("Method Signature: " + sm.getSignature());

    }
}
