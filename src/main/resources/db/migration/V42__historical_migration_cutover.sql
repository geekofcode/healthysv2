-- Operator-driven import: Flyway never transforms live patient history automatically.
CREATE TABLE care.migration_run (
 id UUID PRIMARY KEY, source_fingerprint TEXT NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PLANNED','APPLIED','VERIFIED','ROLLED_BACK')),
 source_snapshot JSONB NOT NULL, report JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(), applied_at TIMESTAMPTZ,
 verified_at TIMESTAMPTZ, verified_by TEXT, evidence JSONB NOT NULL DEFAULT '{}'
);
CREATE TABLE care.migration_mapping (
 run_id UUID NOT NULL REFERENCES care.migration_run(id),
 source_table TEXT NOT NULL, source_id UUID NOT NULL,
 target_table TEXT NOT NULL, target_id UUID NOT NULL,
 target_fingerprint TEXT NOT NULL, source_payload JSONB NOT NULL,
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),
 PRIMARY KEY(source_table,source_id,target_table),
 UNIQUE(target_table,target_id)
);
CREATE INDEX migration_mapping_run_idx ON care.migration_mapping(run_id);
CREATE TABLE care.cutover_state (
 singleton BOOLEAN PRIMARY KEY DEFAULT true CHECK(singleton),
 mode VARCHAR(10) NOT NULL CHECK(mode IN ('LEGACY','PILOT','CARE_ONLY')),
 migration_run_id UUID REFERENCES care.migration_run(id),
 evidence JSONB NOT NULL DEFAULT '{}', approved_by TEXT, approved_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(mode = 'LEGACY' OR (migration_run_id IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
INSERT INTO care.cutover_state(singleton,mode) VALUES(true,'LEGACY');
COMMENT ON TABLE care.migration_mapping IS 'Historical provenance and optimistic rollback guards; not a new payment ledger.';
-- Imported aggregate reports must not publish notes/diagnoses that legacy visibility kept private.
ALTER TABLE care.visit_report ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE care.visit_report_revision ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT true;
