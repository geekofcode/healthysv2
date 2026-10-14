-- Platform access is granted locally, independently of realm roles and organization membership.
CREATE SCHEMA IF NOT EXISTS accessprofile;
CREATE TABLE accessprofile.platform_administrator (
 person_id UUID PRIMARY KEY REFERENCES identity.person(id),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','REVOKED')),
 grant_source VARCHAR(30) NOT NULL DEFAULT 'CONTROLLED_OPERATOR'
   CHECK(grant_source IN ('CONTROLLED_OPERATOR','CONFIGURED_BOOTSTRAP')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 revoked_at TIMESTAMPTZ,
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline()
);
