package component;
import java.util.HashMap;
import java.util.Set;

public class ModuleAccessRecorder {
    private HashMap<String, HashMap<String, AccessRule>> accessRules;

    public ModuleAccessRecorder() {
        this.accessRules = new HashMap<>();
    }

    public void addAccessRule(String moduleName, String packageName, String ruleType, Set<String> allowedModules) {
        accessRules.putIfAbsent(moduleName, new HashMap<>());
        HashMap<String, AccessRule> packageAccess = accessRules.get(moduleName);

        packageAccess.put(packageName, new AccessRule(ruleType, allowedModules));
    }

    public HashMap<String, HashMap<String, AccessRule>> getAccessRules() {
        return accessRules;
    }
}
