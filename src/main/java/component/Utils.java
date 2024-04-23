package component;

import java.util.LinkedHashSet;
import java.util.Set;


import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import soot.util.Chain;

public class Utils {
    public static boolean isApplicationMethod(SootMethod method) {
        Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
        for (SootClass appClass : applicationClasses) {
            if (appClass.getMethods().contains(method)) {
                return true;
            }
        }
        return false;
    }

    public static Set<SootMethod> getApplicationMethods() {
        Chain<SootClass> appClasses = Scene.v().getApplicationClasses();
        Set<SootMethod> appMethods = new LinkedHashSet<SootMethod>();
        for (SootClass clazz : appClasses) {
            appMethods.addAll( clazz.getMethods() );
        }
        return appMethods;
    }

}
