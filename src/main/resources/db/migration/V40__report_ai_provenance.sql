-- Assistance does not validate a report: provenance survives human editing and finalization.
ALTER TABLE care.visit_report ADD COLUMN ai_job_id uuid REFERENCES care.ai_job(id);
ALTER TABLE care.visit_report_revision ADD COLUMN ai_job_id uuid REFERENCES care.ai_job(id);
CREATE INDEX visit_report_ai_job_idx ON care.visit_report(ai_job_id) WHERE ai_job_id IS NOT NULL;
CREATE INDEX visit_report_revision_ai_job_idx ON care.visit_report_revision(ai_job_id) WHERE ai_job_id IS NOT NULL;
