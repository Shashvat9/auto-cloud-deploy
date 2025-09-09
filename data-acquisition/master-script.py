import os
import sys
import subprocess
import argparse

# --- Configuration ---
# This assumes all your scripts (main_pipeline.py, unify_dataset.py, etc.)
# are in the same directory as this master script.
DATA_ACQUISITION_DIR = os.path.dirname(os.path.abspath(__file__))
DATASET_DIR_NAME = "dataset"  # The default name for the dataset folder


def run_step(step_name, command):
    """
    Runs a pipeline step as a subprocess and prints its status.
    Args:
        step_name (str): The name of the step for logging.
        command (list): The command to execute as a list of strings.
    Returns:
        bool: True if the step succeeded, False otherwise.
    """
    print(f"\n{'=' * 20}")
    print(f"  RUNNING: {step_name}")
    print(f"{'=' * 20}\n")
    try:
        # We use sys.executable to ensure we're using the same Python interpreter
        # that is running this script.
        process = subprocess.run(
            [sys.executable] + command,
            cwd=DATA_ACQUISITION_DIR,
            check=True
        )
        print(f"\n--- SUCCESS: {step_name} completed. ---\n")
        return True
    except FileNotFoundError:
        print(f"--- ERROR: Script for '{step_name}' not found. ---", file=sys.stderr)
        return False
    except subprocess.CalledProcessError:
        print(f"--- ERROR: '{step_name}' failed to execute properly. ---", file=sys.stderr)
        return False
    except Exception as e:
        print(f"--- ERROR: An unexpected error occurred during '{step_name}': {e} ---", file=sys.stderr)
        return False


def main():
    """
    The main orchestrator for the entire data acquisition and processing pipeline.
    """
    print("🚀 STARTING AUTO-CLOUD-DEPLOY DATA PIPELINE 🚀")

    dataset_path = os.path.join(DATA_ACQUISITION_DIR, DATASET_DIR_NAME)

    # --- STEP 1: Initial Scrape and Validation ---
    # This script scrapes GitHub, validates the .tf files, and creates an initial
    # instruction.json from the README.md.
    step1_success = run_step(
        "Initial Scrape & Validation",
        ["main_pipeline.py"]
    )
    if not step1_success:
        print("Stopping pipeline due to failure in Step 1.", file=sys.stderr)
        sys.exit(1)

    # --- STEP 2: Unify Dataset ---
    # This script handles the two-way conversion:
    # 1. Creates diagram.xml from code.tf
    # 2. Creates the final, high-quality instruction.json from diagram.xml
    step2_success = run_step(
        "Data Unification (TF -> XML -> JSON)",
        ["unify_dataset.py", dataset_path]
    )
    if not step2_success:
        print("Stopping pipeline due to failure in Step 2.", file=sys.stderr)
        sys.exit(1)

    print("\n🎉 PIPELINE COMPLETE! 🎉")
    print(f"Your fully processed dataset is ready in the '{dataset_path}' directory.")
    print("You can now proceed to the model training phase.")


if __name__ == "__main__":
    main()
