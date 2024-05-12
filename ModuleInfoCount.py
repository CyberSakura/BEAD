def analyze_packages(file_path):
    with open(file_path, 'r') as file:
        content = file.readlines()

    package_count = 0
    exports_count = 0
    exports_to_count = 0
    opens_count = 0
    opens_to_count = 0

    for line in content:
        if line.strip().startswith('Package'):
            package_count += 1
            if 'exports' in line and 'exports to' not in line:
                exports_count += 1
            if 'exports to' in line:
                exports_to_count += 1
            if 'opens' in line and 'opens to' not in line:
                opens_count += 1
            if 'opens to' in line:
                opens_to_count += 1

    print(f"Total packages: {package_count}")
    print(f"'exports' packages: {exports_count}")
    print(f"'exports to' packages: {exports_to_count}")
    print(f"'opens' packages: {opens_count}")
    print(f"'opens to' packages: {opens_to_count}")

# Specify the path to your file
file_path = 'ModuleInfo.txt'
analyze_packages(file_path)