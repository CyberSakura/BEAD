import java.lang.reflect.*;

public class ReflectionExample {

    public void method1() {
        System.out.println("This is method1.");
    }

    private void method2() {
        System.out.println("This is method2.");
    }

    public static void main(String[] args) {
        try {

            Class<?> cls = ReflectionExample.class;

            System.out.println("Class Name: " + cls.getName());
            System.out.println("Class Simple Name: " + cls.getSimpleName());
            System.out.println("Class Package: " + cls.getPackage());
            System.out.println("Class Modifiers: " + Modifier.toString(cls.getModifiers()));
            System.out.println("Class Superclass: " + cls.getSuperclass().getName());
            System.out.println("Class Interfaces: ");
            for (Class<?> c : cls.getInterfaces()) {
                System.out.println("\t" + c.getName());
            }

            Method[] methods = cls.getDeclaredMethods();
            System.out.println("Class Methods: ");
            for (Method method : methods) {
                System.out.println("\t" + method.getName());
            }

            Method method1 = cls.getMethod("method1");
            Method method2 = cls.getDeclaredMethod("method2");
            method2.setAccessible(true);

            Object obj = cls.newInstance();
            method1.invoke(obj);
            method2.invoke(obj);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}