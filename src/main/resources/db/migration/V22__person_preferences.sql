-- Presentation preferences extend the documented business identity, never Keycloak attributes.
CREATE TABLE identity.person_preferences (
    person_id UUID PRIMARY KEY REFERENCES identity.person(id) ON DELETE CASCADE,
    theme VARCHAR(10) NOT NULL DEFAULT 'SYSTEM' CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    avatar_url VARCHAR(2048),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
