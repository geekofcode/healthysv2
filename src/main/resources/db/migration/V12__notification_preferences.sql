-- Notification centre, delivery preferences and query constraints.

ALTER TABLE notification.notification
    ADD COLUMN IF NOT EXISTS action_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

ALTER TABLE notification.notification
    ADD CONSTRAINT ck_notification_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    ADD CONSTRAINT ck_notification_expiration
        CHECK (expires_at IS NULL OR expires_at > created_at);

CREATE TABLE notification.notification_preference (
    person_id UUID PRIMARY KEY REFERENCES identity.person(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    push_enabled BOOLEAN NOT NULL DEFAULT false,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_preference_locale CHECK (locale IN ('en', 'fr')),
    CONSTRAINT ck_notification_quiet_hours_pair CHECK (
        (quiet_hours_start IS NULL AND quiet_hours_end IS NULL)
        OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL)
    )
);

CREATE INDEX idx_notification_created_at
    ON notification.notification(created_at DESC);
CREATE INDEX idx_notification_expires_at
    ON notification.notification(expires_at)
    WHERE expires_at IS NOT NULL;
CREATE INDEX idx_notification_recipient_unread
    ON notification.notification_recipient(person_id, read_at)
    WHERE read_at IS NULL;
