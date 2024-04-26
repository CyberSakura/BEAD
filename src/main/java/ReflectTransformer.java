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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class ReflectTransformer extends SceneTransformer {
    private static final String CLASS_FILE_LIST = "class_file_directory_list.txt";
    private static List<String> paths = new ArrayList<>();
    private static int totalReflectInvokeCount = 0;
    private static int nonStringConstantMethodNameCount = 0;
    private static int methodNameWithoutClassNameCount = 0;
    private static int fullMethodNameCount = 0;
    private static Map<String, Integer> fullMethodCounts = new LinkedHashMap<>();
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

        ReflectTransformer transformer = new ReflectTransformer();

        transformer.run();

        Map<String, Integer> reflectFeatures = new LinkedHashMap<String, Integer>();

        reflectFeatures.put("Total Reflect Invoke Count", totalReflectInvokeCount);
        reflectFeatures.put("Non String Constant Method Name Count", nonStringConstantMethodNameCount);
        reflectFeatures.put("Method Name Without Class Name Count", methodNameWithoutClassNameCount);
        reflectFeatures.put("Full Method Name Count", fullMethodNameCount);

        displayResults();
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

    private static void displayResults() {
        System.out.println("Reflection Analysis Results:");
        System.out.println("Total Reflective Invocations: " + totalReflectInvokeCount);
        fullMethodCounts.forEach((method, count) -> System.out.println("Method " + method + ": " + count));
        partMethodCounts.forEach((method, count) -> System.out.println("Method " + method + ": " + count));
    }

    @Override
    protected void internalTransform(String s, Map<String, String> map) {
        Options.v().set_process_dir(new ArrayList<>(paths));
        Scene.v().loadNecessaryClasses();

        Set<SootMethod> appMethods = Utils.getApplicationMethods();

        System.out.println("Total application methods: " + appMethods.size());
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
                    System.out.println("Method invocation is not on a local variable");
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
                        for (ValueBox box : assignStmt.getUseBoxes()) {
                            if (box.getValue() instanceof FieldRef) {
                                System.out.println("\t\tField reference found: " + box.getValue().toString());
                                FieldRef fieldRef = (FieldRef) box.getValue();
                                for (Tag tag : fieldRef.getField().getTags()) {
                                    System.out.println("\t\t\tTag: " + tag.toString());
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
            System.out.println("First argument of getDeclaredMethod is not a string constant");
            nonStringConstantMethodNameCount++;
            return false;
        }

        StringConstant reflectedMethodName = (StringConstant) getDeclaredMethodExpr.getArg(0);
        if (!(getDeclaredMethodExpr.getBase() instanceof Local)) {
            System.out.println("Reflective invocation receives a non-local method name at " + methodAssignStmt);
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
                        System.out.println("\twith class: " + classNameConst.value);
                        System.out.println("\tFound reflective invocation of " + fullMethodName);
                        System.out.println("Class " + method.getDeclaringClass() + " invokes " + classNameConst.value);
                        foundClassName = true;
                        incrementFullMethodCounts(fullMethodName);
                        fullMethodNameCount++;
                    }
                } else if (classLoaderClass != null) {
                    if (hierarchy.isClassSubclassOfIncluding(invokeExpr.getMethod().getDeclaringClass(), classLoaderClass)) {
                        if (invokeExpr.getMethod().getName().equals("loadClass")) {
                            if (invokeExpr.getArg(0) instanceof StringConstant) {
                                StringConstant classNameConst = (StringConstant) invokeExpr.getArg(0);
                                String fullMethodName = classNameConst.value + "." + reflectedMethodName.value;
                                System.out.println("\twith class: " + classNameConst.value);
                                System.out.println("\tFound reflective invocation of " + fullMethodName);
                                System.out.println("Class " + method.getDeclaringClass() + " invokes " + classNameConst.value);
                                foundClassName = true;
                                incrementFullMethodCounts(fullMethodName);
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
                                        System.out.println("\tFound reflective invocation of " + fullMethodName);

                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    } else if (rightOpBase instanceof FieldRef) {
                                        FieldRef fieldRef = (FieldRef) rightOpBase;
                                        String fullMethodName = fieldRef.getField().getType() + "." + reflectedMethodName.value;
                                        System.out.println("\tFound reflective invocation of " + fullMethodName);
                                        foundClassName = true;
                                        incrementFullMethodCounts(fullMethodName);
                                        fullMethodNameCount++;
                                    }
                                }
                            }
                        }
                        if (!foundClassName) {
                            foundClassName = true;
                            System.out.println("\tCould not find class name for the following reflectively invoked method: " + reflectedMethodName.value);
                            incrementPartMethodCounts(reflectedMethodName.value);
                            methodNameWithoutClassNameCount++;
                        }
                    }
                }

            } else if (rightOp instanceof ClassConstant) {
                ClassConstant classConstant = (ClassConstant) rightOp;
                String fullMethodName = classConstant.getValue() + "." + reflectedMethodName.value;
                System.out.println("\tFound reflective invocation of " + fullMethodName);
                System.out.println("\tClass " + method.getDeclaringClass() + " invokes " + classConstant.getValue());
                foundClassName = true;
                incrementFullMethodCounts(fullMethodName);
                fullMethodNameCount++;
            }
        }

        if (!foundClassName) {
            System.out.println("Could not find class name for reflective invocation at " + methodAssignStmt);
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


}
