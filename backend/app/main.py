import os

import httpx
from fastapi import FastAPI, HTTPException

from .provider import AnalysisProvider, OpenAICompatibleProvider
from .schemas import AnalyzeRequest, AnalyzeResponse


def provider_from_environment() -> AnalysisProvider:
    required = {name: os.getenv(name, "").strip() for name in ("AI_BASE_URL", "AI_API_KEY", "AI_MODEL")}
    missing = [name for name, value in required.items() if not value]
    if missing:
        raise RuntimeError(f"Missing server configuration: {', '.join(missing)}")
    return OpenAICompatibleProvider(required["AI_BASE_URL"], required["AI_API_KEY"], required["AI_MODEL"])


def create_app(provider: AnalysisProvider | None = None) -> FastAPI:
    app = FastAPI(title="WenYao Analysis API", version="0.1.0")
    app.state.provider = provider

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/v1/divinations:analyze", response_model=AnalyzeResponse)
    async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
        try:
            active_provider = app.state.provider or provider_from_environment()
            return await active_provider.analyze(request)
        except RuntimeError as error:
            raise HTTPException(status_code=503, detail={"code": "NOT_CONFIGURED", "retryable": True}) from error
        except (httpx.TimeoutException, httpx.ConnectError) as error:
            raise HTTPException(status_code=504, detail={"code": "PROVIDER_TIMEOUT", "retryable": True}) from error
        except (httpx.HTTPError, ValueError, KeyError) as error:
            raise HTTPException(status_code=502, detail={"code": "PROVIDER_ERROR", "retryable": True}) from error

    return app


app = create_app()
