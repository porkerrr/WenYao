import unittest

from fastapi.testclient import TestClient

from app.main import create_app
from app.provider import AnalysisProvider
from app.schemas import AnalyzeRequest, AnalyzeResponse, KeyFactor


class FixedProvider(AnalysisProvider):
    async def analyze(self, request: AnalyzeRequest) -> AnalyzeResponse:
        return AnalyzeResponse(
            request_id=request.request_id,
            summary="整体处于变化中。",
            trend="mixed",
            key_factors=[KeyFactor(title="动爻", explanation="第三爻变化", evidence_refs=["paipan:moving_lines"])],
            analysis="仅依据结构化卦象进行文化解释。",
            suggestion="结合现实信息谨慎判断。",
            confidence_note="不作确定性预测。",
            provider="test",
        )


def valid_request() -> dict:
    return {
        "request_id": "req-1",
        "record_id": "record-1",
        "schema_version": 1,
        "engine_version": "0.1.0",
        "ruleset_id": "hexagram-core-v1",
        "question": "工作进展如何？",
        "paipan": {
            "original_hexagram": "泽地萃",
            "changed_hexagram": "泽山咸",
            "moving_lines": [3],
            "line_values_bottom_up": [8, 8, 6, 7, 7, 8],
        },
    }


class ApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(create_app(FixedProvider()))

    def test_health(self) -> None:
        self.assertEqual({"status": "ok"}, self.client.get("/health").json())

    def test_analyze_contract(self) -> None:
        response = self.client.post("/v1/divinations:analyze", json=valid_request())
        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("req-1", body["request_id"])
        self.assertEqual("mixed", body["trend"])
        self.assertEqual("test", body["provider"])

    def test_blank_question_is_rejected(self) -> None:
        request = valid_request()
        request["question"] = "  "
        self.assertEqual(422, self.client.post("/v1/divinations:analyze", json=request).status_code)

    def test_oversized_question_is_rejected(self) -> None:
        request = valid_request()
        request["question"] = "问" * 201
        self.assertEqual(422, self.client.post("/v1/divinations:analyze", json=request).status_code)


if __name__ == "__main__":
    unittest.main()
