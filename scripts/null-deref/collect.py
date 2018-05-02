import argparse
import json
import os

def load_km(path):
    print("Loading project information from {}".format(path))

    with open(path) as f:
        return json.load(f)

def get_source_file(km, object_file, function_name):
    for source_file in km["functions"][function_name]:
        if "compiled to" in km["source files"][source_file]:
            if object_file in km["source files"][source_file]["compiled to"]:
                return source_file

def to_bool(s):
    return s == "true"

def collect_annotations(km, annotations_dir):
    print("Collecting annotations from {}".format(annotations_dir))

    annotations = {}

    for object_file, object_file_info in km["object files"].items():
        if "compiled from" not in object_file_info:
            continue

        annotation_file_path = os.path.join(annotations_dir, object_file, "deref_annotation.txt")

        if not os.path.exists(annotation_file_path):
            continue

        with open(annotation_file_path) as f:
            for line in f:
                parts = line.split()

                if parts[0] == "Function":
                    function_name = parts[1]
                    function_signature = next(f).strip()
                    source_file = get_source_file(km, object_file, function_name)
                    annotation = {
                        "object file": object_file,
                        "signature": function_signature,
                        "params": [],
                        "returns pointer": False,
                        "returns signed": False
                    }

                    annotations.setdefault(function_name, {})[source_file] = annotation
                elif parts[0] == "Param":
                    param_name = parts[1]
                    is_pointer = parts[2] == "Pointer"

                    param_annotation = {
                        "name": param_name,
                        "is pointer": is_pointer
                    }

                    if is_pointer:
                        if parts[3] == "MustDeref":
                            param_annotation["may deref"] = True
                            param_annotation["must deref"] = True
                        elif parts[3] == "MayDeref":
                            param_annotation["may deref"] = True
                            param_annotation["must deref"] = False
                        else:
                            param_annotation["may deref"] = False
                            param_annotation["must deref"] = False

                    annotation["params"].append(param_annotation)
                elif parts[0] == "Returns":
                    if parts[1] == "Pointer":
                        annotation["returns pointer"] = True
                        annotation["may return null"] = parts[2] == "MayBeNull"
                        annotation["may return errptr"] = parts[3] == "MayBeError"
                    elif parts[1] == "Signed":
                        annotation["returns signed"] = True
                        annotation["may return negative"] = parts[2] == "MayBeNegative"
                        annotation["may return positive"] = parts[3] == "MayBePositive"

    return annotations

def save_annotations(annotations, path):
    print("Saving annotations to {}".format(path))

    with open(path, "w") as f:
        json.dump(annotations, f, sort_keys=True, indent=4)

def main():
    parser = argparse.ArgumentParser(
        description="Annotation collector for null deref annotation algorithm")

    parser.add_argument(
        "km",
        help="Path to a JSON file containing information about analysed project, as produced by kartographer.")

    parser.add_argument(
        "annotations_dir",
        help="Path to annotation directory.")


    parser.add_argument(
        "annotations_json",
        help="Path used to write JSON file with collected annotations.")

    args = parser.parse_args()
    km = load_km(args.km)
    annotations = collect_annotations(km, args.annotations_dir)
    save_annotations(annotations, args.annotations_json)

if __name__ == "__main__":
    main()
