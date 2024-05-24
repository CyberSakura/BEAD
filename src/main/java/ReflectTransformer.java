import component.MethodConstants;
import component.Utils;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.Chain;

import java.io.*;
import java.util.*;

public class ReflectTransformer extends SceneTransformer {
    private static final String CLASS_FILE_LIST = "class_file_directory_list.txt";
    private static List<String> paths = new ArrayList<>();
    private static String outputFileName;
    static PrintWriter writer;
    private static int totalReflectInvokeCount = 0;
    private static int nonStringConstantMethodNameCount = 0;
    private static int methodNameWithoutClassNameCount = 0;
    private static int fullMethodNameCount = 0;
    private static Map<SootMethod, Map<String, Integer>> fullMethodCounts = new LinkedHashMap<>();
    private static Map<String, Integer> partMethodCounts = new LinkedHashMap<>();

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader(CLASS_FILE_LIST))) {
            String path;
            while ((path = reader.readLine()) != null) {
                paths.add(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        outputFileName = createFileName(paths);
        try{
            writer = new PrintWriter(new File("Result", outputFileName), "UTF-8");
        } catch (Exception e){
            e.printStackTrace();
        }

        ReflectTransformer transformer = new ReflectTransformer();

        transformer.run();
        displayResults();

        System.out.println("Reflective Analysis result written to " + outputFileName);

        writer.close();
    }

    private static String createFileName(List<String> classPaths) {
        return classPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('\\') + 1).replace(".jar", ""))
                .reduce("", (acc, name) -> acc + name + "_") + "Reflect_Invoke.txt";
    }


    public void run() {
        Options.v().set_whole_program(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_debug(true);
        Options.v().no_writeout_body_releasing();

        PackManager.v().getPack("wjtp")
                .add(new Transform("wjtp.ru", this));
        PackManager.v().getPack("wjtp").apply();
    }

    public void initializeAndRun(List<String> classPaths) {
        paths.addAll(classPaths);
        outputFileName = createFileName(paths);
        try{
            writer = new PrintWriter(new File("Result", outputFileName), "UTF-8");
        } catch (Exception e){
            e.printStackTrace();
        }

        System.out.println("Start analysis from: " + paths);
        run();
        displayResults();

        System.out.println("Reflective Analysis result written to " + outputFileName);

        writer.close();
    }

    private static void displayResults() {
        if(writer != null){
            writer.println("Reflection Analysis Results:");
            writer.println("Total Reflective Invocations: " + totalReflectInvokeCount);
            writer.println("Total Non-String Constant Method Names: " + nonStringConstantMethodNameCount);
            writer.println("Total Method Names without Class Name: " + methodNameWithoutClassNameCount);
            writer.println("Total Full Method Names: " + fullMethodNameCount);

            if(!fullMethodCounts.isEmpty()){
                writer.println("Full Method Invoke:");
                fullMethodCounts.forEach((declaringClass, methods) -> {
                    writer.println("Class " + declaringClass + " invokes:");
                    methods.forEach((method, count) -> writer.println("\tMethod " + method + ": " + count));
                });
            }

            if(!partMethodCounts.isEmpty()){
                writer.println("Partial Method Invoke:");
                partMethodCounts.forEach((method, count) -> writer.println("Method " + method + ": " + count));
            }
        }
    }

    public Map<SootMethod, Map<String,Integer>> getFullMethodCounts() {
        return fullMethodCounts;
    }

//    public Map<String, Integer> getPartMethodCounts() {
//        return partMethodCounts;
//    }

    @Override
    protected void internalTransform(String s, Map<String, String> map) {
        Options.v().set_process_dir(new ArrayList<>(paths));
        Scene.v().loadNecessaryClasses();

        Set<SootMethod> appMethods = Utils.getApplicationMethods();
        if(writer != null){
            writer.println("Total application methods running in reflection analyzing: " + appMethods.size());
            for (SootMethod method : appMethods) {
                if (Utils.isApplicationMethod(method)) {
                    if (method.isConcrete()) {
                        Body body = method.retrieveActiveBody();
                        PackManager.v().getPack("jtp").apply(body);
                        if (Options.v().validate()) {
                            body.validate();
                        }
                    }

                    if (method.hasActiveBody()) {
                        doAnalysisOnMethod(method);
                    }

                    if (Thread.interrupted()) {
                        {
                            try {
                                throw new InterruptedException();
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void doAnalysisOnMethod(SootMethod method) {
        int methodReflectInvokeCount = 0;
        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        UnitGraph graph = new BriefUnitGraph(body);
        SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);

        for (Unit unit : units) {
            if (unit instanceof JAssignStmt) {
                JAssignStmt assignStmt = (JAssignStmt) unit;
                Value rightOp = assignStmt.getRightOp();
                if (rightOp instanceof VirtualInvokeExpr) {
                    VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr) rightOp;
                    methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, localDefs, assignStmt, invokeExpr);
                }
            } else if (unit instanceof JInvokeStmt) {
                JInvokeStmt invokeStmt = (JInvokeStmt) unit;
                InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
                if (checkForReflectInvocation(invokeExpr)) {
                    if (invokeStmt.getInvokeExpr() instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeStmt.getInvokeExpr();
                        methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, localDefs, invokeStmt, virtualInvokeExpr);
                    }
                }
            }
        }

        totalReflectInvokeCount += methodReflectInvokeCount;
    }

    public int identifyReflectiveCall(SootMethod method, int methodReflectInvokeCount, SimpleLocalDefs defs, Stmt inStmt, VirtualInvokeExpr invokeExpr) {
        if (checkForReflectInvocation(invokeExpr)) {
            Hierarchy hierarchy = Scene.v().getActiveHierarchy();
            SootClass classLoaderClass = null;

            Chain<SootClass> libraryClasses = Scene.v().getLibraryClasses();
            for (SootClass libraryClass : libraryClasses) {
                if (libraryClass.getName().equals("java.lang.ClassLoader")) {
                    classLoaderClass = libraryClass;
                    break;
                }
            }

            methodReflectInvokeCount++;
//            System.out.println(method.getSignature() + " has reflective invocation: " + invokeExpr.getMethod().getDeclaringClass() + "." + invokeExpr.getMethod().getName());
            if (invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.reflect.Method")) {
                if (!(invokeExpr.getBase() instanceof Local)) {
                    writer.println("Method invocation is not on a local variable");
                    return methodReflectInvokeCount;
                }

                Local base = (Local) invokeExpr.getBase();
                List<Unit> defsOfAt = defs.getDefsOfAt(base, inStmt);
                for (Unit defUnit : defsOfAt) {
                    if (defUnit instanceof JAssignStmt) {
                        JAssignStmt assignStmt = (JAssignStmt) defUnit;
                        Value rightOp = assignStmt.getRightOp();
                        if (rightOp instanceof VirtualInvokeExpr) {
                            VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) rightOp;
                            if (virtualInvokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && MethodConstants.reflectiveGetMethodsSet.contains(virtualInvokeExpr.getMethod().getName())) {
                                boolean result = handleReflectiveGetMethods(virtualInvokeExpr, assignStmt, defs, inStmt, hierarchy, classLoaderClass, method);
                                if (!result) {
                                    continue;
                                }
                            }

                        }
                    }

                }
            }
        }

        return methodReflectInvokeCount;
    }

    private boolean handleReflectiveGetMethods(VirtualInvokeExpr getDeclaredMethodExpr, JAssignStmt methodAssignStmt, SimpleLocalDefs defs, Stmt inStmt,
                                               Hierarchy hierarchy, SootClass classLoaderClass, SootMethod method) {
        if (!(getDeclaredMethodExpr.getArg(0) instanceof StringConstant)) {
            writer.println("First argument of getDeclaredMethod is not a string constant");
            nonStringConstantMethodNameCount++;
            return false;
        }

        StringConstant reflectedMethodName = (StringConstant) getDeclaredMethodExpr.getArg(0);
        if (!(getDeclaredMethodExpr.getBase() instanceof Local)) {
            writer.println("Reflective invocation receives a non-local method name at " + methodAssignStmt);
            return false;
        }

        Local classLocal = (Local) getDeclaredMethodExpr.getBase();
        List<Unit> classDefs = defs.getDefsOfAt(classLocal, methodAssignStmt);
        boolean foundClassName = false;
        for (Unit classDef : classDefs) {
            if (!(classDef instanceof JAssignStmt)) {
                return false;
            }
            JAssignStmt classAssignStmt = (JAssignStmt) classDef;
            Value rightOp = classAssignStmt.getRightOp();
            if (rightOp instanceof InvokeExpr) {
                InvokeExpr invokeExpr = (InvokeExpr) rightOp;
                if (invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && invokeExpr.getMethod().getName().equals("forName")) {
                    if (invokeExpr.getArg(0) instanceof StringConstant) {
                        StringConstant classNameConst = (StringConstant) invokeExpr.getArg(0);
                        String fullMethodName = classNameConst.value + "." + reflectedMethodName.value;
                        writer.println("\twith class: " + classNameConst.value);
                        writer.println("\tFound reflective invocation of " + fullMethodName);
                        writer.println("Class " + method.getDeclaringClass() + " invokes " + classNameConst.value);
                        foundClassName = true;
                        incrementFullMethodCounts(method, fullMethodName);
                        fullMethodNameCount++;
                    }
                } else if (classLoaderClass != null) {
                    if (hierarchy.isClassSubclassOfIncluding(invokeExpr.getMethod().getDeclaringClass(), classLoaderClass)) {
                        if (invokeExpr.getMethod().getName().equals("loadClass")) {
                            if (invokeExpr.getArg(0) instanceof StringConstant) {
                                StringConstant classNameConst = (StringConstant) invokeExpr.getArg(0);
                                String fullMethodName = classNameConst.value + "." + reflectedMethodName.value;
                                writer.println("\twith class: " + classNameConst.value);
                                writer.println("\tFound reflective invocation of " + fullMethodName);
                                writer.println("Class " + method.getDeclaringClass() + " invokes " + classNameConst.value);
                                foundClassName = true;
                                incrementFullMethodCounts(method, fullMethodName);
                                fullMethodNameCount++;
                            }
                        }
                    }
                } else if (invokeExpr.getMethod().getName().equals("getClass")) {
                    if (invokeExpr instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                        if (virtualInvokeExpr.getBase() instanceof Local) {
                            Local base = (Local) virtualInvokeExpr.getBase();
                            for (Unit defUnit : defs.getDefsOfAt(base, classAssignStmt)) {
                                if (defUnit instanceof JAssignStmt) {
                                    JAssignStmt baseAssignStmt = (JAssignStmt) defUnit;
                                    Value rightOpBase = baseAssignStmt.getRightOp();
                                    if (rightOpBase instanceof InvokeExpr) {
                                        InvokeExpr baseInvokeExpr = (InvokeExpr) rightOpBase;
                                        String fullMethodName = baseInvokeExpr.getMethod().getReturnType() + "." + reflectedMethodName.value;
                                        writer.println("\tFound reflective invocation of " + fullMethodName);

                                        foundClassName = true;
                                        incrementFullMethodCounts(method, fullMethodName);
                                        fullMethodNameCount++;
                                    } else if (rightOpBase instanceof FieldRef) {
                                        FieldRef fieldRef = (FieldRef) rightOpBase;
                                        String fullMethodName = fieldRef.getField().getType() + "." + reflectedMethodName.value;
                                        writer.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(method, fullMethodName);
                                        fullMethodNameCount++;
                                    }
                                }
                            }
                        }
                        if (!foundClassName) {
                            foundClassName = true;
                            writer.println("\tCould not find class name for the following reflectively invoked method: " + reflectedMethodName.value);
                            incrementPartMethodCounts(reflectedMethodName.value);
                            methodNameWithoutClassNameCount++;
                        }
                    }
                }

            } else if (rightOp instanceof ClassConstant) {
                ClassConstant classConstant = (ClassConstant) rightOp;
                String fullMethodName = classConstant.getValue() + "." + reflectedMethodName.value;
                writer.println("\tFound reflective invocation of " + fullMethodName);
                writer.println("\tClass " + method.getDeclaringClass() + " invokes " + classConstant.getValue());
                foundClassName = true;
                incrementFullMethodCounts(method, fullMethodName);
                fullMethodNameCount++;
            }
        }

        if (!foundClassName) {
            writer.println("Could not find class name for reflective invocation at " + methodAssignStmt);
            incrementPartMethodCounts(reflectedMethodName.value);
            methodNameWithoutClassNameCount++;
        }

        return true;
    }

    private boolean checkForReflectInvocation(InvokeExpr invokeExpr) {
        return invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith("java.lang.reflect")
                ||
                invokeExpr.getMethod().getDeclaringClass().getName().startsWith("java.lang.Class")
                ||
                invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith("java.lang.invoke");
    }

    private void incrementFullMethodCounts(SootMethod method, String fullMethodName) {
        Map<String, Integer> methodCounts = fullMethodCounts.getOrDefault(method, new HashMap<>());
        int count = methodCounts.getOrDefault(fullMethodName, 0);
        count++;
        methodCounts.put(fullMethodName, count);
        fullMethodCounts.put(method, methodCounts);
    }

    private void incrementPartMethodCounts(String methodNameOnly) {
        Integer count = null;
        if (partMethodCounts.containsKey(methodNameOnly)) {
            count = partMethodCounts.get(methodNameOnly);
        } else {
            count = 0;
        }
        count++;
        partMethodCounts.put(methodNameOnly, count);

    }


}
