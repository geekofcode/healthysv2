CREATE TABLE patient.access_request (
 id UUID PRIMARY KEY,
 patient_id UUID NOT NULL REFERENCES patient.patient(id),
 requester_person_id UUID NOT NULL REFERENCES identity.person(id),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 scope VARCHAR(40) NOT NULL CHECK(scope IN ('FULL_RECORD','MEDICAL_RECORD','DOCUMENT')),
 reason TEXT NOT NULL CHECK(length(trim(reason))>0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 CHECK(expires_at>created_at)
);
CREATE TABLE patient.access_grant (
 id UUID PRIMARY KEY,
 patient_id UUID NOT NULL REFERENCES patient.patient(id),
 person_id UUID NOT NULL REFERENCES identity.person(id),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 request_id UUID UNIQUE REFERENCES patient.access_request(id),
 scope VARCHAR(40) NOT NULL CHECK(scope IN ('FULL_RECORD','MEDICAL_RECORD','DOCUMENT')),
 source VARCHAR(30) NOT NULL CHECK(source IN ('PATIENT_ACCEPTED','EMERGENCY')),
 reason TEXT NOT NULL,
 granted_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 CHECK(expires_at>granted_at)
);
CREATE INDEX idx_patient_access_effective ON patient.access_grant(patient_id,person_id,organization_id,expires_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_patient_access_requests ON patient.access_request(patient_id,created_at DESC);
CREATE TABLE patient.access_revocation (
 patient_id UUID NOT NULL REFERENCES patient.patient(id),
 person_id UUID NOT NULL REFERENCES identity.person(id),
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 revoked_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(patient_id,person_id,organization_id)
);
