ALTER TABLE patient.care_relationship ALTER COLUMN organization_id DROP NOT NULL;
ALTER TABLE consultation.consultation ALTER COLUMN organization_id DROP NOT NULL;
