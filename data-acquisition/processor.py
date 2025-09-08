# processor.py
import os
import google.generativeai as genai
from config import GEMINI_API_KEY, GEMINI_MODEL_NAME


def get_readme_content(repo_path):
    """Finds and returns the content of the README.md file."""
    readme_path = os.path.join(repo_path, "README.md")
    if not os.path.exists(readme_path):
        print(f"No README.md found in {repo_path}")
        return None

    try:
        with open(readme_path, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        print(f"Error reading README.md in {repo_path}: {e}")
        return None


def process_repository_readme(repo_path, repo_name):
    """
    Uses the Gemini API to convert a repository's README into a structured
    JSON instruction for generating Terraform code.
    """
    if not GEMINI_API_KEY or GEMINI_API_KEY == 'PASTE_YOUR_GEMINI_API_KEY_HERE':
        print("ERROR: Gemini API Key not configured in config.py.")
        return None

    try:
        genai.configure(api_key=GEMINI_API_KEY)
        model = genai.GenerativeModel(GEMINI_MODEL_NAME)
    except Exception as e:
        print(f"Error configuring Gemini API: {e}")
        return None

    readme_content = get_readme_content(repo_path)
    if not readme_content:
        return None

    # This is the core prompt engineering part.
    # It instructs the model on its role, the task, and the desired output format.
    prompt = f"""
    You are an expert DevOps engineer specializing in Terraform. Your task is to act as a text-to-JSON converter.
    Read the following README.md file for a Terraform module and generate a JSON object that represents the intended infrastructure.
    This JSON will be used as an instruction set for another model to write the Terraform code.

    The JSON output should be structured and hierarchical. Infer resource types, properties, and relationships from the text.
    Focus on capturing the core components being created (e.g., VPC, subnets, EC2 instances, S3 buckets, IAM roles, etc.).

    Here is the content of the README.md for the repository '{repo_name}':
    ---
    {readme_content}
    ---

    Generate the JSON instruction object now. The output must be ONLY the JSON object, nothing else.
    """

    try:
        response = model.generate_content(prompt)
        # Clean up the response to ensure it's valid JSON
        cleaned_response = response.text.strip().replace("```json", "").replace("```", "").strip()
        return cleaned_response
    except Exception as e:
        print(f"An error occurred with the Gemini API call for {repo_name}: {e}")
        return None
