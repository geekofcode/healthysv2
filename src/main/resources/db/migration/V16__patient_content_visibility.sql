-- Explicit publication: existing professional content remains private.
ALTER TABLE consultation.consultation_note ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE consultation.diagnosis ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE document.document ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_document_patient_visible ON document.document(owner_patient_id, uploaded_at DESC, id)
    WHERE patient_visible=true AND status='ACTIVE';
