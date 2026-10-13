-- Identifiers are platform-wide. NULL/blank issuer and letter case cannot create a duplicate.
-- Existing collisions must be reconciled explicitly; never merge medical records automatically.
CREATE UNIQUE INDEX uq_patient_identifier_normalized
    ON patient.patient_identifier (upper(trim(type)), upper(trim(value)), coalesce(upper(trim(issuer)), ''));
CREATE INDEX idx_person_identity_search ON identity.person (lower(last_name), lower(first_name), birth_date);
