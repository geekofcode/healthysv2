-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS organization;
CREATE SCHEMA IF NOT EXISTS professional;

CREATE TABLE organization.organization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_number VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255),
    organization_type_id UUID REFERENCES catalog.organization_type(id),
    phone VARCHAR(50),
    email VARCHAR(255),
    website VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE organization.organization_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    address_id UUID NOT NULL REFERENCES shared.address(id),
    address_type VARCHAR(30) NOT NULL DEFAULT 'MAIN',
    is_primary BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_org_address UNIQUE(organization_id, address_id)
);

CREATE TABLE organization.department (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_department_code UNIQUE(organization_id, code),
    CONSTRAINT uq_department_org_id UNIQUE(organization_id, id)
);

CREATE TABLE organization.service (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id UUID NOT NULL REFERENCES organization.department(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_service_code UNIQUE(department_id, code),
    CONSTRAINT uq_service_department_id UNIQUE(department_id, id)
);

CREATE TABLE organization.room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    department_id UUID REFERENCES organization.department(id),
    room_number VARCHAR(50) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uq_room_number UNIQUE(organization_id, room_number)
);

CREATE TABLE organization.bed (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES organization.room(id),
    bed_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uq_bed_number UNIQUE(room_id, bed_number)
);

CREATE TABLE professional.professional (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL UNIQUE REFERENCES identity.person(id),
    professional_number VARCHAR(50) NOT NULL UNIQUE,
    professional_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE professional.professional_license (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    license_number VARCHAR(100) NOT NULL,
    issuing_authority VARCHAR(255),
    country_id UUID REFERENCES shared.country(id),
    issued_at DATE,
    expires_at DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_prof_license UNIQUE(license_number, issuing_authority)
);

CREATE TABLE professional.professional_speciality (
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    speciality_catalog_id UUID NOT NULL REFERENCES catalog.speciality_catalog(id),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (professional_id, speciality_catalog_id)
);

CREATE TABLE professional.professional_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    department_id UUID,
    service_id UUID,
    position VARCHAR(150),
    employee_number VARCHAR(100),
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_assignment_department
        FOREIGN KEY (organization_id, department_id)
        REFERENCES organization.department(organization_id, id),
    CONSTRAINT fk_assignment_service
        FOREIGN KEY (department_id, service_id)
        REFERENCES organization.service(department_id, id),
    CONSTRAINT chk_assignment_dates CHECK(end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_assignment_professional ON professional.professional_assignment(professional_id);
CREATE INDEX idx_assignment_org ON professional.professional_assignment(organization_id);

CREATE TABLE professional.professional_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES professional.professional_assignment(id),
    day_of_week SMALLINT NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL CHECK(slot_duration_minutes > 0),
    CHECK(end_time > start_time)
);

CREATE TABLE professional.professional_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES professional.professional_assignment(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    availability_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    CHECK(end_at > start_at)
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_org_address_org ON organization.organization_address(organization_id);
CREATE INDEX IF NOT EXISTS idx_department_org ON organization.department(organization_id);
CREATE INDEX IF NOT EXISTS idx_service_department ON organization.service(department_id);
CREATE INDEX IF NOT EXISTS idx_room_org ON organization.room(organization_id);
CREATE INDEX IF NOT EXISTS idx_room_department ON organization.room(department_id);
CREATE INDEX IF NOT EXISTS idx_bed_room ON organization.bed(room_id);
CREATE INDEX IF NOT EXISTS idx_prof_license_prof ON professional.professional_license(professional_id);
CREATE INDEX IF NOT EXISTS idx_prof_schedule_assignment ON professional.professional_schedule(assignment_id);
CREATE INDEX IF NOT EXISTS idx_prof_availability_assignment ON professional.professional_availability(assignment_id);
