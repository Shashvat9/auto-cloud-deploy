from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel
from pathlib import Path
import importlib.util

app = FastAPI(title="Auto Cloud Deploy - Terraform->XML API")


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
    return {"status": "ok", "service": "terraform-to-xml"}


@app.post("/terraform-to-xml")
def terraform_to_xml(data: TerraformInput):
    """Convert Terraform HCL string to Draw.io XML using the existing converter module."""
    if tf_converter is None or not hasattr(tf_converter, "generate_drawio_xml"):
        raise HTTPException(status_code=500, detail="Terraform->XML converter not available on server.")

    xml = tf_converter.generate_drawio_xml(data.terraform)
    if not xml:
        raise HTTPException(status_code=500, detail="Failed to generate XML. Check converter configuration (e.g. Gemini API key).")

    return Response(content=xml, media_type="application/xml")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8001)
