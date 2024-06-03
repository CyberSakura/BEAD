# BEAD

## Description

BEAD (Breaking Encapsulation Abuse Detection) is an automatic detection tool leveraging static analysis to identify encapsulation abuse instances in Java projects. 
It scans JDK source code to extract module details and analyzes Java programs to extract abuse information from reflection and compile-time invocations. 
BEAD reports abuse instances based on previous combined information, thus helping developers address Break Strong Encapsulation (BSE) issues.

## Prerequisites

- The jar package of the program used to analyze
- JDK version 9+
- The path of local JDK source code
