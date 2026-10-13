CREATE TABLE care.visit_prescription (
    visit_id UUID PRIMARY KEY REFERENCES care.care_visit(id),
    version BIGINT NOT NULL DEFAULT 0,
    current_revision INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','VALIDATED')),
    items JSONB NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(items)='array'),
    correction_reason TEXT,
    validated_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    retention_expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE care.visit_prescription_revision (
    id UUID PRIMARY KEY,
    visit_id UUID NOT NULL REFERENCES care.visit_prescription(visit_id),
    revision INTEGER NOT NULL CHECK(revision>0),
    items JSONB NOT NULL CHECK(jsonb_typeof(items)='array'),
    correction_reason TEXT,
    validated_at TIMESTAMPTZ NOT NULL,
    validated_by UUID NOT NULL REFERENCES identity.person(id),
    previous_revision_id UUID REFERENCES care.visit_prescription_revision(id),
    retention_expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(visit_id,revision)
);
CREATE TRIGGER visit_prescription_revision_immutable BEFORE UPDATE ON care.visit_prescription_revision
    FOR EACH ROW EXECUTE FUNCTION care.reject_report_revision_update();
CREATE TABLE care.visit_document (
    document_id UUID PRIMARY KEY REFERENCES document.document(id),
    visit_id UUID NOT NULL REFERENCES care.care_visit(id),
    organization_id UUID NOT NULL REFERENCES organization.organization(id),
    document_type VARCHAR(20) NOT NULL CHECK(document_type IN ('REPORT','PRESCRIPTION')),
    report_revision_id UUID NOT NULL REFERENCES care.visit_report_revision(id),
    report_revision BIGINT NOT NULL CHECK(report_revision>0),
    prescription_revision_id UUID REFERENCES care.visit_prescription_revision(id),
    prescription_revision INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    retention_expires_at TIMESTAMPTZ NOT NULL,
    CHECK((document_type='REPORT' AND prescription_revision_id IS NULL AND prescription_revision=0)
       OR (document_type='PRESCRIPTION' AND prescription_revision_id IS NOT NULL AND prescription_revision>0)),
    UNIQUE(visit_id,document_type,report_revision,prescription_revision)
);
CREATE INDEX visit_document_organization ON care.visit_document(organization_id,visit_id);
CREATE TRIGGER visit_document_immutable BEFORE UPDATE ON care.visit_document
    FOR EACH ROW EXECUTE FUNCTION care.reject_report_revision_update();
