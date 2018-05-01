import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import time

def load_plan(path):
    print("Loading plan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def write_object_file_plan(object_file_plan, object_file_plan_path):
    with open(object_file_plan_path, "w") as f:
        f.write("File {}\n".format(object_file_plan["object file"]))

        for function in object_file_plan["functions"]:
            f.write("Function {}\n".format(function["name"]))

            for called_function in function["called functions"]:
                f.write("  Calls {} {}\n".format(called_function["name"], called_function["object file"]))

def run(cpachecker, sources, annotations, plan, debug, overview_log, heap, time_limit, timeout, from_file, to_file, resume):
    print("Running plan")
    total_start = time.time()

    successes = 0
    failures = 0
    errors = 0
    timed_outs = 0

    if overview_log is None:
        overview_file = sys.stdout
    elif not resume or not os.path.exists(overview_log):
        overview_file = open(overview_log, "w")
    else:
        with open(overview_log) as overview_file:
            has_trailing_newline = True
            last_file = 0

            for line in overview_file:
                has_trailing_newline = line.endswith("\n")
                line = line.strip()
                match = re.match(r"^Analysing object file #(\d+)/\d+: \S+ [^\-]* \- (.*)$", line)

                if match is None:
                    continue

                last_file = int(match.group(1))

        from_file = max(from_file, last_file + 1)

        overview_file = open(overview_log, "a")

        if not has_trailing_newline:
            overview_file.write("\n")
            overview_file.flush()

    with overview_file:
        for i in range(from_file - 1, to_file if to_file is not None else len(plan)):
            object_file_plan = plan[i]
            name = object_file_plan["object file"]
            path = os.path.join(sources, name, os.path.basename(name))
            log_dir = os.path.join(os.path.abspath(annotations), name)
            start = time.time()
            start_datetime = datetime.datetime.fromtimestamp(start)
            overview_file.write("[{}] File #{}/{}: {} ({} functions)".format(
                start_datetime.strftime("%Y-%m-%d %H:%M:%S"),
                i + 1, len(plan), name, len(object_file_plan["functions"])))
            overview_file.flush()

            if not os.path.exists(path):
                overview_file.write(" - file missing\n")
                overview_file.flush()
                errors += 1
                continue

            object_file_plan_path = "object_file_plan.txt"
            write_object_file_plan(object_file_plan, object_file_plan_path)

            args = [
                "scripts/cpa.sh",
                "-config", "config/ldv-deref.properties",
                "-spec", "config/specification/default.spc",
                os.path.abspath(path),
                "-setprop", "nullDerefArgAnnotationAlgorithm.annotationDirectory={}".format(os.path.abspath(annotations)),
                "-setprop", "analysis.entryFunction={}".format(object_file_plan["functions"][0]["name"]),
                "-setprop", "nullDerefArgAnnotationAlgorithm.plan={}".format(os.path.abspath(object_file_plan_path)),
                "-setprop", "parser.usePreprocessor=true",
                "-heap", heap,
                "-timelimit", time_limit
            ]

            if debug:
                args.extend([
                    "-setprop", "nullDerefArgAnnotationAlgorithm.distinctTempSpecNames=true",
                    "-setprop", "log.consoleLevel=ALL",
                    "-setprop", "log.consoleExclude=CONFIG"
                ])

            if not os.path.exists(log_dir):
                os.makedirs(log_dir)

            log_path = os.path.join(log_dir, "log.txt")

            with open(log_path, "w") as f:
                f.write("RUN {}\n\n".format(" ".join(args)))
                f.flush()

                popen = subprocess.Popen(args, cwd=cpachecker, stdout=f, stderr=subprocess.STDOUT, universal_newlines=True)

                timed_out = False
                errorred = False

                try:
                    popen.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    timed_out = True
                except:
                    errorred = True

                finish = time.time()

            with open(log_path) as f:
                output = f.read()

            if timed_out:
                status = "timed out"
                timed_outs += 1
            elif errorred or popen.returncode != 0:
                status = "error"
                errors += 1
            elif "Verification result: UNKNOWN, incomplete analysis." in output:
                status = "success"
                successes += 1
            else:
                status = "failure"
                failures += 1

            overview_file.write(" - {}, took {:.2f} seconds\n".format(status, finish - start))
            overview_file.flush()

        total_finish = time.time()
        overview_file.write("Completed - {} successes, {} failures, {} errors, {} timeouts, took {:.2f} seconds\n".format(successes, failures, errors, timed_outs, total_finish - total_start))
        overview_file.flush()

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
        "--debug",
        help="Use distinct names for temporary spec files and make more logs.",
        action="store_true",
        default=False
    )

    parser.add_argument(
        "--log",
        help="Write overview information into a file instead of stdout."
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
        help="Start from given index in plan.",
        type=int,
        default=1
    )

    parser.add_argument(
        "--to-file",
        help="Stop after given index in plan.",
        type=int)

    parser.add_argument(
        "--resume",
        help="If log exists, continue where left off.",
        default=False,
        action="store_true")

    args = parser.parse_args()
    plan = load_plan(args.plan)
    run(args.cpachecker, args.sources, args.annotations, plan, args.debug, args.log, args.heap, args.time, args.timeout, args.from_file, args.to_file, args.resume)

if __name__ == "__main__":
    main()
