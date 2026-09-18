-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS laboratory;
CREATE SCHEMA IF NOT EXISTS pharmacy;
CREATE SCHEMA IF NOT EXISTS maternal_child;
CREATE SCHEMA IF NOT EXISTS document;

CREATE TABLE document.document_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE document.document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_number VARCHAR(50) NOT NULL UNIQUE,
    owner_patient_id UUID REFERENCES patient.patient(id),
    category_id UUID REFERENCES document.document_category(id),
    file_name VARCHAR(500) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL UNIQUE,
    storage_provider VARCHAR(50) NOT NULL,
    mime_type VARCHAR(150),
    size_bytes BIGINT CHECK(size_bytes IS NULL OR size_bytes >= 0),
    checksum VARCHAR(255),
    uploaded_by UUID REFERENCES identity.person(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE document.document_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document.document(id),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_link_resource ON document.document_link(resource_type, resource_id);

CREATE TABLE document.document_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document.document(id),
    grantee_type VARCHAR(30) NOT NULL,
    grantee_id UUID NOT NULL,
    permission VARCHAR(30) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE laboratory.lab_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    consultation_id UUID REFERENCES consultation.consultation(id),
    ordering_professional_id UUID NOT NULL REFERENCES professional.professional(id),
    laboratory_organization_id UUID REFERENCES organization.organization(id),
    priority VARCHAR(30) NOT NULL DEFAULT 'ROUTINE',
    ordered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'ORDERED'
);

CREATE TABLE laboratory.lab_order_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id UUID NOT NULL REFERENCES laboratory.lab_order(id),
    lab_exam_catalog_id UUID NOT NULL REFERENCES catalog.laboratory_exam_catalog(id),
    instructions TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ORDERED'
);

CREATE TABLE laboratory.specimen (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_item_id UUID NOT NULL REFERENCES laboratory.lab_order_item(id),
    specimen_number VARCHAR(100) NOT NULL UNIQUE,
    specimen_type VARCHAR(100),
    collected_at TIMESTAMPTZ,
    collected_by UUID REFERENCES professional.professional(id),
    status VARCHAR(30) NOT NULL DEFAULT 'EXPECTED'
);

CREATE TABLE laboratory.lab_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_order_id UUID NOT NULL REFERENCES laboratory.lab_order(id),
    result_number VARCHAR(50) NOT NULL UNIQUE,
    performed_by UUID REFERENCES professional.professional(id),
    validated_by UUID REFERENCES professional.professional(id),
    performed_at TIMESTAMPTZ,
    validated_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    notes TEXT
);

CREATE TABLE laboratory.lab_result_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lab_result_id UUID NOT NULL REFERENCES laboratory.lab_result(id),
    lab_order_item_id UUID REFERENCES laboratory.lab_order_item(id),
    parameter_catalog_id UUID REFERENCES catalog.laboratory_parameter_catalog(id),
    parameter VARCHAR(255),
    value VARCHAR(500),
    unit VARCHAR(80),
    reference_min NUMERIC,
    reference_max NUMERIC,
    interpretation TEXT,
    abnormal_flag VARCHAR(30)
);

CREATE TABLE pharmacy.prescription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    consultation_id UUID REFERENCES consultation.consultation(id),
    prescriber_id UUID NOT NULL REFERENCES professional.professional(id),
    organization_id UUID REFERENCES organization.organization(id),
    prescribed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE pharmacy.prescription_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES pharmacy.prescription(id),
    medication_catalog_id UUID NOT NULL REFERENCES catalog.medication_catalog(id),
    dosage VARCHAR(100),
    frequency VARCHAR(100),
    route VARCHAR(100),
    duration VARCHAR(100),
    quantity NUMERIC(12,3),
    instructions TEXT
);

CREATE TABLE pharmacy.dispense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_number VARCHAR(50) NOT NULL UNIQUE,
    prescription_id UUID NOT NULL REFERENCES pharmacy.prescription(id),
    pharmacy_organization_id UUID NOT NULL REFERENCES organization.organization(id),
    pharmacist_id UUID REFERENCES professional.professional(id),
    dispensed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'
);

CREATE TABLE pharmacy.dispense_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispense_id UUID NOT NULL REFERENCES pharmacy.dispense(id),
    prescription_item_id UUID NOT NULL REFERENCES pharmacy.prescription_item(id),
    quantity_dispensed NUMERIC(12,3) NOT NULL,
    batch_number VARCHAR(100)
);

CREATE TABLE pharmacy.medication_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    medication_catalog_id UUID NOT NULL REFERENCES catalog.medication_catalog(id),
    batch_number VARCHAR(100),
    quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
    expiration_date DATE,
    CONSTRAINT uq_medication_stock UNIQUE(organization_id, medication_catalog_id, batch_number)
);

