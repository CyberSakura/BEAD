package component;

public class JDKMethod {
    private String name;
    private String accessType;

    public JDKMethod(String name, String accessType) {
        this.name = name;
        this.accessType = accessType;
    }

    public String getName() {
        return name;
    }

    public String getAccessType() {
        return accessType;
    }
}
