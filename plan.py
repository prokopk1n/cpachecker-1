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

def order_functions(function_graph):
    print("Ordering functions")
    return reverse_postorder(function_graph)

def add_depending_object_files(object_file_graph, depending_object_files, object_file):
    if object_file in depending_object_files:
        return

    depending_object_files.add(object_file)

    if object_file in object_file_graph:
        for depending_object_file in object_file_graph[object_file]:
            add_depending_object_files(object_file_graph, depending_object_files, depending_object_file)

def assign_functions_to_object_files(km, function_graph, function_order):
    print("Assigning functions to object files")

    reversed_function_graph = {function: set() for function in function_graph}

    for function, successors in function_graph.items():
        for successor in successors:
            reversed_function_graph[successor].add(function)

    function_to_object_file = {}
    object_file_graph = {}

    for function in function_order:
        name, file = function
        candidates = km["source files"][file]["compiled to"]

        # Get object files that depend on this function.
        depending_object_files = set()
        immediately_depending_object_files = []

        for depending_function in reversed_function_graph[function]:
            if depending_function in function_to_object_file:
                object_file = function_to_object_file[depending_function]
                add_depending_object_files(object_file_graph, depending_object_files, object_file)
                immediately_depending_object_files.append(object_file)

        # For each candidate, count how many dependencies it has on dependencies of the function,
        # this is an estimate of how many cycles selecting it would add.
        candidate_to_dep_num = {}

        for candidate in candidates:
            candidate_to_dep_num[candidate] = sum(
                1 for depending in depending_object_files if depending in object_file_graph and
                candidate in object_file_graph[depending] and depending != candidate)

        selected = min(candidates, key=lambda candidate: candidate_to_dep_num[candidate])

        # Object files depending on the function now depend on the selected object file.
        selected_dependents = object_file_graph.setdefault(selected, set())

        for depending in immediately_depending_object_files:
            selected_dependents.add(depending)

        # Object files that this function depend on now are dependents of the selected object file.
        for dependent_function in function_graph[function]:
            if dependent_function in function_to_object_file:
                object_file = function_to_object_file[dependent_function]
                object_file_graph.setdefault(object_file, set()).add(selected)

        function_to_object_file[function] = selected

    print("Assigned {} functions to {} object files".format(len(function_graph), len(object_file_graph)))
    return function_to_object_file, object_file_graph

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

def make_plan(object_file_order, object_file_to_function_order):
    print("Assembling plan")

    plan = []

    for object_file in object_file_order:
        object_file_plan = {
            "object file": object_file,
            "functions": []
        }

        for function in object_file_to_function_order[object_file]:
            name, file = function

            object_file_plan["functions"].append({
                "name": name,
                "source file": file
            })

        plan.append(object_file_plan)

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
    function_graph = build_function_graph(km)
    function_order = order_functions(function_graph)
    function_to_object_file, object_file_graph = assign_functions_to_object_files(km, function_graph, function_order)
    object_file_order = order_object_files(object_file_graph)
    object_file_to_function_order = order_functions_within_object_files(function_to_object_file, function_graph, object_file_graph)
    plan = make_plan(object_file_order, object_file_to_function_order)
    save_plan(plan, args.plan)

if __name__ == "__main__":
    main()
