import argparse
import json
import random
import os
import sys

def load_preplan(path):
    print("Loading preplan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def random_order(iterable):
    items = list(iterable)
    items.sort()
    random.shuffle(items)
    return items

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

def assign_functions_to_object_files(candidate_object_files, function_graph):
    reversed_function_graph = {function: set() for function in function_graph}

    for function, successors in function_graph.items():
        for successor in successors:
            reversed_function_graph[successor].add(function)

    function_to_object_file = {}
    object_file_graph = {}
    object_file_to_num_functions = {}

    for function in reverse_postorder(reversed_function_graph):
        name, file = function
        candidates = candidate_object_files[file]
        filtered_candidates = [candidate for candidate in candidates if os.path.basename(candidate) != "a.out"]

        if len(filtered_candidates) > 0:
            candidates = filtered_candidates

        selected = max(candidates, key=lambda candidate: object_file_to_num_functions.get(candidate, 0))

        immediately_depending_object_files = []

        for depending_function in reversed_function_graph[function]:
            if depending_function in function_to_object_file:
                object_file = function_to_object_file[depending_function]
                immediately_depending_object_files.append(object_file)

        # Object files depending on the function now depend on the selected object file.
        selected_dependents = object_file_graph.setdefault(selected, set())

        for depending_function in reversed_function_graph[function]:
            if depending_function in function_to_object_file:
                object_file = function_to_object_file[depending_function]
                selected_dependents.add(object_file)

        # Object files that this function depend on now are dependents of the selected object file.
        for dependent_function in function_graph[function]:
            if dependent_function in function_to_object_file:
                object_file = function_to_object_file[dependent_function]
                object_file_graph.setdefault(object_file, set()).add(selected)

        function_to_object_file[function] = selected
        object_file_to_num_functions[selected] = object_file_to_num_functions.get(selected, 0) + 1

    return function_to_object_file, object_file_graph

def visit(graph, visited, postorder, node):
    if node in visited:
        return

    visited.add(node)

    for successor in random_order(graph[node]):
        visit(graph, visited, postorder, successor)

    postorder.append(node)

def reverse_postorder(graph):
    visited = set()
    postorder = []

    for node in random_order(graph):
        visit(graph, visited, postorder, node)

    postorder.reverse()
    return postorder

def order_functions_within_object_files(function_to_object_file, function_graph, object_file_graph):
    object_file_to_function_graph = {}

    for function, object_file in function_to_object_file.items():
        object_file_graph = object_file_to_function_graph.setdefault(object_file, {})
        object_file_graph.setdefault(function, set())

        for dependent_function in function_graph[function]:
            if function_to_object_file[dependent_function] == object_file:
                object_file_graph.setdefault(dependent_function, set()).add(function)

    object_file_to_function_order = {}

    for object_file, object_file_graph in object_file_to_function_graph.items():
        object_file_to_function_order[object_file] = reverse_postorder(object_file_graph)

    return object_file_to_function_order

def assemble_plan(function_graph, function_to_object_file, object_file_order, object_file_to_function_order):
    plan = []
    processed_functions = set()
    calls = 0
    dropped = 0

    for object_file in object_file_order:
        object_file_plan = {
            "object file": object_file,
            "functions": []
        }

        for function in object_file_to_function_order[object_file]:
            called_functions = []

            for called_function in function_graph[function]:
                calls += 1

                if called_function in processed_functions:
                    called_functions.append({
                        "name": called_function[0],
                        "object file": function_to_object_file[called_function]
                    })
                else:
                    dropped += 1

            object_file_plan["functions"].append({
                "name": function[0],
                "called functions": called_functions
            })

            processed_functions.add(function)

        plan.append(object_file_plan)

    return plan, {
        "dropped": dropped,
        "calls": calls,
        "object files": len(object_file_order),
        "functions": len(function_graph) 
    }

def make_plan(preplan):
    function_graph = preplan["function graph"]
    candidate_object_files = preplan["candidate object files"]

    function_to_object_file, object_file_graph = assign_functions_to_object_files(candidate_object_files, function_graph)
    object_file_order = reverse_postorder(object_file_graph)
    object_file_to_function_order = order_functions_within_object_files(function_to_object_file, function_graph, object_file_graph)
    return assemble_plan(function_graph, function_to_object_file, object_file_order, object_file_to_function_order)

def save_plan(plan, path):
    print("Saving plan to {}".format(path))

    with open(path, "w") as f:
        json.dump(plan, f, sort_keys=True, indent=4)

def main():
    parser = argparse.ArgumentParser(
        description="Plan generator for null deref annotation algorithm")

    parser.add_argument(
        "preplan",
        help="Path to a JSON file containing preplan, as generated by preplan.py.")

    parser.add_argument(
        "plan",
        help="Path used to write generated plan.")

    parser.add_argument(
        "--attempts",
        type=int,
        default="1",
        help="Number of plan rearrangement attempts, will pick the one with the least number of interprocedural dependencies dropped.")

    args = parser.parse_args()
    preplan = load_preplan(args.preplan)
    preplan["function graph"] = from_json_function_graph(preplan["function graph"])

    random.seed()

    min_dropped_deps = None
    best_plan = None

    for attempt in range(args.attempts):
        sys.stdout.flush()
        plan, stats = make_plan(preplan)
        dropped_deps = stats["dropped"]

        if min_dropped_deps is None or dropped_deps < min_dropped_deps:
            min_dropped_deps = dropped_deps
            best_plan = plan
            best_stats = stats

        sys.stdout.write("\rBest plan after {} attempts: {} functions in {} object files, {} dropped interprocedural dependencies out of {} ({:.4f}%)".format(
                attempt + 1, best_stats["functions"], best_stats["object files"], best_stats["dropped"], best_stats["calls"], best_stats["dropped"] / best_stats["calls"] * 100))

    print()
    save_plan(best_plan, args.plan)

if __name__ == "__main__":
    main()
