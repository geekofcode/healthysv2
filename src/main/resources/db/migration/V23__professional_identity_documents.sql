ALTER TABLE professional.registration_request
 ADD COLUMN speciality_name varchar(255),
 ADD COLUMN identity_document_type varchar(30) CHECK (identity_document_type IN ('PASSPORT','NATIONAL_ID','DRIVING_LICENSE')),
 ADD COLUMN identity_expires_on date,
 ADD COLUMN identity_front bytea,
 ADD COLUMN identity_front_type varchar(100),
 ADD COLUMN identity_front_name varchar(255),
 ADD COLUMN identity_back bytea,
 ADD COLUMN identity_back_type varchar(100),
 ADD COLUMN identity_back_name varchar(255),
 ADD CONSTRAINT identity_front_size CHECK (identity_front IS NULL OR octet_length(identity_front) <= 5242880),
 ADD CONSTRAINT identity_back_size CHECK (identity_back IS NULL OR octet_length(identity_back) <= 5242880);
-- Existing approved records retain their status; any new approval requires the complete dossier.
