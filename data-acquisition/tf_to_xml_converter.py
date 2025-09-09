# tf_to_xml_converter.py
import os
import time
import argparse
import sys
import xml.etree.ElementTree as ET
import google.generativeai as genai
from config import GEMINI_API_KEY, GEMINI_MODEL_NAME


def get_tf_file_content(directory_path):
    """
    Finds the first .tf file in a directory and returns its content.
    """
    for item in os.listdir(directory_path):
        if item.endswith(".tf"):
            file_path = os.path.join(directory_path, item)
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    return f.read()
            except Exception as e:
                print(f"  - Error reading {file_path}: {e}", file=sys.stderr)
                return None
    return None


def generate_drawio_xml(terraform_code, retries=3, delay=5):
    """
    Uses the Gemini API to convert Terraform code into a Draw.io XML string.
    """
    if not GEMINI_API_KEY or GEMINI_API_KEY == 'PASTE_YOUR_GEMINI_API_KEY_HERE':
        print("  - ERROR: Gemini API Key not configured.", file=sys.stderr)
        return None

    try:
        genai.configure(api_key=GEMINI_API_KEY)
        model = genai.GenerativeModel(GEMINI_MODEL_NAME)
    except Exception as e:
        print(f"  - Error configuring Gemini API: {e}", file=sys.stderr)
        return None

    prompt = f"""
    You are an expert cloud architect and a specialist in both Terraform HCL and the Draw.io XML format.
    Your task is to convert the following Terraform code into a complete, valid, and visually coherent Draw.io XML file.

    Analyze the resources, their relationships (e.g., a subnet inside a VPC, an instance in a subnet), and any explicit dependencies.
    Create a visual representation of this architecture.
    - Use appropriate AWS icons from the Draw.io library (e.g., `shape=mxgraph.aws4.group_vpc`, `resIcon=mxgraph.aws4.ec2`).
    - Arrange the elements logically with clear containment (e.g., resources inside subnets, subnets inside VPCs).
    - Ensure the final output is a single, complete XML block that can be opened directly in Draw.io.

    The output MUST be only the raw XML content, starting with `<?xml version="1.0" encoding="UTF-8"?>` and enclosed in `<mxfile>`. Do not include any other text, explanations, or markdown code fences.

    Terraform Code:
    ---
    {terraform_code}
    ---
    """

    for attempt in range(retries):
        try:
            response = model.generate_content(prompt)
            # Clean up the response to get raw XML
            xml_content = response.text.strip()
            if xml_content.startswith("```xml"):
                xml_content = xml_content[6:].strip()
            if xml_content.endswith("```"):
                xml_content = xml_content[:-3].strip()

            # Validate that the response is well-formed XML
            ET.fromstring(xml_content)

            return xml_content
        except ET.ParseError as e:
            print(f"  - Attempt {attempt + 1} failed: Gemini returned invalid XML. Error: {e}", file=sys.stderr)
        except Exception as e:
            print(f"  - Attempt {attempt + 1} failed: An error occurred with the Gemini API call: {e}", file=sys.stderr)

        if attempt < retries - 1:
            print(f"  - Retrying in {delay} seconds...", file=sys.stderr)
            time.sleep(delay)

    return None


def main():
    """Main function to run the automation script."""
    parser = argparse.ArgumentParser(
        description="Automates the generation of Draw.io XML diagrams from Terraform files in a dataset."
    )
    parser.add_argument(
        "dataset_dir",
        help="The path to the dataset directory (e.g., 'data-acquisition/dataset')."
    )
    args = parser.parse_args()

    if not os.path.isdir(args.dataset_dir):
        print(f"Error: Directory not found at '{args.dataset_dir}'", file=sys.stderr)
        sys.exit(1)

    print(f"--- Starting Terraform to Draw.io XML Conversion ---")
    print(f"Scanning directory: {args.dataset_dir}\n")

    subdirectories = [d for d in os.listdir(args.dataset_dir) if os.path.isdir(os.path.join(args.dataset_dir, d))]

    for i, dir_name in enumerate(subdirectories):
        full_dir_path = os.path.join(args.dataset_dir, dir_name)
        print(f"[{i + 1}/{len(subdirectories)}] Processing: {dir_name}")

        output_xml_path = os.path.join(full_dir_path, "diagram.xml")
        if os.path.exists(output_xml_path):
            print(f"  - Skipping: 'diagram.xml' already exists.")
            continue

        tf_code = get_tf_file_content(full_dir_path)
        if not tf_code:
            print(f"  - Skipping: No .tf file found.")
            continue

        print("  - Found Terraform code. Generating diagram with Gemini Pro...")
        drawio_xml = generate_drawio_xml(tf_code)

        if drawio_xml:
            try:
                with open(output_xml_path, 'w', encoding='utf-8') as f:
                    f.write(drawio_xml)
                print(f"  - ✅ Successfully created 'diagram.xml'")
            except Exception as e:
                print(f"  - ERROR: Could not write to {output_xml_path}. Error: {e}", file=sys.stderr)
        else:
            print(f"  - ❌ Failed to generate diagram for {dir_name}.")

        # Add a small delay to be respectful of API rate limits
        time.sleep(2)

    print("\n--- Pipeline Finished ---")


if __name__ == "__main__":
    main()
