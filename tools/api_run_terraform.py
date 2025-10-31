from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import subprocess
import tempfile
import os
import shutil
from typing import Optional

app = FastAPI(title="Auto Cloud Deploy - Run Terraform API")


class TerraformInput(BaseModel):
    terraform: str


@app.get("/health")
def health():
    return {"status": "ok", "service": "run-terraform"}


@app.post("/run-terraform")
def run_terraform(data: TerraformInput, timeout_seconds: Optional[int] = 120):
    """Save incoming Terraform code to a temporary directory and run it inside a Dockerized Terraform image."""
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
        raise HTTPException(status_code=500, detail=f"Docker not found on server: {str(e)}")

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Execution error: {str(e)}")

    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8002)
