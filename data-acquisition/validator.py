# validator.py
import os
import subprocess


def find_and_combine_tf_files(repo_path):
    """Finds all .tf files in a directory and combines their content."""
    combined_code = []
    has_tf_files = False
    for root, _, files in os.walk(repo_path):
        # Avoid processing .terraform directories which contain provider plugins
        if '.terraform' in root:
            continue
        for file in files:
            if file.endswith(".tf"):
                has_tf_files = True
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        # Add a comment to denote the origin of the code block
                        combined_code.append(f"# --- From: {os.path.relpath(file_path, repo_path)} ---")
                        combined_code.append(f.read())
                except Exception as e:
                    print(f"Could not read file {file_path}: {e}")

    if not has_tf_files:
        return None

    return "\n\n".join(combined_code)


def validate_terraform_code(repo_path):
    """
    Runs `terraform init` and `terraform validate` in the specified directory.
    Returns a tuple of (is_valid, combined_tf_code).
    """
    combined_code = find_and_combine_tf_files(repo_path)
    if not combined_code:
        print(f"No .tf files found in {repo_path}.")
        return False, None

    try:
        # Step 1: Run terraform init
        print("Running 'terraform init'...")
        init_process = subprocess.run(
            ["terraform", "init", "-no-color"],
            cwd=repo_path,
            capture_output=True,
            text=True,
            check=True
        )
        print("'terraform init' successful.")

        # Step 2: Run terraform validate
        print("Running 'terraform validate'...")
        validate_process = subprocess.run(
            ["terraform", "validate", "-no-color"],
            cwd=repo_path,
            capture_output=True,
            text=True,
            check=True
        )
        print("'terraform validate' successful.")

        return True, combined_code

    except FileNotFoundError:
        print("ERROR: 'terraform' command not found.")
        print("Please install Terraform and ensure it's in your system's PATH.")
        return False, None
    except subprocess.CalledProcessError as e:
        print(f"Terraform command failed in {repo_path}.")
        print(f"Stderr:\n{e.stderr}")
        return False, None
    except Exception as e:
        print(f"An unexpected error occurred during validation: {e}")
        return False, None
