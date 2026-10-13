ALTER TABLE care.care_visit ADD COLUMN reason TEXT;
UPDATE care.care_visit v SET reason=e.reason FROM care.care_episode e WHERE e.id=v.episode_id;
CREATE TABLE care.visit_intake (
 visit_id UUID PRIMARY KEY REFERENCES care.care_visit(id),
 observations TEXT NOT NULL DEFAULT '',
 vitals JSONB NOT NULL DEFAULT '{}',
 recorded_by UUID NOT NULL REFERENCES identity.person(id),
 recorded_at TIMESTAMPTZ NOT NULL,
 retention_expires_at TIMESTAMPTZ NOT NULL,
 CHECK(retention_expires_at>recorded_at)
);
INSERT INTO care.visit_intake(visit_id,observations,vitals,recorded_by,recorded_at,retention_expires_at)
 SELECT v.id,i.observations,i.vitals,i.recorded_by,i.recorded_at,i.retention_expires_at
 FROM care.reception_intake i JOIN care.care_visit v ON v.episode_id=i.episode_id
 WHERE NOT v.follow_up AND v.status<>'CANCELLED';
-- Compatibility for the original episode-level reception endpoint: it only updates the initial visit.
CREATE FUNCTION care.sync_initial_intake() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 INSERT INTO care.visit_intake(visit_id,observations,vitals,recorded_by,recorded_at,retention_expires_at)
 SELECT v.id,NEW.observations,NEW.vitals,NEW.recorded_by,NEW.recorded_at,NEW.retention_expires_at
 FROM care.care_visit v WHERE v.episode_id=NEW.episode_id AND NOT v.follow_up AND v.status<>'CANCELLED'
 ON CONFLICT(visit_id) DO UPDATE SET observations=excluded.observations,vitals=excluded.vitals,
 recorded_by=excluded.recorded_by,recorded_at=excluded.recorded_at;
 RETURN NEW; END $$;
CREATE TRIGGER sync_initial_intake AFTER INSERT OR UPDATE ON care.reception_intake FOR EACH ROW EXECUTE FUNCTION care.sync_initial_intake();
CREATE TABLE care.follow_up_request (
 episode_id UUID NOT NULL REFERENCES care.care_episode(id),
 idempotency_key VARCHAR(128) NOT NULL,
 fingerprint TEXT NOT NULL,
 visit_id UUID NOT NULL UNIQUE REFERENCES care.care_visit(id),
 PRIMARY KEY(episode_id,idempotency_key)
);
