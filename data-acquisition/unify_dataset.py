# unify_dataset.py
import os
import sys
import argparse
import json
import subprocess


# --- Helper Functions from your existing scripts ---
# We assume tf_to_xml_converter.py and xml_parser_v3.py are in the same directory
# or accessible via python path. For simplicity, we can call them as subprocesses.

def run_tf_to_xml(dataset_dir):
    """Runs the Terraform to Draw.io converter script and returns success status."""
    print("\n--- Running Step 1: Terraform to XML Conversion ---")
    try:
        # We pass stdout and stderr to the main process to see real-time progress
        process = subprocess.run(
            [sys.executable, "tf_to_xml_converter.py", dataset_dir],
            check=True
        )
        print("--- Finished XML Conversion ---\n")
        return True
    except FileNotFoundError:
        print("Error: 'tf_to_xml_converter.py' not found.", file=sys.stderr)
        return False
    except subprocess.CalledProcessError as e:
        print(f"Error running tf_to_xml_converter.py. It exited with a non-zero status.", file=sys.stderr)
        return False


def run_xml_to_json(xml_file_path, output_json_path):
    """Runs the XML to JSON parser for a single file."""
    try:
        result = subprocess.run(
            [sys.executable, "xml_parser_v3.py", xml_file_path],
            capture_output=True,
            text=True,
            check=True
        )
        with open(output_json_path, 'w', encoding='utf-8') as f:
            f.write(result.stdout)
        return True
    except FileNotFoundError:
        print(f"  - Error: 'xml_parser_v3.py' not found.", file=sys.stderr)
        return False
    except subprocess.CalledProcessError as e:
        print(f"  - Error parsing {xml_file_path}: {e.stderr}", file=sys.stderr)
        return False


def main():
    """
    Main orchestrator to ensure every dataset entry has all three required files:
    code.tf, diagram.xml, and instruction.json.
    """
    parser = argparse.ArgumentParser(description="Unify the dataset by generating missing files.")
    parser.add_argument(
        "dataset_dir",
        help="The path to the dataset directory (e.g., 'data-acquisition/dataset')."
    )
    args = parser.parse_args()

    if not os.path.isdir(args.dataset_dir):
        print(f"Error: Directory not found at '{args.dataset_dir}'", file=sys.stderr)
        sys.exit(1)

    # Step 1: Ensure all .tf files have a corresponding .xml file
    run_tf_to_xml(args.dataset_dir)

    # Step 2: Ensure all .xml files have a corresponding .json file
    print("--- Running Step 2: XML to JSON Conversion ---")
    subdirectories = [d for d in os.listdir(args.dataset_dir) if os.path.isdir(os.path.join(args.dataset_dir, d))]

    successful_json_conversions = 0
    failed_json_conversions = []

    for i, dir_name in enumerate(subdirectories):
        full_dir_path = os.path.join(args.dataset_dir, dir_name)
        print(f"[{i + 1}/{len(subdirectories)}] Verifying: {dir_name}")

        xml_path = os.path.join(full_dir_path, "diagram.xml")
        json_path = os.path.join(full_dir_path, "instruction.json")

        if os.path.exists(json_path):
            print("  - Skipping: 'instruction.json' already exists.")
            successful_json_conversions += 1
            continue

        if not os.path.exists(xml_path):
            print("  - Skipping: 'diagram.xml' not found.")
            # This directory is skipped because the pre-requisite is missing
            continue

        print("  - Found 'diagram.xml', generating 'instruction.json'...")
        if run_xml_to_json(xml_path, json_path):
            print("  - ✅ Successfully created 'instruction.json'")
            successful_json_conversions += 1
        else:
            print("  - ❌ Failed to create 'instruction.json'")
            failed_json_conversions.append(dir_name)

    print("\n--- Unification Complete ---")
    print(f"JSON Conversion Summary: {successful_json_conversions} successful, {len(failed_json_conversions)} failed.")
    if failed_json_conversions:
        print("Failed directories for JSON conversion:", failed_json_conversions)
    print("Your dataset is now ready for model training.")


if __name__ == "__main__":
    main()

