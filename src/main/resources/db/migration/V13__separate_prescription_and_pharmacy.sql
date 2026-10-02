CREATE SCHEMA IF NOT EXISTS prescription;

ALTER TABLE pharmacy.prescription SET SCHEMA prescription;
ALTER TABLE pharmacy.prescription_item SET SCHEMA prescription;

ALTER TABLE prescription.prescription_item
    ADD COLUMN quantity_dispensed NUMERIC(12,3) NOT NULL DEFAULT 0;

UPDATE prescription.prescription_item item
SET quantity_dispensed = totals.quantity_dispensed
FROM (
    SELECT prescription_item_id, COALESCE(SUM(quantity_dispensed), 0) AS quantity_dispensed
    FROM pharmacy.dispense_item
    GROUP BY prescription_item_id
) totals
WHERE totals.prescription_item_id = item.id;

ALTER TABLE prescription.prescription_item
    ADD CONSTRAINT ck_prescription_item_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT ck_prescription_item_dispensed_range
        CHECK (quantity_dispensed >= 0 AND quantity_dispensed <= quantity);

CREATE INDEX IF NOT EXISTS idx_dispense_prescription
    ON pharmacy.dispense(prescription_id, dispensed_at DESC);
