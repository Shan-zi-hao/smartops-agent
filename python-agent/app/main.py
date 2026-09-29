import os
import httpx
from fastapi import FastAPI, HTTPException
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from app.diagnosis import diagnose
from app.operations import ServiceName

app = FastAPI(title="Smart Ops Agent Service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


class DiagnosisRequest(BaseModel):
    service: ServiceName
    question: str = Field(min_length=1, max_length=2000)


@app.post("/diagnose")
def diagnose_service(request: DiagnosisRequest) -> dict:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not configured")
    model = ChatOpenAI(
        model=os.getenv("OPENAI_MODEL", "qwen3.8-27b"),
        api_key=api_key,
        base_url=os.getenv("OPENAI_BASE_URL", "https://ws-xitphf35sqxqrwsx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"),
        timeout=30,
        max_retries=1,
    )
    with httpx.Client(base_url=os.getenv("OPERATION_SERVICE_URL", "http://127.0.0.1:8083"),
                      timeout=5) as http:
        return diagnose(model, http, request.service, request.question)
