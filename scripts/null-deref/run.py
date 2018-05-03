import argparse
import datetime
import json
import os
import subprocess

def write_object_file_plan(object_file_plan, object_file_plan_path):
    with open(object_file_plan_path, "w") as f:
        f.write("File {}\n".format(object_file_plan["object file"]))

        for function in object_file_plan["functions"]:
            f.write("Function {}\n".format(function["name"]))

            for called_function in function["called functions"]:
                f.write("  Calls {} {}\n".format(called_function["name"], called_function["object file"]))

class Runner:
    def __init__(self, cpachecker, sources, annotations, plan_path, workdir, debug, heap, time_limit, timeout, from_file, generations):
        self.cpachecker = cpachecker
        self.sources = sources
        self.annotations = annotations
        self.workdir = workdir
        self.debug = debug
        self.heap = heap
        self.time_limit = time_limit
        self.timeout = timeout
        self.from_file = from_file
        self.generations = generations

        self.successes = 0
        self.skipped = 0
        self.failures = 0
        self.errors = 0
        self.timeouts = 0

        if not os.path.exists(self.workdir):
            os.makedirs(self.workdir)

        self.new_annotations = os.path.join(self.workdir, "new_annotations")
        self.resume_path = os.path.join(self.workdir, "resume.txt")

        self.open_log()
        self.load_plan(plan_path)
        self.load_state()

    def log(self, s):
        self.log_file.write("[{}] {}\n".format(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), s))
        self.log_file.flush()

    def load_plan(self, plan_path):
        self.log("Loading plan from {}".format(plan_path))

        with open(plan_path) as f:
            self.plan = json.load(f)

    def open_log(self):
        log_path = os.path.join(self.workdir, "log.txt")

        if os.path.exists(log_path):
            self.log_file = open(log_path, "a")
            self.log("Continuing log")
        else:
            self.log_file = open(log_path, "w")
            self.log("Starting log")

    def load_state(self):
        self.generation = 1
        self.file_index = 1
        self.functions = {}

        if not os.path.exists(self.resume_path):
            self.log("No resume file found, restarting")
            return

        max_gen = None

        if self.from_file is not None:
            last_line = None

            with open(self.resume_path) as f:
                for line in f:
                    last_line = line

            if last_line is not None:
                last_changeset = json.loads(last_line)

                max_gen = last_changeset["gen"]

        with open(self.resume_path) as f:
            for line in f:
                changeset = json.loads(line)

                if max_gen is not None:
                    if changeset["gen"] == max_gen and changeset["file index"] == self.from_file:
                        break

                self.generation = changeset["gen"]
                self.file_index = changeset["file index"]
                object_file = changeset["object file"]

                for function_name, state in changeset["functions"].items():
                    self.functions[(function_name, object_file)] = state

        self.log("Recovered work up to and including (gen {}, file {}/{})".format(self.generation, self.file_index, len(self.plan)))
        self.next_file()

    def save_changeset(self):
        with open(self.resume_path, "a") as f:
            json.dump(self.changeset, f, separators=(',', ':'))
            f.write("\n")

    def next_file(self):
        self.file_index += 1

        if self.file_index > len(self.plan):
            self.generation += 1
            self.file_index = 1

    def file_log(self, s):
        file_name = self.plan[self.file_index - 1]["object file"]
        self.log("(gen {}, file {}/{}) {}: {}".format(self.generation, self.file_index, len(self.plan), file_name, s))

    def set_status(self, function, status):
        self.changeset["functions"][function[0]] = status
        self.functions[function] = status

    def collect_new_annotations(self, file_plan):
        object_file = file_plan["object file"]

        new_functions_dir = os.path.join(self.new_annotations, object_file, "functions")
        old_functions_dir = os.path.join(self.annotations, object_file, "functions")

        os.makedirs(old_functions_dir, exist_ok=True)

        for function_plan in file_plan["functions"]:
            name = function_plan["name"]
            function = (name, object_file)

            new_path = os.path.join(new_functions_dir, "{}.txt".format(name))
            old_path = os.path.join(old_functions_dir, "{}.txt".format(name))

            if not os.path.exists(new_path):
                self.set_status(function, "error")
                continue

            if os.path.exists(old_path):
                with open(old_path) as f:
                    old_annotation = f.read()

                with open(new_path) as f:
                    new_annotation = f.read()

                self.set_status(function, "stale" if new_annotation == old_annotation else "new")
            else:
                self.set_status(function, "new")

            os.replace(new_path, old_path)

        if not os.listdir(new_functions_dir):
            os.rmdir(new_functions_dir)

    def run_cpachecker(self, file_plan):
        name = file_plan["object file"]
        path = os.path.join(self.sources, name, os.path.basename(name))
        file_dir = os.path.join(os.path.abspath(self.new_annotations), name)

        if os.path.exists(path):
            if not os.path.exists(file_dir):
                os.makedirs(file_dir)

            file_plan_path = os.path.join(file_dir, "object_file_plan.txt")
            write_object_file_plan(file_plan, file_plan_path)

            args = [
                "scripts/cpa.sh",
                "-config", "config/ldv-deref.properties",
                "-spec", "config/specification/default.spc",
                os.path.abspath(path),
                "-setprop", "nullDerefArgAnnotationAlgorithm.readAnnotationDirectory={}".format(os.path.abspath(self.annotations)),
                "-setprop", "nullDerefArgAnnotationAlgorithm.writeAnnotationDirectory={}".format(os.path.abspath(self.new_annotations)),
                "-setprop", "analysis.entryFunction={}".format(file_plan["functions"][0]["name"]),
                "-setprop", "nullDerefArgAnnotationAlgorithm.plan={}".format(os.path.abspath(file_plan_path)),
                "-setprop", "parser.usePreprocessor=true",
                "-heap", self.heap,
                "-timelimit", self.time_limit
            ]

            if self.debug:
                args.extend([
                    "-setprop", "nullDerefArgAnnotationAlgorithm.distinctTempSpecNames=true",
                    "-setprop", "log.consoleLevel=ALL",
                    "-setprop", "log.consoleExclude=CONFIG"
                ])

            log_path = os.path.join(file_dir, "log.txt")

            with open(log_path, "w") as f:
                f.write("RUN {}\n\n".format(" ".join(args)))
                f.flush()

                popen = subprocess.Popen(args, cwd=self.cpachecker, stdout=f, stderr=subprocess.STDOUT, universal_newlines=True)

                timed_out = False
                errorred = False

                try:
                    popen.wait(timeout=self.timeout)
                except subprocess.TimeoutExpired:
                    timed_out = True
                except:
                    errorred = True

            with open(log_path) as f:
                output = f.read()

            if timed_out:
                status = "timed out"
                self.timeouts += 1
            elif errorred or popen.returncode != 0:
                status = "error"
                self.errors += 1
            elif "Verification result: UNKNOWN, incomplete analysis." in output:
                status = "success"
                self.successes += 1
            else:
                status = "failure"
                self.failures += 1

            self.file_log("result - {}".format(status))
        else:
            self.file_log("result - file missing")
            self.errors += 1

        self.collect_new_annotations(file_plan)

    def run_file(self):
        full_file_plan = self.plan[self.file_index - 1]
        object_file = full_file_plan["object file"]
        filtered_function_plans = []
        filtered_function_names = set()
        filtered_file_plan = {
            "object file": object_file,
            "functions": filtered_function_plans
        }

        self.changeset = {
            "gen": self.generation,
            "file index": self.file_index,
            "object file": object_file,
            "functions": {}
        }

        for function_plan in full_file_plan["functions"]:
            name = function_plan["name"]
            function = (name, object_file)

            should_analyse_function = False

            if function not in self.functions or self.functions[function] == "error":
                should_analyse_function = True
            else:
                for called in function_plan["called functions"]:
                    called_name = called["name"]
                    called_object_file = called["object file"]

                    if called_object_file == object_file and called_name in filtered_function_names:
                        should_analyse_function = True
                        break

                    called_function = (called_name, called_object_file)

                    if called_function in self.functions and self.functions[called_function] == "new":
                        should_analyse_function = True
                        break

            if should_analyse_function:
                filtered_function_names.add(name)
                filtered_function_plans.append(function_plan)
            else:
                self.set_status(function, "stale")

        if len(filtered_function_plans) > 0:
            self.file_log("running CPAChecker ({}/{} functions)".format(len(filtered_function_plans), len(full_file_plan["functions"])))
            self.run_cpachecker(filtered_file_plan)
        else:
            self.file_log("skipping ({} functions)".format(len(full_file_plan["functions"])))
            self.skipped += 1

        self.save_changeset()

    def run(self):
        while self.generation <= self.generations:
            self.run_file()
            self.next_file()

        self.log("Done")
        self.log("{} successes, {} skipped, {} failures, {} errors, {} timeouts".format(self.successes, self.skipped, self.failures, self.errors, self.timeouts))

