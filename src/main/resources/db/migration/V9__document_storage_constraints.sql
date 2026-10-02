ALTER TABLE document.document
    ADD CONSTRAINT ck_document_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    ADD CONSTRAINT ck_document_provider CHECK (storage_provider IN ('MINIO', 'S3'));

ALTER TABLE document.document_link
    ADD CONSTRAINT uq_document_link UNIQUE (document_id, resource_type, resource_id);

CREATE INDEX idx_document_uploaded_at ON document.document(uploaded_at DESC);
CREATE INDEX idx_document_category ON document.document(category_id);

INSERT INTO document.document_category(code, name) VALUES
    ('CONSULTATION', 'Consultation'),
    ('LAB_RESULT', 'Laboratory result'),
    ('PRESCRIPTION', 'Prescription'),
    ('MEDICAL_IMAGING', 'Medical imaging'),
    ('IDENTITY', 'Identity document'),
    ('INSURANCE', 'Insurance document'),
    ('OTHER', 'Other')
ON CONFLICT (code) DO NOTHING;
