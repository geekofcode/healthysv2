import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    service_token: str
    api_key: str
    text_model: str = "gpt-4.1-mini"
    transcription_model: str = "gpt-4o-mini-transcribe"
    timeout_seconds: float = 60
    max_snapshot_bytes: int = 180_000
    max_audio_bytes: int = 10_485_760
    max_notes_chars: int = 30_000
    max_output_chars: int = 50_000
    max_output_tokens: int = 6_000
    max_concurrency: int = 4

    def __post_init__(self):
        if len(self.service_token) < 32 or not self.api_key:
            raise ValueError("AI service requires configured credentials and a token of at least 32 characters")
        if any(value <= 0 for value in (
            self.timeout_seconds, self.max_snapshot_bytes, self.max_audio_bytes,
            self.max_notes_chars, self.max_output_chars, self.max_output_tokens, self.max_concurrency,
        )):
            raise ValueError("AI service limits must be positive")
        if self.max_audio_bytes > 24_000_000:
            raise ValueError("AI audio limit exceeds provider upload limit")

    @property
    def max_request_bytes(self):
        return self.max_audio_bytes * 4 // 3 + self.max_snapshot_bytes + self.max_notes_chars * 6 + 4_096

    @classmethod
    def from_env(cls):
        return cls(
            service_token=os.getenv("HEALTHYS_AI_SERVICE_TOKEN", ""),
            api_key=os.getenv("OPENAI_API_KEY", ""),
            text_model=os.getenv("OPENAI_TEXT_MODEL", "gpt-4.1-mini"),
            transcription_model=os.getenv("OPENAI_TRANSCRIPTION_MODEL", "gpt-4o-mini-transcribe"),
            timeout_seconds=float(os.getenv("AI_TIMEOUT_SECONDS", "60")),
            max_snapshot_bytes=int(os.getenv("AI_MAX_SNAPSHOT_BYTES", "180000")),
            max_audio_bytes=int(os.getenv("AI_MAX_AUDIO_BYTES", "10485760")),
            max_notes_chars=int(os.getenv("AI_MAX_NOTES_CHARS", "30000")),
            max_output_chars=int(os.getenv("AI_MAX_OUTPUT_CHARS", "50000")),
            max_output_tokens=int(os.getenv("AI_MAX_OUTPUT_TOKENS", "6000")),
            max_concurrency=int(os.getenv("AI_MAX_CONCURRENCY", "4")),
        )
