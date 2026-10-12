-- Durable audience permits administrator first-login delivery without enumerating Keycloak users.
CREATE TABLE notification.notification_audience (
    notification_id UUID PRIMARY KEY REFERENCES notification.notification(id) ON DELETE CASCADE,
    required_role VARCHAR(80) NOT NULL CHECK (required_role = 'ROLE_PLATFORM_ADMIN')
);