CREATE TABLE maternal_child.pregnancy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_number VARCHAR(50) NOT NULL UNIQUE,
    mother_patient_id UUID NOT NULL REFERENCES patient.patient(id),
    estimated_conception_date DATE,
    last_menstrual_period DATE,
    expected_delivery_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE maternal_child.prenatal_visit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_id UUID NOT NULL REFERENCES maternal_child.pregnancy(id),
    consultation_id UUID REFERENCES consultation.consultation(id),
    professional_id UUID REFERENCES professional.professional(id),
    visit_date TIMESTAMPTZ NOT NULL,
    gestational_age_weeks INTEGER,
    weight NUMERIC(7,2),
    systolic_pressure INTEGER,
    diastolic_pressure INTEGER,
    fetal_heart_rate INTEGER,
    notes TEXT
);

CREATE TABLE maternal_child.pregnancy_risk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_id UUID NOT NULL REFERENCES maternal_child.pregnancy(id),
    risk_type VARCHAR(150) NOT NULL,
    severity VARCHAR(30),
    identified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE maternal_child.delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_id UUID NOT NULL UNIQUE REFERENCES maternal_child.pregnancy(id),
    organization_id UUID REFERENCES organization.organization(id),
    delivery_date TIMESTAMPTZ NOT NULL,
    delivery_type VARCHAR(50),
    professional_id UUID REFERENCES professional.professional(id),
    complications TEXT,
    notes TEXT
);

CREATE TABLE maternal_child.newborn (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES maternal_child.delivery(id),
    child_patient_id UUID NOT NULL UNIQUE REFERENCES patient.patient(id),
    birth_order INTEGER NOT NULL DEFAULT 1,
    birth_weight NUMERIC(7,2),
    birth_height NUMERIC(6,2),
    head_circumference NUMERIC(6,2),
    apgar_1 SMALLINT CHECK(apgar_1 BETWEEN 0 AND 10),
    apgar_5 SMALLINT CHECK(apgar_5 BETWEEN 0 AND 10),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE maternal_child.postpartum_visit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pregnancy_id UUID NOT NULL REFERENCES maternal_child.pregnancy(id),
    mother_patient_id UUID NOT NULL REFERENCES patient.patient(id),
    professional_id UUID REFERENCES professional.professional(id),
    visit_date TIMESTAMPTZ NOT NULL,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'
);

CREATE TABLE maternal_child.child_health_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_patient_id UUID NOT NULL UNIQUE REFERENCES patient.patient(id),
    mother_patient_id UUID REFERENCES patient.patient(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE maternal_child.vaccination (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_patient_id UUID NOT NULL REFERENCES patient.patient(id),
    vaccine_catalog_id UUID NOT NULL REFERENCES catalog.vaccine_catalog(id),
    dose_number INTEGER,
    administered_at TIMESTAMPTZ,
    administered_by UUID REFERENCES professional.professional(id),
    organization_id UUID REFERENCES organization.organization(id),
    batch_number VARCHAR(100),
    next_due_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'PLANNED'
);

CREATE TABLE maternal_child.growth_measurement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    child_patient_id UUID NOT NULL REFERENCES patient.patient(id),
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    weight NUMERIC(7,2),
    height NUMERIC(6,2),
    head_circumference NUMERIC(6,2),
    bmi NUMERIC(5,2),
    measured_by UUID REFERENCES professional.professional(id)
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_document_owner ON document.document(owner_patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_patient ON laboratory.lab_order(patient_id);
CREATE INDEX IF NOT EXISTS idx_lab_order_consultation ON laboratory.lab_order(consultation_id);
CREATE INDEX IF NOT EXISTS idx_lab_result_order ON laboratory.lab_result(lab_order_id);
CREATE INDEX IF NOT EXISTS idx_prescription_patient ON pharmacy.prescription(patient_id);
CREATE INDEX IF NOT EXISTS idx_prescription_consultation ON pharmacy.prescription(consultation_id);
CREATE INDEX IF NOT EXISTS idx_pregnancy_mother ON maternal_child.pregnancy(mother_patient_id);
CREATE INDEX IF NOT EXISTS idx_prenatal_pregnancy ON maternal_child.prenatal_visit(pregnancy_id);
CREATE INDEX IF NOT EXISTS idx_vaccination_child ON maternal_child.vaccination(child_patient_id);
CREATE INDEX IF NOT EXISTS idx_growth_child ON maternal_child.growth_measurement(child_patient_id);
