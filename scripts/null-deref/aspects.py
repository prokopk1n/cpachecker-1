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

        for function in functions:
            f.write(function["aspect"].replace("NULLDEREFCHECKTYPE", check_type))

def get_functions_with_aspects(km, annotations):
    functions = []

    for name, source_files in sorted(annotations.items()):
        source_file, function = min(source_files.items())

        aspect_lines = []

        for index, parameter in enumerate(function["params"]):
            if parameter["is pointer"] and parameter["must deref"]:
                aspect_lines.append("  null_deref_NULLDEREFCHECKTYPE_check($arg{});".format(index + 1))

        if len(aspect_lines) == 0:
            continue

        signature = function["signature"]
        # Replace argument list with `..`.
        match = re.match(r"^(.*{})\(.*\)$".format(name), signature)
        signature = match.group(1) + "(..)"

        aspect = "around: call({})\n{{\n{}\n}}\n\n".format(signature, "\n".join(aspect_lines))

        function = {
            "name": name,
            "source_file": source_file,
            "aspect": aspect
        }

        function_info = km["functions"][name][source_file]
        called_in = function_info.get("called in", {})

        driver_files = [call_file for function_call_files in called_in.values() for call_file in function_call_files if call_file.startswith("drivers/") and not call_file.startswith("drivers/base/")]

        if len(driver_files) > 0:
            print("Function {} has an aspect and is called by drivers: {}".format(name, ", ".join(sorted(driver_files))))

        functions.append(function)

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
        help="Path used to write generated aspects using ldv_assert.")

    parser.add_argument(
        "assume_aspect",
        help="Path used to write generated aspects using ldv_assume.")

    args = parser.parse_args()
    km = load_km(args.km)
    annotations = load_annotations(args.annotations)
    functions = get_functions_with_aspects(km, annotations)
    write_aspects(functions, args.assert_aspect, "assert")
    write_aspects(functions, args.assume_aspect, "assume")

if __name__ == "__main__":
    main()
