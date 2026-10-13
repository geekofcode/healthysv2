ALTER TABLE care.care_visit ADD COLUMN slot_minutes INTEGER NOT NULL DEFAULT 30 CHECK(slot_minutes BETWEEN 1 AND 240);
ALTER TABLE care.care_visit DROP CONSTRAINT chk_visit_status;
ALTER TABLE care.care_visit DROP CONSTRAINT chk_visit_lifecycle;
ALTER TABLE care.care_visit ADD CONSTRAINT chk_visit_status CHECK(status IN ('PENDING_PAYMENT','AWAITING_SCHEDULING','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'));
ALTER TABLE care.care_visit ADD CONSTRAINT chk_visit_lifecycle CHECK (
 (status IN ('PENDING_PAYMENT','AWAITING_SCHEDULING','SCHEDULED','CANCELLED') AND started_at IS NULL AND completed_at IS NULL)
 OR (status='IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
 OR (status='COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL AND completed_at>=started_at));
CREATE TABLE care.visit_schedule_history (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 visit_id UUID NOT NULL REFERENCES care.care_visit(id),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 previous_professional_id UUID NOT NULL REFERENCES professional.professional(id),
 professional_id UUID NOT NULL REFERENCES professional.professional(id),
 previous_scheduled_at TIMESTAMPTZ NOT NULL,
 scheduled_at TIMESTAMPTZ NOT NULL,
 previous_status VARCHAR(30) NOT NULL,
 status VARCHAR(30) NOT NULL,
 actor_person_id UUID NOT NULL REFERENCES identity.person(id),
 reason VARCHAR(2000),
 changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_visit_schedule_history ON care.visit_schedule_history(visit_id,changed_at);
