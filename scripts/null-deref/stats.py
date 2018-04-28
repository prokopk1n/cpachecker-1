import argparse
import json

def load_plan(path):
    print("Loading plan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def get_plan_stats(plan):
    stats = {
        "files": set(),
        "functions": 0
    }

    for object_file_plan in plan:
        stats["files"].add(object_file_plan["object file"])
        stats["functions"] += len(object_file_plan["functions"])

    return stats

def get_annotations_stats(annotations):
    stats = {
        "files": set(),
        "functions": 0,
        "functions_with_pointers": set(),
        "functions_returning_pointers": set(),
        "parameters": 0,
        "pointers": 0,
        "pointer returns": 0,
        "may not return null": 0,
        "no deref": 0,
        "may deref": 0,
        "must deref": 0
    }

    for name, source_files in annotations.items():
        for source_file, function in source_files.items():
            stats["functions"] += 1
            stats["files"].add(function["object file"])

            if function["returns pointer"]:
                stats["functions_returning_pointers"].add((name, source_file))

                if not function["may return null"]:
                    stats["may not return null"] += 1

            for parameter in function["params"]:
                stats["parameters"] += 1

                if parameter["is pointer"]:
                    stats["pointers"] += 1
                    stats["functions_with_pointers"].add((name, source_file))

                    if parameter["must deref"]:
                        stats["must deref"] += 1
                    elif parameter["may deref"]:
                        stats["may deref"] += 1
                    else:
                        stats["no deref"] += 1

    return stats

def main():
    parser = argparse.ArgumentParser(
        description="Annotation stats analyser for null deref annotation algorithm")

    parser.add_argument(
        "plan",
        help="Path to a JSON file containing plan.")

    parser.add_argument(
        "annotations",
        help="Path to a JSON file with collected annotations.")

    args = parser.parse_args()
    plan = load_plan(args.plan)
    annotations = load_annotations(args.annotations)
    plan_stats = get_plan_stats(plan)
    annotations_stats = get_annotations_stats(annotations)

    print("Analysed {} functions in {} files out of {} functions in {} files".format(
        annotations_stats["functions"], len(annotations_stats["files"]),
        plan_stats["functions"], len(plan_stats["files"])))
    print("{} functions have pointer parameters".format(len(annotations_stats["functions_with_pointers"])))
    print("{} functions return a pointer".format(len(annotations_stats["functions_returning_pointers"])))
    print("Average number of functions in a file: {}".format(plan_stats["functions"] / len(plan_stats["files"])))

    plan = sorted(plan, key=lambda of: -len(of["functions"]))
    print("Median number of functions in a file: {}".format(len(plan[len(plan) // 2]["functions"])))
    print("10 largest files contain {} functions".format(sum(len(of["functions"]) for of in plan[:10])))

    for of in plan[:10]:
        print("  {} - {} functions".format(of["object file"], len(of["functions"])))

    deps = sum(len(func["called functions"]) for of in plan for func in of["functions"])
    print("Total number of dependencies in plan: {}".format(deps))

    print("{} out of {} returned pointers may not be NULL".format(
        annotations_stats["may not return null"], len(annotations_stats["functions_returning_pointers"])))

    print("{} out of {} parameters are pointers".format(
        annotations_stats["pointers"], annotations_stats["parameters"]))
    print("{} pointer parameters always cause NULL dereference when NULL".format(
        annotations_stats["must deref"]))
    print("{} pointer parameters may cause NULL dereference when NULL".format(
        annotations_stats["may deref"]))
    print("{} pointer parameters can not cause NULL dereference when NULL".format(
        annotations_stats["no deref"]))

    bad_files = plan_stats["files"] - annotations_stats["files"]

    if len(bad_files) > 0:
        print()
        print("Files that could not be analysed:")

        num_functions = {}

        for file in plan:
            num_functions[file["object file"]] = len(file["functions"])

        for file in sorted(bad_files, key=lambda file: -num_functions[file]):
            print("  {} - {} functions".format(file, num_functions[file]))

if __name__ == "__main__":
    main()
