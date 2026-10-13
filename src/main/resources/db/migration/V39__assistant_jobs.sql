CREATE TABLE care.ai_job (
 id UUID PRIMARY KEY, visit_id UUID NOT NULL REFERENCES care.care_visit(id),
 actor_id UUID NOT NULL REFERENCES identity.person(id), organization_id UUID NOT NULL REFERENCES organization.organization(id),
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('SUMMARY','REPORT','TRANSCRIPTION')),
 status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
 idempotency_key VARCHAR(128) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 snapshot JSONB NOT NULL, snapshot_hash VARCHAR(64) NOT NULL, notes TEXT,
 output JSONB, model VARCHAR(100), error_code VARCHAR(80), attempts INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), deadline_at TIMESTAMPTZ NOT NULL, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 lease_token UUID, lease_until TIMESTAMPTZ, finished_at TIMESTAMPTZ,
 audio_key TEXT, audio_mime_type VARCHAR(100), audio_expires_at TIMESTAMPTZ,
 retention_expires_at TIMESTAMPTZ NOT NULL,
 UNIQUE(visit_id,actor_id,idempotency_key), CHECK(deadline_at>created_at), CHECK(attempts>=0),
 CHECK ((audio_key IS NULL) OR (kind='TRANSCRIPTION' AND audio_expires_at IS NOT NULL))
);
CREATE INDEX ai_job_pending ON care.ai_job(next_attempt_at) WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX ai_job_audio_cleanup ON care.ai_job(audio_expires_at) WHERE audio_key IS NOT NULL;
CREATE FUNCTION care.immutable_ai_input() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF ROW(NEW.id,NEW.visit_id,NEW.actor_id,NEW.organization_id,NEW.kind,NEW.snapshot,NEW.snapshot_hash,NEW.notes,NEW.request_hash,NEW.idempotency_key)
 IS DISTINCT FROM ROW(OLD.id,OLD.visit_id,OLD.actor_id,OLD.organization_id,OLD.kind,OLD.snapshot,OLD.snapshot_hash,OLD.notes,OLD.request_hash,OLD.idempotency_key)
 THEN RAISE EXCEPTION 'AI provenance input is immutable'; END IF;
 IF OLD.status='SUCCEEDED' AND ROW(NEW.output,NEW.model,NEW.status) IS DISTINCT FROM ROW(OLD.output,OLD.model,OLD.status)
 THEN RAISE EXCEPTION 'Successful AI output is immutable'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER ai_input_immutable BEFORE UPDATE ON care.ai_job FOR EACH ROW EXECUTE FUNCTION care.immutable_ai_input();
