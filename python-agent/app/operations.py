import math
from typing import Literal

import httpx
from langchain_core.tools import tool


ServiceName = Literal["order-service", "user-service", "payment-service"]
MetricName = Literal[
    "scrape_up",
    "request_rate",
    "http_5xx_rate",
    "avg_latency_seconds",
    "process_cpu_usage",
    "jvm_heap_used_bytes",
]


def read_only_tools(http: httpx.Client, service: ServiceName):
    path = f"/api/operations/services/{service}"

    def get(suffix: str):
        response = http.get(path + suffix)
        response.raise_for_status()
        return response.json()

    @tool
    def get_service_status() -> dict:
        """查询当前服务的 Docker 容器状态；空列表表示没有匹配容器。"""
        return {"service": service, "containers": get("/status")}

    @tool
    def query_logs() -> dict:
        """查询当前服务最近 15 分钟的日志，用于寻找错误、异常和时间线。"""
        payload = get("/logs")
        streams = payload["data"]["data"]["result"]
        lines = [
            {"timestamp_ns": timestamp, "line": line[:500]}
            for stream in streams
            for timestamp, line in stream["values"]
        ]
        lines.sort(key=lambda item: item["timestamp_ns"], reverse=True)
        return {"service": service, "lines": lines[:100]}

    @tool
    def query_metrics(metric: MetricName) -> dict:
        """查询当前服务的一个固定 Prometheus 指标；空样本不能当作数值 0。"""
        payload = get(f"/metrics/{metric}")
        samples = []
        for item in payload["data"]["data"]["result"]:
            timestamp, raw_value = item["value"]
            value = float(raw_value)
            if math.isfinite(value):
                samples.append({"timestamp": timestamp, "value": value})
        return {"service": service, "metric": metric, "samples": samples,
                "sample_available": bool(samples)}

    return [get_service_status, query_logs, query_metrics]
