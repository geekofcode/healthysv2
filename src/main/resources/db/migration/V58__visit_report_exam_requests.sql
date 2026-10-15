-- Simple examination instructions belong to each report and its immutable revisions.
-- This is deliberately not an operational laboratory order workflow.
ALTER TABLE care.visit_report ADD COLUMN exam_requests JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE care.visit_report_revision ADD COLUMN exam_requests JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE care.visit_report ADD CONSTRAINT ck_visit_report_exams_array CHECK(jsonb_typeof(exam_requests)='array');
ALTER TABLE care.visit_report_revision ADD CONSTRAINT ck_visit_revision_exams_array CHECK(jsonb_typeof(exam_requests)='array');
