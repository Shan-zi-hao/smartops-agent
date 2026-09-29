import httpx
import app.main as main_module
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.diagnosis import diagnose
from app.main import app
from app.operations import read_only_tools


class ScriptedModel:
    def __init__(self):
        self.responses = [
            AIMessage(content="", tool_calls=[
                {"name": "get_service_status", "args": {}, "id": "status"},
                {"name": "query_logs", "args": {}, "id": "logs"},
                {"name": "query_metrics", "args": {"metric": "http_5xx_rate"}, "id": "metrics"},
            ]),
            AIMessage(content="订单服务在运行；日志显示 Redis 失败，500 指标有样本，可能是业务 Redis 不可用。"),
        ]

    def bind_tools(self, tools):
        assert {tool.name for tool in tools} == {
            "get_service_status", "query_logs", "query_metrics"
        }
        return self

    def invoke(self, messages):
        return self.responses.pop(0)


def mock_response(request):
    if request.url.path.endswith("/status"):
        return httpx.Response(200, json=[{"service": "order-service", "state": "running"}])
    if request.url.path.endswith("/logs"):
        return httpx.Response(200, json={"data": {"data": {"result": [
            {"values": [["100", "Redis operation failed"]]}
        ]}}})
    if request.url.path.endswith("/metrics/http_5xx_rate"):
        return httpx.Response(200, json={"data": {"data": {"result": [
            {"value": [123, "0.5"]}
        ]}}})
    raise AssertionError(f"Unexpected URL: {request.url}")


def test_agent_loops_through_three_read_only_tools():
    with httpx.Client(base_url="http://operation-service:8080",
                      transport=httpx.MockTransport(mock_response)) as http:
        result = diagnose(ScriptedModel(), http, "order-service", "为什么大量 500？")

    assert result["answer"].startswith("订单服务在运行")
    assert [item["tool"] for item in result["evidence"]] == [
        "get_service_status", "query_logs", "query_metrics"
    ]
    assert result["evidence"][2]["output"]["samples"][0]["value"] == 0.5


def test_empty_metric_result_is_not_reported_as_zero():
    def empty_response(request):
        return httpx.Response(200, json={"data": {"data": {"result": []}}})

    with httpx.Client(base_url="http://operation-service:8080",
                      transport=httpx.MockTransport(empty_response)) as http:
        metric_tool = read_only_tools(http, "order-service")[2]
        result = metric_tool.invoke({"metric": "http_5xx_rate"})

    assert result["samples"] == []
    assert result["sample_available"] is False


def test_model_cannot_claim_cause_without_tool_evidence():
    class GuessingModel(ScriptedModel):
        def __init__(self):
            self.responses = [AIMessage(content="肯定是 Redis 坏了。")]

    with httpx.Client(base_url="http://operation-service:8080",
                      transport=httpx.MockTransport(mock_response)) as http:
        result = diagnose(GuessingModel(), http, "order-service", "为什么 500？")

    assert result["evidence"] == []
    assert "无法可靠诊断" in result["answer"]


def test_diagnose_requires_model_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    response = TestClient(app).post("/diagnose", json={
        "service": "order-service", "question": "为什么 500？"
    })
    assert response.status_code == 503


def test_diagnose_rejects_unknown_service():
    response = TestClient(app).post("/diagnose", json={
        "service": "redis-app", "question": "怎么样？"
    })
    assert response.status_code == 422


def test_uses_supplied_qwen_defaults_without_storing_key(monkeypatch):
    captured = {}
    monkeypatch.setenv("OPENAI_API_KEY", "test-only-key")
    monkeypatch.delenv("OPENAI_MODEL", raising=False)
    monkeypatch.delenv("OPENAI_BASE_URL", raising=False)
    monkeypatch.setattr(main_module, "ChatOpenAI", lambda **kwargs: captured.update(kwargs))
    monkeypatch.setattr(main_module, "diagnose", lambda *args: {"answer": "test"})

    response = TestClient(app).post("/diagnose", json={
        "service": "order-service", "question": "为什么 500？"
    })

    assert response.status_code == 200
    assert captured["model"] == "qwen3.8-27b"
    assert captured["base_url"].endswith("/compatible-mode/v1")
