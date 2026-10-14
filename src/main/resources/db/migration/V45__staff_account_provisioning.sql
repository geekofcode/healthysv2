-- Identity-only outbox. No password, clinical data or organization-wide role is stored here.
CREATE TABLE identity.staff_account_outbox (
 person_id UUID PRIMARY KEY REFERENCES identity.person(id),
 email VARCHAR(255) NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
   CHECK(status IN ('PENDING','PROCESSING','ACCOUNT_CREATED','DISPATCHED','FAILED','BLOCKED')),
 remote_user_id UUID,
 attempts INTEGER NOT NULL DEFAULT 0,
 last_error VARCHAR(60),
 lease_until TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline()
);
CREATE UNIQUE INDEX staff_account_outbox_email ON identity.staff_account_outbox(lower(email));
