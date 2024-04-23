package edu.uci.seal;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;



import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.ClassConstant;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.options.Options;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;

public class ReflectTransformer extends SceneTransformer {
    static PrintWriter writer;
    static PrintWriter writerClassReflection;


    static {
        try {
            String projectName="soot_test";
            String logPath = "log_"+projectName+".txt";
            String logClassPath= "log_"+projectName+"_class_reflection.txt";
            writer = new PrintWriter(new File(logPath));
            writerClassReflection= new PrintWriter(new File(logClassPath));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static String[] paths;
    private static int totalReflectInvokeCount = 0;
    private static int nonStringConstantMethodNameCount = 0;
    private static int methodNameWithoutClassNameCount = 0;
    private static int fullMethodNameCount = 0;
    private static Map<String,Integer> fullMethodCounts = new LinkedHashMap<String,Integer>();
    private static Map<String,Integer> partMethodCounts = new LinkedHashMap<String,Integer>();

    public ReflectTransformer(String[] paths) throws FileNotFoundException {
        this.paths=paths;
    }

    public static void main(String[] args) throws FileNotFoundException {
        StopWatch allPhaseStopWatch = new StopWatch();
        allPhaseStopWatch.start();

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

        ReflectTransformer transformer = new ReflectTransformer(paths);

        StopWatch singlePhaseStopWatch = new StopWatch();
        singlePhaseStopWatch.start();
        transformer.run();
        singlePhaseStopWatch.stop();

        Map<String,Integer> reflectFeatures = new LinkedHashMap<String,Integer>();

        String RIC = "reflect_invoke_count";
        String NCMC = "nonstring_constant_method_count";
        String MNCC = "method_no_class_count";
        String FMC = "full_method_count";
        writer.println("RU analysis time (milliseconds):" + singlePhaseStopWatch.getElapsedTime());

        writer.println("Total reflection API invocations: " + totalReflectInvokeCount);
        writer.println(RIC + ": " + totalReflectInvokeCount);
        reflectFeatures.put(RIC,totalReflectInvokeCount);

        writer.println(NCMC + ": " + nonStringConstantMethodNameCount);
        reflectFeatures.put(NCMC,nonStringConstantMethodNameCount);

        writer.println(MNCC + ": " + methodNameWithoutClassNameCount);
        reflectFeatures.put(MNCC,methodNameWithoutClassNameCount);

        writer.println(FMC + ": " + fullMethodNameCount);
        reflectFeatures.put(FMC,fullMethodNameCount);

        writer.println("Full method counts:");
        for (String methodName : fullMethodCounts.keySet()) {
            writer.println("\t" + methodName + ": " + fullMethodCounts.get(methodName));
            reflectFeatures.put(methodName,fullMethodCounts.get(methodName));
        }
        System.out.println("Partial method counts:");
        for (String methodName : partMethodCounts.keySet()) {
            writer.println("\t" + methodName + ": " + partMethodCounts.get(methodName));
            reflectFeatures.put(methodName,partMethodCounts.get(methodName));
        }


        allPhaseStopWatch.stop();
        System.out.println("total runtime for all phases (milliseconds):" + allPhaseStopWatch.getElapsedTime());
        System.out.println("Reached end of RU main...");
        writer.close();
        writerClassReflection.close();
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Options.v().set_whole_program(true);
        Options.v().set_debug(true);
        Options.v().set_allow_phantom_refs(true);
//        Options.v().set_src_prec(Options.src_prec_only_class);

        List<String> processDirs = new ArrayList<String>();
        for (String path : paths) {
            processDirs.add(path);
        }

        Options.v().set_process_dir(processDirs);
        Options.v().no_writeout_body_releasing();
        Scene.v().loadNecessaryClasses();

        Set<SootMethod> methods = Utils.getApplicationMethods();

        int currMethodCount = 0;
        writer.println("total number of possible methods to analyze: " + methods.size());
        for (SootMethod method : methods) {
            if (Utils.isApplicationMethod(method)) {
                if (method.isConcrete()) {
                    Body body = method.retrieveActiveBody();
                    PackManager.v().getPack("jtp").apply(body);
                    if( Options.v().validate() ) {
                        body.validate();
                    }
                }
                if (method.hasActiveBody()) {
                    doAnalysisOnMethod(method);
                }

                if (Thread.interrupted()) {
                    try {
                        throw new InterruptedException();
                    } catch (InterruptedException e) {
                        return;
                    }
                }

            }
            currMethodCount++;
        }
    }

    private void doAnalysisOnMethod(SootMethod method) {
            int methodReflectInvokeCount = 0;
            Body body = method.getActiveBody();
            PatchingChain<Unit> units = body.getUnits();
            UnitGraph unitGraph = new BriefUnitGraph(body);
            SimpleLocalDefs defs = new SimpleLocalDefs(unitGraph);

            for (Unit unit : units) {
                if (unit instanceof JAssignStmt) {
                    JAssignStmt assignStmt = (JAssignStmt)unit;
                    if (assignStmt.getRightOp() instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr invokeExpr = (VirtualInvokeExpr)assignStmt.getRightOp();
                         methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, assignStmt, invokeExpr);
                    }
                }
                else if (unit instanceof JInvokeStmt) {
                    JInvokeStmt invokeStmt = (JInvokeStmt)unit;
                    if ( checkForReflectInvocation(invokeStmt.getInvokeExpr()) ) {
                        if (invokeStmt.getInvokeExpr() instanceof VirtualInvokeExpr) {
                            VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr)invokeStmt.getInvokeExpr();
                            methodReflectInvokeCount = identifyReflectiveCall(method, methodReflectInvokeCount, defs, invokeStmt, virtualInvokeExpr);
                        }
                    }
                }
            }


        totalReflectInvokeCount += methodReflectInvokeCount;
    }

