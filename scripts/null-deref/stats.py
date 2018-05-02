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
        "functions with pointers": set(),
        "parameters": 0,
        "pointers": 0,
        "pointer returns": 0,
        "signed returns": 0,
        "may return null": 0,
        "may return errptr": 0,
        "may return any pointer": 0,
        "must return valid pointer": 0,
        "may return any signed": 0,
        "must return not negative": 0,
        "must return not positive": 0,
        "must return zero": 0,
        "no deref": 0,
        "may deref": 0,
        "must deref": 0
    }

    for name, source_files in annotations.items():
        for source_file, function in source_files.items():
            stats["functions"] += 1
            stats["files"].add(function["object file"])

            if function["returns pointer"]:
                stats["pointer returns"] += 1

                if function["may return null"]:
                    if function["may return errptr"]:
                        stats["may return any pointer"] += 1
                    else:
                        stats["may return null"] += 1
                else:
                    if function["may return errptr"]:
                        stats["may return errptr"] += 1
                    else:
                        stats["must return valid pointer"] += 1
            elif function["returns signed"]:
                stats["signed returns"] += 1

                if function["may return positive"]:
                    if function["may return negative"]:
                        stats["may return any signed"] += 1
                    else:
                        stats["must return not negative"] += 1
                else:
                    if function["may return negative"]:
                        stats["must return not positive"] += 1
                    else:
                        stats["must return zero"] += 1

            for parameter in function["params"]:
                stats["parameters"] += 1

                if parameter["is pointer"]:
                    stats["pointers"] += 1
                    stats["functions with pointers"].add((name, source_file))

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
    print("{} functions have pointer parameters".format(len(annotations_stats["functions with pointers"])))
    print("{} functions return a pointer".format(annotations_stats["pointer returns"]))
    print("{} functions return a signed int".format(annotations_stats["signed returns"]))
    print("Average number of functions in a file: {}".format(plan_stats["functions"] / len(plan_stats["files"])))

    plan = sorted(plan, key=lambda of: -len(of["functions"]))
    print("Median number of functions in a file: {}".format(len(plan[len(plan) // 2]["functions"])))
    print("10 largest files contain {} functions".format(sum(len(of["functions"]) for of in plan[:10])))

    for of in plan[:10]:
        print("  {} - {} functions".format(of["object file"], len(of["functions"])))

    deps = sum(len(func["called functions"]) for of in plan for func in of["functions"])
    print("Total number of dependencies in plan: {}".format(deps))

    print()
    print("Returned pointers:")

    print("  {} out of {} functions may return any pointer".format(
        annotations_stats["may return any pointer"], annotations_stats["pointer returns"]))
    print("  {} out of {} functions returning pointers signal errors with NULL".format(
        annotations_stats["may return null"], annotations_stats["pointer returns"]))
    print("  {} out of {} functions returning pointers signal errors with ERR_PTR".format(
        annotations_stats["may return errptr"], annotations_stats["pointer returns"]))
    print("  {} out of {} functions always return valid pointers".format(
        annotations_stats["must return valid pointer"], annotations_stats["pointer returns"]))

    print()
    print("Returned signed ints:")

    print("  {} out of {} functions may return any int".format(
        annotations_stats["may return any signed"], annotations_stats["signed returns"]))
    print("  {} out of {} functions returning signed ints return only non-negative values".format(
        annotations_stats["must return not negative"], annotations_stats["signed returns"]))
    print("  {} out of {} functions returning signed ints return only non-positive values".format(
        annotations_stats["must return not positive"], annotations_stats["signed returns"]))
    print("  {} out of {} functions always return zero".format(
        annotations_stats["must return zero"], annotations_stats["signed returns"]))

    print()
    print("Pointer parameters:")

    print("  {} out of {} parameters are pointers".format(
        annotations_stats["pointers"], annotations_stats["parameters"]))
    print("  {} pointer parameters always cause NULL dereference when NULL".format(
        annotations_stats["must deref"]))
    print("  {} pointer parameters may cause NULL dereference when NULL".format(
        annotations_stats["may deref"]))
    print("  {} pointer parameters can not cause NULL dereference when NULL".format(
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
