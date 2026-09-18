-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS appointment;
CREATE SCHEMA IF NOT EXISTS registration;
CREATE SCHEMA IF NOT EXISTS consultation;

CREATE TABLE appointment.appointment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    professional_id UUID REFERENCES professional.professional(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    department_id UUID REFERENCES organization.department(id),
    type VARCHAR(50) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    scheduled_end TIMESTAMPTZ NOT NULL,
    reason TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_by UUID REFERENCES identity.person(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK(scheduled_end > scheduled_start)
);
CREATE INDEX idx_appointment_patient_date ON appointment.appointment(patient_id, scheduled_start);
CREATE INDEX idx_appointment_prof_date ON appointment.appointment(professional_id, scheduled_start);

CREATE TABLE appointment.appointment_participant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointment.appointment(id),
    person_id UUID NOT NULL REFERENCES identity.person(id),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INVITED',
    CONSTRAINT uq_appointment_participant UNIQUE(appointment_id, person_id)
);

CREATE TABLE appointment.appointment_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointment.appointment(id),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID REFERENCES identity.person(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason TEXT
);

CREATE TABLE appointment.waiting_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID REFERENCES appointment.appointment(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    queue_number VARCHAR(50) NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    called_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING'
);

CREATE TABLE registration.encounter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    appointment_id UUID REFERENCES appointment.appointment(id),
    type VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
);

CREATE TABLE registration.admission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL UNIQUE REFERENCES registration.encounter(id),
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    department_id UUID REFERENCES organization.department(id),
    admitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason TEXT,
    admitting_professional_id UUID REFERENCES professional.professional(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ADMITTED'
);

CREATE TABLE registration.bed_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id UUID NOT NULL REFERENCES registration.admission(id),
    bed_id UUID NOT NULL REFERENCES organization.bed(id),
    start_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    end_at TIMESTAMPTZ
);

CREATE TABLE registration.transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id UUID NOT NULL REFERENCES registration.admission(id),
    from_department_id UUID REFERENCES organization.department(id),
    to_department_id UUID REFERENCES organization.department(id),
    from_bed_id UUID REFERENCES organization.bed(id),
    to_bed_id UUID REFERENCES organization.bed(id),
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason TEXT
);

CREATE TABLE registration.discharge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_id UUID NOT NULL UNIQUE REFERENCES registration.admission(id),
    discharged_at TIMESTAMPTZ NOT NULL,
    discharged_by UUID REFERENCES professional.professional(id),
    discharge_type VARCHAR(50),
    summary TEXT,
    follow_up_instruction TEXT
);

CREATE TABLE consultation.consultation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    professional_id UUID NOT NULL REFERENCES professional.professional(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    appointment_id UUID REFERENCES appointment.appointment(id),
    encounter_id UUID REFERENCES registration.encounter(id),
    type VARCHAR(50) NOT NULL,
    reason TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_consultation_patient ON consultation.consultation(patient_id, started_at);

CREATE TABLE consultation.vital_sign (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    temperature NUMERIC(4,1),
    weight NUMERIC(7,2),
    height NUMERIC(6,2),
    bmi NUMERIC(5,2),
    systolic_pressure INTEGER,
    diastolic_pressure INTEGER,
    heart_rate INTEGER,
    respiratory_rate INTEGER,
    oxygen_saturation NUMERIC(5,2),
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    measured_by UUID REFERENCES professional.professional(id)
);

CREATE TABLE consultation.diagnosis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    diagnosis_catalog_id UUID REFERENCES catalog.diagnosis_catalog(id),
    diagnosis_type VARCHAR(50),
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    diagnosed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consultation.consultation_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    author_id UUID NOT NULL REFERENCES professional.professional(id),
    note_type VARCHAR(50),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consultation.clinical_observation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    author_id UUID REFERENCES professional.professional(id),
    type VARCHAR(100) NOT NULL,
    value TEXT,
    notes TEXT,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consultation.treatment_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    description TEXT NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE consultation.follow_up (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation.consultation(id),
    recommended_date DATE,
    instructions TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED'
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_appointment_org ON appointment.appointment(organization_id);
CREATE INDEX IF NOT EXISTS idx_appointment_department ON appointment.appointment(department_id);
CREATE INDEX IF NOT EXISTS idx_encounter_patient ON registration.encounter(patient_id);
CREATE INDEX IF NOT EXISTS idx_encounter_org ON registration.encounter(organization_id);
CREATE INDEX IF NOT EXISTS idx_admission_patient ON registration.admission(patient_id);
CREATE INDEX IF NOT EXISTS idx_admission_org ON registration.admission(organization_id);
CREATE INDEX IF NOT EXISTS idx_consultation_professional ON consultation.consultation(professional_id);
CREATE INDEX IF NOT EXISTS idx_consultation_org ON consultation.consultation(organization_id);
CREATE INDEX IF NOT EXISTS idx_vital_sign_consultation ON consultation.vital_sign(consultation_id);
CREATE INDEX IF NOT EXISTS idx_diagnosis_consultation ON consultation.diagnosis(consultation_id);
CREATE INDEX IF NOT EXISTS idx_consultation_note_consultation ON consultation.consultation_note(consultation_id);
