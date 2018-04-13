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

def assign_functions_to_object_files(km, function_graph):
    print("Assigning functions to object files")

    reversed_function_graph = {function: set() for function in function_graph}

    for function, successors in function_graph.items():
        for successor in successors:
            reversed_function_graph[successor].add(function)

    function_to_object_file = {}
    object_file_graph = {}

    for function in function_graph:
        name, file = function
        candidates = km["source files"][file]["compiled to"]
        selected = next(iter(candidates))

        immediately_depending_object_files = []

        for depending_function in reversed_function_graph[function]:
            if depending_function in function_to_object_file:
                object_file = function_to_object_file[depending_function]
                immediately_depending_object_files.append(object_file)

        selected = list(candidates)[0]

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

    print("Assigned {} functions to {} object files".format(len(function_graph), len(object_file_graph)))
    return function_to_object_file, object_file_graph

def visit(graph, visited, postorder, node):
    if node in visited:
        return

    visited.add(node)

    for successor in graph[node]:
        visit(graph, visited, postorder, successor)

    postorder.append(node)

def reverse_postorder(graph):
    visited = set()
    postorder = []

    for node in graph:
        visit(graph, visited, postorder, node)

    postorder.reverse()
    return postorder

def order_object_files(object_file_graph):
    print("Ordering object files")
    return reverse_postorder(object_file_graph)

def order_functions_within_object_files(function_to_object_file, function_graph, object_file_graph):
    print("Ordering functions within each object file")

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

def make_plan(function_graph, function_to_object_file, object_file_order, object_file_to_function_order):
    print("Assembling plan")

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

    print("Plan drops {} out of {} interprocedural dependencies".format(dropped, calls))
    return plan

def save_plan(plan, path):
    print("Saving plan to {}".format(path))

    with open(path, "w") as f:
        json.dump(plan, f, sort_keys=True, indent=4)

def main():
    parser = argparse.ArgumentParser(
        description="Plan generator for null deref annotation algorithm")

    parser.add_argument(
        "km",
        help="Path to a JSON file containing information about analysed project, as produced by kartographer.")

    parser.add_argument(
        "plan",
        help="Path used to write generated plan.")

    args = parser.parse_args()
    km = load_km(args.km)
    function_graph = prune_static_functions(km, build_function_graph(km))
    function_to_object_file, object_file_graph = assign_functions_to_object_files(km, function_graph)
    object_file_order = order_object_files(object_file_graph)
    object_file_to_function_order = order_functions_within_object_files(function_to_object_file, function_graph, object_file_graph)
    plan = make_plan(function_graph, function_to_object_file, object_file_order, object_file_to_function_order)
    save_plan(plan, args.plan)

if __name__ == "__main__":
    main()
