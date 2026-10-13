CREATE TABLE care.visit_report (
    visit_id uuid PRIMARY KEY REFERENCES care.care_visit(id),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    current_revision integer NOT NULL DEFAULT 1 CHECK (current_revision > 0),
    status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','VALIDATED')),
    reason text NOT NULL DEFAULT '', observations text NOT NULL DEFAULT '',
    diagnosis text NOT NULL DEFAULT '', treatment text NOT NULL DEFAULT '', follow_up text NOT NULL DEFAULT '',
    correction_reason text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid NOT NULL REFERENCES identity.person(id),
    retention_expires_at timestamptz NOT NULL
);
CREATE TABLE care.visit_report_revision (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL REFERENCES care.care_visit(id),
    revision integer NOT NULL CHECK (revision > 0),
    reason text NOT NULL, observations text NOT NULL, diagnosis text NOT NULL,
    treatment text NOT NULL, follow_up text NOT NULL,
    validated_at timestamptz NOT NULL, validated_by uuid NOT NULL REFERENCES identity.person(id),
    correction_reason text, previous_revision_id uuid REFERENCES care.visit_report_revision(id),
    retention_expires_at timestamptz NOT NULL,
    UNIQUE (visit_id, revision)
);
CREATE INDEX visit_report_revision_visit_idx ON care.visit_report_revision(visit_id, revision);
COMMENT ON TABLE care.visit_report_revision IS 'Append-only validated clinical reports; corrections create a new revision.';

-- Validated clinical text is immutable even if a future caller accidentally issues an UPDATE.
-- Deletion is left available to the controlled retention and personal-data deletion workflows.
CREATE FUNCTION care.reject_report_revision_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Validated visit report revisions are immutable';
END;
$$;
CREATE TRIGGER immutable_visit_report_revision
BEFORE UPDATE ON care.visit_report_revision
FOR EACH ROW EXECUTE FUNCTION care.reject_report_revision_update();
