import base64
from types import SimpleNamespace
from unittest.mock import AsyncMock
from fastapi.testclient import TestClient
from healthys_ai.app import create_app
from healthys_ai.config import Settings
from healthys_ai.models import SummaryOutput
from healthys_ai.provider import OpenAIProvider

TOKEN = "test-service-token-32-characters-long"
SETTINGS = Settings(service_token=TOKEN, api_key="test-key")
HEADERS = {"Authorization": "Bearer " + TOKEN}


class FakeProvider:
    def __init__(self, result=None, error=None):
        self.result = result or {"reason": "Toux", "observations": "", "diagnosis": "", "treatment": "", "followUp": ""}
        self.error = error
        self.calls = []

    async def generate(self, data, audio):
        self.calls.append((data, audio))
        if self.error:
            raise self.error
        return "fake-model", self.result


def test_unauthorized_rejected_before_parsing():
    provider = FakeProvider()
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", content="not-json")
    assert response.status_code == 401
    assert provider.calls == []


def test_validation_error_never_echoes_sensitive_input():
    with TestClient(create_app(SETTINGS, FakeProvider())) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "patient-secret", "kind": "INVALID", "snapshot": {"private": "diagnosis-secret"}})
    assert response.status_code == 422
    assert "secret" not in response.text


def test_report_draft_requires_notes_and_keeps_absent_fields_empty():
    provider = FakeProvider()
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}, "notes": "Toux"})
        missing = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}})
    assert response.status_code == 200
    assert response.json()["output"]["diagnosis"] == ""
    assert missing.status_code == 422
    assert len(provider.calls) == 1


def test_unknown_and_missing_citations_rejected():
    snapshot = {"sources": [{"id": "report-1", "label": "Rapport validé"}]}
    for sources in ([{"id": "invented", "label": "Rapport validé"}], [], [{"id": "report-1", "label": "Wrong"}]):
        provider = FakeProvider({"summary": "Toux", "sources": sources, "missingData": []})
        with TestClient(create_app(SETTINGS, provider)) as client:
            response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "SUMMARY", "snapshot": snapshot})
        assert response.status_code == 502


def test_authorized_citation_and_missing_data_retained():
    source = {"id": "report-1", "label": "Rapport validé"}
    provider = FakeProvider({"summary": "Toux documentée [report-1]", "sources": [source], "missingData": ["Allergies inconnues"]})
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "SUMMARY", "snapshot": {"sources": [source]}})
    assert response.status_code == 200
    assert response.json()["output"]["missingData"] == ["Allergies inconnues"]


def test_transcription_bytes_are_in_memory_and_mime_restricted():
    provider = FakeProvider({"text": "Douleur depuis hier"})
    data = {"jobId": "j", "kind": "TRANSCRIPTION", "snapshot": {}, "audioBase64": base64.b64encode(b"audio").decode(), "audioMimeType": "audio/webm"}
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json=data)
        invalid = client.post("/v1/generate", headers=HEADERS, json={**data, "audioMimeType": "application/pdf"})
        invalid_base64 = client.post("/v1/generate", headers=HEADERS, json={**data, "audioBase64": "!!!"})
    assert response.status_code == 200
    assert provider.calls[0][1] == b"audio"
    assert invalid.status_code == invalid_base64.status_code == 422
    assert len(provider.calls) == 1


def test_no_patient_data_in_provider_errors_or_metrics():
    provider = FakeProvider(error=RuntimeError("patient-secret api-key-secret"))
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "patient-secret", "kind": "REPORT", "snapshot": {}, "notes": "secret"})
        metrics = client.get("/metrics", headers=HEADERS)
        denied = client.get("/metrics")
    assert response.status_code == 503
    assert "secret" not in response.text + metrics.text
    assert 'kind="REPORT",outcome="failed"' in metrics.text
    assert denied.status_code == 401


def test_limits_reject_before_upstream_request():
    provider = FakeProvider()
    settings = Settings(service_token=TOKEN, api_key="test", max_audio_bytes=8, max_snapshot_bytes=20, max_notes_chars=3)
    with TestClient(create_app(settings, provider)) as client:
        large_notes = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}, "notes": "1234"})
        large_snapshot = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "SUMMARY", "snapshot": {"text": "large" * 10}})
        large_body = client.post("/v1/generate", headers=HEADERS, content=b" " * (settings.max_request_bytes + 1))
    assert large_notes.status_code == large_snapshot.status_code == large_body.status_code == 413
    assert provider.calls == []


def test_output_shape_strictly_checked():
    provider = FakeProvider({"reason": "Toux", "unexpected": "secret"})
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}, "notes": "Toux"})
    assert response.status_code == 502
    assert "secret" not in response.text


def test_timeout_retriable_without_hidden_retries():
    import asyncio
    class Slow(FakeProvider):
        async def generate(self, data, audio):
            await asyncio.sleep(1)
    settings = Settings(service_token=TOKEN, api_key="test", timeout_seconds=0.01)
    with TestClient(create_app(settings, Slow())) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}, "notes": "Toux"})
    assert response.status_code == 504


