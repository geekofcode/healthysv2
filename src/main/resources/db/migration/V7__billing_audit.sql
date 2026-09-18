-- HEALTH'YS PostgreSQL / Flyway migration
-- UUID technical identifiers; Keycloak manages authentication separately.

CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE billing.invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES patient.patient(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    encounter_id UUID REFERENCES registration.encounter(id),
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.invoice_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES billing.invoice(id),
    item_type VARCHAR(50) NOT NULL,
    reference_id UUID,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL DEFAULT 1,
    unit_price NUMERIC(14,2) NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL
);

CREATE TABLE billing.payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_id UUID NOT NULL REFERENCES billing.invoice(id),
    amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    paid_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE billing.payment_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES billing.payment(id),
    provider VARCHAR(100),
    external_transaction_id VARCHAR(255),
    transaction_type VARCHAR(50) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payload JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit.audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_person_id UUID,
    organization_id UUID,
    module VARCHAR(80) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    action VARCHAR(80) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    correlation_id VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit.audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor_time ON audit.audit_log(actor_person_id, occurred_at);

CREATE TABLE audit.data_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_person_id UUID,
    patient_id UUID,
    organization_id UUID,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    action VARCHAR(50) NOT NULL,
    access_reason TEXT,
    access_context JSONB,
    ip_address INET,
    correlation_id VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_data_access_patient_time ON audit.data_access_log(patient_id, occurred_at);

CREATE TABLE audit.authentication_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id UUID,
    person_id UUID,
    event_type VARCHAR(80) NOT NULL,
    success BOOLEAN NOT NULL,
    ip_address INET,
    user_agent TEXT,
    details JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit.security_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_person_id UUID,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    description TEXT,
    details JSONB,
    ip_address INET,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

-- FK/query indexes
CREATE INDEX IF NOT EXISTS idx_invoice_patient ON billing.invoice(patient_id);
CREATE INDEX IF NOT EXISTS idx_invoice_org ON billing.invoice(organization_id);
CREATE INDEX IF NOT EXISTS idx_invoice_item_invoice ON billing.invoice_item(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_invoice ON billing.payment(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_transaction_payment ON billing.payment_transaction(payment_id);
CREATE INDEX IF NOT EXISTS idx_auth_log_person_time ON audit.authentication_log(person_id, occurred_at);
