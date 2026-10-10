CREATE TABLE professional.registration_request (
 id uuid PRIMARY KEY, keycloak_user_id uuid NOT NULL UNIQUE, person_id uuid NOT NULL UNIQUE REFERENCES identity.person(id),
 profession varchar(30) NOT NULL CHECK (profession IN ('medecin','nurse','laboratoire')), license_number varchar(100) NOT NULL, issuing_authority varchar(255) NOT NULL,
 country_id uuid NOT NULL REFERENCES shared.country(id), speciality_catalog_id uuid REFERENCES catalog.speciality_catalog(id),
 status varchar(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','SUSPENDED')),
 reason varchar(2000), professional_id uuid REFERENCES professional.professional(id),
 proof bytea, proof_type varchar(100), proof_name varchar(255),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (proof IS NULL OR octet_length(proof) <= 5242880)
);
CREATE TABLE professional.organization_invitation (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organization.organization(id), email varchar(254) NOT NULL,
 position varchar(150), token_hash varchar(64) NOT NULL UNIQUE, status varchar(20) NOT NULL DEFAULT 'PENDING',
 expires_at timestamptz NOT NULL, accepted_by uuid REFERENCES identity.person(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE professional.role_sync_outbox (
 subject_id uuid PRIMARY KEY, realm_role varchar(30) NOT NULL, enabled boolean NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'PENDING', attempts integer NOT NULL DEFAULT 0,
 last_error varchar(255), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX professional_registration_status_idx ON professional.registration_request(status);
