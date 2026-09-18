-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS patient;

CREATE TABLE patient.patient (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL UNIQUE REFERENCES identity.person(id),
    patient_number VARCHAR(50) NOT NULL UNIQUE,
    blood_group VARCHAR(10),
    rhesus VARCHAR(10),
    marital_status VARCHAR(30),
    occupation VARCHAR(150),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE patient.patient_identifier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    type VARCHAR(50) NOT NULL,
    value VARCHAR(150) NOT NULL,
    issuer VARCHAR(255),
    country_id UUID REFERENCES shared.country(id),
    expiration_date DATE,
    CONSTRAINT uq_patient_identifier UNIQUE(type, value, issuer)
);

CREATE TABLE patient.patient_insurance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    insurance_company_id UUID NOT NULL REFERENCES catalog.insurance_company(id),
    policy_number VARCHAR(100),
    member_number VARCHAR(100),
    start_date DATE,
    end_date DATE,
    is_primary BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE patient.allergy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    allergen VARCHAR(255) NOT NULL,
    allergy_type VARCHAR(80),
    reaction TEXT,
    severity VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID REFERENCES identity.person(id)
);

CREATE TABLE patient.chronic_disease (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    diagnosis_catalog_id UUID REFERENCES catalog.diagnosis_catalog(id),
    diagnosed_at DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT
);

CREATE TABLE patient.medical_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    condition VARCHAR(500) NOT NULL,
    diagnosed_at DATE,
    resolved_at DATE,
    notes TEXT
);

CREATE TABLE patient.surgical_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    procedure_name VARCHAR(500) NOT NULL,
    procedure_date DATE,
    organization_id UUID REFERENCES organization.organization(id),
    notes TEXT
);

CREATE TABLE patient.family_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    relationship VARCHAR(100),
    condition VARCHAR(500) NOT NULL,
    notes TEXT
);

CREATE TABLE patient.disability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    type VARCHAR(100) NOT NULL,
    description TEXT,
    start_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE patient.patient_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    author_person_id UUID REFERENCES identity.person(id),
    note_type VARCHAR(50),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patient.patient_flag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    flag_type VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    severity VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patient.patient_registration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    registration_number VARCHAR(100) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_patient_org_registration UNIQUE(organization_id, registration_number),
    CONSTRAINT uq_patient_org UNIQUE(patient_id, organization_id)
);

CREATE TABLE patient.care_relationship (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    relationship_type VARCHAR(50) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_date TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_care_relationship_patient ON patient.care_relationship(patient_id);
CREATE INDEX idx_care_relationship_professional ON patient.care_relationship(professional_id);

CREATE TABLE patient.consent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    grantee_person_id UUID REFERENCES identity.person(id),
    grantee_organization_id UUID REFERENCES organization.organization(id),
    scope VARCHAR(255) NOT NULL,
    purpose VARCHAR(255),
    reason TEXT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_consent_single_grantee CHECK (
        (grantee_person_id IS NOT NULL AND grantee_organization_id IS NULL)
        OR
        (grantee_person_id IS NULL AND grantee_organization_id IS NOT NULL)
    ),
    CONSTRAINT chk_consent_expiry CHECK(expires_at IS NULL OR expires_at >= granted_at),
    CONSTRAINT chk_consent_revocation CHECK(revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE TABLE patient.emergency_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL UNIQUE REFERENCES patient.patient(id),
    emergency_code VARCHAR(100) UNIQUE,
    blood_group_visible BOOLEAN NOT NULL DEFAULT true,
    allergies_visible BOOLEAN NOT NULL DEFAULT true,
    conditions_visible BOOLEAN NOT NULL DEFAULT true,
    medications_visible BOOLEAN NOT NULL DEFAULT true,
    emergency_contact_visible BOOLEAN NOT NULL DEFAULT true,
    active BOOLEAN NOT NULL DEFAULT true
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_patient_identifier_patient ON patient.patient_identifier(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_insurance_patient ON patient.patient_insurance(patient_id);
CREATE INDEX IF NOT EXISTS idx_allergy_patient ON patient.allergy(patient_id);
CREATE INDEX IF NOT EXISTS idx_chronic_disease_patient ON patient.chronic_disease(patient_id);
CREATE INDEX IF NOT EXISTS idx_medical_history_patient ON patient.medical_history(patient_id);
CREATE INDEX IF NOT EXISTS idx_surgical_history_patient ON patient.surgical_history(patient_id);
CREATE INDEX IF NOT EXISTS idx_family_history_patient ON patient.family_history(patient_id);
CREATE INDEX IF NOT EXISTS idx_disability_patient ON patient.disability(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_note_patient ON patient.patient_note(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_flag_patient ON patient.patient_flag(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_registration_patient ON patient.patient_registration(patient_id);
CREATE INDEX IF NOT EXISTS idx_patient_registration_org ON patient.patient_registration(organization_id);
CREATE INDEX IF NOT EXISTS idx_care_relationship_org ON patient.care_relationship(organization_id);
CREATE INDEX IF NOT EXISTS idx_consent_patient ON patient.consent(patient_id);
CREATE INDEX IF NOT EXISTS idx_consent_person ON patient.consent(grantee_person_id);
CREATE INDEX IF NOT EXISTS idx_consent_org ON patient.consent(grantee_organization_id);
