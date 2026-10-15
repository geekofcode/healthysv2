CREATE TABLE care.appointment_request (
 id UUID PRIMARY KEY,
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 patient_id UUID NOT NULL REFERENCES patient.patient(id),
 speciality_id UUID NOT NULL REFERENCES catalog.speciality_catalog(id),
 reason VARCHAR(4000) NOT NULL,
 preferred_at TIMESTAMPTZ NOT NULL,
 source VARCHAR(20) NOT NULL CHECK (source IN ('SELF','PHONE','RECEPTION')),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
 created_by UUID NOT NULL REFERENCES identity.person(id),
 created_at TIMESTAMPTZ NOT NULL,
 decided_by UUID REFERENCES identity.person(id),
 decided_at TIMESTAMPTZ,
 rejection_reason VARCHAR(2000),
 episode_id UUID REFERENCES care.care_episode(id),
 visit_id UUID REFERENCES care.care_visit(id),
 retention_expires_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT appointment_request_decision CHECK (
 (status='PENDING' AND decided_by IS NULL AND decided_at IS NULL AND episode_id IS NULL AND visit_id IS NULL AND rejection_reason IS NULL)
 OR (status='APPROVED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND episode_id IS NOT NULL AND visit_id IS NOT NULL AND rejection_reason IS NULL)
 OR (status='REJECTED' AND decided_by IS NOT NULL AND decided_at IS NOT NULL AND rejection_reason IS NOT NULL AND episode_id IS NULL AND visit_id IS NULL))
);
CREATE INDEX appointment_request_org_status ON care.appointment_request(organization_id,status,created_at);
CREATE INDEX appointment_request_patient ON care.appointment_request(patient_id,created_at);
