ALTER TABLE care.care_episode ADD COLUMN speciality_id UUID REFERENCES catalog.speciality_catalog(id);
-- Replace the two checks which constrain visit lifecycle to allow reception before cash settlement.
DO $$ DECLARE item RECORD; BEGIN
 FOR item IN SELECT conname FROM pg_constraint WHERE conrelid='care.care_visit'::regclass
 AND contype='c' AND pg_get_constraintdef(oid) LIKE '%status%' LOOP
 EXECUTE format('ALTER TABLE care.care_visit DROP CONSTRAINT %I', item.conname);
 END LOOP;
END $$;
ALTER TABLE care.care_visit ADD CONSTRAINT chk_visit_status CHECK(status IN ('PENDING_PAYMENT','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE care.care_visit ADD CONSTRAINT chk_visit_lifecycle CHECK (
 (status IN ('PENDING_PAYMENT','SCHEDULED','CANCELLED') AND started_at IS NULL AND completed_at IS NULL)
 OR (status='IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
 OR (status='COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at>=started_at));
CREATE TABLE care.reception_intake (
 episode_id UUID PRIMARY KEY REFERENCES care.care_episode(id),
 observations TEXT NOT NULL DEFAULT '',
 vitals JSONB NOT NULL DEFAULT '{}',
 recorded_by UUID NOT NULL REFERENCES identity.person(id),
 recorded_at TIMESTAMPTZ NOT NULL,
 retention_expires_at TIMESTAMPTZ NOT NULL,
 CHECK(retention_expires_at>recorded_at)
);
