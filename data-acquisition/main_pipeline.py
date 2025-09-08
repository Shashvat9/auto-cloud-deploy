# main_pipeline.py
import os
import shutil
import time
from scraper import get_terraform_repos
from processor import process_repository_readme
from validator import validate_terraform_code

from config import TEMP_DIR, DATASET_DIR, MAX_REPOS_TO_SCRAPE


def setup_directories():
    """Create necessary directories for the pipeline."""
    os.makedirs(TEMP_DIR, exist_ok=True)
    os.makedirs(DATASET_DIR, exist_ok=True)
    print(f"Directories '{TEMP_DIR}' and '{DATASET_DIR}' are ready.")


def cleanup():
    """Remove the temporary directory."""
    if os.path.exists(TEMP_DIR):
        shutil.rmtree(TEMP_DIR)
        print(f"Temporary directory '{TEMP_DIR}' has been removed.")


def run_pipeline():
    """
    Main function to run the entire data acquisition pipeline.
    """
    print("--- Starting Data Acquisition Pipeline ---")
    setup_directories()

    try:
        # Step 1: Scrape repositories
        print(f"\n[Step 1/3] Scraping up to {MAX_REPOS_TO_SCRAPE} repositories...")
        repos_to_process = get_terraform_repos(limit=MAX_REPOS_TO_SCRAPE)
        if not repos_to_process:
            print("No repositories found or failed to scrape. Exiting.")
            return
        print(f"Successfully cloned {len(repos_to_process)} repositories.")

        successful_pairs = 0
        # Process each repository
        for i, (repo_name, repo_path) in enumerate(repos_to_process.items()):
            print(f"\n--- Processing repository {i + 1}/{len(repos_to_process)}: {repo_name} ---")

            try:
                # Step 2: Process with Gemini
                print(f"[Step 2/3] Processing README with Gemini Pro...")
                instruction_json = process_repository_readme(repo_path, repo_name)
                if not instruction_json:
                    print(f"Skipping '{repo_name}' due to processing failure.")
                    continue
                print(f"Successfully processed README for '{repo_name}'.")

                # Step 3: Validate Terraform code
                print(f"[Step 3/3] Validating Terraform code...")
                is_valid, combined_tf_code = validate_terraform_code(repo_path)
                if not is_valid:
                    print(f"Skipping '{repo_name}' due to invalid Terraform code.")
                    continue
                print(f"Terraform code for '{repo_name}' is valid.")

                # Save the final pair
                pair_dir = os.path.join(DATASET_DIR, repo_name.replace('/', '_'))
                os.makedirs(pair_dir, exist_ok=True)

                instruction_path = os.path.join(pair_dir, "instruction.json")
                code_path = os.path.join(pair_dir, "code.tf")

                with open(instruction_path, 'w', encoding='utf-8') as f:
                    f.write(instruction_json)
                with open(code_path, 'w', encoding='utf-8') as f:
                    f.write(combined_tf_code)

                successful_pairs += 1
                print(f"✅ Successfully created data pair for '{repo_name}'!")

            except Exception as e:
                print(f"An unexpected error occurred while processing {repo_name}: {e}")

            # Small delay to respect API rate limits if any
            time.sleep(1)

        print(f"\n--- Pipeline Summary ---")
        print(f"Processed {len(repos_to_process)} repositories.")
        print(f"Successfully generated {successful_pairs} data pairs.")
        print(f"Dataset saved in: '{DATASET_DIR}'")

    finally:
        # Clean up temporary files
        # cleanup()
        print("\n--- Pipeline Finished ---")


if __name__ == "__main__":
    run_pipeline()
