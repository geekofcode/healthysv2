CREATE SCHEMA patienthealth;
CREATE TABLE patienthealth.parameter (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),patient_id UUID REFERENCES patient.patient(id),code VARCHAR(80) NOT NULL UNIQUE,
 label VARCHAR(120) NOT NULL,value_type VARCHAR(10) NOT NULL CHECK(value_type IN ('NUMERIC','TEXT','BOOLEAN')),unit VARCHAR(30),loinc_code VARCHAR(30),active BOOLEAN NOT NULL DEFAULT true,
 created_by UUID REFERENCES identity.person(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
-- Initial core subset aligned with HL7 FHIR R4 Vital Signs (LOINC/UCUM). Not an exhaustive catalog or a FHIR API.
INSERT INTO patienthealth.parameter(code,label,value_type,unit,loinc_code) VALUES
 ('heartRate','Fréquence cardiaque','NUMERIC','/min','8867-4'),
 ('respiratoryRate','Fréquence respiratoire','NUMERIC','/min','9279-1'),
 ('temperature','Température corporelle','NUMERIC','Cel','8310-5'),
 ('height','Taille','NUMERIC','cm','8302-2'),
 ('headCircumference','Périmètre crânien','NUMERIC','cm','9843-4'),
 ('weight','Poids','NUMERIC','kg','29463-7'),
 ('bmi','Indice de masse corporelle','NUMERIC','kg/m2','39156-5'),
 ('oxygenSaturation','Saturation en oxygène','NUMERIC','%','2708-6'),
 ('systolicPressure','Pression artérielle systolique','NUMERIC','mm[Hg]','8480-6'),
 ('diastolicPressure','Pression artérielle diastolique','NUMERIC','mm[Hg]','8462-4');
-- Additional common local definitions: deliberately no unverified/invented LOINC mappings.
INSERT INTO patienthealth.parameter(code,label,value_type,unit) VALUES
 ('glucoseMgDl','Glycémie (mg/dL)','NUMERIC','mg/dL'),('glucoseMmolL','Glycémie (mmol/L)','NUMERIC','mmol/L'),
 ('painScore','Douleur (échelle 0 à 10)','NUMERIC','{score}'),('waistCircumference','Périmètre abdominal','NUMERIC','cm'),
 ('hipCircumference','Périmètre des hanches','NUMERIC','cm'),('peakFlow','Débit expiratoire de pointe','NUMERIC','L/min'),
 ('visionLeft','Acuité visuelle gauche','TEXT',NULL),('visionRight','Acuité visuelle droite','TEXT',NULL);
CREATE TABLE patienthealth.observation (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),patient_id UUID NOT NULL REFERENCES patient.patient(id),parameter_id UUID NOT NULL REFERENCES patienthealth.parameter(id),
 code VARCHAR(80) NOT NULL,label VARCHAR(120) NOT NULL,value_type VARCHAR(10) NOT NULL,unit VARCHAR(30),value_text VARCHAR(2000) NOT NULL,note TEXT,
 measured_at TIMESTAMPTZ NOT NULL,recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),actor_person_id UUID NOT NULL REFERENCES identity.person(id),
 self_reported BOOLEAN NOT NULL,organization_id UUID REFERENCES organization.organization(id),visit_id UUID REFERENCES care.care_visit(id),supersedes_id UUID,
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),UNIQUE(id,patient_id,parameter_id),
 FOREIGN KEY(supersedes_id,patient_id,parameter_id) REFERENCES patienthealth.observation(id,patient_id,parameter_id),
 CHECK((self_reported AND organization_id IS NULL AND visit_id IS NULL) OR (NOT self_reported AND organization_id IS NOT NULL AND visit_id IS NOT NULL)));
CREATE INDEX patient_observation_history ON patienthealth.observation(patient_id,measured_at DESC,id);
CREATE TABLE patienthealth.declaration (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),patient_id UUID NOT NULL REFERENCES patient.patient(id),actor_person_id UUID NOT NULL REFERENCES identity.person(id),
 kind VARCHAR(30) NOT NULL CHECK(kind IN ('BLOOD_GROUP','RHESUS','ALLERGY','CHRONIC_CONDITION','MEDICAL_HISTORY','SURGICAL_HISTORY','FAMILY_HISTORY','DISABILITY')),
 label VARCHAR(200) NOT NULL,value_text VARCHAR(2000) NOT NULL,detail TEXT,occurred_on DATE,recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),supersedes_id UUID,
 retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline(),UNIQUE(id,patient_id),
 FOREIGN KEY(supersedes_id,patient_id) REFERENCES patienthealth.declaration(id,patient_id));
CREATE INDEX patient_declaration_history ON patienthealth.declaration(patient_id,recorded_at DESC,id);
CREATE TABLE patienthealth.photo (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),patient_id UUID NOT NULL REFERENCES patient.patient(id),document_id UUID NOT NULL UNIQUE REFERENCES document.document(id),
 actor_person_id UUID NOT NULL REFERENCES identity.person(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),retention_expires_at TIMESTAMPTZ NOT NULL DEFAULT lifecycle.retention_deadline());
CREATE FUNCTION patienthealth.immutable_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF TG_OP='DELETE' AND current_setting('healthys.retention_purge',true)='on' AND OLD.retention_expires_at<=clock_timestamp() THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'PATIENT_HEALTH_HISTORY_IMMUTABLE' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_observation BEFORE UPDATE OR DELETE ON patienthealth.observation FOR EACH ROW EXECUTE FUNCTION patienthealth.immutable_history();
CREATE TRIGGER immutable_declaration BEFORE UPDATE OR DELETE ON patienthealth.declaration FOR EACH ROW EXECUTE FUNCTION patienthealth.immutable_history();
-- Preserve the available legacy intake snapshot and its original recording/retention timestamps.
-- Earlier values overwritten by the old intake model cannot be reconstructed or fabricated.
INSERT INTO patienthealth.observation(patient_id,parameter_id,code,label,value_type,unit,value_text,measured_at,recorded_at,actor_person_id,self_reported,organization_id,visit_id,retention_expires_at)
 SELECT e.patient_id,p.id,p.code,p.label,p.value_type,p.unit,j.value#>>'{}',i.recorded_at,i.recorded_at,i.recorded_by,false,e.organization_id,v.id,i.retention_expires_at
 FROM care.visit_intake i JOIN care.care_visit v ON v.id=i.visit_id JOIN care.care_episode e ON e.id=v.episode_id
 CROSS JOIN LATERAL jsonb_each(i.vitals) j
 JOIN patienthealth.parameter p ON p.code=CASE j.key WHEN 'temperatureC' THEN 'temperature' WHEN 'systolicBp' THEN 'systolicPressure' WHEN 'diastolicBp' THEN 'diastolicPressure' WHEN 'weightKg' THEN 'weight' WHEN 'heightCm' THEN 'height' ELSE j.key END
 WHERE i.recorded_by IS NOT NULL AND p.patient_id IS NULL AND jsonb_typeof(j.value)='number';
