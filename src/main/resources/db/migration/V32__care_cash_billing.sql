-- Care prices are chosen from an establishment catalogue and frozen on invoice lines.
CREATE TABLE billing.care_tariff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    code VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
    unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price > 0),
    tax_rate NUMERIC(7,4) NOT NULL CHECK(tax_rate BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(organization_id,code)
);
CREATE TABLE billing.care_invoice (
    episode_id UUID PRIMARY KEY REFERENCES care.care_episode(id),
    invoice_id UUID NOT NULL UNIQUE REFERENCES billing.invoice(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    idempotency_key VARCHAR(128) NOT NULL,
    fingerprint TEXT NOT NULL,
    UNIQUE(organization_id,idempotency_key)
);
CREATE TABLE billing.cash_operation (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    invoice_id UUID NOT NULL REFERENCES billing.invoice(id),
    payment_id UUID REFERENCES billing.payment(id),
    operation_type VARCHAR(10) NOT NULL CHECK(operation_type IN ('PAYMENT','REFUND')),
    amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(500),
    cashier_person_id UUID NOT NULL REFERENCES identity.person(id),
    idempotency_key VARCHAR(128) NOT NULL,
    fingerprint TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    retention_expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(invoice_id,idempotency_key),
    CHECK(operation_type <> 'REFUND' OR length(trim(reason)) > 0)
);
CREATE INDEX idx_cash_operation_invoice ON billing.cash_operation(invoice_id,occurred_at);
CREATE TRIGGER cash_operation_append_only BEFORE UPDATE OR DELETE ON billing.cash_operation
    FOR EACH ROW EXECUTE FUNCTION audit.reject_log_mutation();
