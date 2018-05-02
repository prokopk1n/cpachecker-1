import argparse
import os
import shutil
import subprocess

def title(s):
    print("=" * 60)
    print("  " + s)
    print("=" * 60)

def run_test(name):
    title("Running test {}".format(name))
    print()

    test_dir = os.path.join("null_deref_tests", name)
    source = os.path.join(test_dir, "test.c")
    annotations = os.path.join(test_dir, "annotations")
    plan = os.path.join(test_dir, "plan.txt")
    expected = os.path.join(test_dir, "expected")
    actual = os.path.join(annotations, "test.c", "functions")

    if os.path.exists(annotations):
        shutil.rmtree(annotations)

    ok = True

    try:
        subprocess.check_call([
            "scripts/cpa.sh",
            "-config", "config/ldv-deref.properties",
            "-spec", "config/specification/default.spc",
            source,
            "-setprop", "nullDerefArgAnnotationAlgorithm.writeAnnotationDirectory={}".format(annotations),
            "-setprop", "analysis.entryFunction=f1",
            "-setprop", "nullDerefArgAnnotationAlgorithm.plan={}".format(plan),
        ])

        subprocess.check_call(["diff", "-r", expected, actual])
    except subprocess.CalledProcessError:
        ok = False

    print()
    title("Test {} {}!".format(name, "passed" if ok else "failed"))
    print()

    return ok

def main():
    parser = argparse.ArgumentParser(description="Null deref annotation test runner.")

    parser.add_argument("tests", nargs="*", help="Names of the tests to run, run all by default.")

    args = parser.parse_args()

    tests = args.tests

    if len(tests) == 0:
        tests = sorted([name for name in os.listdir("null_deref_tests") if os.path.isdir(os.path.join("null_deref_tests", name))])

    results = []
    total_ok = 0
    total_fail = 0

    for name in tests:
        ok = run_test(name)
        results.append(ok)

        if ok:
            total_ok += 1
        else:
            total_fail += 1

    title("Summary")
    print()

    for name, ok in zip(tests, results):
        print("{} - {}".format(name, "passed" if ok else "failed"))

    print()
    print("Total: {} passed, {} failed".format(total_ok, total_fail))

if __name__ == "__main__":
    main()
