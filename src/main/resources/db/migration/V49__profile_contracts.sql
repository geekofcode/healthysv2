CREATE SCHEMA profilecontract;
CREATE TABLE profilecontract.template (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), category VARCHAR(12) NOT NULL CHECK(category IN ('PATIENT','HOSPITAL','STAFF','ADMIN')),
 version INTEGER NOT NULL CHECK(version>0), terms_url TEXT NOT NULL, terms_version VARCHAR(100) NOT NULL, body_text TEXT NOT NULL,
 created_by UUID NOT NULL REFERENCES identity.person(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ,
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(), UNIQUE(category,version));
CREATE FUNCTION profilecontract.immutable_published_template() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF TG_OP='DELETE' AND current_setting('healthys.retention_purge',true)='on' AND OLD.retention_expires_at<=clock_timestamp() THEN RETURN OLD; END IF; IF OLD.published_at IS NOT NULL THEN RAISE EXCEPTION 'PUBLISHED_CONTRACT_TEMPLATE_IMMUTABLE' USING ERRCODE='23514'; END IF; IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW; END $$;
CREATE TRIGGER published_template_immutable BEFORE UPDATE OR DELETE ON profilecontract.template FOR EACH ROW EXECUTE FUNCTION profilecontract.immutable_published_template();
CREATE TABLE profilecontract.agreement (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), actor_person_id UUID NOT NULL REFERENCES identity.person(id), profile_id UUID NOT NULL,
 template_id UUID NOT NULL REFERENCES profilecontract.template(id), template_version INTEGER NOT NULL,
 category VARCHAR(12) NOT NULL, profile_type VARCHAR(40) NOT NULL, source_type VARCHAR(50) NOT NULL, source_id UUID,
 organization_id UUID REFERENCES organization.organization(id), profile_label TEXT NOT NULL, organization_name TEXT NOT NULL,
 patient_name TEXT NOT NULL, signer_name TEXT NOT NULL, signer_email VARCHAR(254) NOT NULL,
 terms_url TEXT NOT NULL, terms_version VARCHAR(100) NOT NULL, body_snapshot TEXT NOT NULL,
 accepted_terms BOOLEAN NOT NULL CHECK(accepted_terms), guardian_acknowledgement BOOLEAN NOT NULL,
 signature_png BYTEA NOT NULL, signature_sha256 CHAR(64) NOT NULL, snapshot_sha256 CHAR(64) NOT NULL,
 signed_at TIMESTAMPTZ NOT NULL DEFAULT now(), pdf_key TEXT, signature_key TEXT, pdf_sha256 CHAR(64),
 status VARCHAR(20) NOT NULL DEFAULT 'SIGNED' CHECK(status IN ('SIGNED','PDF_READY')),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(), UNIQUE(actor_person_id,profile_id,profile_type,template_id));
CREATE FUNCTION profilecontract.immutable_agreement_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND current_setting('healthys.retention_purge',true)='on' AND OLD.retention_expires_at<=clock_timestamp() THEN RETURN OLD; END IF;
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'SIGNED_CONTRACT_IMMUTABLE' USING ERRCODE='23514'; END IF;
 IF (to_jsonb(NEW)-ARRAY['pdf_key','signature_key','pdf_sha256','status']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['pdf_key','signature_key','pdf_sha256','status']) THEN
 RAISE EXCEPTION 'SIGNED_CONTRACT_IMMUTABLE' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER agreement_snapshot_immutable BEFORE UPDATE OR DELETE ON profilecontract.agreement FOR EACH ROW EXECUTE FUNCTION profilecontract.immutable_agreement_snapshot();
CREATE TABLE profilecontract.delivery_job (
 agreement_id UUID PRIMARY KEY REFERENCES profilecontract.agreement(id), phase VARCHAR(10) NOT NULL DEFAULT 'PDF' CHECK(phase IN ('PDF','EMAIL','DONE')),
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_token UUID, lease_until TIMESTAMPTZ,
 error_code VARCHAR(80), email_sent_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
CREATE INDEX contract_delivery_pending ON profilecontract.delivery_job(next_attempt_at) WHERE phase<>'DONE';
