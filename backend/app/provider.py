import json
from abc import ABC, abstractmethod
from typing import Any

import httpx

from .schemas import AnalyzeRequest, AnalyzeResponse


SYSTEM_PROMPT = """你是传统六爻文化解释助手。只能依据输入的结构化卦象，不得重新排盘、补造纳甲、六亲、世应或历法字段。避免确定性预测，不替代医疗、法律或财务专业意见。每个关键因素必须引用输入字段。只输出符合给定 JSON Schema 的 JSON。"""


class AnalysisProvider(ABC):
    @abstractmethod
    async def analyze(self, request: AnalyzeRequest) -> AnalyzeResponse:
        raise NotImplementedError


class OpenAICompatibleProvider(AnalysisProvider):
    def __init__(self, base_url: str, api_key: str, model: str) -> None:
        if not base_url.startswith("https://"):
            raise ValueError("AI_BASE_URL must use HTTPS")
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model

    async def analyze(self, request: AnalyzeRequest) -> AnalyzeResponse:
        schema = AnalyzeResponse.model_json_schema()
        payload: dict[str, Any] = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": json.dumps(request.model_dump(), ensure_ascii=False)},
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "liuyao_analysis", "strict": True, "schema": schema},
            },
            "temperature": 0.2,
        }
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        timeout = httpx.Timeout(45.0, connect=10.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.post(f"{self.base_url}/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
        content = response.json()["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        parsed["request_id"] = request.request_id
        return AnalyzeResponse.model_validate(parsed)
