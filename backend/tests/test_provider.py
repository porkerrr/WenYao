import unittest
from unittest.mock import patch

from app.provider import OpenAICompatibleProvider
from app.schemas import AnalyzeRequest


class FakeResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return {
            "choices": [{
                "message": {
                    "content": (
                        '{"request_id":"ignored","summary":"摘要","trend":"neutral",'
                        '"key_factors":[{"title":"本卦","explanation":"测试",'
                        '"evidence_refs":["paipan:original_hexagram"]}],'
                        '"analysis":"分析","suggestion":"建议",'
                        '"confidence_note":"不作确定预测","provider":"fake"}'
                    )
                }
            }]
        }


class FakeClient:
    def __init__(self, *args, **kwargs) -> None:
        self.last_url = None
        self.last_payload = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback) -> None:
        return None

    async def post(self, url: str, headers: dict, json: dict) -> FakeResponse:
        self.last_url = url
        self.last_payload = json
        return FakeResponse()


class ProviderTest(unittest.IsolatedAsyncioTestCase):
    async def test_provider_sends_structured_request_and_validates_response(self) -> None:
        request = AnalyzeRequest(
            request_id="req-7",
            record_id="record-7",
            engine_version="0.1.0",
            ruleset_id="hexagram-core-v1",
            question="工作进展如何？",
            paipan={"original_hexagram": "泽地萃"},
        )
        fake_client = FakeClient()
        provider = OpenAICompatibleProvider("https://provider.example/v1", "secret-for-test", "model-test")

        with patch("app.provider.httpx.AsyncClient", return_value=fake_client):
            result = await provider.analyze(request)

        self.assertEqual("https://provider.example/v1/chat/completions", fake_client.last_url)
        self.assertEqual("json_schema", fake_client.last_payload["response_format"]["type"])
        self.assertEqual("req-7", result.request_id)
        self.assertEqual("fake", result.provider)

    def test_provider_rejects_cleartext_base_url(self) -> None:
        with self.assertRaises(ValueError):
            OpenAICompatibleProvider("http://provider.example/v1", "secret", "model")


if __name__ == "__main__":
    unittest.main()
