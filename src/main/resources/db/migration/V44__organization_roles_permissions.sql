CREATE SCHEMA organizationaccess;
CREATE TABLE organizationaccess.role (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL REFERENCES organization.organization(id),
 name VARCHAR(100) NOT NULL, description VARCHAR(500), version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),
 UNIQUE (id,organization_id));
CREATE UNIQUE INDEX organization_role_name ON organizationaccess.role(organization_id,lower(name));
CREATE TABLE organizationaccess.permission (
 role_id UUID NOT NULL REFERENCES organizationaccess.role(id) ON DELETE CASCADE,
 resource VARCHAR(60) NOT NULL, action VARCHAR(10) NOT NULL CHECK(action IN ('CREATE','READ','UPDATE','DELETE')),
 PRIMARY KEY(role_id,resource,action), retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
-- An explicit policy row distinguishes an intentionally empty grant from legacy defaults.
CREATE TABLE organizationaccess.member_policy (
 member_id UUID PRIMARY KEY REFERENCES licensing.member(id) ON DELETE CASCADE,
 organization_id UUID NOT NULL REFERENCES organization.organization(id),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
CREATE TABLE organizationaccess.member_role (
 member_id UUID NOT NULL REFERENCES organizationaccess.member_policy(member_id) ON DELETE CASCADE,
 organization_id UUID NOT NULL, role_id UUID NOT NULL,
 FOREIGN KEY(role_id,organization_id) REFERENCES organizationaccess.role(id,organization_id) ON DELETE CASCADE,
 PRIMARY KEY(member_id,role_id), retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
CREATE FUNCTION organizationaccess.check_member_tenant() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM licensing.member m WHERE m.id=NEW.member_id AND m.organization_id=NEW.organization_id) THEN
  RAISE EXCEPTION 'MEMBER_TENANT_MISMATCH' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER policy_member_tenant BEFORE INSERT OR UPDATE ON organizationaccess.member_policy FOR EACH ROW EXECUTE FUNCTION organizationaccess.check_member_tenant();
CREATE TRIGGER assigned_member_tenant BEFORE INSERT OR UPDATE ON organizationaccess.member_role FOR EACH ROW EXECUTE FUNCTION organizationaccess.check_member_tenant();
