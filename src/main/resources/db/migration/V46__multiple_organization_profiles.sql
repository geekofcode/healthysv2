-- A membership is an access profile, scoped to a role and establishment.
-- A single account may hold several distinct profiles in the same establishment.
DROP INDEX licensing.uq_license_member_person;
DROP INDEX licensing.uq_license_member_email;
CREATE UNIQUE INDEX uq_license_member_person_role ON licensing.member(organization_id,person_id,role)
 WHERE person_id IS NOT NULL AND status<>'INACTIVE';
CREATE UNIQUE INDEX uq_license_member_email_role ON licensing.member(organization_id,lower(email),role)
 WHERE email IS NOT NULL AND status<>'INACTIVE';
