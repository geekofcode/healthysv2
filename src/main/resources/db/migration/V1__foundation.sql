-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE shared.identifier_sequence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    year INTEGER NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_identifier_sequence UNIQUE(entity_type, prefix, year)
);

CREATE TABLE shared.country (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    iso2 VARCHAR(2) NOT NULL UNIQUE,
    iso3 VARCHAR(3) UNIQUE,
    name VARCHAR(150) NOT NULL
);

CREATE TABLE shared.language (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(10) NOT NULL UNIQUE,
    label VARCHAR(100) NOT NULL
);

CREATE TABLE shared.address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(150) NOT NULL,
    province VARCHAR(150),
    postal_code VARCHAR(30),
    country_id UUID REFERENCES shared.country(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.person (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_number VARCHAR(50) NOT NULL UNIQUE,
    keycloak_user_id UUID UNIQUE,
    first_name VARCHAR(120) NOT NULL,
    middle_name VARCHAR(120),
    last_name VARCHAR(120) NOT NULL,
    gender VARCHAR(30),
    birth_date DATE,
    preferred_language_id UUID REFERENCES shared.language(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_person_name ON identity.person(last_name, first_name);
CREATE INDEX idx_person_keycloak ON identity.person(keycloak_user_id);

CREATE TABLE identity.person_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    address_id UUID NOT NULL REFERENCES shared.address(id),
    address_type VARCHAR(30) NOT NULL DEFAULT 'HOME',
    is_primary BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_person_address UNIQUE(person_id, address_id)
);

CREATE TABLE identity.person_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    type VARCHAR(20) NOT NULL,
    value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    verified BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_person_contact_person ON identity.person_contact(person_id);

CREATE TABLE identity.emergency_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120),
    relationship VARCHAR(80),
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE catalog.diagnosis_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    system VARCHAR(30) NOT NULL DEFAULT 'ICD10',
    label VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.medication_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) UNIQUE,
    name VARCHAR(255) NOT NULL,
    generic_name VARCHAR(255),
    form VARCHAR(100),
    strength VARCHAR(100),
    atc_code VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.laboratory_exam_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    specimen_type VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.laboratory_parameter_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID REFERENCES catalog.laboratory_exam_catalog(id),
    code VARCHAR(80),
    name VARCHAR(255) NOT NULL,
    unit VARCHAR(80),
    reference_text VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.vaccine_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) UNIQUE,
    name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.speciality_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.procedure_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.insurance_company (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) UNIQUE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE catalog.organization_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    label VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_address_country ON shared.address(country_id);
CREATE INDEX IF NOT EXISTS idx_person_address_person ON identity.person_address(person_id);
CREATE INDEX IF NOT EXISTS idx_person_address_address ON identity.person_address(address_id);
CREATE INDEX IF NOT EXISTS idx_emergency_contact_person ON identity.emergency_contact(person_id);
CREATE INDEX IF NOT EXISTS idx_lab_parameter_exam ON catalog.laboratory_parameter_catalog(exam_id);
