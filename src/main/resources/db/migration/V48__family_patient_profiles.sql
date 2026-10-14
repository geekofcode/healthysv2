CREATE SCHEMA IF NOT EXISTS family;
CREATE TABLE family.guardianship (
 id UUID PRIMARY KEY,
 guardian_person_id UUID NOT NULL REFERENCES identity.person(id),
 patient_id UUID NOT NULL REFERENCES patient.patient(id),
 relationship VARCHAR(20) NOT NULL CHECK(relationship IN ('PARENT','GUARDIAN')),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REVOKED')),
 adulthood_at DATE NOT NULL,
 guardian_attested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 idempotency_key VARCHAR(128) NOT NULL,
 request_fingerprint VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),
 UNIQUE(guardian_person_id,patient_id),
 UNIQUE(guardian_person_id,idempotency_key)
);
CREATE INDEX family_guardianship_patient ON family.guardianship(patient_id,status);
COMMENT ON TABLE family.guardianship IS 'New child profiles only: self-attested relationship, not a claim over an existing patient.';
