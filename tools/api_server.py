from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel
import subprocess
import tempfile
import os
import shutil
from pathlib import Path
import importlib.util
from typing import Optional

app = FastAPI(title="Auto Cloud Deploy - Terraform APIs")


class TerraformInput(BaseModel):
    terraform: str



TF_CONVERTER_PATH = Path(__file__).resolve().parent.parent / "data-acquisition" / "tf_to_xml_converter.py"
tf_converter = None
if TF_CONVERTER_PATH.exists():
    spec = importlib.util.spec_from_file_location("tf_to_xml_converter", str(TF_CONVERTER_PATH))
    tf_mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(tf_mod)
        tf_converter = tf_mod
    except Exception:
        tf_converter = None


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/terraform-to-xml")
def terraform_to_xml(data: TerraformInput):
    """Convert Terraform HCL string to Draw.io XML using the existing converter module.

    Returns raw XML (Content-Type: application/xml) on success.
    """
    if tf_converter is None or not hasattr(tf_converter, "generate_drawio_xml"):
        raise HTTPException(status_code=500, detail="Terraform->XML converter not available on server.")

    xml = tf_converter.generate_drawio_xml(data.terraform)
    if not xml:
        raise HTTPException(status_code=500, detail="Failed to generate XML. Check converter configuration (e.g. Gemini API key).")

    return Response(content=xml, media_type="application/xml")


@app.post("/run-terraform")
def run_terraform(data: TerraformInput, timeout_seconds: Optional[int] = 120):
    """Save incoming Terraform code to a temporary directory and run it inside a Dockerized Terraform image.

    Security notes:
    - Networking is disabled inside the container (`--net none`).
    - CPU and memory are limited.
    - The container image used is `hashicorp/terraform:1.9.0`.

    If Docker is not available or the command fails, the response will contain a `failed` status and `error` message.
    """
    tmpdir = tempfile.mkdtemp()
    tf_file = os.path.join(tmpdir, "main.tf")

    try:
        with open(tf_file, "w", encoding="utf-8") as f:
            f.write(data.terraform)
            
        cmd = [
            "docker", "run", "--rm",
            "--net", "none",
            "--cpus", "1", "--memory", "512m",
            "-v", f"{tmpdir}:/workspace",
            "-w", "/workspace",
            "hashicorp/terraform:1.9.0",
            "sh", "-c",
            "terraform init -input=false && terraform apply -auto-approve"
        ]

        process = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout_seconds)

        if process.returncode == 0:
            return {"status": "success", "output": process.stdout.strip()}
        else:
            return {"status": "failed", "error": process.stderr.strip()}

    except subprocess.TimeoutExpired:
        return {"status": "failed", "error": "Terraform execution timed out"}

    except FileNotFoundError as e:
        # Docker binary not found on host
        raise HTTPException(status_code=500, detail=f"Docker not found on server: {str(e)}")

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Execution error: {str(e)}")

    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
