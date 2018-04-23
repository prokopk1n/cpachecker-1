import argparse
import json
import re
import sys

def load_km(path):
    print("Loading project information from {}".format(path))

    with open(path) as f:
        return json.load(f)

def load_preplan(path):
    print("Loading preplan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def from_json_function_graph(json_function_graph):
    print("Converting function graph from JSON-friendly format")
    function_graph = {}

    for name, files in json_function_graph.items():
        for file, json_called_functions in files.items():
            called_functions = set()
            function_graph[(name, file)] = called_functions

            for called_name, called_files in json_called_functions.items():
                for called_file in called_files:
                    called_functions.add((called_name, called_file))

    return function_graph

def load_plan(path):
    print("Loading plan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def load_log(path):
    print("Loading log from {}".format(path))

    log = {}

    with open(path) as f:
        for line in f:
            line = line.strip()

            match = re.match(r"^Analysing object file #\d+/\d+: (\S+) [^\-]* \- (.*)$", line)

            if match is None:
                continue

            object_file, status = match.groups()

            match = re.match(r"^([^,]*), took (\S+) seconds$", status)

            if match is None:
                seconds = 0.0
            else:
                status, seconds = match.groups()
                seconds = float(seconds)

            log[object_file] = {
                "status": status,
                "seconds": seconds
            }

    return log

def build_model(preplan, plan, annotations, log):
    print("Indexing functions")

    model = {
        "functions": {},
        "object files": {}
    }

    for object_file_plan in plan:
        file = object_file_plan["object file"]

        object_file_stats = {
            "file": file,
            "functions": {},
            "status": "not analysed",
            "seconds": 0.0
        }
        model["object files"][file] = object_file_stats

        for function in object_file_plan["functions"]:
            name = function["name"]

            function_stats = {
                "name": name,
                "has annotation": False
            }

            model["functions"].setdefault(name, {})[file] = function_stats
            object_file_stats["functions"][name] = function_stats

    for name, source_files in annotations.items():
        for source_file, annotation in source_files.items():
            file = annotation["object file"]

            function_stats = model["functions"][name][file]
            function_stats["has annotation"] = True
            function_stats["signature"] = annotation["signature"]
            function_stats["params"] = []

            for param in annotation["params"]:
                if not param["is pointer"]:
                    descr = "not pointer"
                elif param["must deref"]:
                    descr = "must deref"
                elif param["may deref"]:
                    descr = "may deref"
                else:
                    descr = "no deref"

                function_stats["params"].append("{}: {}".format(param["name"], descr))

    for file, log_info in log.items():
        model["object files"][file]["status"] = log_info["status"]
        model["object files"][file]["seconds"] = log_info["seconds"]

    return model

def show_file_stats(file_stats, indent):
    print("{}Object file: '{}'".format(indent, file_stats["file"]))
    print("{}Number of planned functions: {}".format(indent, len(file_stats["functions"])))
    print("{}Analysis status: {}".format(indent, file_stats["status"]))
    print("{}Analysis duration: {} seconds".format(indent, file_stats["seconds"]))

def show_function_stats(function_stats, indent):
    print("{}Function name: '{}'".format(indent, function_stats["name"]))
    print("{}Has annotation: {}".format(indent, function_stats["has annotation"]))


    if function_stats["has annotation"]:
        print("{}Signature: {}".format(indent, function_stats["signature"]))
        print("{}Parameter descriptions:".format(indent))

        for param in function_stats["params"]:
            print("{}    {}".format(indent, param))

def show_function(model, name):
    if name not in model["functions"]:
        print("Not recognizing function '{}'".format(name))
        return

    object_file_to_stats = model["functions"][name]

    if len(object_file_to_stats) == 1:
        file = next(iter(object_file_to_stats))
        show_file_stats(model["object files"][file], "")
        print()
        show_function_stats(object_file_to_stats[file], "")
    else:
        print("Function '{}' analysed in {} object files".format(name, len(object_file_to_stats)))

        for file, function_stats in sorted(object_file_to_stats.items()):
            print()
            show_file_stats(model["object files"][file], "    ")
            print()
            show_function_stats(function_stats, "    ")

def show_object_file(model, file):
    if file not in model["object files"]:
        print("Not recognizing object file '{}'".format(file))
        return

    show_file_stats(model["object files"][file], "")

def show_help():
    print("  -f <function>    Show function info")
    print("  -o <object file> Show object file info")
    print("  -h               Show this help")
    print("  -q               Quit")

def main():
    parser = argparse.ArgumentParser(
        description="Annotation stats analyser for null deref annotation algorithm")

    parser.add_argument(
        "preplan",
        help="Path to a JSON file containing preplan, as generated by preplan.py.")

    parser.add_argument(
        "plan",
        help="Path to a JSON file containing plan.")

    parser.add_argument(
        "annotations",
        help="Path to a JSON file with collected annotations.")

    parser.add_argument(
        "log",
        help="Path to run.py log file.")

    parser.add_argument(
        "--cmds",
        help="Read commands from a file instead of stdin.")

    args = parser.parse_args()
    preplan = load_preplan(args.preplan)
    preplan["function graph"] = from_json_function_graph(preplan["function graph"])
    plan = load_plan(args.plan)
    annotations = load_annotations(args.annotations)
    log = load_log(args.log)

    model = build_model(preplan, plan, annotations, log)

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

        if line.startswith("-f"):
            name = line[2:].strip()
            show_function(model, name)
        elif line.startswith("-o"):
            name = line[2:].strip()
            show_object_file(model, name)
        else:
            print("Invalid command.")

if __name__ == "__main__":
    main()
