from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class GenerateRequest(StrictModel):
    jobId: str = Field(min_length=1, max_length=128)
    kind: Literal["SUMMARY", "REPORT", "TRANSCRIPTION"]
    snapshot: dict[str, Any]
    notes: str | None = None
    audioBase64: str | None = None
    audioMimeType: str | None = None


class Source(StrictModel):
    id: str
    label: str


class SummaryOutput(StrictModel):
    summary: str
    sources: list[Source]
    missingData: list[str]


class ReportOutput(StrictModel):
    reason: str = Field(max_length=10_000)
    observations: str = Field(max_length=50_000)
    diagnosis: str = Field(max_length=20_000)
    treatment: str = Field(max_length=20_000)
    followUp: str = Field(max_length=20_000)


class TranscriptionOutput(StrictModel):
    text: str = Field(max_length=30_000)


class GenerateResponse(StrictModel):
    model: str
    output: SummaryOutput | ReportOutput | TranscriptionOutput
