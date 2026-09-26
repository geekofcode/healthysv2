CREATE INDEX idx_audit_module_time ON audit.audit_log(module, occurred_at DESC);
CREATE INDEX idx_audit_organization_time ON audit.audit_log(organization_id, occurred_at DESC);
CREATE INDEX idx_audit_correlation ON audit.audit_log(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_data_access_actor_time ON audit.data_access_log(actor_person_id, occurred_at DESC);
CREATE INDEX idx_data_access_organization_time ON audit.data_access_log(organization_id, occurred_at DESC);
CREATE INDEX idx_data_access_resource ON audit.data_access_log(resource_type, resource_id);
CREATE INDEX idx_data_access_action_time ON audit.data_access_log(action, occurred_at DESC);
CREATE INDEX idx_data_access_correlation ON audit.data_access_log(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_security_event_open_time ON audit.security_event(severity, occurred_at DESC) WHERE resolved_at IS NULL;

CREATE OR REPLACE FUNCTION audit.reject_log_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit and data-access logs are append-only';
END;
$$;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION audit.reject_log_mutation();

CREATE TRIGGER data_access_log_append_only
    BEFORE UPDATE OR DELETE ON audit.data_access_log
    FOR EACH ROW EXECUTE FUNCTION audit.reject_log_mutation();
