import argparse
import json
import re

def load_km(path):
    print("Loading project information from {}".format(path))

    with open(path) as f:
        return json.load(f)

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def write_aspects(functions, path, check_type):
    print("Writing aspect file {} using check type '{}'".format(path, check_type))

    with open(path, "w") as f:
        f.write('before: file ("$this")\n')
        f.write('{\n')
        f.write('#include <null_deref_{}.h>\n'.format(check_type))
        f.write('}\n')
        f.write('\n')

        for name, function in sorted(functions.items()):
            if "aspect" in function:
                f.write(function["aspect"].replace("NULLDEREFCHECKTYPE", check_type))

nondet_functions = {
    "char": "__VERIFIER_nondet_char",
    "int": "__VERIFIER_nondet_int",
    "float": "__VERIFIER_nondet_float",
    "long": "__VERIFIER_nondet_long",
    "size_t": "__VERIFIER_nondet_size_t",
    "loff_t": "__VERIFIER_nondet_loff_t",
    "u32": "__VERIFIER_nondet_u32",
    "u16": "__VERIFIER_nondet_u16",
    "u8": "__VERIFIER_nondet_u8",
    "unsigned char": "__VERIFIER_nondet_uchar",
    "unsigned int": "__VERIFIER_nondet_uint",
    "unsigned short": "__VERIFIER_nondet_ushort",
    "unsigned": "__VERIFIER_nondet_unsigned",
    "unsigned long": "__VERIFIER_nondet_ulong",
    "unsigned long long": "__VERIFIER_nondet_ulonglong"
}

def get_functions(km, annotations):
    functions = {}

    for name, source_files in sorted(annotations.items()):
        source_file, annotation = min(source_files.items())

        if not any(parameter["is pointer"] for parameter in annotation["params"]):
            continue

        if name not in km["functions"]:
            print("{} not found in km".format(name))
            continue

        function = {
            "source_file": source_file,
            "called_files": set()
        }
        functions[name] = function

        function_info = km["functions"][name].get(source_file, min(km["functions"][name].items())[1])

        if "called in" in function_info:
            for call_files in function_info["called in"].values():
                function["called_files"].update(call_files)

        aspect_lines = []

        for index, parameter in enumerate(annotation["params"]):
            if parameter["is pointer"] and parameter["must deref"]:
                aspect_lines.append("  null_deref_NULLDEREFCHECKTYPE_check($arg{});".format(index + 1))

        if len(aspect_lines) == 0:
            continue

        signature = annotation["signature"]
        # Replace argument list with `..`.
        match = re.match(r"^(.*){}\(.*\)$".format(name), signature)
        ret_type = match.group(1).strip()
        signature = "{} {}(..)".format(ret_type, name)

        if "*" in ret_type:
            aspect_lines.append("  return external_allocated_data();")
        elif ret_type.startswith("struct "):
            aspect_lines.append("  {} *retp = external_allocated_data();".format(ret_type))
            aspect_lines.append("  return *retp;")
        elif ret_type in nondet_functions:
            aspect_lines.append("  return {}();".format(nondet_functions[ret_type]))
        elif ret_type != "void":
            aspect_lines.append("  return ({}) __VERIFIER_nondet_ulonglong();".format(ret_type))

        function["aspect"] = "around: call({})\n{{\n{}\n}}\n\n".format(signature, "\n".join(aspect_lines))

    return functions

def get_calling_drivers(functions):
    drivers = {}

    for name, function in functions.items():
        for called_file in function["called_files"]:
            if called_file.startswith("drivers/") and not called_file.startswith("drivers/base/"):
                drivers.setdefault(called_file, []).append(name)

    return drivers

def filter_aspected(drivers, functions):
    filtered_drivers = {}

    for driver, called_names in drivers.items():
        for name in called_names:
            if "aspect" in functions[name]:
                filtered_drivers.setdefault(driver, []).append(name)

    return filtered_drivers

def report_drivers(drivers, functions, only_aspected):
    functions_descr = "functions with aspects" if only_aspected else "all functions with pointer arguments"

    print("Looking at drivers that call {}.".format(functions_descr))

    if only_aspected:
        drivers = filter_aspected(drivers, functions)

    print("Total number of drivers: {}".format(len(drivers)))
    print("Total number of calls: {}".format(sum(len(names) for names in drivers.values())))

    print("Most calling drivers:")
    print()

    for driver, names in sorted(drivers.items(), key=lambda pair: -len(pair[1])):
        print("  {}: {} calls".format(driver, len(names)))

        for name in sorted(names):
            print("    {}".format(name))

        print()

def main():
    parser = argparse.ArgumentParser(description="Aspect file generator")

    parser.add_argument(
        "km",
        help="Path to a JSON file containing information about analysed project, as produced by kartographer.")

    parser.add_argument(
        "annotations",
        help="Path to a JSON file with collected annotations.")

    parser.add_argument(
        "assert_aspect",
        help="Path used to write generated aspects using ldv_assert.")

    parser.add_argument(
        "assume_aspect",
        help="Path used to write generated aspects using ldv_assume.")

    args = parser.parse_args()
    km = load_km(args.km)
    annotations = load_annotations(args.annotations)
    functions = get_functions(km, annotations)
    drivers = get_calling_drivers(functions)
    report_drivers(drivers, functions, True)
    report_drivers(drivers, functions, False)
    write_aspects(functions, args.assert_aspect, "assert")
    write_aspects(functions, args.assume_aspect, "assume")

if __name__ == "__main__":
    main()
