APIs for Auto Cloud Deploy

This file documents the small FastAPI server implemented at `tools/api_server.py`.

Endpoints
  - Input JSON: {"terraform": "<hcl code>"}
  - Output: raw Draw.io XML (Content-Type: application/xml). Uses `data-acquisition/tf_to_xml_converter.py` under the hood; that script requires a configured Gemini API key.

  - Input JSON: {"terraform": "<hcl code>"}
  - Output JSON: {"status": "success", "output": "..."} or {"status": "failed", "error": "..."}
  - Runs Terraform in a Docker container (`hashicorp/terraform:1.9.0`). Docker must be installed and accessible from the server.

Run locally

1. From the `repo/auto-cloud-deploy` directory install requirements:

```powershell
python -m pip install -r requirements.txt
```

2. Start the server (development):

```powershell
python tools/api_server.py
```

Notes
APIs for Auto Cloud Deploy

The project now exposes the two services as separate FastAPI apps (one file per service):

- `tools/api_tf_to_xml.py` — Terraform -> Draw.io XML service
  - Endpoint: POST /terraform-to-xml
  - Input JSON: {"terraform": "<hcl code>"}
  - Output: raw Draw.io XML (Content-Type: application/xml)
  - Notes: Uses `data-acquisition/tf_to_xml_converter.py` under the hood and therefore requires the Gemini config and `google-generativeai` package.

- `tools/api_run_terraform.py` — Run Terraform service
  - Endpoint: POST /run-terraform
  - Input JSON: {"terraform": "<hcl code>"}
  - Output JSON: {"status": "success", "output": "..."} or {"status": "failed", "error": "..."}
  - Notes: Runs Terraform inside Docker (`hashicorp/terraform:1.9.0`). Docker must be installed and accessible from the server.

Run locally

1. From the `repo/auto-cloud-deploy` directory install requirements:

```powershell
python -m pip install -r requirements.txt
```

2. Start the services (development):

```powershell
# Terraform -> XML (port 8001)
python tools/api_tf_to_xml.py

# Run Terraform (port 8002)
python tools/api_run_terraform.py
```

Or start with uvicorn for production-like usage:

```powershell
# start terraform->xml service
python -m uvicorn repo.auto_cloud_deploy.tools.api_tf_to_xml:app --host 0.0.0.0 --port 8001
# start run-terraform service
python -m uvicorn repo.auto_cloud_deploy.tools.api_run_terraform:app --host 0.0.0.0 --port 8002
```

Notes
- The converter calls an external model API (Gemini) and needs its API key set in the converter's config and the `google-generativeai` package installed.
- The Terraform runner uses Docker to sandbox execution and disables container networking. Consider using `terraform plan` instead of `apply` for safer operation.
