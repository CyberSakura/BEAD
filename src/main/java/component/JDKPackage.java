package component;

import java.util.*;
public class JDKPackage {
    private String name;
    private Set<String> accessRules = new HashSet<>();
    private List<String> allowedModules = new ArrayList<>();
    private Map<String, JDKClass> classes = new HashMap<>();

    public JDKPackage(String name) {
        this.name = name;
    }

    public void addAllowedModule(String moduleName) {
        allowedModules.add(moduleName);
    }

    public void addClass(JDKClass cls) {
        classes.put(cls.getName(), cls);
    }

    public void addAccessRule(String rule) {
        accessRules.add(rule);
    }

    public String getName() {
        return name;
    }

    public Set<String> getAccessRules() {
        return accessRules;
    }

    public List<String> getAllowedModules() {
        return allowedModules;
    }

    public Map<String, JDKClass> getClasses() {
        return classes;
    }
}
