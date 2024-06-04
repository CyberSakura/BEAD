# BEAD

BEAD (Breaking Encapsulation Abuse Detection) is an automatic detection tool leveraging static analysis to identify encapsulation abuse instances in Java projects. 
It scans JDK source code to extract module details and analyzes Java programs to extract abuse information from reflection and compile-time invocations. 
BEAD reports abuse instances based on previous combined information, thus helping developers address Break Strong Encapsulation (BSE) issues.

## Prerequisites

- The jar package of the program used to analyze
- JDK version 9+
- The path of local JDK source code zip file & jmods file directory

## Project Structure

- `src`: The source code of the BEAD tool.
- `Extracted Module Classes`: The directory used to store retrieved `module-info.class` files from input JDK source code.
- `directives`: The directory used to store extracted module directive statements from JDK `module-info.class` files.
- `TestJar`: The directory used to store the jar package of the program used to analyze.
- `Result`: The directory used to store the abuse analysis results of the BEAD tool.
- Additional files required during the usage of BEAD:
    - `ModuleInfo.txt`: The txt file used to store the module information of the input JDK source code by running `ModuleAccessParser.java`.
    - `PkgInfo.txt`: The txt file used to store the package information of the input JDK source code by running `JavaSourceAnalyzer.java`.
    - `data.txt`: The txt file used to store the combined information based on `ModuleInfo.txt` and `PkgInfo.txt`. The file is generated by running `JDKDataCombiner.java`. 
    - `class_file_directory_list.txt`: The txt file used to input jar path used for `ReflectionAnalyzer.java`.
    - `src.zip`: The zip file of input JDK source code used to analyze module and related package details.
    - (optional) `ModuleInfoCount.py`: A support python script used to count the number of modules in the input JDK source code for verify. (Note: Running BEAD doesn't require to run this script.)

## How to Use BEAD

1. **Extract Module Information**: Run `ExtractModuleInfoClasses.java` to extract module information from the input JDK source code. 
    - Input: The absolute path of the local JDK jmods file directory (usually listed under jdk main root).
    - Output: `Extracted Module Classes` directory which stored extracted JDK module description class files.

2. **Extract Module Directives**: Run `ModuleInfoExtractJavap.java` to extract module directives from the extracted module description class files.
    - Input: The path of `Extracted Module Classes` directory.
    - Output: `directives` directory which stored extracted module directives stored in each JDK modules' individual `directives.txt` files.

3. **Filter directives related to Breaking Strong Encapsulation (BSE) problem**: Run `ModuleAccessParser.java` to filter out module directives related to BSE problem.
    - Input: The path of `directives` directory.
      (Note: The input should be configured in the run configuration of the IDE, or the path should be manually set in the code.)
    - Output: `ModuleInfo.txt` file which stored the module information of the input JDK source code.

4. **Combine Module and Package Information**: Run `JDKDataCombiner.java` to combine module and package information. 
    - Input: The path of `ModuleInfo.txt` and `PkgInfo.txt`.
    - Output: `data.txt` file which stored the combined information based on `ModuleInfo.txt` and `PkgInfo.txt`.

5. **Analyze Reflection Invocations**: Run `ReflectionAnalyzer.java` to analyze reflection and compile-time invocations.
    - Input: The path of the jar package of the program used to analyze. (Note: The path should be absolute path listed in `class_file_directory_list.txt`.)
    - Output: Reflection Invocation Analysis Result of input jar file and stored in `Result` directory, formatted as `XXX_Reflect_Invoke.txt`.

6. **Analyze Compile-time Invocations**: `CompileTimeAnalyzer.java` is run within `AbuseAnalyzer.java`. Therefore, run `AbuseAnalyzer.java` to analyze compile-time invocations.
    - Output: Compile-time Invocation Analysis Result stored in `Result` directory, formatted as `XXX_Compile_Invoke.txt`.

7. **Analyze Encapsulation Abuse**: Run `AbuseAnalyzer.java` to analyze encapsulation abuse.
    - Input: The path of `ModuleInfo.txt`, `PkgInfo.txt`, and input jar file name inside `TestJar` directory.
    - Output: Encapsulation Abuse Analysis Result stored in `Result` directory, formatted as `XXX_Reflect_Abuse.txt` and `XXX_Compile_Abuse.txt`.


## BEAD Abuse Analyze Example

A successful run of BEAD will generate the following output in the console:
```plaintext
Start analyzing
Reading class files...
--------------------
Analyzing reflectively method invoke...
Start analysis from: [xxx\xxx\xxx.jar]
SLF4J: No SLF4J providers were found.
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See https://www.slf4j.org/codes.html#noProviders for further details.
Reflective Analysis result written to xxx_Reflect_Invoke.txt
Analyzing reflectively method invoke done
--------------------
Analyzing compile-time method invoke...
Loaded necessary classes for xxx\xxx\xxx.jar
Compile-time invoke result has stored in xxx_Compile_Time_Invoke.txt
Analyzing compile-time method invoke done
--------------------
Checking abuses...
--------------------
Checking reflective abuses...

Checking reflective abuses done, result has been stored in xxx_Reflect_Abuse.txt
--------------------
Checking compile-time abuses...

Checking compile-time abuses done, result has been stored in xxx_Compile_Time_Abuse.txt
--------------------
Reflective method invoke analysis duration: xxx.xxx ms
Compile-time method invoke analysis duration: xxx.xxx ms
Reflective abuse analysis duration: xxx.xxx ms
Compile-time abuse analysis duration: xxx.xxx ms
Inconsistency analysis done

Process finished with exit code 0

```

If BEAD didn't find abuse instances, BEAD will notify as follows:
```plaintext
    No reflective abuses found
```