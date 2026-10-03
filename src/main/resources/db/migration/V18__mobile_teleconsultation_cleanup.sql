-- Completion commits first; workers disconnect the provider room with durable retries.
CREATE TABLE teleconsultation.room_cleanup (
    video_session_id UUID PRIMARY KEY REFERENCES teleconsultation.video_session(id),
    room_name VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(40),
    failed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_room_cleanup_pending ON teleconsultation.room_cleanup(next_attempt_at) WHERE completed_at IS NULL AND failed_at IS NULL;
