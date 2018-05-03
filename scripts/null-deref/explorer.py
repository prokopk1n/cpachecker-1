import argparse
import json
import sys

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def build_model(annotations):
    print("Indexing annotations")

    model = {
        "functions": {},
        "object files": {},
        "source files": {}
    }

    for name, source_files in annotations.items():
        for source_file, annotation in source_files.items():
            object_file = annotation["object file"]
            annotation["name"] = name
            annotation["source file"] = source_file
            model["functions"].setdefault(name, []).append(annotation)
            model["object files"].setdefault(object_file, []).append(annotation)
            model["source files"].setdefault(source_file, []).append(annotation)

    for mapping in model.values():
        for annotation_list in mapping.values():
            annotation_list.sort(key=lambda annotation: annotation["name"])

    return model

def show_function_stats(function_stats, indent):
    print("{}Function name: '{}'".format(indent, function_stats["name"]))
    print("{}Has annotation: {}".format(indent, function_stats["has annotation"]))


    if function_stats["has annotation"]:
        print("{}Source file: '{}'".format(indent, function_stats["source file"]))
        print("{}Signature: {}".format(indent, function_stats["signature"]))
        print("{}Parameter descriptions:".format(indent))

        for param in function_stats["params"]:
            print("{}    {}".format(indent, param))

def show_annotation(annotation):
    print("Function name: {}".format(annotation["name"]))
    print("Signature: {}".format(annotation["signature"]))
    print("Object file: {}".format(annotation["object file"]))
    print("Source file: {}".format(annotation["source file"]))

    if annotation["returns signed"]:
        if annotation["may return negative"]:
            if annotation["may return positive"]:
                return_description = "any signed"
            else:
                return_description = "signed <= 0"
        else:
            if annotation["may return positive"]:
                return_description = "signed >= 0"
            else:
                return_description = "signed == 0"
    elif annotation["returns pointer"]:
        if annotation["may return null"]:
            if annotation["may return errptr"]:
                return_description = "any pointer"
            else:
                return_description = "valid pointer or NULL"
        else:
            if annotation["may return errptr"]:
                return_description = "valid pointer or ERR_PTR"
            else:
                return_description = "valid pointer"
    else:
        return_description = "other"

    print("Return annotation: {}".format(return_description))

    if len(annotation["params"]) == 0:
        return

    print("Parameter annotations:")

    for param in annotation["params"]:
        if param["is pointer"]:
            if param["must deref"]:
                parameter_description = "must deref pointer"
            elif param["may deref"]:
                parameter_description = "may deref pointer"
            else:
                parameter_description = "no deref pointer"
        else:
            parameter_description = "other"

        print("  {}: {}".format(param["name"], parameter_description))

def show_annotations(annotation_list):
    print("Found {} annotation{}".format(len(annotation_list), "" if len(annotation_list) == 1 else "s"))

    for annotation in annotation_list:
        print()
        show_annotation(annotation)

def show_help():
    print("  -f <function>    Show function info")
    print("  -o <object file> Show object file info")
    print("  -s <function>    Show source file info")
    print("  -h               Show this help")
    print("  -q               Quit")

def main():
    parser = argparse.ArgumentParser(
        description="Annotation explorer for null deref annotation algorithm")

    parser.add_argument(
        "annotations",
        help="Path to a JSON file with collected annotations.")

    parser.add_argument(
        "--cmds",
        help="Read commands from a file instead of stdin.")

    args = parser.parse_args()
    annotations = load_annotations(args.annotations)

    model = build_model(annotations)

    print("Ready to process commands:")
    show_help()
    print()

    if args.cmds is None:
        f = sys.stdin
    else:
        f = open(args.cmds)

    line_iter = iter(f)

    while True:
        sys.stdout.write("> ")
        sys.stdout.flush()

        try:
            line = next(line_iter)
        except StopIteration:
            print()
            break

        if args.cmds is not None:
            sys.stdout.write(line)

        line = line.strip()

        if line == "":
            continue

        if line == "-q":
            break

        if line == "-h":
            show_help()
            continue

        if line.startswith("-f"):
            name = line[2:].strip()
            show_annotations(model["functions"].get(name, []))
        elif line.startswith("-o"):
            name = line[2:].strip()
            show_annotations(model["object files"].get(name, []))
        elif line.startswith("-s"):
            name = line[2:].strip()
            show_annotations(model["source files"].get(name, []))
        else:
            print("Invalid command.")

if __name__ == "__main__":
    main()
