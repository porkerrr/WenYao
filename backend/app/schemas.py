from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


class AnalyzeRequest(BaseModel):
    request_id: str = Field(min_length=1, max_length=64)
    record_id: str = Field(min_length=1, max_length=64)
    schema_version: int = 1
    engine_version: str = Field(min_length=1, max_length=32)
    ruleset_id: str = Field(min_length=1, max_length=64)
    question: str = Field(min_length=2, max_length=200)
    paipan: dict[str, Any]

    @field_validator("question")
    @classmethod
    def question_must_not_be_blank(cls, value: str) -> str:
        cleaned = value.strip()
        if len(cleaned) < 2:
            raise ValueError("question must contain at least two non-space characters")
        return cleaned


class KeyFactor(BaseModel):
    title: str = Field(min_length=1, max_length=80)
    explanation: str = Field(min_length=1, max_length=500)
    evidence_refs: list[str] = Field(min_length=1, max_length=8)


class AnalyzeResponse(BaseModel):
    request_id: str
    analysis_revision: int = 1
    schema_version: int = 1
    summary: str = Field(min_length=1, max_length=500)
    trend: Literal["positive", "neutral", "cautious", "mixed"]
    key_factors: list[KeyFactor] = Field(min_length=1, max_length=6)
    analysis: str = Field(min_length=1, max_length=3000)
    suggestion: str = Field(min_length=1, max_length=1000)
    confidence_note: str = Field(min_length=1, max_length=500)
    safety_note: str = "结果仅供传统文化研究与娱乐参考。"
    provider: str = "openai-compatible"
