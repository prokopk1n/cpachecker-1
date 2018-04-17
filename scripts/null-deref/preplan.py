import argparse
import json

def load_km(path):
    print("Loading project information from {}".format(path))

    with open(path) as f:
        return json.load(f)

def build_function_graph(km):
    print("Building function graph")

    function_graph = {}

    for name, files in km["functions"].items():
        for file, function_info in files.items():
            if "compiled to" not in km["source files"][file]:
                continue

            function = (name, file)
            called_functions = function_graph.setdefault(function, set())

            if "calls" in function_info:
                for called_name, called_files in function_info["calls"].items():
                    for called_file in called_files:
                        if "compiled to" not in km["source files"][called_file]:
                            continue

                        called_function = (called_name, called_file)
                        called_functions.add(called_function)

    print("Function graph has {} nodes".format(len(function_graph)))
    return function_graph

def prune_static_functions(km, function_graph):
    print("Pruning static functions not called by global functions")

    marked = set()

    def mark(function):
        if function in marked:
            return

        marked.add(function)

        for called_function in function_graph[function]:
            mark(called_function)

    for function in function_graph:
        name, file = function
        function_info = km["functions"][name][file]

        if "type" in function_info and function_info["type"] == "global":
            mark(function)

    pruned_function_graph = {}

    for function, called_functions in function_graph.items():
        if function in marked:
            pruned_function_graph[function] = called_functions

    print("Pruned function graph has {} nodes".format(len(pruned_function_graph)))
    return pruned_function_graph

def get_candidate_object_files(km, function_graph):
    candidate_object_files = {}

    for function in function_graph:
        file = function[1]

        if file not in candidate_object_files:
            candidate_object_files[file] = list(km["source files"][file]["compiled to"])

    return candidate_object_files

def to_json_function_graph(function_graph):
    print("Converting function graph to JSON-friendly format")
    json_function_graph = {}

    for function, called_functions in function_graph.items():
        name, file = function

        json_called_functions = json_function_graph.setdefault(name, {}).setdefault(file, {})

        for called_function in called_functions:
            called_name, called_file = called_function

            json_called_functions.setdefault(called_name, []).append(called_file)

    return json_function_graph

def save_preplan(preplan, path):
    print("Saving preplan to {}".format(path))

    with open(path, "w") as f:
        json.dump(preplan, f, sort_keys=True, indent=4)

def main():
    parser = argparse.ArgumentParser(
        description="Preplan generator for null deref annotation algorithm")

    parser.add_argument(
        "km",
        help="Path to a JSON file containing information about analysed project, as produced by kartographer.")

    parser.add_argument(
        "preplan",
        help="Path used to write generated preplan.")

    args = parser.parse_args()
    km = load_km(args.km)
    function_graph = prune_static_functions(km, build_function_graph(km))
    candidate_object_files = get_candidate_object_files(km, function_graph)
    json_function_graph = to_json_function_graph(function_graph)
    preplan = {
        "function graph": json_function_graph,
        "candidate object files": candidate_object_files
    }
    save_preplan(preplan, args.preplan)

if __name__ == "__main__":
    main()
