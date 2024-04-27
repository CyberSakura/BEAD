package component;

import java.util.HashSet;
import java.util.Set;

public class AccessRule {
    Set<String> types;
    HashSet<String> allowedModules;

    public AccessRule() {
        this.types = new HashSet<>();
        this.allowedModules = new HashSet<>();
    }

    public void addRule(String type, Set<String> allowedModules) {
        this.types.add(type);
        if (allowedModules != null) {
            this.allowedModules.addAll(allowedModules);
        }
    }

    public Set<String> getTypes() {
        return types;
    }

    public HashSet<String> getAllowedModules() {
        return allowedModules;
    }

    @Override
    public String toString() {
        return "{Types: " + types + ", AllowedModules: " + allowedModules + "}";
    }
}