def test_openai_contract_uses_strict_schema_no_storage_and_memory_audio(monkeypatch):
    import asyncio
    from healthys_ai.models import GenerateRequest
    async def run():
        parse = AsyncMock(return_value=SimpleNamespace(status="completed", model="fake-model", output_parsed=SummaryOutput(summary="", sources=[], missingData=["Historique absent"])))
        transcribe = AsyncMock(return_value=SimpleNamespace(text="Texte dicté"))
        client = SimpleNamespace(responses=SimpleNamespace(parse=parse), audio=SimpleNamespace(transcriptions=SimpleNamespace(create=transcribe)), close=AsyncMock())
        def factory(**kwargs):
            assert kwargs["max_retries"] == 0
            assert kwargs["timeout"] == SETTINGS.timeout_seconds
            return client
        monkeypatch.setattr("healthys_ai.provider.AsyncOpenAI", factory)
        provider = OpenAIProvider(SETTINGS)
        _, result = await provider.generate(GenerateRequest(jobId="j", kind="SUMMARY", snapshot={}), None)
        assert result.missingData == ["Historique absent"]
        kwargs = parse.call_args.kwargs
        assert kwargs["store"] is False
        assert kwargs["text_format"] is SummaryOutput
        await provider.generate(GenerateRequest(jobId="j", kind="TRANSCRIPTION", snapshot={}, audioMimeType="audio/webm"), b"audio")
        assert transcribe.call_args.kwargs["file"][0] == "dictation.webm"
        assert transcribe.call_args.kwargs["file"][1].closed
        await provider.close()
        client.close.assert_awaited_once()
    asyncio.run(run())


def test_real_sdk_wire_schema_and_store_flag_without_network(monkeypatch):
    import asyncio
    import json
    import httpx
    from openai import AsyncOpenAI
    from healthys_ai.models import GenerateRequest
    async def run():
        captured = []
        result = {"summary": "Historique absent", "sources": [], "missingData": ["Antécédents inconnus"]}
        def wire(request):
            captured.append(json.loads(request.content))
            return httpx.Response(200, json={
                "id": "resp_test", "object": "response", "created_at": 1,
                "model": "gpt-4.1-mini-2025-04-14", "status": "completed",
                "output": [{"id": "msg_test", "type": "message", "status": "completed", "role": "assistant", "content": [{"type": "output_text", "text": json.dumps(result), "annotations": []}]}],
            })
        http_client = httpx.AsyncClient(transport=httpx.MockTransport(wire), trust_env=False)
        monkeypatch.setattr("healthys_ai.provider.AsyncOpenAI", lambda **kwargs: AsyncOpenAI(**kwargs, http_client=http_client))
        provider = OpenAIProvider(SETTINGS)
        try:
            model, output = await provider.generate(GenerateRequest(jobId="j", kind="SUMMARY", snapshot={}), None)
            assert model == "gpt-4.1-mini-2025-04-14"
            assert output.missingData == ["Antécédents inconnus"]
            assert captured[0]["store"] is False
            schema = captured[0]["text"]["format"]
            assert schema["type"] == "json_schema"
            assert schema["strict"] is True
            assert schema["schema"]["additionalProperties"] is False
        finally:
            await provider.close()
    asyncio.run(run())


def test_inline_summary_references_must_match_exact_source_list():
    source = {"id": "report-1", "label": "Rapport"}
    for summary in ("Fait sans référence", "Fait [invented]"):
        provider = FakeProvider({"summary": summary, "sources": [source], "missingData": []})
        with TestClient(create_app(SETTINGS, provider)) as client:
            response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "SUMMARY", "snapshot": {"sources": [source]}})
        assert response.status_code == 502


def test_report_fields_reject_oversize_even_with_large_aggregate_limit():
    settings = Settings(service_token=TOKEN, api_key="test", max_output_chars=200_000)
    base = {"reason": "", "observations": "", "diagnosis": "", "treatment": "", "followUp": ""}
    for field, limit in (("reason", 10_000), ("observations", 50_000), ("diagnosis", 20_000), ("treatment", 20_000), ("followUp", 20_000)):
        provider = FakeProvider({**base, field: "x" * (limit + 1)})
        with TestClient(create_app(settings, provider)) as client:
            response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "REPORT", "snapshot": {}, "notes": "Toux"})
        assert response.status_code == 502
        assert response.json()["error"]["code"] == "INVALID_PROVIDER_OUTPUT"


def test_transcription_rejects_text_longer_than_notes_limit():
    provider = FakeProvider({"text": "x" * 30_001})
    with TestClient(create_app(SETTINGS, provider)) as client:
        response = client.post("/v1/generate", headers=HEADERS, json={"jobId": "j", "kind": "TRANSCRIPTION", "snapshot": {}, "audioBase64": base64.b64encode(b"audio").decode(), "audioMimeType": "audio/webm"})
    assert response.status_code == 502
