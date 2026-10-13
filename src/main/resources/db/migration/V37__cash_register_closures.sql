CREATE TABLE billing.cash_closure (
    id UUID PRIMARY KEY,
    closure_number VARCHAR(50) NOT NULL UNIQUE,
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    cashier_person_id UUID NOT NULL REFERENCES identity.person(id),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by_person_id UUID NOT NULL REFERENCES identity.person(id),
    note VARCHAR(1000),
    amounts JSONB NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    fingerprint TEXT NOT NULL,
    retention_expires_at TIMESTAMPTZ NOT NULL,
    CHECK(period_start < period_end),
    CHECK(period_end <= created_at),
    UNIQUE(organization_id,idempotency_key)
);
CREATE INDEX idx_cash_closure_cashier_period ON billing.cash_closure(organization_id,cashier_person_id,period_start,period_end);
CREATE TABLE billing.cash_closure_receipt (
    closure_id UUID NOT NULL REFERENCES billing.cash_closure(id),
    cash_operation_id UUID PRIMARY KEY REFERENCES billing.cash_operation(id)
);
CREATE TRIGGER cash_closure_append_only BEFORE UPDATE OR DELETE ON billing.cash_closure
    FOR EACH ROW EXECUTE FUNCTION audit.reject_log_mutation();
CREATE TRIGGER cash_closure_receipt_append_only BEFORE UPDATE OR DELETE ON billing.cash_closure_receipt
    FOR EACH ROW EXECUTE FUNCTION audit.reject_log_mutation();

CREATE INDEX idx_cash_operation_cashier_time ON billing.cash_operation(cashier_person_id,occurred_at);
