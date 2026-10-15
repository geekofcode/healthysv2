ALTER TABLE organizationaccess.role ADD COLUMN reserved BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE organizationaccess.role ADD COLUMN system_key VARCHAR(10) CHECK(system_key IN ('DOCTOR','NURSE'));
CREATE UNIQUE INDEX reserved_role_system_key ON organizationaccess.role(organization_id,system_key) WHERE system_key IS NOT NULL;
-- Keep user-created historical roles/assignments intact rather than silently elevating them.
UPDATE organizationaccess.role SET name=substring(name,1,65)||' (historique '||left(id::text,8)||')' WHERE lower(name) IN ('doctor','nurse');
CREATE FUNCTION organizationaccess.seed_reserved_roles(org UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE kind text; resource_key text; role_id uuid;
BEGIN
 FOREACH kind IN ARRAY ARRAY['DOCTOR','NURSE'] LOOP
  INSERT INTO organizationaccess.role(organization_id,name,description,reserved,system_key)
   VALUES(org,kind,'Rôle clinique réservé - droits configurables par l’établissement',true,kind)
   ON CONFLICT DO NOTHING;
  SELECT id INTO role_id FROM organizationaccess.role WHERE organization_id=org AND system_key=kind;
  IF kind='DOCTOR' THEN
   FOREACH resource_key IN ARRAY ARRAY['patients','clinicalrecords','episodes','intakes','visits','scheduling','queue','reports','prescriptions','documents','patientaccess','professionalavailability','ai','examrequests'] LOOP
    INSERT INTO organizationaccess.permission(role_id,resource,action) SELECT role_id,resource_key,x FROM unnest(ARRAY['CREATE','READ','UPDATE','DELETE']) x ON CONFLICT DO NOTHING;
   END LOOP;
  ELSE
   FOREACH resource_key IN ARRAY ARRAY['patients','clinicalrecords','episodes','intakes','visits','scheduling','queue','documents','invoices','patientaccess','professionalavailability','reports','prescriptions','ai'] LOOP
    INSERT INTO organizationaccess.permission(role_id,resource,action) SELECT role_id,resource_key,x FROM unnest(ARRAY['CREATE','READ','UPDATE','DELETE']) x ON CONFLICT DO NOTHING;
   END LOOP;
  END IF;
 END LOOP;
END $$;
SELECT organizationaccess.seed_reserved_roles(id) FROM organization.organization;
CREATE FUNCTION organizationaccess.seed_organization_roles() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM organizationaccess.seed_reserved_roles(NEW.id); RETURN NEW; END $$;
CREATE TRIGGER organization_reserved_roles AFTER INSERT ON organization.organization FOR EACH ROW EXECUTE FUNCTION organizationaccess.seed_organization_roles();
CREATE FUNCTION organizationaccess.protect_reserved_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN IF OLD.reserved THEN RAISE EXCEPTION 'RESERVED_ROLE_IMMUTABLE' USING ERRCODE='23514'; END IF; RETURN OLD; END IF;
 IF TG_OP='UPDATE' AND OLD.reserved AND (NEW.name<>OLD.name OR NEW.system_key IS DISTINCT FROM OLD.system_key OR NEW.reserved<>OLD.reserved OR NEW.organization_id<>OLD.organization_id) THEN RAISE EXCEPTION 'RESERVED_ROLE_IMMUTABLE' USING ERRCODE='23514'; END IF;
 IF lower(NEW.name) IN ('doctor','nurse') AND NOT NEW.reserved THEN RAISE EXCEPTION 'RESERVED_ROLE_NAME' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER protect_reserved_role BEFORE INSERT OR UPDATE OR DELETE ON organizationaccess.role FOR EACH ROW EXECUTE FUNCTION organizationaccess.protect_reserved_role();
CREATE TABLE identity.account_reset_outbox (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),person_id UUID NOT NULL REFERENCES identity.person(id),actor_person_id UUID NOT NULL REFERENCES identity.person(id),
 idempotency_key VARCHAR(128) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RUNNING','SENT','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0,lease_token UUID,lease_until TIMESTAMPTZ,error_code VARCHAR(80),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),UNIQUE(person_id,actor_person_id,idempotency_key));
