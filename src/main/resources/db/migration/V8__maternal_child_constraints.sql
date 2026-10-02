ALTER TABLE maternal_child.prenatal_visit
    ADD CONSTRAINT ck_prenatal_gestational_age CHECK (gestational_age_weeks IS NULL OR gestational_age_weeks BETWEEN 0 AND 45),
    ADD CONSTRAINT ck_prenatal_weight CHECK (weight IS NULL OR weight > 0),
    ADD CONSTRAINT ck_prenatal_systolic CHECK (systolic_pressure IS NULL OR systolic_pressure BETWEEN 40 AND 300),
    ADD CONSTRAINT ck_prenatal_diastolic CHECK (diastolic_pressure IS NULL OR diastolic_pressure BETWEEN 20 AND 200),
    ADD CONSTRAINT ck_prenatal_fetal_heart_rate CHECK (fetal_heart_rate IS NULL OR fetal_heart_rate BETWEEN 50 AND 250);

ALTER TABLE maternal_child.newborn
    ADD CONSTRAINT uq_newborn_delivery_birth_order UNIQUE (delivery_id, birth_order),
    ADD CONSTRAINT ck_newborn_birth_order CHECK (birth_order > 0),
    ADD CONSTRAINT ck_newborn_weight CHECK (birth_weight IS NULL OR birth_weight > 0),
    ADD CONSTRAINT ck_newborn_height CHECK (birth_height IS NULL OR birth_height > 0),
    ADD CONSTRAINT ck_newborn_head CHECK (head_circumference IS NULL OR head_circumference > 0);

ALTER TABLE maternal_child.vaccination
    ADD CONSTRAINT ck_vaccination_dose CHECK (dose_number IS NULL OR dose_number > 0);

ALTER TABLE maternal_child.growth_measurement
    ADD CONSTRAINT ck_growth_values CHECK (weight IS NOT NULL OR height IS NOT NULL OR head_circumference IS NOT NULL),
    ADD CONSTRAINT ck_growth_weight CHECK (weight IS NULL OR weight > 0),
    ADD CONSTRAINT ck_growth_height CHECK (height IS NULL OR height > 0),
    ADD CONSTRAINT ck_growth_head CHECK (head_circumference IS NULL OR head_circumference > 0);

CREATE UNIQUE INDEX uq_vaccination_child_vaccine_dose
    ON maternal_child.vaccination(child_patient_id, vaccine_catalog_id, COALESCE(dose_number, 0));
CREATE INDEX idx_pregnancy_status ON maternal_child.pregnancy(status);
CREATE INDEX idx_pregnancy_risk ON maternal_child.pregnancy_risk(pregnancy_id, status);
CREATE INDEX idx_delivery_pregnancy ON maternal_child.delivery(pregnancy_id);
CREATE INDEX idx_newborn_delivery ON maternal_child.newborn(delivery_id);
CREATE INDEX idx_child_record_mother ON maternal_child.child_health_record(mother_patient_id);
