-- Cash activation is separate from the first clinical encounter's coverage clock.
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='care.care_episode'::regclass AND contype='c'
 LOOP EXECUTE format('ALTER TABLE care.care_episode DROP CONSTRAINT %I',c.conname); END LOOP;
END $$;
UPDATE care.care_episode SET status=CASE status WHEN 'AWAITING_PAYMENT' THEN 'DRAFT' WHEN 'READY' THEN 'ACTIVE' ELSE status END;
ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_status CHECK(status IN ('DRAFT','ACTIVE','CLOSED'));
ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_duration CHECK(follow_up_seconds>0);
ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_clock CHECK((started_at IS NULL AND expires_at IS NULL) OR (started_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at=started_at+follow_up_seconds*interval '1 second' AND started_at>=created_at));
ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_draft CHECK(status<>'DRAFT' OR started_at IS NULL);
ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_retention CHECK(retention_expires_at>created_at);
ALTER TABLE care.care_visit ADD COLUMN parent_visit_id UUID REFERENCES care.care_visit(id);
UPDATE care.care_visit v SET parent_visit_id=(SELECT i.id FROM care.care_visit i WHERE i.episode_id=v.episode_id AND NOT i.follow_up ORDER BY i.created_at,i.id LIMIT 1) WHERE v.follow_up;
ALTER TABLE care.care_visit ADD CONSTRAINT care_visit_parent CHECK(parent_visit_id IS NULL OR (follow_up AND parent_visit_id<>id));
ALTER TABLE care.physician_queue ADD COLUMN closed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE care.physician_queue ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE care.physician_queue ADD COLUMN closed_by UUID REFERENCES identity.person(id);
CREATE TABLE care.speciality_policy (
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 speciality_id UUID NOT NULL REFERENCES catalog.speciality_catalog(id),
 tariff_id UUID NOT NULL REFERENCES billing.care_tariff(id),
 follow_up_seconds BIGINT NOT NULL CHECK(follow_up_seconds>0),
 version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(organization_id,speciality_id)
);
ALTER TABLE care.care_episode ADD COLUMN tariff_id UUID REFERENCES billing.care_tariff(id);
ALTER TABLE care.care_episode ADD COLUMN tariff_price NUMERIC(14,2);
ALTER TABLE care.care_episode ADD COLUMN tariff_currency VARCHAR(3);
ALTER TABLE care.care_episode ADD COLUMN tariff_tax_rate NUMERIC(7,4);
CREATE TABLE care.reassignment_request (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 visit_id UUID NOT NULL REFERENCES care.care_visit(id),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 source_professional_id UUID NOT NULL REFERENCES professional.professional(id),
 source_date DATE NOT NULL,
 reason TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RESOLVED')),
 created_by UUID NOT NULL REFERENCES identity.person(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 resolved_at TIMESTAMPTZ,
 resolved_by UUID REFERENCES identity.person(id)
);
CREATE UNIQUE INDEX care_reassignment_pending ON care.reassignment_request(visit_id) WHERE status='PENDING';
CREATE FUNCTION care.check_visit_parent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.parent_visit_id IS NOT NULL AND NOT EXISTS (
   SELECT 1 FROM care.care_visit p WHERE p.id=NEW.parent_visit_id AND p.episode_id=NEW.episode_id AND NOT p.follow_up
 ) THEN RAISE EXCEPTION 'Follow-up parent must be the initial visit in the same episode'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER care_visit_parent_guard BEFORE INSERT OR UPDATE OF parent_visit_id,episode_id ON care.care_visit FOR EACH ROW EXECUTE FUNCTION care.check_visit_parent();

ALTER TABLE care.care_episode ADD CONSTRAINT care_episode_reason CHECK(length(trim(reason))>0);
CREATE TABLE care.closed_calendar_window (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 professional_id UUID NOT NULL REFERENCES professional.professional(id),
 starts_at TIMESTAMPTZ NOT NULL,
 ends_at TIMESTAMPTZ NOT NULL CHECK(ends_at>starts_at),
 reason TEXT NOT NULL,
 closed_by UUID NOT NULL REFERENCES identity.person(id),
 closed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX care_closed_calendar_window_lookup ON care.closed_calendar_window(organization_id,professional_id,starts_at,ends_at);

ALTER TABLE care.reassignment_request ADD COLUMN retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline();
ALTER TABLE care.closed_calendar_window ADD COLUMN retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline();
