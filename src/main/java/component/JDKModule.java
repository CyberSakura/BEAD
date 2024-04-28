package component;
import java.util.HashMap;
import java.util.Map;

public class JDKModule {
    private String name;
    private Map<String, JDKPackage> packages = new HashMap<>();

    public JDKModule(String name) {
        this.name = name;
    }

    public void addPackage(JDKPackage pkg) {
        packages.put(pkg.getName(), pkg);
    }

    public String getName() {
        return name;
    }

    public Map<String, JDKPackage> getPackages() {
        return packages;
    }

    public JDKPackage getPackage(String name) {
        return packages.get(name);
    }
}
