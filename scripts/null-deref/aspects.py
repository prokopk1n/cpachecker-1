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

        function_info = km["functions"][name].get(source_file, min(km["functions"][name].items())[1])

        if "called in" in function_info:
            for call_files in function_info["called in"].values():
                for call_file in call_files:
                    if call_file.startswith("drivers/"):
                        function["called_files"].add(call_file)

        if len(function["called_files"]) == 0:
            continue

        functions[name] = function

        aspect_lines = []

        for index, parameter in enumerate(annotation["params"]):
            if parameter["is pointer"] and parameter["must deref"]:
                aspect_lines.append("  null_deref_NULLDEREFCHECKTYPE_check($arg{});".format(index + 1))

        has_param_aspect = len(aspect_lines) > 0
        has_return_aspect = False

        signature = annotation["signature"]
        # Replace argument list with `..`.
        match = re.match(r"^(.*){}\(.*\)$".format(name), signature)
        ret_type = match.group(1).strip()
        signature = "{} {}(..)".format(ret_type, name)

        if "*" in ret_type:
            if annotation["returns pointer"] and annotation["may return null"]:
                has_return_aspect = True
                aspect_lines.append("  if (__VERIFIER_nondet_int())")
                aspect_lines.append("    return (void *) 0;")
                aspect_lines.append("  else")
                aspect_lines.append("    return external_allocated_data();")
            else:
                aspect_lines.append("  return external_allocated_data();")
        elif ret_type in nondet_functions:
            if annotation["returns signed"] and not annotation["may return negative"] and not annotation["may return positive"]:
                has_return_aspect = True
                aspect_lines.append("  return 0;")
            elif annotation["returns signed"] and not annotation["may return negative"]:
                has_return_aspect = True
                aspect_lines.append("  {} ret = {}();".format(ret_type, nondet_functions[ret_type]))
                aspect_lines.append("  __VERIFIER_assume(ret >= 0);")
                aspect_lines.append("  return ret;")
            elif annotation["returns signed"] and not annotation["may return positive"]:
                has_return_aspect = True
                aspect_lines.append("  {} ret = {}();".format(ret_type, nondet_functions[ret_type]))
                aspect_lines.append("  __VERIFIER_assume(ret <= 0);")
                aspect_lines.append("  return ret;")
            else:
                aspect_lines.append("  return {}();".format(nondet_functions[ret_type]))
        elif ret_type != "void":
            aspect_lines.append("  {} *retp = external_allocated_data();".format(ret_type))
            aspect_lines.append("  return *retp;")

        if has_param_aspect or has_return_aspect:
            function["aspect"] = "around: call({})\n{{\n{}\n}}\n\n".format(signature, "\n".join(aspect_lines))

    return functions

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
        help="Path used to write generated aspects with assertions.")

    args = parser.parse_args()
    km = load_km(args.km)
    annotations = load_annotations(args.annotations)
    functions = get_functions(km, annotations)
    write_aspects(functions, args.assert_aspect, "assert")

if __name__ == "__main__":
    main()
