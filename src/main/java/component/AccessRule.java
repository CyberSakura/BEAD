package component;

import java.util.HashSet;
import java.util.Set;

public class AccessRule {
    String type;
    HashSet<String> allowedModules;

    public AccessRule(String type, Set<String> allowedModules) {
        this.type = type;
        this.allowedModules = new HashSet<>();
        if (allowedModules != null) {
            this.allowedModules.addAll(allowedModules);
        }
    }

    public String getType() {
        return type;
    }

    public HashSet<String> getAllowedModules() {
        return allowedModules;
    }


    @Override
    public String toString() {
        return "{Type: " + type + ", AllowedModules: " + allowedModules + "}";
    }
}