def main():
    parser = argparse.ArgumentParser(
        description="Null deref annotation algorithm runner.")

    parser.add_argument(
        "cpachecker",
        help="Path to cpachecker directory.")

    parser.add_argument(
        "sources",
        help="Path to preprocessed sources directory.")

    parser.add_argument(
        "plan",
        help="Path to a JSON file containing plan.")

    parser.add_argument(
        "annotations",
        help="Path to annotation directory, it will be created if missing.")

    parser.add_argument(
        "workdir",
        help="Path to a directory for logs and resume information, it will be created if missing."
    )

    parser.add_argument(
        "--debug",
        help="Use distinct names for temporary spec files and make more logs.",
        action="store_true",
        default=False
    )

    parser.add_argument(
        "--heap",
        help="Heap limit for cpachecker.",
        default="1200M"
    )

    parser.add_argument(
        "--time",
        help="Time limit for cpachecker.",
        default="900s"
    )

    parser.add_argument(
        "--timeout",
        help="Timeout for cpachecker, in seconds.",
        type=int,
        default=None
    )

    parser.add_argument(
        "--from-file",
        help="Continue from a given file in the last generation.",
        type=int,
        default=None)

    parser.add_argument(
        "--generations",
        help="Aim to complete a given number of generations.",
        type=int,
        default=1)

    args = parser.parse_args()
    runner = Runner(args.cpachecker, args.sources, args.annotations, args.plan, args.workdir, args.debug, args.heap, args.time, args.timeout, args.from_file, args.generations)
    runner.run()

if __name__ == "__main__":
    main()
