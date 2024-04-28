package component;
import java.util.HashMap;
import java.util.Map;

public class JDKClass {
    private String name;
    private Map<String, JDKMethod> methods = new HashMap<>();

    public JDKClass(String name) {
        this.name = name;
    }

    public void addMethod(JDKMethod method) {
        methods.put(method.getName(), method);
    }

    public String getName() {
        return name;
    }

    public Map<String, JDKMethod> getMethods() {
        return methods;
    }

    public JDKMethod getMethod(String name) {
        return methods.get(name);
    }
}
