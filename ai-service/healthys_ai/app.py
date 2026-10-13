import asyncio
import base64
import binascii
import hmac
import json
import re
import time
from collections import Counter
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import ValidationError
from .config import Settings
from .models import GenerateRequest, GenerateResponse, ReportOutput, SummaryOutput, TranscriptionOutput
from .provider import OpenAIProvider, ProviderFailure

AUDIO_TYPES = {"audio/webm", "audio/ogg", "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4"}


def problem(status, code):
    return JSONResponse(status_code=status, content={"error": {"code": code}})


def validate_input(data, settings):
    if len(json.dumps(data.snapshot, ensure_ascii=False).encode()) > settings.max_snapshot_bytes:
        raise HTTPException(413, "SNAPSHOT_TOO_LARGE")
    if data.notes is not None and len(data.notes) > settings.max_notes_chars:
        raise HTTPException(413, "NOTES_TOO_LARGE")
    if data.kind != "TRANSCRIPTION":
        if data.audioBase64 is not None or data.audioMimeType is not None:
            raise HTTPException(422, "UNEXPECTED_AUDIO")
        if data.kind == "REPORT" and not (data.notes or "").strip():
            raise HTTPException(422, "NOTES_REQUIRED")
        return None
    if data.audioMimeType not in AUDIO_TYPES or not data.audioBase64:
        raise HTTPException(422, "INVALID_AUDIO")
    if len(data.audioBase64) > 4 * ((settings.max_audio_bytes + 2) // 3):
        raise HTTPException(413, "AUDIO_TOO_LARGE")
    try:
        audio = base64.b64decode(data.audioBase64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(422, "INVALID_AUDIO") from None
    if not audio or len(audio) > settings.max_audio_bytes:
        raise HTTPException(413, "AUDIO_TOO_LARGE")
    return audio


def validate_output(data, result, settings):
    schema = {"SUMMARY": SummaryOutput, "REPORT": ReportOutput, "TRANSCRIPTION": TranscriptionOutput}[data.kind]
    output = schema.model_validate(result.model_dump() if hasattr(result, "model_dump") else result)
    if len(output.model_dump_json()) > settings.max_output_chars:
        raise ProviderFailure()
    if isinstance(output, SummaryOutput):
        allowed = {(s.get("id"), s.get("label")) for s in data.snapshot.get("sources", []) if isinstance(s, dict) and isinstance(s.get("id"), str) and isinstance(s.get("label"), str)}
        actual = [(s.id, s.label) for s in output.sources]
        if any(s not in allowed for s in actual) or len(actual) != len(set(actual)):
            raise ProviderFailure()
        if allowed and output.summary.strip() and not actual:
            raise ProviderFailure()
        references = set(re.findall(r"\[([^\[\]\n]+)\]", output.summary))
        source_ids = {source.id for source in output.sources}
        if references != source_ids:
            raise ProviderFailure()
    return output


def create_app(settings=None, provider=None):
    settings = settings or Settings.from_env()
    provider = provider or OpenAIProvider(settings)
    metrics = Counter()
    durations = Counter()
    slots = asyncio.Semaphore(settings.max_concurrency)

    @asynccontextmanager
    async def lifespan(app):
        yield
        if hasattr(provider, "close"):
            await provider.close()

    app = FastAPI(title="HEALTH'YS private AI", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)

    @app.middleware("http")
    async def guard(request, call_next):
        # Authenticate before parsing any patient data. No query/body/header logging.
        expected = "Bearer " + settings.service_token
        if not hmac.compare_digest(request.headers.get("authorization", "").encode(), expected.encode()):
            return problem(401, "UNAUTHORIZED")
        if request.url.path == "/v1/generate":
            # Read bounded chunks, also rejects chunked requests without Content-Length.
            body = bytearray()
            async for chunk in request.stream():
                if len(body) + len(chunk) > settings.max_request_bytes:
                    return problem(413, "REQUEST_TOO_LARGE")
                body.extend(chunk)
            request._body = bytes(body)
        return await call_next(request)

    @app.exception_handler(RequestValidationError)
    async def invalid(request, exc):
        # FastAPI defaults include submitted input: never return clinical data in errors.
        return problem(422, "INVALID_REQUEST")

    @app.exception_handler(HTTPException)
    async def rejected(request, exc):
        return problem(exc.status_code, exc.detail)

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    @app.get("/metrics", response_class=PlainTextResponse)
    async def collect_metrics():
        lines = []
        for (kind, outcome), count in sorted(metrics.items()):
            lines.append(f'healthys_ai_requests_total{{kind="{kind}",outcome="{outcome}"}} {count}')
        for kind, duration in sorted(durations.items()):
            lines.append(f'healthys_ai_duration_seconds_sum{{kind="{kind}"}} {duration:.6f}')
        return "\n".join(lines) + "\n"

    @app.post("/v1/generate", response_model=GenerateResponse)
    async def generate(data: GenerateRequest):
        audio = validate_input(data, settings)
        acquired = False
        started = time.monotonic()
        outcome = "failed"
        try:
            # A saturated worker is retried by the durable backend queue.
            await asyncio.wait_for(slots.acquire(), timeout=0.1)
            acquired = True
            model, result = await asyncio.wait_for(provider.generate(data, audio), timeout=settings.timeout_seconds)
            output = validate_output(data, result, settings)
            outcome = "succeeded"
            return GenerateResponse(model=model, output=output)
        except (TimeoutError, asyncio.TimeoutError):
            outcome = "timeout" if acquired else "busy"
            raise HTTPException(504 if acquired else 503, "AI_TIMEOUT" if acquired else "AI_BUSY") from None
        except (ProviderFailure, ValidationError):
            raise HTTPException(502, "INVALID_PROVIDER_OUTPUT") from None
        except Exception:
            # No exception text or traceback: provider errors can contain prompts/PHI/keys.
            raise HTTPException(503, "AI_PROVIDER_UNAVAILABLE") from None
        finally:
            audio = None
            if acquired:
                slots.release()
            metrics[(data.kind, outcome)] += 1
            durations[data.kind] += time.monotonic() - started

    return app
