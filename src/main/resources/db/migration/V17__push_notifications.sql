CREATE TABLE notification.push_device (
 installation_id UUID PRIMARY KEY,
 person_id UUID NOT NULL REFERENCES identity.person(id),
 token TEXT,
 token_hash VARCHAR(64) UNIQUE,
 revocation_hash VARCHAR(64),
 platform VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID','IOS')),
 active BOOLEAN NOT NULL DEFAULT true,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((active AND token IS NOT NULL AND token_hash IS NOT NULL) OR NOT active)
);
CREATE INDEX idx_push_device_person ON notification.push_device(person_id) WHERE active;
CREATE TABLE notification.push_outbox (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 notification_id UUID NOT NULL REFERENCES notification.notification(id),
 installation_id UUID NOT NULL REFERENCES notification.push_device(installation_id),
 person_id UUID NOT NULL REFERENCES identity.person(id),
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 last_error VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(notification_id,installation_id)
);
CREATE INDEX idx_push_outbox_ready ON notification.push_outbox(next_attempt_at) WHERE status='PENDING';