    public int identifyReflectiveCall(SootMethod method, int methodReflectInvokeCount, SimpleLocalDefs defs, Stmt inStmt, VirtualInvokeExpr invokeExpr) {
        if ( checkForReflectInvocation(invokeExpr) ) {
            Hierarchy hierarchy = Scene.v().getActiveHierarchy();
            SootClass classLoaderClass = Utils.getLibraryClass("java.lang.ClassLoader");
            methodReflectInvokeCount++;
            writer.println(method.getSignature() + " reflectively invokes "  + invokeExpr.getMethod().getDeclaringClass() + "." + invokeExpr.getMethod().getName());
            if (invokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.reflect.Method")) {
                if (!(invokeExpr.getBase() instanceof Local)) {
                    writer.println("\tThis reflection API usage invocation has a callee of a non-local class");
                    return methodReflectInvokeCount;
                }
                Local invokeExprLocal = (Local) invokeExpr.getBase();
                List<Unit> defUnits = defs.getDefsOfAt(invokeExprLocal, inStmt);
                for (Unit defUnit : defUnits) {
                    if (!(defUnit instanceof JAssignStmt)) {
                        continue;
                    }
                    JAssignStmt methodAssignStmt = (JAssignStmt) defUnit;
                    if (methodAssignStmt.getRightOp() instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr getDeclaredMethodExpr = (VirtualInvokeExpr)methodAssignStmt.getRightOp();
                        if (getDeclaredMethodExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && MethodConstants.reflectiveGetMethodsSet.contains(getDeclaredMethodExpr.getMethod().getName())) {
                            boolean result = handleReflectiveGetMethods(getDeclaredMethodExpr, methodAssignStmt, defs, inStmt, hierarchy, classLoaderClass,method);
                            if (!result) {
                                continue;
                            }
                        }
                    }
                    for (ValueBox useBox : methodAssignStmt.getUseBoxes()) {
                        if (useBox.getValue() instanceof FieldRef) {
                            writer.println("\t\t" + useBox + " is instanceof FieldRef");
                            FieldRef fieldRef = (FieldRef)useBox.getValue();
                            for (Tag tag : fieldRef.getField().getTags()) {
                                writer.println("\t\t\ttag: " + tag);
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
            writer.println("Reflective invocation is not a string constant at " + methodAssignStmt);
            nonStringConstantMethodNameCount++;
            return false;
        }
        StringConstant reflectivelyInvokedMethodName = (StringConstant)getDeclaredMethodExpr.getArg(0);
        writer.println( "Found the following method invoked reflectively: " + reflectivelyInvokedMethodName);
        if (!(getDeclaredMethodExpr.getBase() instanceof Local)) {
            writer.println("Reflective invocation receives a non-local method name at " + methodAssignStmt);
            return false;
        }
        Local classLocal = (Local)getDeclaredMethodExpr.getBase();
        List<Unit> classDefUnits = defs.getDefsOfAt(classLocal,inStmt);
        boolean foundClassName = false;
        for (Unit classDefUnit : classDefUnits) {
            if (!(classDefUnit instanceof JAssignStmt)) {
                return false;
            }
            JAssignStmt classAssignStmt = (JAssignStmt) classDefUnit;
            if (classAssignStmt.getRightOp() instanceof InvokeExpr) {
                InvokeExpr classInvokeExpr = (InvokeExpr) classAssignStmt.getRightOp();
                if (classInvokeExpr.getMethod().getDeclaringClass().getName().equals("java.lang.Class") && classInvokeExpr.getMethod().getName().equals("forName")) {
                    if (classInvokeExpr.getArg(0) instanceof StringConstant) {
                        StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
                        String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
                        writer.println("\twith class: " + classNameConst.value);
                        writer.println("\tFound reflective invocation of " + fullMethodName);
                        writerClassReflection.println("Class "+method.getDeclaringClass() + " invokes "+classNameConst.value);
//                        writerClassReflection.println("\twith class: " + classNameConst.value);
//                        writerClassReflection.println("\tFound reflective invocation of " + fullMethodName);
                        foundClassName = true;
                        incrementFullMethodCounts(fullMethodName);
                        fullMethodNameCount++;
                    }
                } else if (classLoaderClass != null) {
                    if (hierarchy.isClassSubclassOfIncluding(classInvokeExpr.getMethod().getDeclaringClass(), classLoaderClass)) {
                        if (classInvokeExpr.getMethod().getName().equals("loadClass")) {
                            if (classInvokeExpr.getArg(0) instanceof StringConstant) {
                                StringConstant classNameConst = (StringConstant) classInvokeExpr.getArg(0);
                                String fullMethodName = classNameConst.value + "." + reflectivelyInvokedMethodName.value;
                                writer.println("\twith class: " + classNameConst.value);
                                writer.println("\tFound reflective invocation of " + fullMethodName);
                                writerClassReflection.println("Class "+method.getDeclaringClass() + " invokes "+classNameConst.value);
//                                writerClassReflection.println("\twith class: " + classNameConst.value);
//                                writerClassReflection.println("\tFound reflective invocation of " + fullMethodName);
                                foundClassName = true;
                                incrementFullMethodCounts(fullMethodName);
                                fullMethodNameCount++;
                            }
                        }
                    }
                } else if (classInvokeExpr.getMethod().getName().equals("getClass")) {
                    if (classInvokeExpr instanceof VirtualInvokeExpr) {
                        VirtualInvokeExpr classVirtualInvokeExpr = (VirtualInvokeExpr) classInvokeExpr;
                        if (classVirtualInvokeExpr.getBase() instanceof Local) {
                            Local objLocal = (Local) classVirtualInvokeExpr.getBase();
                            for (Unit objDefUnit : defs.getDefsOfAt(objLocal, classAssignStmt)) {
                                if (objDefUnit instanceof JAssignStmt) {
                                    JAssignStmt objAssignStmt = (JAssignStmt) objDefUnit;
                                    if (objAssignStmt.getRightOp() instanceof InvokeExpr) {
                                        InvokeExpr objInvokeExpr = (InvokeExpr) objAssignStmt.getRightOp();
                                        String fullMethodName = objInvokeExpr.getMethod().getReturnType() + "." + reflectivelyInvokedMethodName.value;
                                        writer.println("\tFound reflective invocation of " + fullMethodName);

                                        writerClassReflection.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    } else if (objAssignStmt.getRightOp() instanceof FieldRef) {
                                        FieldRef fieldRef = (FieldRef)objAssignStmt.getRightOp();
                                        String fullMethodName = fieldRef.getField().getType() + "." + reflectivelyInvokedMethodName.value;
                                        writer.println("\tFound reflective invocation of " + fullMethodName);
                                        writerClassReflection.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    }
                                }
                            }
                        }
                        if (!foundClassName) {
                            foundClassName = true;
                            writer.println("\tCould not find class name for the following reflectively invoked method: " + reflectivelyInvokedMethodName.value);
                            incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
                            methodNameWithoutClassNameCount++;
                        }
                    }
                }
            } else if (classAssignStmt.getRightOp() instanceof ClassConstant) {
                ClassConstant classConstant = (ClassConstant)classAssignStmt.getRightOp();
                String fullMethodName = classConstant.getValue() + "." + reflectivelyInvokedMethodName.value;
                writer.println("\tFound reflective invocation of " + fullMethodName);
//              writerClassReflection.println("\tFound reflective invocation of " + fullMethodName);
                writerClassReflection.println("Class "+method.getDeclaringClass() + " invokes "+classConstant.getValue());
                foundClassName = true;
                incrementFullMethodCounts(fullMethodName);
                fullMethodNameCount++;
            }
        }
        if (!foundClassName) {
            writer.println("\tCould not find class name to match reflective invocation of method: " + reflectivelyInvokedMethodName);
            incrementPartMethodCounts(reflectivelyInvokedMethodName.value);
            methodNameWithoutClassNameCount++;
        }
        return true;
    }

    private void incrementFullMethodCounts(String fullMethodName) {
        Integer count = null;
        if (fullMethodCounts.containsKey(fullMethodName)) {
            count = fullMethodCounts.get(fullMethodName);
        } else {
            count = 0;
        }
        count++;
        fullMethodCounts.put(fullMethodName, count);

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

    private boolean checkForReflectInvocation(InvokeExpr invokeExpr) {
        String string = invokeExpr.getMethod().getDeclaringClass().getPackageName();
        return invokeExpr.getMethod().getDeclaringClass().getPackageName().startsWith("java.lang.reflect")
                ||
                invokeExpr.getMethod().getDeclaringClass().getName().startsWith("java.lang.Class");
    }

    public void run() {
        Options.v().set_whole_program(true);
        Options.v().set_output_format(Options.v().output_format_none);

        PackManager.v().getPack("wjtp")
                   .add(new Transform("wjtp.ru", this));
        PackManager.v().getPack("wjtp").apply();
    }

}
