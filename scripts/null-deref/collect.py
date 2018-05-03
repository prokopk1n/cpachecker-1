import argparse
import json
import os

def load_plan(path):
    print("Loading plan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def collect_annotations(plan, annotations_dir):
    print("Collecting annotations from {}".format(annotations_dir))

    annotations = {}

    for file_plan in plan:
        object_file = file_plan["object file"]

        for function_plan in file_plan["functions"]:
            name = function_plan["name"]
            source_file = function_plan["source file"]

            annotation_path = os.path.join(annotations_dir, object_file, "functions", "{}.txt".format(name))

            if not os.path.exists(annotation_path):
                continue

            with open(annotation_path) as f:
                for line in f:
                    parts = line.split()

                    if parts[0] == "Function":
                        function_name = parts[1]
                        function_signature = next(f).strip()
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
        "plan",
        help="Path to a JSON file containing plan.")

    parser.add_argument(
        "annotations_dir",
        help="Path to annotation directory.")


    parser.add_argument(
        "annotations_json",
        help="Path used to write JSON file with collected annotations.")

    args = parser.parse_args()
    plan = load_plan(args.plan)
    annotations = collect_annotations(plan, args.annotations_dir)
    save_annotations(annotations, args.annotations_json)

if __name__ == "__main__":
    main()
