APIs for Auto Cloud Deploy

This file documents the small FastAPI server implemented at `tools/api_server.py`.

Endpoints
- POST /terraform-to-xml
  - Input JSON: {"terraform": "<hcl code>"}
  - Output: raw Draw.io XML (Content-Type: application/xml). Uses `data-acquisition/tf_to_xml_converter.py` under the hood; that script requires a configured Gemini API key.

- POST /run-terraform
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
- The converter calls an external model API (Gemini) and needs its API key set in the converter's config.
- The Terraform runner uses Docker to sandbox execution and disables container networking.
