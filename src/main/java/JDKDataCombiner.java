import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class JDKDataCombiner {

    Map<String, JDKModule> modules = new HashMap<>();

    public void parseModuleInfoFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            JDKModule currentModule = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Module:")) {
                    String moduleName = line.substring(7).trim();
                    currentModule = new JDKModule(moduleName);
                    modules.put(moduleName, currentModule);
                } else if (line.contains("Package:")) {
                    String[] parts = line.split("->");
                    String packageName = parts[0].substring(10).trim();
                    String accessInfo = parts[1].trim();
                    String[] accessParts = accessInfo.split(",");
                    JDKPackage pkg = new JDKPackage(packageName);
                    pkg.accessRule = accessParts[0].substring(7).trim();
                    if (accessParts.length > 1) {
                        pkg.allowedModules = Arrays.asList(accessParts[1].trim().split("\\[")[1].split("\\]")[0].split(","));
                    }
                    currentModule.addPackage(pkg);
                }
            }
        }
    }

    public void parsePkgInfoFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            JDKPackage currentPackage = null;
            JDKClass currentClass = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Package:")) {
                    String packageName = line.substring(8).trim();
                    for (JDKModule module : modules.values()) {
                        if (module.packages.containsKey(packageName)) {
                            currentPackage = module.packages.get(packageName);
                            break;
                        }
                    }
                } else if (line.contains("Class:")) {
                    currentClass = new JDKClass(line.substring(8).trim());
                    currentPackage.addClass(currentClass);
                } else if (line.contains("Method:")) {
                    String[] parts = line.substring(11).trim().split(",");
                    String methodName = parts[0].trim();
                    String accessType = (parts.length > 1 && parts[1].length() > "Access: ".length())
                            ? parts[1].substring("Access: ".length()).trim()
                            : "default";

                    JDKMethod method = new JDKMethod(methodName, accessType);
                    currentClass.addMethod(method);
                }
            }
        }
    }

    public void printData() {
        File outputFile = new File("data.txt");
        try (PrintWriter out = new PrintWriter(outputFile)) {
            for (JDKModule mod : modules.values()) {
                out.println("Module " + mod.name);
                for (JDKPackage pkg : mod.packages.values()) {
                    out.print("----Package " + pkg.name + ", AccessRule: " + pkg.accessRule);
                    if (!pkg.allowedModules.isEmpty() && !pkg.allowedModules.get(0).isEmpty()) {
                        out.print(", Allowed Modules: " + String.join(", ", pkg.allowedModules));
                    }
                    out.println();  // Finish the line after printing the package details
                    for (JDKClass cls : pkg.classes.values()) {
                        out.println("       ---- Class " + cls.name);
                        for (JDKMethod meth : cls.methods.values()) {
                            out.println("               ---- Method " + meth.name + ", AccessType: " + meth.accessType);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error: Unable to create or write to the file 'data.txt'.");
            e.printStackTrace();
        }
    }

//    public void printDataIntoExcel(){
//        XSSFWorkbook workbook = new XSSFWorkbook();
//        XSSFSheet sheet = workbook.createSheet("Module Data");
//
//        int rowNum = 0;
//        for (JDKModule mod : modules.values()) {
//            XSSFRow row = sheet.createRow(rowNum++);
//            row.createCell(0).setCellValue("Module " + mod.name);
//
//            for (JDKPackage pkg : mod.packages.values()) {
//                row = sheet.createRow(rowNum++);
//                row.createCell(1).setCellValue("Package " + pkg.name + ", AccessRule: " + pkg.accessRule);
//
//                if (!pkg.allowedModules.isEmpty() && !pkg.allowedModules.get(0).isEmpty()) {
//                    row.createCell(2).setCellValue("Allowed Modules: " + String.join(", ", pkg.allowedModules));
//                }
//
//                for (JDKClass cls : pkg.classes.values()) {
//                    row = sheet.createRow(rowNum++);
//                    row.createCell(2).setCellValue("Class " + cls.name);
//
//                    for (JDKMethod meth : cls.methods.values()) {
//                        row = sheet.createRow(rowNum++);
//                        row.createCell(3).setCellValue("Method " + meth.name + ", AccessType: " + meth.accessType);
//                    }
//                }
//            }
//        }
//
//        try (FileOutputStream outputStream = new FileOutputStream("ModuleData.xlsx")) {
//            workbook.write(outputStream);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                workbook.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }


    public static void main(String[] args) {
        JDKDataCombiner combiner = new JDKDataCombiner();
        try {
            combiner.parseModuleInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out1.txt");
            combiner.parsePkgInfoFile("C:\\Users\\cyb19\\IdeaProjects\\AbuseDetection\\out.txt");
            combiner.printData();
//            combiner.printDataIntoExcel();
            System.out.println("Done");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class JDKModule {
    String name;
    Map<String, JDKPackage> packages = new HashMap<>();

    public JDKModule(String name) {
        this.name = name;
    }

    void addPackage(JDKPackage pkg) {
        packages.put(pkg.name, pkg);
    }
}

class JDKPackage {
    String name;
    String accessRule;
    List<String> allowedModules = new ArrayList<>();
    Map<String, JDKClass> classes = new HashMap<>();

    public JDKPackage(String name) {
        this.name = name;
    }

    void addClass(JDKClass cls) {
        classes.put(cls.name, cls);
    }
}

class JDKClass {
    String name;
    Map<String, JDKMethod> methods = new HashMap<>();

    public JDKClass(String name) {
        this.name = name;
    }

    void addMethod(JDKMethod method) {
        methods.put(method.name, method);
    }
}

class JDKMethod {
    String name;
    String accessType;

    public JDKMethod(String name, String accessType) {
        this.name = name;
        this.accessType = accessType;
    }
}
