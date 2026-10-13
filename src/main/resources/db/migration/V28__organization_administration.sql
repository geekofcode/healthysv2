ALTER TABLE organization.organization
    ADD COLUMN timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    ADD COLUMN administrator_person_id UUID REFERENCES identity.person(id);
CREATE INDEX idx_organization_administrator ON organization.organization(administrator_person_id);
-- Preserve active legacy personnel in quota accounting. Administrative identities
-- must be explicitly assigned by platform administration after the upgrade.
INSERT INTO licensing.member(organization_id,person_id,role,status)
SELECT DISTINCT a.organization_id,p.person_id,
       CASE WHEN p.professional_type IN ('DOCTOR','MEDECIN') THEN 'DOCTOR' ELSE 'NURSE' END,'ACTIVE'
FROM professional.professional_assignment a
JOIN professional.professional p ON p.id=a.professional_id
WHERE a.status='ACTIVE' AND p.status='ACTIVE'
  AND a.start_date<=current_date AND (a.end_date IS NULL OR a.end_date>=current_date)
  AND p.professional_type IN ('DOCTOR','MEDECIN','NURSE')
ON CONFLICT DO NOTHING;
-- Existing statuses remain readable; new writes are restricted to ACTIVE/INACTIVE by the domain.
