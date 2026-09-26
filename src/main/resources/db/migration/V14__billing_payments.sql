ALTER TABLE billing.invoice
    ADD CONSTRAINT ck_invoice_amounts_non_negative
        CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0 AND amount_paid >= 0),
    ADD CONSTRAINT ck_invoice_paid_not_above_total CHECK (amount_paid <= total_amount),
    ADD CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','CANCELLED'));

ALTER TABLE billing.invoice_item
    ADD CONSTRAINT ck_invoice_item_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT ck_invoice_item_amounts_non_negative
        CHECK (unit_price >= 0 AND tax_amount >= 0 AND total_amount >= 0);

ALTER TABLE billing.payment
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_payment_status CHECK (status IN ('PENDING','COMPLETED','FAILED','CANCELLED')),
    ADD CONSTRAINT ck_payment_method CHECK (payment_method IN
        ('CASH','CARD','MOBILE_MONEY','INSURANCE','MUTUAL','THIRD_PARTY'));

ALTER TABLE billing.payment_transaction
    ADD CONSTRAINT ck_payment_transaction_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT ck_payment_transaction_status CHECK (status IN ('COMPLETED','FAILED'));

CREATE UNIQUE INDEX uq_payment_provider_reference
    ON billing.payment_transaction(provider, external_transaction_id)
    WHERE provider IS NOT NULL AND external_transaction_id IS NOT NULL;

CREATE INDEX idx_invoice_status_issued ON billing.invoice(status, issued_at DESC);
CREATE INDEX idx_payment_status_paid ON billing.payment(status, paid_at DESC);
