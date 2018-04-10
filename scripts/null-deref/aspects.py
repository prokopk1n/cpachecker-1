import argparse
import json

def load_annotations(path):
    print("Loading annotations from {}".format(path))

    with open(path) as f:
        return json.load(f)

def write_aspects(annotations, path, check_type):
    print("Writing aspect file {} using check type '{}'".format(path, check_type))

    with open(path, "w") as f:
        f.write('before: file ("$this")\n')
        f.write('{\n')
        f.write('#include <null_deref_{}.h>\n'.format(check_type))
        f.write('}\n')
        f.write('\n');


        for name, source_files in sorted(annotations.items()):
            source_file, function = min(source_files.items())
            signature = function.get("signature", "void {}(..)".format(name))
            need_aspect = False

            for index, parameter in enumerate(function["params"]):
                if parameter["is pointer"] and parameter["must deref"]:
                    if not need_aspect:
                        need_aspect = True

                        f.write('around: call({}\n'.format(signature))
                        f.write('{\n')

                    f.write('  null_deref_{}_check($arg{});\n'.format(check_type, index + 1));

            if need_aspect:
                f.write('}\n')
                f.write('\n')

def main():
    parser = argparse.ArgumentParser(description="Aspect file generator")

    parser.add_argument(
        "annotations",
        help="Path to a JSON file with collected annotations.")

    parser.add_argument(
        "assert_aspect",
        help="Path used to write generated aspects using ldv_assert.")

    parser.add_argument(
        "assume_aspect",
        help="Path used to write generated aspects using ldv_assume.")

    args = parser.parse_args()
    annotations = load_annotations(args.annotations)
    write_aspects(annotations, args.assert_aspect, "assert")
    write_aspects(annotations, args.assume_aspect, "assume")

if __name__ == "__main__":
    main()
