ALTER TABLE identity.person_preferences
 ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'SYSTEM'
 CHECK(language IN ('EN','FR','SYSTEM'));
